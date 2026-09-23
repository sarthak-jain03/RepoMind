package com.repomind.service;

import com.repomind.dto.ChatResponse;
import com.repomind.model.CodeChunk;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;
import java.util.stream.Collectors;


@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    private static final int TOP_K = 8; 

    private final VectorStoreService vectorStoreService;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String baseUrl;
    private final String chatModel;

    public RagService(VectorStoreService vectorStoreService,
                      WebClient webClient,
                      ObjectMapper objectMapper,
                      @Value("${app.fireworks.api-key}") String apiKey,
                      @Value("${app.fireworks.base-url}") String baseUrl,
                      @Value("${app.fireworks.chat-model}") String chatModel) {
        this.vectorStoreService = vectorStoreService;
        this.webClient = webClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.chatModel = chatModel;
    }

    
    public RagResult query(Long repoId, String userQuestion) {
        
        List<CodeChunk> relevantChunks = vectorStoreService.similaritySearch(repoId, userQuestion, TOP_K);

        if (relevantChunks.isEmpty()) {
            return new RagResult(
                    "I couldn't find any relevant code in this repository for your question. " +
                    "Make sure the repository is indexed and try rephrasing your question.",
                    Collections.emptyList()
            );
        }

        
        StringBuilder context = new StringBuilder();
        List<ChatResponse.SourceReference> sources = new ArrayList<>();

        for (int i = 0; i < relevantChunks.size(); i++) {
            CodeChunk chunk = relevantChunks.get(i);
            context.append("\n--- Source ").append(i + 1).append(": ")
                    .append(chunk.getFilePath())
                    .append(" (lines ").append(chunk.getStartLine())
                    .append("-").append(chunk.getEndLine()).append(") ---\n")
                    .append(chunk.getContent())
                    .append("\n");

            sources.add(ChatResponse.SourceReference.builder()
                    .filePath(chunk.getFilePath())
                    .startLine(chunk.getStartLine())
                    .endLine(chunk.getEndLine())
                    .snippet(chunk.getContent().length() > 200
                            ? chunk.getContent().substring(0, 200) + "..."
                            : chunk.getContent())
                    .build());
        }

        
        String systemPrompt = """
                You are RepoMind, an expert coding assistant. You have been given relevant code snippets
                from the user's GitHub repository to answer their question.

                IMPORTANT RULES:
                1. ONLY answer based on the provided code context. Do NOT make up code or functionality
                   that doesn't exist in the provided snippets.
                2. If the code context doesn't contain enough information to fully answer the question,
                   say so explicitly and explain what information is missing.
                3. When referencing code, mention the specific file path and line numbers.
                4. Provide clear, actionable explanations with code examples when relevant.
                5. Format your response using Markdown for better readability.
                6. If you identify potential bugs or improvements in the code, mention them.

                REPOSITORY CODE CONTEXT:
                """ + context.toString();

        
        String aiResponse = callChatCompletion(systemPrompt, userQuestion);

        return new RagResult(aiResponse, sources);
    }

    
    private String callChatCompletion(String systemPrompt, String userMessage) {
        try {
            List<Map<String, String>> messages = List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userMessage)
            );

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", chatModel);
            requestBody.put("messages", messages);
            requestBody.put("max_tokens", 2048);
            requestBody.put("temperature", 0.1); 
            requestBody.put("top_p", 0.9);

            String response = webClient.post()
                    .uri(baseUrl + "/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            JsonNode choices = root.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                return choices.get(0).get("message").get("content").asText();
            }

            return "I received an empty response. Please try again.";

        } catch (Exception e) {
            log.error("Failed to call Fireworks.ai chat completion: {}", e.getMessage(), e);
            return "I encountered an error while processing your request. Please try again later. Error: " + e.getMessage();
        }
    }

    
    public record RagResult(String response, List<ChatResponse.SourceReference> sources) {}
}

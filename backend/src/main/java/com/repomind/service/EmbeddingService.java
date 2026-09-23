package com.repomind.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;


@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String baseUrl;
    private final String embeddingModel;

    public EmbeddingService(WebClient webClient,
                            ObjectMapper objectMapper,
                            @Value("${app.fireworks.api-key}") String apiKey,
                            @Value("${app.fireworks.base-url}") String baseUrl,
                            @Value("${app.fireworks.embedding-model}") String embeddingModel) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.embeddingModel = embeddingModel;
    }

    
    public float[] generateEmbedding(String text) {
        List<float[]> embeddings = generateEmbeddings(List.of(text));
        return embeddings.isEmpty() ? null : embeddings.get(0);
    }

    
    public List<float[]> generateEmbeddings(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            
            List<String> truncatedTexts = texts.stream()
                    .map(t -> t.length() > 8000 ? t.substring(0, 8000) : t)
                    .toList();

            Map<String, Object> requestBody = Map.of(
                    "model", embeddingModel,
                    "input", truncatedTexts
            );

            String response = webClient.post()
                    .uri(baseUrl + "/embeddings")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            JsonNode dataArray = root.get("data");

            List<float[]> embeddings = new ArrayList<>();
            for (JsonNode item : dataArray) {
                JsonNode embeddingNode = item.get("embedding");
                float[] vector = new float[embeddingNode.size()];
                for (int i = 0; i < embeddingNode.size(); i++) {
                    vector[i] = (float) embeddingNode.get(i).asDouble();
                }
                embeddings.add(vector);
            }

            return embeddings;

        } catch (Exception e) {
            log.error("Failed to generate embeddings: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    
    public String vectorToString(float[] vector) {
        if (vector == null) return null;
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}

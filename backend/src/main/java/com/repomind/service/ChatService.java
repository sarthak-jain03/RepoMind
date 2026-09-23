package com.repomind.service;

import com.repomind.dto.ChatRequest;
import com.repomind.dto.ChatResponse;
import com.repomind.model.ChatMessage;
import com.repomind.model.Repository;
import com.repomind.model.User;
import com.repomind.repository.ChatMessageRepository;
import com.repomind.repository.RepoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final RagService ragService;
    private final ChatMessageRepository chatMessageRepository;
    private final RepoRepository repoRepository;

    public ChatService(RagService ragService,
                       ChatMessageRepository chatMessageRepository,
                       RepoRepository repoRepository) {
        this.ragService = ragService;
        this.chatMessageRepository = chatMessageRepository;
        this.repoRepository = repoRepository;
    }

    
    @Transactional
    public ChatResponse chat(ChatRequest request, User user) {
        Repository repo = repoRepository.findById(request.getRepoId())
                .orElseThrow(() -> new RuntimeException("Repository not found"));

        
        if (!repo.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Repository does not belong to user");
        }

        
        if (repo.getIndexStatus() != Repository.IndexStatus.INDEXED) {
            return ChatResponse.builder()
                    .message("This repository hasn't been indexed yet. Please index it first before asking questions.")
                    .role("ASSISTANT")
                    .sources(Collections.emptyList())
                    .timestamp(LocalDateTime.now())
                    .build();
        }

        
        ChatMessage userMessage = ChatMessage.builder()
                .repository(repo)
                .user(user)
                .role(ChatMessage.Role.USER)
                .content(request.getMessage())
                .build();
        chatMessageRepository.save(userMessage);

        
        RagService.RagResult result = ragService.query(request.getRepoId(), request.getMessage());

        
        String sourcesStr = result.sources().stream()
                .map(s -> s.getFilePath() + ":" + s.getStartLine() + "-" + s.getEndLine())
                .reduce((a, b) -> a + "," + b)
                .orElse("");

        ChatMessage assistantMessage = ChatMessage.builder()
                .repository(repo)
                .user(user)
                .role(ChatMessage.Role.ASSISTANT)
                .content(result.response())
                .sources(sourcesStr)
                .build();
        chatMessageRepository.save(assistantMessage);

        return ChatResponse.builder()
                .message(result.response())
                .role("ASSISTANT")
                .sources(result.sources())
                .timestamp(LocalDateTime.now())
                .build();
    }

    
    public List<ChatResponse> getChatHistory(Long repoId, User user) {
        List<ChatMessage> messages = chatMessageRepository
                .findByRepositoryIdAndUserIdOrderByCreatedAtAsc(repoId, user.getId());

        List<ChatResponse> responses = new ArrayList<>();
        for (ChatMessage msg : messages) {
            List<ChatResponse.SourceReference> sources = new ArrayList<>();
            if (msg.getSources() != null && !msg.getSources().isBlank()) {
                for (String src : msg.getSources().split(",")) {
                    String[] parts = src.split(":");
                    if (parts.length >= 2) {
                        String[] lines = parts[1].split("-");
                        sources.add(ChatResponse.SourceReference.builder()
                                .filePath(parts[0])
                                .startLine(Integer.parseInt(lines[0]))
                                .endLine(lines.length > 1 ? Integer.parseInt(lines[1]) : Integer.parseInt(lines[0]))
                                .build());
                    }
                }
            }

            responses.add(ChatResponse.builder()
                    .message(msg.getContent())
                    .role(msg.getRole().name())
                    .sources(sources)
                    .timestamp(msg.getCreatedAt())
                    .build());
        }

        return responses;
    }
}

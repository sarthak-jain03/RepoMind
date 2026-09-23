package com.repomind.controller;

import com.repomind.dto.ChatRequest;
import com.repomind.dto.ChatResponse;
import com.repomind.model.User;
import com.repomind.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    
    @PostMapping
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request,
                                             Authentication auth) {
        User user = (User) auth.getPrincipal();
        ChatResponse response = chatService.chat(request, user);
        return ResponseEntity.ok(response);
    }

    
    @GetMapping("/history/{repoId}")
    public ResponseEntity<List<ChatResponse>> getChatHistory(@PathVariable Long repoId,
                                                             Authentication auth) {
        User user = (User) auth.getPrincipal();
        List<ChatResponse> history = chatService.getChatHistory(repoId, user);
        return ResponseEntity.ok(history);
    }
}

package com.wallo.chat.controller;

import com.wallo.auth.CurrentUserProvider;
import com.wallo.chat.dto.ChatRequest;
import com.wallo.chat.dto.ChatResponse;
import com.wallo.chat.service.ChatService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatService chatService;
    private final CurrentUserProvider currentUserProvider;

    public ChatController(
            ChatService chatService,
            CurrentUserProvider currentUserProvider
    ) {
        this.chatService = chatService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8"
    )
    public ResponseEntity<ChatResponse> chat(
            @RequestBody ChatRequest request
    ) {
        return ResponseEntity.ok(
                chatService.chat(request, currentUserProvider.getCurrentUserId())
        );
    }
}

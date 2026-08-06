package com.wallo.chat.controller;

import com.wallo.auth.CurrentUserProvider;
import com.wallo.chat.dto.ConversationResponse;
import com.wallo.chat.dto.CreateConversationRequest;
import com.wallo.chat.dto.ChatMessageResponse;
import com.wallo.chat.dto.SendConversationMessageRequest;
import com.wallo.chat.dto.SendConversationMessageResponse;
import com.wallo.chat.dto.UpdateConversationTitleRequest;
import com.wallo.chat.service.ConversationMessageService;
import com.wallo.chat.service.ConversationService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;
    private final ConversationMessageService conversationMessageService;
    private final CurrentUserProvider currentUserProvider;

    public ConversationController(
            ConversationService conversationService,
            ConversationMessageService conversationMessageService,
            CurrentUserProvider currentUserProvider
    ) {
        this.conversationService = conversationService;
        this.conversationMessageService = conversationMessageService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public ResponseEntity<List<ConversationResponse>> getConversations(
            @RequestParam Long userId
    ) {
        return ResponseEntity.ok(conversationService.getConversations(userId));
    }

    @PostMapping
    public ResponseEntity<ConversationResponse> createConversation(
            @RequestBody CreateConversationRequest request
    ) {
        ConversationResponse response = conversationService.createConversation(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<List<ChatMessageResponse>> getMessages(
            @org.springframework.web.bind.annotation.PathVariable Long conversationId,
            @RequestParam Long userId
    ) {
        return ResponseEntity.ok(
                conversationMessageService.getMessages(conversationId, userId)
        );
    }

    @PostMapping("/{conversationId}/messages")
    public ResponseEntity<SendConversationMessageResponse> sendMessage(
            @PathVariable Long conversationId,
            @RequestBody SendConversationMessageRequest request
    ) {
        return ResponseEntity.ok(
                conversationMessageService.sendMessage(
                        conversationId,
                        currentUserProvider.getCurrentUserId(),
                        request
                )
        );
    }

    @PatchMapping("/{conversationId}")
    public ResponseEntity<ConversationResponse> updateTitle(
            @PathVariable Long conversationId,
            @RequestBody UpdateConversationTitleRequest request
    ) {
        return ResponseEntity.ok(conversationService.updateTitle(conversationId, request));
    }

    @DeleteMapping("/{conversationId}")
    public ResponseEntity<Void> deleteConversation(
            @PathVariable Long conversationId,
            @RequestParam Long userId
    ) {
        conversationService.deleteConversation(conversationId, userId);
        return ResponseEntity.noContent().build();
    }
}

package com.wallo.chat.service;

import com.wallo.chat.domain.Conversation;
import com.wallo.chat.dto.ConversationResponse;
import com.wallo.chat.dto.CreateConversationRequest;
import com.wallo.chat.mapper.ConversationMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationService {

    private static final String DEFAULT_TITLE = "새 채팅";
    private static final String ACTIVE_STATUS = "ACTIVE";

    private final ConversationMapper conversationMapper;

    public ConversationService(ConversationMapper conversationMapper) {
        this.conversationMapper = conversationMapper;
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> getConversations(Long userId) {
        validateUserId(userId);
        return conversationMapper.findAllByUserId(userId)
                .stream()
                .map(ConversationResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public ConversationResponse createConversation(CreateConversationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("채팅방 생성 요청이 필요합니다.");
        }
        validateUserId(request.getUserId());

        LocalDateTime now = LocalDateTime.now();
        Conversation conversation = new Conversation();
        conversation.setUserId(request.getUserId());
        conversation.setTitle(normalizeTitle(request.getTitle()));
        conversation.setStatus(ACTIVE_STATUS);
        conversation.setCreatedAt(now);
        conversation.setUpdatedAt(now);

        conversationMapper.insert(conversation);
        return ConversationResponse.from(conversation);
    }

    @Transactional(readOnly = true)
    public void validateOwnership(Long conversationId, Long userId) {
        validateUserId(userId);
        if (conversationId == null || conversationId < 1) {
            throw new IllegalArgumentException("올바른 채팅방 ID가 필요합니다.");
        }
        if (conversationMapper.findByIdAndUserId(conversationId, userId) == null) {
            throw new IllegalArgumentException("접근할 수 없는 채팅방입니다.");
        }
    }

    @Transactional
    public void updateAfterUserMessage(Long conversationId, String message) {
        conversationMapper.updateAfterUserMessage(
                conversationId,
                createTitle(message)
        );
    }

    @Transactional
    public void touch(Long conversationId) {
        conversationMapper.touch(conversationId);
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId < 1) {
            throw new IllegalArgumentException("올바른 사용자 ID가 필요합니다.");
        }
    }

    private String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            return DEFAULT_TITLE;
        }
        String trimmedTitle = title.trim();
        return trimmedTitle.length() > 100
                ? trimmedTitle.substring(0, 100)
                : trimmedTitle;
    }

    private String createTitle(String message) {
        String trimmedMessage = message.trim();
        return trimmedMessage.length() > 30
                ? trimmedMessage.substring(0, 30) + "..."
                : trimmedMessage;
    }
}

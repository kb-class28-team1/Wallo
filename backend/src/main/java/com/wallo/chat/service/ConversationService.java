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
}

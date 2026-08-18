package com.wallo.chat.service;

import com.wallo.chat.domain.Conversation;
import com.wallo.chat.dto.ConversationResponse;
import com.wallo.chat.dto.CreateConversationRequest;
import com.wallo.chat.dto.UpdateConversationTitleRequest;
import com.wallo.chat.mapper.ConversationMapper;
import com.wallo.goal.mapper.GoalMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationService {

    private static final String DEFAULT_TITLE = "새 채팅";
    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final String GOAL_CONVERSATION_DELETE_MESSAGE =
            "목표 설정이 완료된 채팅은 계좌 변경에 필요하므로 삭제할 수 없습니다.";

    private final ConversationMapper conversationMapper;
    private final GoalMapper goalMapper;

    public ConversationService(ConversationMapper conversationMapper, GoalMapper goalMapper) {
        this.conversationMapper = conversationMapper;
        this.goalMapper = goalMapper;
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

    @Transactional(readOnly = true)
    public Conversation getConversationMemory(Long conversationId, Long userId) {
        validateUserId(userId);
        if (conversationId == null || conversationId < 1) {
            throw new IllegalArgumentException("올바른 채팅방 ID가 필요합니다.");
        }
        Conversation conversation = conversationMapper.findByIdAndUserId(conversationId, userId);
        if (conversation == null) {
            throw new IllegalArgumentException("접근할 수 없는 채팅방입니다.");
        }
        return conversation;
    }

    @Transactional
    public void updateSummary(Long conversationId, String summary, Long summarizedMessageId) {
        if (summary == null || summary.isBlank() || summarizedMessageId == null) {
            throw new IllegalArgumentException("채팅방 요약 정보가 올바르지 않습니다.");
        }
        conversationMapper.updateSummary(conversationId, summary.trim(), summarizedMessageId);
    }

    @Transactional
    public ConversationResponse updateTitle(
            Long conversationId,
            UpdateConversationTitleRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException("채팅방 제목 변경 요청이 필요합니다.");
        }
        validateOwnership(conversationId, request.getUserId());
        String title = requireTitle(request.getTitle());
        if (conversationMapper.updateTitle(conversationId, request.getUserId(), title) != 1) {
            throw new IllegalArgumentException("채팅방 제목을 변경할 수 없습니다.");
        }
        return ConversationResponse.from(
                conversationMapper.findByIdAndUserId(conversationId, request.getUserId())
        );
    }

    @Transactional
    public void deleteConversation(Long conversationId, Long userId) {
        validateOwnership(conversationId, userId);
        if (goalMapper.findGoalByConversationId(userId, conversationId) != null) {
            throw new IllegalArgumentException(GOAL_CONVERSATION_DELETE_MESSAGE);
        }
        if (conversationMapper.softDelete(conversationId, userId) != 1) {
            throw new IllegalArgumentException("채팅방을 삭제할 수 없습니다.");
        }
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

    private String requireTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("채팅방 제목을 입력해 주세요.");
        }
        return normalizeTitle(title);
    }

    private String createTitle(String message) {
        String trimmedMessage = message.trim();
        return trimmedMessage.length() > 30
                ? trimmedMessage.substring(0, 30) + "..."
                : trimmedMessage;
    }
}

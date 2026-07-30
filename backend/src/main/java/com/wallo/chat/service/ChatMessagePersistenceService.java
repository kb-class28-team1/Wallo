package com.wallo.chat.service;

import com.wallo.chat.domain.ChatMessage;
import com.wallo.chat.mapper.ChatMessageMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatMessagePersistenceService {

    private final ChatMessageMapper chatMessageMapper;

    public ChatMessagePersistenceService(ChatMessageMapper chatMessageMapper) {
        this.chatMessageMapper = chatMessageMapper;
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> getMessages(Long conversationId) {
        return chatMessageMapper.findAllByConversationId(conversationId);
    }

    @Transactional
    public ChatMessage saveMessage(
            Long conversationId,
            String role,
            String content
    ) {
        ChatMessage message = new ChatMessage();
        message.setConversationId(conversationId);
        message.setRole(role);
        message.setContent(content);
        message.setCreatedAt(LocalDateTime.now());
        chatMessageMapper.insert(message);
        return message;
    }
}

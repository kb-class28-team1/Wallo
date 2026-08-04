package com.wallo.chat.mapper;

import com.wallo.chat.domain.ChatMessage;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ChatMessageMapper {

    List<ChatMessage> findAllByConversationId(
            @Param("conversationId") Long conversationId
    );

    int countByConversationId(@Param("conversationId") Long conversationId);

    int insert(ChatMessage message);
}

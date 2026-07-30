package com.wallo.chat.mapper;

import com.wallo.chat.domain.Conversation;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ConversationMapper {

    List<Conversation> findAllByUserId(@Param("userId") Long userId);

    Conversation findByIdAndUserId(
            @Param("conversationId") Long conversationId,
            @Param("userId") Long userId
    );

    int insert(Conversation conversation);
}

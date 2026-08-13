package com.wallo.feed.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallo.auth.JwtCurrentUserProvider;
import com.wallo.feed.dto.FeedDtos.MessageRequest;
import com.wallo.feed.service.FeedService;
import java.io.IOException;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** /ws/challenges/{challengeId} native WebSocket 채팅 endpoint. */
@Component
public class ChallengeChatWebSocketHandler extends TextWebSocketHandler {
    private static final Pattern CHALLENGE_PATH =
            Pattern.compile(".*/ws/challenges/(\\d+)(?:/)?$");
    private static final String CHALLENGE_ID_ATTRIBUTE =
            ChallengeChatWebSocketHandler.class.getName() + ".challengeId";
    private static final String USER_ID_ATTRIBUTE =
            ChallengeChatWebSocketHandler.class.getName() + ".userId";

    private final FeedService feedService;
    private final ChallengeChatBroadcaster broadcaster;
    private final ObjectMapper objectMapper;

    public ChallengeChatWebSocketHandler(
            FeedService feedService,
            ChallengeChatBroadcaster broadcaster,
            ObjectMapper objectMapper) {
        this.feedService = feedService;
        this.broadcaster = broadcaster;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long challengeId = parseChallengeId(session.getUri());
        Long userId = asLong(session.getAttributes().get(JwtCurrentUserProvider.USER_ID_ATTRIBUTE));

        if (challengeId == null || userId == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        try {
            // REST와 동일하게 로그인 사용자와 챌린지 참여 여부를 연결 시점에 검증한다.
            feedService.getMessages(userId, challengeId);
        } catch (RuntimeException exception) {
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        session.getAttributes().put(CHALLENGE_ID_ATTRIBUTE, challengeId);
        session.getAttributes().put(USER_ID_ATTRIBUTE, userId);
        broadcaster.register(challengeId, session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Long challengeId = asLong(session.getAttributes().get(CHALLENGE_ID_ATTRIBUTE));
        Long userId = asLong(session.getAttributes().get(USER_ID_ATTRIBUTE));
        if (challengeId == null || userId == null) return;

        try {
            MessageRequest request = objectMapper.readValue(message.getPayload(), MessageRequest.class);
            feedService.sendMessage(userId, challengeId, request);
        } catch (Exception exception) {
            sendError(session, exception.getMessage() == null
                    ? "메시지를 보내지 못했습니다." : exception.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        unregister(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        unregister(session);
    }

    private void sendError(WebSocketSession session, String message) {
        if (!session.isOpen()) return;
        try {
            session.sendMessage(new TextMessage(
                    objectMapper.writeValueAsString(new ErrorEvent("ERROR", message))));
        } catch (IOException ignored) {
            unregister(session);
        }
    }

    private void unregister(WebSocketSession session) {
        Long challengeId = asLong(session.getAttributes().get(CHALLENGE_ID_ATTRIBUTE));
        if (challengeId != null) broadcaster.unregister(challengeId, session.getId());
    }

    private Long parseChallengeId(URI uri) {
        if (uri == null) return null;
        Matcher matcher = CHALLENGE_PATH.matcher(uri.getPath());
        if (!matcher.matches()) return null;
        try {
            return Long.valueOf(matcher.group(1));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null) return null;
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private record ErrorEvent(String type, String message) {}
}

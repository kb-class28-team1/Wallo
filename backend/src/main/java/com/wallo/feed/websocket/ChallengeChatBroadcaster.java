package com.wallo.feed.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallo.feed.domain.FeedMessage;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

/** 같은 챌린지에 접속한 WebSocket 사용자에게 새 채팅 메시지를 전달한다. */
@Component
public class ChallengeChatBroadcaster {
    private final ObjectMapper objectMapper;
    private final Map<Long, Map<String, WebSocketSession>> sessionsByChallenge =
            new ConcurrentHashMap<>();

    public ChallengeChatBroadcaster(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void register(Long challengeId, WebSocketSession session) {
        WebSocketSession safeSession = new ConcurrentWebSocketSessionDecorator(
                session, 10_000, 64 * 1024);
        sessionsByChallenge
                .computeIfAbsent(challengeId, ignored -> new ConcurrentHashMap<>())
                .put(session.getId(), safeSession);
    }

    public void unregister(Long challengeId, String sessionId) {
        Map<String, WebSocketSession> sessions = sessionsByChallenge.get(challengeId);
        if (sessions == null) return;
        sessions.remove(sessionId);
        if (sessions.isEmpty()) sessionsByChallenge.remove(challengeId, sessions);
    }

    /** DB 트랜잭션이 성공적으로 커밋된 뒤에만 다른 사용자에게 메시지를 전송한다. */
    public void broadcastAfterCommit(Long challengeId, FeedMessage message) {
        if (message == null) return;

        Runnable broadcast = () -> broadcast(challengeId, message);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    broadcast.run();
                }
            });
            return;
        }
        broadcast.run();
    }

    private void broadcast(Long challengeId, FeedMessage message) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(new MessageEvent("MESSAGE", message));
        } catch (JsonProcessingException exception) {
            return;
        }

        Map<String, WebSocketSession> sessions = sessionsByChallenge.get(challengeId);
        if (sessions == null) return;

        TextMessage textMessage = new TextMessage(payload);
        sessions.forEach((sessionId, session) -> {
            if (!session.isOpen()) {
                unregister(challengeId, sessionId);
                return;
            }
            try {
                session.sendMessage(textMessage);
            } catch (IOException exception) {
                unregister(challengeId, sessionId);
            }
        });
    }

    private record MessageEvent(String type, FeedMessage message) {}
}

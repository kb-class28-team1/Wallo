package com.wallo.chat.dto;

public record ChatRequest(String message, boolean generateTitle) {

    public ChatRequest(String message) {
        this(message, false);
    }
}

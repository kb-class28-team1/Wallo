package com.wallo.chat.dto;

public record ChatResponse(String answer, String title) {

    public ChatResponse(String answer) {
        this(answer, null);
    }
}

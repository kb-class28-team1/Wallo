USE wallo;

CREATE TABLE IF NOT EXISTS CONSUMPTION_ANALYSIS_RESULTS (
    analysis_result_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    assistant_message_id BIGINT NOT NULL,
    request_message TEXT NOT NULL,
    calculated_result JSON NOT NULL,
    ai_response TEXT NOT NULL,
    generated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_consumption_analysis_user_generated (user_id, generated_at),
    UNIQUE INDEX uk_consumption_analysis_message (assistant_message_id),
    CONSTRAINT fk_consumption_analysis_user
        FOREIGN KEY (user_id) REFERENCES USERS(id) ON DELETE CASCADE,
    CONSTRAINT fk_consumption_analysis_message
        FOREIGN KEY (assistant_message_id) REFERENCES CHAT_MESSAGES(message_id) ON DELETE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

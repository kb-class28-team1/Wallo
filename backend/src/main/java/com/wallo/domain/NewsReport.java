package com.wallo.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class NewsReport {

    private Long reportId;
    private Long newsId;
    private String summary;
    private String eventDescription;
    private String cause;
    private String socialImpact;
    private String userImpact;
    private String responseStrategy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

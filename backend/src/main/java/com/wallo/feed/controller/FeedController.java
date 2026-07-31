package com.wallo.feed.controller;

import com.wallo.auth.CurrentUserProvider;
import com.wallo.feed.domain.Feed;
import com.wallo.feed.dto.FeedDtos.AnalysisResponse;
import com.wallo.feed.dto.FeedDtos.FeedListResponse;
import com.wallo.feed.dto.FeedDtos.MessageRequest;
import com.wallo.feed.dto.FeedDtos.RoomResponse;
import com.wallo.feed.service.FeedService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/challenges/{challengeId}")
public class FeedController {
    private final FeedService feedService;
    private final CurrentUserProvider currentUserProvider;

    public FeedController(FeedService feedService, CurrentUserProvider currentUserProvider) {
        this.feedService = feedService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/feeds")
    public FeedListResponse feeds(@PathVariable Long challengeId,
                                  @RequestParam(defaultValue = "false") boolean mineOnly) {
        return feedService.getFeeds(currentUserProvider.getCurrentUserId(), challengeId, mineOnly);
    }

    @PostMapping(value = "/feeds/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AnalysisResponse analyze(@PathVariable Long challengeId,
                                    @RequestParam MultipartFile media,
                                    @RequestParam String spendingType,
                                    @RequestParam String category) {
        return feedService.analyze(currentUserProvider.getCurrentUserId(), challengeId,
                media, spendingType, category);
    }

    @PostMapping(value = "/feeds", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Feed> create(
            @PathVariable Long challengeId, @RequestParam MultipartFile media,
            @RequestParam String spendingType, @RequestParam String category,
            @RequestParam(required = false) String customCategory,
            @RequestParam(required = false) String caption,
            @RequestParam(defaultValue = "0") int savingAmount,
            @RequestParam(defaultValue = "") String analysisSummary,
            @RequestParam(defaultValue = "0") double confidenceScore) {
        Feed feed = feedService.create(currentUserProvider.getCurrentUserId(), challengeId,
                media, spendingType, category, customCategory, caption, savingAmount,
                analysisSummary, confidenceScore);
        return ResponseEntity.status(HttpStatus.CREATED).body(feed);
    }

    @GetMapping("/messages")
    public RoomResponse messages(@PathVariable Long challengeId) {
        return feedService.getMessages(currentUserProvider.getCurrentUserId(), challengeId);
    }

    @PostMapping("/messages")
    public ResponseEntity<Void> send(@PathVariable Long challengeId,
                                     @RequestBody MessageRequest request) {
        feedService.sendMessage(currentUserProvider.getCurrentUserId(), challengeId, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}

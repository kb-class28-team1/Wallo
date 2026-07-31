package com.wallo.feed.service;

import com.wallo.feed.analysis.FeedAnalysisClient;
import com.wallo.feed.domain.Feed;
import com.wallo.feed.dto.FeedDtos.AnalysisResponse;
import com.wallo.feed.dto.FeedDtos.FeedListResponse;
import com.wallo.feed.dto.FeedDtos.MessageRequest;
import com.wallo.feed.dto.FeedDtos.RoomResponse;
import com.wallo.feed.mapper.FeedMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FeedService {
    private static final Set<String> SPENDING_TYPES = Set.of("SPENT", "REDUCED", "SAVED");
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;
    private final FeedMapper feedMapper;
    private final FeedAnalysisClient analysisClient;

    public FeedService(FeedMapper feedMapper, FeedAnalysisClient analysisClient) {
        this.feedMapper = feedMapper;
        this.analysisClient = analysisClient;
    }

    public FeedListResponse getFeeds(Long userId, Long challengeId, boolean mineOnly) {
        requireMember(userId, challengeId);
        Long total = feedMapper.sumSavingAmount(challengeId, userId);
        return new FeedListResponse(feedMapper.findChallengeName(challengeId),
                total == null ? 0 : total, feedMapper.findFeeds(challengeId, userId, mineOnly));
    }

    public RoomResponse getMessages(Long userId, Long challengeId) {
        requireMember(userId, challengeId);
        return new RoomResponse(feedMapper.findChallengeName(challengeId),
                feedMapper.findMessages(challengeId));
    }

    public AnalysisResponse analyze(Long userId, Long challengeId, MultipartFile media,
                                    String spendingType, String category) {
        requireMember(userId, challengeId);
        validate(media, spendingType, category);
        return analysisClient.analyze(media, spendingType, category);
    }

    @Transactional
    public Feed create(Long userId, Long challengeId, MultipartFile media,
                       String spendingType, String category, String customCategory,
                       String caption, int savingAmount, String analysisSummary,
                       double confidenceScore) {
        requireMember(userId, challengeId);
        validate(media, spendingType, category);
        String mediaType = media.getContentType() != null
                && media.getContentType().toLowerCase(Locale.ROOT).startsWith("video/")
                ? "VIDEO" : "IMAGE";
        String mediaUrl = store(media);

        Feed feed = new Feed();
        feed.setUserId(userId);
        feed.setChallengeId(challengeId);
        feed.setMediaUrl(mediaUrl);
        feed.setThumbnailUrl(mediaType.equals("IMAGE") ? mediaUrl : null);
        feed.setMediaType(mediaType);
        feed.setSpendingType(spendingType);
        feed.setSavingAmount(Math.max(0, savingAmount));
        feed.setCategory(category);
        feed.setCustomCategory(blankToNull(customCategory));
        feed.setCaption(blankToNull(caption));
        feedMapper.insertFeed(feed);
        feedMapper.insertAnalysis(feed.getId(), spendingType, category,
                feed.getSavingAmount(), analysisSummary, confidenceScore);
        feedMapper.insertFeedShareMessage(challengeId, userId, feed.getId());
        return feed;
    }

    @Transactional
    public void sendMessage(Long userId, Long challengeId, MessageRequest request) {
        requireMember(userId, challengeId);
        String content = request == null ? null : blankToNull(request.content());
        if (content == null) throw new IllegalArgumentException("메시지를 입력해 주세요.");
        String type = request.referenceFeedId() == null ? "TEXT" : "REPLY";
        feedMapper.insertMessage(challengeId, userId, content,
                request.referenceFeedId(), type);
    }

    private void requireMember(Long userId, Long challengeId) {
        if (challengeId == null || feedMapper.isChallengeMember(userId, challengeId) != 1) {
            throw new IllegalArgumentException("참여 중인 챌린지만 이용할 수 있습니다.");
        }
    }

    private void validate(MultipartFile media, String spendingType, String category) {
        if (media == null || media.isEmpty()) throw new IllegalArgumentException("사진이나 영상을 선택해 주세요.");
        if (media.getSize() > MAX_FILE_SIZE) throw new IllegalArgumentException("파일은 50MB 이하만 올릴 수 있습니다.");
        String contentType = media.getContentType() == null ? "" : media.getContentType();
        if (!contentType.startsWith("image/") && !contentType.startsWith("video/")) {
            throw new IllegalArgumentException("사진 또는 영상 파일만 올릴 수 있습니다.");
        }
        if (!SPENDING_TYPES.contains(spendingType)) throw new IllegalArgumentException("소비 종류를 선택해 주세요.");
        if (category == null || category.isBlank()) throw new IllegalArgumentException("카테고리를 선택해 주세요.");
    }

    private String store(MultipartFile media) {
        try {
            Path directory = Path.of(System.getProperty("java.io.tmpdir"), "wallo-feed-media");
            Files.createDirectories(directory);
            String original = media.getOriginalFilename() == null ? "" : media.getOriginalFilename();
            String extension = original.lastIndexOf('.') >= 0
                    ? original.substring(original.lastIndexOf('.')).replaceAll("[^A-Za-z0-9.]", "") : "";
            String filename = UUID.randomUUID() + extension;
            Files.copy(media.getInputStream(), directory.resolve(filename),
                    StandardCopyOption.REPLACE_EXISTING);
            return "/api/feed-media/" + filename;
        } catch (IOException exception) {
            throw new IllegalStateException("미디어 파일을 저장하지 못했습니다.", exception);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}

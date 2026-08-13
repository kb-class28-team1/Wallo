package com.wallo.feed.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallo.feed.analysis.FeedAnalysisClient;
import com.wallo.feed.analysis.SavingFeedbackAwareFeedAnalysisClient;
import com.wallo.feed.domain.Feed;
import com.wallo.feed.domain.FeedMessage;
import com.wallo.feed.dto.FeedDtos.AnalysisResponse;
import com.wallo.feed.dto.FeedDtos.FeedListResponse;
import com.wallo.feed.dto.FeedDtos.LikeResponse;
import com.wallo.feed.dto.FeedDtos.MessageRequest;
import com.wallo.feed.dto.FeedDtos.RoomResponse;
import com.wallo.feed.dto.FeedDtos.SavingAmountFeedbackSummary;
import com.wallo.feed.dto.FeedDtos.UpdateFeedRequest;
import com.wallo.feed.mapper.FeedMapper;
import com.wallo.feed.websocket.ChallengeChatBroadcaster;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FeedService {
    private static final Set<String> SPENDING_TYPES = Set.of("SPENT", "REDUCED", "SAVED");
    private static final Set<String> SAVING_AMOUNT_FEEDBACK_TYPES =
            Set.of("SAME", "DIFFERENT", "UNKNOWN");
    private static final Set<String> FEED_CATEGORIES = Set.of(
            "FOOD", "CAFE", "TRANSPORT", "SHOPPING", "DELIVERY",
            "HOUSING", "LIVING", "CULTURE", "HEALTH", "ETC");
    private static final Set<String> FEEDBACK_TYPES = Set.of("ACCEPTED", "ADJUSTED", "MANUAL");
    private static final Set<String> ANALYSIS_STATUSES = Set.of("AI_COMPLETED", "AI_FAILED", "MANUAL");
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;
    private final FeedMapper feedMapper;
    private final FeedAnalysisClient analysisClient;
    private final ChallengeChatBroadcaster chatBroadcaster;
    private final PriceReferenceService priceReferenceService;
    private final ObjectMapper objectMapper;

    /** 분석 단위 테스트와 기존 호출부의 호환을 위한 생성자. */
    public FeedService(FeedMapper feedMapper, FeedAnalysisClient analysisClient) {
        this(feedMapper, analysisClient, null, null, new ObjectMapper());
    }

    @Autowired
    public FeedService(FeedMapper feedMapper, FeedAnalysisClient analysisClient,
                       ChallengeChatBroadcaster chatBroadcaster,
                       PriceReferenceService priceReferenceService,
                       ObjectMapper objectMapper) {
        this.feedMapper = feedMapper;
        this.analysisClient = analysisClient;
        this.chatBroadcaster = chatBroadcaster;
        this.priceReferenceService = priceReferenceService;
        this.objectMapper = objectMapper;
    }

    public FeedListResponse getFeeds(Long userId, Long challengeId, boolean mineOnly) {
        requireMember(userId, challengeId);
        Long total = feedMapper.sumSavingAmount(challengeId, userId);
        return new FeedListResponse(
                feedMapper.findChallengeName(challengeId),
                feedMapper.findChallengeInviteCode(challengeId),
                total == null ? 0 : total,
                feedMapper.findFeeds(challengeId, userId, mineOnly));
    }

    public RoomResponse getMessages(Long userId, Long challengeId) {
        requireMember(userId, challengeId);
        return new RoomResponse(feedMapper.findChallengeName(challengeId),
                feedMapper.findMessages(challengeId));
    }

    public AnalysisResponse analyze(Long userId, Long challengeId, MultipartFile media,
                                    String spendingType, String category) {
        requireMember(userId, challengeId);
        String normalizedCategory = normalizeCategory(category);
        validate(media, spendingType, normalizedCategory);
        SavingAmountFeedbackSummary feedbackSummary = feedMapper.findSavingAmountFeedbackSummary(
                userId, normalizedCategory);
        AnalysisResponse analysis;
        if (analysisClient instanceof SavingFeedbackAwareFeedAnalysisClient feedbackAwareClient) {
            analysis = feedbackAwareClient.analyze(
                    media, spendingType, normalizedCategory, feedbackSummary);
        } else {
            analysis = analysisClient.analyze(media, spendingType, normalizedCategory);
        }
        if (priceReferenceService != null) {
            analysis = priceReferenceService.enrich(analysis);
        }
        return applyCategoryAverageFallback(userId, analysis);
    }

    @Transactional
    public Feed create(Long userId, Long challengeId, MultipartFile media,
                       String spendingType, String category, String customCategory,
                       String caption, int savingAmount, String analysisSummary,
        double confidenceScore) {
        return create(userId, challengeId, media, spendingType, category, customCategory,
                caption, savingAmount, savingAmount, analysisSummary, confidenceScore,
                "UNKNOWN", null, null, null);
    }

    @Transactional
    public Feed create(Long userId, Long challengeId, MultipartFile media,
                       String spendingType, String category, String customCategory,
                       String caption, int savingAmount, int aiEstimatedSavingAmount,
                       String analysisSummary, double confidenceScore,
                       String savingAmountFeedback, Integer verifiedSavingAmount,
                       String savingAmountFeedbackNote) {
        return create(userId, challengeId, media, spendingType, category, customCategory,
                caption, savingAmount, aiEstimatedSavingAmount, analysisSummary, confidenceScore,
                savingAmountFeedback, verifiedSavingAmount, savingAmountFeedbackNote, null);
    }

    @Transactional
    public Feed create(Long userId, Long challengeId, MultipartFile media,
                       String spendingType, String category, String customCategory,
                       String caption, int savingAmount, int aiEstimatedSavingAmount,
                       String analysisSummary, double confidenceScore,
                       String savingAmountFeedback, Integer verifiedSavingAmount,
                       String savingAmountFeedbackNote, String analysisDetails) {
        requireMember(userId, challengeId);
        String normalizedCategory = normalizeCategory(category);
        validate(media, spendingType, normalizedCategory);
        validateCaption(caption);
        String normalizedFeedback = normalizeSavingAmountFeedback(savingAmountFeedback);
        int normalizedAiAmount = clampAmount(aiEstimatedSavingAmount);
        int finalSavingAmount = clampAmount(savingAmount);
        Integer normalizedVerifiedAmount = normalizeVerifiedSavingAmount(
                normalizedFeedback, normalizedAiAmount, verifiedSavingAmount);
        if ("SAME".equals(normalizedFeedback)) {
            finalSavingAmount = normalizedAiAmount;
        } else if ("DIFFERENT".equals(normalizedFeedback)) {
            finalSavingAmount = normalizedVerifiedAmount;
        }
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
        feed.setSavingAmount(finalSavingAmount);
        feed.setCategory(normalizedCategory);
        // 새 인증 글은 공통 지출 카테고리만 사용하며 기존 custom_category 데이터는 유지함.
        feed.setCustomCategory(null);
        feed.setCaption(blankToNull(caption));
        feed.setAnalysisSummary(blankToNull(analysisSummary));
        feed.setAnalysisStatus(normalizedAnalysisStatus);
        feedMapper.insertFeed(feed);
        AnalysisResponse details = parseAnalysisDetails(analysisDetails);
        feedMapper.insertAnalysis(feed.getId(), spendingType, normalizedCategory,
                normalizedAiAmount, analysisSummary, confidenceScore,
                details == null ? 0 : safeLong(details.referenceValue()),
                details == null ? 0 : safeLong(details.actualCost()),
                details == null ? 0 : safeLong(details.savingDifference()),
                details == null ? null : toJson(details.detectedItems()),
                details == null ? null : toJson(details.priceReferences()));
        feedMapper.insertSavingAmountFeedback(
                feed.getId(), userId, normalizedCategory, normalizedAiAmount,
                normalizedFeedback, normalizedVerifiedAmount,
                blankToNull(savingAmountFeedbackNote));
        feedMapper.insertFeedShareMessage(challengeId, userId, feed.getId());
        publishMessageAfterCommit(challengeId, feedMapper.findMessageByLastInsertId());
        return feed;
    }

    @Transactional
    public LikeResponse addLike(Long userId, Long challengeId, Long feedId) {
        requireMember(userId, challengeId);
        if (feedId == null || feedMapper.countActiveFeed(feedId, challengeId) != 1) {
            throw new IllegalArgumentException("좋아요를 누를 피드를 찾을 수 없습니다.");
        }

        if (feedMapper.incrementLikeCount(feedId) != 1) {
            throw new IllegalArgumentException("좋아요 처리에 실패했습니다.");
        }

        Integer likeCount = feedMapper.findLikeCount(feedId, challengeId);
        return new LikeResponse(feedId, likeCount == null ? 0 : likeCount);
    }

    @Transactional
    public void update(Long userId, Long challengeId, Long feedId, UpdateFeedRequest request) {
        requireMember(userId, challengeId);
        if (request == null) {
            throw new IllegalArgumentException("수정할 내용을 입력해 주세요.");
        }

        if (request.spendingType() == null || !SPENDING_TYPES.contains(request.spendingType())) {
            throw new IllegalArgumentException("소비 종류를 선택해 주세요.");
        }
        String category = normalizeCategory(request.category());
        if (category.isBlank() || !FEED_CATEGORIES.contains(category)) {
            throw new IllegalArgumentException("지원하지 않는 카테고리입니다.");
        }
        int savingAmount = normalizeAmount(request.savingAmount());
        int updated = feedMapper.updateFeed(
                feedId, userId, challengeId, category, request.spendingType(),
                blankToNull(request.caption()), savingAmount);
        if (updated != 1) {
            throw new IllegalArgumentException("내 피드만 수정할 수 있습니다.");
        }
        feedMapper.updateFeedAnalysis(feedId, request.spendingType(), category, savingAmount);
    }

    @Transactional
    public void delete(Long userId, Long challengeId, Long feedId) {
        requireMember(userId, challengeId);
        int deleted = feedMapper.softDeleteFeed(feedId, userId, challengeId);
        if (deleted != 1) {
            throw new IllegalArgumentException("내 피드만 삭제할 수 있습니다.");
        }
    }

    @Transactional
    public void sendMessage(Long userId, Long challengeId, MessageRequest request) {
        requireMember(userId, challengeId);
        String content = request == null ? null : blankToNull(request.content());
        Long referenceFeedId = request == null ? null : request.referenceFeedId();
        if (content == null && referenceFeedId == null) {
            throw new IllegalArgumentException("메시지를 입력해 주세요.");
        }
        if (referenceFeedId != null && feedMapper.countActiveFeed(referenceFeedId, challengeId) != 1) {
            throw new IllegalArgumentException("언급할 피드를 찾을 수 없습니다.");
        }
        String type = referenceFeedId == null ? "TEXT" : "REPLY";
        feedMapper.insertMessage(challengeId, userId, content,
                referenceFeedId, type);
        publishMessageAfterCommit(challengeId, feedMapper.findMessageByLastInsertId());
    }

    private void publishMessageAfterCommit(Long challengeId, FeedMessage message) {
        if (chatBroadcaster != null) {
            chatBroadcaster.broadcastAfterCommit(challengeId, message);
        }
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
        if (!FEED_CATEGORIES.contains(category)) throw new IllegalArgumentException("지원하지 않는 카테고리입니다.");
    }

    private void validateCaption(String caption) {
        if (caption == null || caption.isBlank()) {
            throw new IllegalArgumentException("한줄요약을 작성해주세요");
        }
    }

    private String normalizeSavingAmountFeedback(String feedback) {
        String normalized = feedback == null ? "UNKNOWN" : feedback.trim().toUpperCase(Locale.ROOT);
        if (!SAVING_AMOUNT_FEEDBACK_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("절약 금액 확인 방법을 선택해 주세요.");
        }
        return normalized;
    }

    private Integer normalizeVerifiedSavingAmount(
            String feedback, int aiAmount, Integer verifiedAmount) {
        if ("UNKNOWN".equals(feedback)) return null;
        if ("SAME".equals(feedback)) return aiAmount;
        if (verifiedAmount == null || verifiedAmount < 0) {
            throw new IllegalArgumentException("실제 절약 금액을 입력해 주세요.");
        }
        return clampAmount(verifiedAmount);
    }

    private int clampAmount(int amount) {
        return Math.min(10_000_000, Math.max(0, amount));
    }

    private String normalizeCategory(String category) {
        return category == null ? "" : category.trim().toUpperCase(Locale.ROOT);
    }

    private AnalysisResponse applyCategoryAverageFallback(Long userId, AnalysisResponse analysis) {
        if (analysis == null
                || analysis.estimatedSavingAmount() > 0
                || analysis.referenceValue() > 0
                || "SPENT".equals(analysis.spendingType())) {
            return analysis;
        }

        LocalDate endDate = LocalDate.now().minusDays(1);
        CategoryExpenseAverage average = feedMapper.findCategoryExpenseAverage(
                userId,
                analysis.category(),
                endDate.minusDays(PRIMARY_HISTORY_DAYS - 1L),
                endDate);
        int historyDays = PRIMARY_HISTORY_DAYS;

        if (!hasEnoughHistory(average)) {
            average = feedMapper.findCategoryExpenseAverage(
                    userId,
                    analysis.category(),
                    endDate.minusDays(EXTENDED_HISTORY_DAYS - 1L),
                    endDate);
            historyDays = EXTENDED_HISTORY_DAYS;
        }

        if (!hasEnoughHistory(average)) {
            return analysis;
        }

        int estimatedAmount = toSavingAmount(average.getAverageAmount());
        if (estimatedAmount == 0) {
            return analysis;
        }

        String summary = appendHistoryFallbackSummary(analysis.summary(), historyDays);
        return new AnalysisResponse(
                analysis.spendingType(), analysis.category(), estimatedAmount,
                summary, analysis.confidenceScore(), analysis.detectedItems(),
                analysis.referenceValue(), analysis.actualCost(), analysis.savingDifference(),
                analysis.priceReferences());
    }

    private Integer normalizeOptionalAmount(Integer amount) {
        return amount == null ? null : Math.max(0, amount);
    }

    private double normalizeConfidence(double confidence) {
        if (Double.isNaN(confidence) || Double.isInfinite(confidence)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, confidence));
    }

    private String normalizeFeedbackType(String feedbackType, Integer aiEstimatedAmount) {
        String normalized = feedbackType == null ? "" : feedbackType.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return aiEstimatedAmount == null ? "MANUAL" : "ACCEPTED";
        }
        if (!FEEDBACK_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("지원하지 않는 AI 피드백 유형입니다.");
        }
        return normalized;
    }

    private String normalizeAnalysisStatus(String analysisStatus, Integer aiEstimatedAmount) {
        String normalized = analysisStatus == null ? "" : analysisStatus.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return aiEstimatedAmount == null ? "MANUAL" : "AI_COMPLETED";
        }
        if (!ANALYSIS_STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("지원하지 않는 AI 분석 상태입니다.");
        }
        return normalized;
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

    private AnalysisResponse parseAnalysisDetails(String analysisDetails) {
        if (analysisDetails == null || analysisDetails.isBlank()
                || analysisDetails.length() > 100_000) {
            return null;
        }
        try {
            return objectMapper.readValue(analysisDetails, AnalysisResponse.class);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private long safeLong(long value) {
        return Math.max(0, Math.min(10_000_000_000L, value));
    }
}

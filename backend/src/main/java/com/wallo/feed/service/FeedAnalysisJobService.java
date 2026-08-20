package com.wallo.feed.service;

import com.wallo.feed.dto.FeedDtos.AnalysisJobStartResponse;
import com.wallo.feed.dto.FeedDtos.AnalysisProgressResponse;
import com.wallo.feed.dto.FeedDtos.AnalysisResponse;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 분석 요청을 백그라운드에서 실행하고 프론트가 실제 처리 단계를 조회할 수 있게 한다.
 * 분석 결과와 업로드 파일은 작업이 끝난 뒤 잠시만 보관한다.
 */
@Service
public class FeedAnalysisJobService {
    private static final Duration JOB_TTL = Duration.ofMinutes(10);

    private final FeedService feedService;
    private final Executor analysisExecutor;
    private final Map<String, AnalysisJob> jobs = new ConcurrentHashMap<>();

    @Autowired
    public FeedAnalysisJobService(
            FeedService feedService,
            @Qualifier("feedAnalysisExecutor") Executor analysisExecutor) {
        this.feedService = feedService;
        this.analysisExecutor = analysisExecutor;
    }

    public AnalysisJobStartResponse start(
            Long userId, Long challengeId, MultipartFile media,
            String spendingType, String category) {
        purgeExpiredJobs();
        if (media == null || media.isEmpty()) {
            throw new IllegalArgumentException("분석할 사진이나 영상을 선택해 주세요.");
        }

        ByteArrayMultipartFile copiedMedia;
        try {
            copiedMedia = ByteArrayMultipartFile.copyOf(media);
        } catch (IOException exception) {
            throw new IllegalArgumentException("업로드 파일을 분석용으로 준비하지 못했습니다.", exception);
        }

        String jobId = UUID.randomUUID().toString();
        AnalysisJob job = new AnalysisJob(
                jobId, userId, challengeId, copiedMedia, spendingType, category);
        jobs.put(jobId, job);
        try {
            analysisExecutor.execute(() -> run(job));
        } catch (RejectedExecutionException exception) {
            jobs.remove(jobId);
            throw new IllegalStateException("분석 요청이 많아 잠시 후 다시 시도해 주세요.", exception);
        }
        return new AnalysisJobStartResponse(jobId);
    }

    public AnalysisProgressResponse getProgress(Long userId, Long challengeId, String jobId) {
        purgeExpiredJobs();
        AnalysisJob job = jobs.get(jobId);
        if (job == null
                || !userId.equals(job.userId)
                || !challengeId.equals(job.challengeId)) {
            throw new IllegalArgumentException("분석 작업을 찾을 수 없습니다.");
        }
        return job.snapshot();
    }

    private void run(AnalysisJob job) {
        job.update(1, "분석 요청 준비 중...");
        try {
            AnalysisResponse result = feedService.analyze(
                    job.userId, job.challengeId, job.media,
                    job.spendingType, job.category, job::update);
            job.complete(result);
        } catch (Exception exception) {
            job.fail(resolveErrorMessage(exception));
        } finally {
            job.releaseMedia();
        }
    }

    private String resolveErrorMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? "AI 분석에 실패했습니다." : message;
    }

    private void purgeExpiredJobs() {
        Instant now = Instant.now();
        jobs.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }

    private static final class AnalysisJob {
        private final String jobId;
        private final Long userId;
        private final Long challengeId;
        private final String spendingType;
        private final String category;
        private ByteArrayMultipartFile media;
        private String status = "QUEUED";
        private int progress;
        private String message = "분석 대기 중...";
        private AnalysisResponse result;
        private String error;
        private Instant lastTouched = Instant.now();

        private AnalysisJob(
                String jobId, Long userId, Long challengeId,
                ByteArrayMultipartFile media, String spendingType, String category) {
            this.jobId = jobId;
            this.userId = userId;
            this.challengeId = challengeId;
            this.media = media;
            this.spendingType = spendingType;
            this.category = category;
        }

        private synchronized void update(int progress, String message) {
            this.status = "ANALYZING";
            this.progress = Math.max(0, Math.min(100, progress));
            this.message = message == null || message.isBlank() ? "분석 중..." : message;
            this.lastTouched = Instant.now();
        }

        private synchronized void complete(AnalysisResponse result) {
            this.status = "COMPLETED";
            this.progress = 100;
            this.message = "분석 완료";
            this.result = result;
            this.lastTouched = Instant.now();
        }

        private synchronized void fail(String error) {
            this.status = "FAILED";
            this.message = "분석 실패";
            this.error = error;
            this.lastTouched = Instant.now();
        }

        private synchronized AnalysisProgressResponse snapshot() {
            this.lastTouched = Instant.now();
            return new AnalysisProgressResponse(status, progress, message, result, error);
        }

        private synchronized void releaseMedia() {
            media = null;
        }

        private synchronized boolean isExpired(Instant now) {
            return lastTouched.plus(JOB_TTL).isBefore(now);
        }
    }

    private static final class ByteArrayMultipartFile implements MultipartFile {
        private final String name;
        private final String originalFilename;
        private final String contentType;
        private final byte[] content;

        private ByteArrayMultipartFile(
                String name, String originalFilename, String contentType, byte[] content) {
            this.name = name;
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.content = content;
        }

        private static ByteArrayMultipartFile copyOf(MultipartFile source) throws IOException {
            return new ByteArrayMultipartFile(
                    source.getName(), source.getOriginalFilename(), source.getContentType(),
                    source.getBytes());
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public byte[] getBytes() {
            return content.clone();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(File destination) throws IOException {
            Files.write(destination.toPath(), content);
        }
    }
}

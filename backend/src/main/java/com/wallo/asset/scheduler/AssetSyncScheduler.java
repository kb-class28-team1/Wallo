package com.wallo.asset.scheduler;

import com.wallo.asset.dto.AssetSyncDto;
import com.wallo.asset.mapper.ConnectionMapper;
import com.wallo.asset.service.AssetSyncOrchestrator;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs the same user synchronization flow as the manual refresh endpoint. */
@Component
public class AssetSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(AssetSyncScheduler.class);

    private final ConnectionMapper connectionMapper;
    private final AssetSyncOrchestrator assetSyncOrchestrator;
    private final boolean enabled;

    public AssetSyncScheduler(
            ConnectionMapper connectionMapper,
            AssetSyncOrchestrator assetSyncOrchestrator,
            @Value("${asset-sync.scheduler.enabled:true}") boolean enabled
    ) {
        this.connectionMapper = connectionMapper;
        this.assetSyncOrchestrator = assetSyncOrchestrator;
        this.enabled = enabled;
    }

    @Scheduled(
            cron = "${asset-sync.scheduler.cron:0 0 */12 * * *}",
            zone = "Asia/Seoul"
    )
    public void syncAssets() {
        if (!enabled) {
            log.info("asset-sync scheduler is disabled");
            return;
        }

        for (Long userId : activeUserIds()) {
            if (userId == null) {
                continue;
            }
            try {
                AssetSyncDto.SyncResponse result = assetSyncOrchestrator.syncNow(userId);
                if (result.getFailedConnections() > 0) {
                    log.warn(
                            "asset-sync scheduler completed with failures userId={} inserted={} updated={} "
                                    + "failedConnections={}",
                            userId,
                            result.getInserted(),
                            result.getUpdated(),
                            result.getFailedConnections()
                    );
                } else {
                    log.info(
                            "asset-sync scheduler completed userId={} inserted={} updated={}",
                            userId,
                            result.getInserted(),
                            result.getUpdated()
                    );
                }
            } catch (RuntimeException exception) {
                log.error("asset-sync scheduler failed userId={}", userId, exception);
            }
        }
    }

    private List<Long> activeUserIds() {
        List<Long> userIds = connectionMapper.findUserIdsWithActiveConnections();
        return userIds == null ? Collections.emptyList() : userIds;
    }
}

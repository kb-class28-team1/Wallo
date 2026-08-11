package com.wallo.goal.service;

import com.wallo.asset.domain.Institution;
import com.wallo.asset.service.AssetSyncService;
import com.wallo.external.CodefResponseValidator;
import com.wallo.external.client.CodefClient;
import com.wallo.external.dto.CodefDto;
import com.wallo.goal.dto.GoalAccountDto;
import com.wallo.goal.mapper.GoalAccountMapper;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoalAccountSyncService {

    private static final Logger LOGGER = Logger.getLogger(GoalAccountSyncService.class.getName());
    private final GoalAccountMapper goalAccountMapper;
    private final CodefClient codefClient;
    private final AssetSyncService assetSyncService;

    public GoalAccountSyncService(
            GoalAccountMapper goalAccountMapper,
            CodefClient codefClient,
            AssetSyncService assetSyncService
    ) {
        this.goalAccountMapper = goalAccountMapper;
        this.codefClient = codefClient;
        this.assetSyncService = assetSyncService;
    }

    /** 목표 조회 전에 선택된 계좌가 속한 연결기관의 최신 잔액을 반영한다. */
    @Transactional
    public void syncSelectedAccounts(long userId) {
        List<GoalAccountDto.SyncTarget> targets = goalAccountMapper.findSelectedAccountSyncTargets(userId);
        for (GoalAccountDto.SyncTarget target : values(targets)) {
            try {
                CodefDto.Response response = codefClient.connectInstitution(
                        new CodefDto.Request(
                                target.getCodefOrganizationCode(),
                                target.getInstitutionType(),
                                target.getLoginType(),
                                target.getLoginId(),
                                target.getLoginPassword()
                        )
                );
                if (!isSuccess(response)) {
                    LOGGER.warning(String.format(
                            "goal account sync failed: institution=%s code=%s",
                            target.getCodefOrganizationCode(),
                            CodefResponseValidator.codeOrDefault(response, "NO_RESPONSE")
                    ));
                    continue;
                }

                assetSyncService.syncAccountBalances(
                        target.getConnectionId(),
                        toInstitution(target),
                        response
                );
            } catch (RuntimeException exception) {
                // 잔액 동기화가 실패해도 마지막으로 저장된 목표 금액은 조회할 수 있도록 한다.
                LOGGER.log(
                        Level.WARNING,
                        "goal account sync exception: institution=" + target.getCodefOrganizationCode(),
                        exception
                );
            }
        }
    }

    private Institution toInstitution(GoalAccountDto.SyncTarget target) {
        return new Institution(
                target.getInstitutionId(),
                target.getCodefOrganizationCode(),
                target.getInstitutionName(),
                target.getFinancialGroupCode(),
                target.getFinancialGroupName(),
                target.getInstitutionType(),
                target.getLogoUrl()
        );
    }

    private boolean isSuccess(CodefDto.Response response) {
        return CodefResponseValidator.isSuccess(response);
    }

    private <T> List<T> values(List<T> values) {
        return values == null ? Collections.emptyList() : values;
    }
}

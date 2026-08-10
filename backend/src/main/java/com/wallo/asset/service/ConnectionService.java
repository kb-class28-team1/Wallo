package com.wallo.asset.service;

import com.wallo.asset.domain.Institution;
import com.wallo.asset.dto.ConnectionDto;
import com.wallo.asset.exception.ConnectionConsentRequiredException;
import com.wallo.asset.exception.ConnectionNotFoundException;
import com.wallo.asset.mapper.ConnectionMapper;
import com.wallo.external.client.CodefClient;
import com.wallo.external.dto.CodefDto;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConnectionService {

    private static final Logger LOGGER = Logger.getLogger(ConnectionService.class.getName());

    private final CodefClient codefClient;
    private final InstitutionService institutionService;
    private final ConnectionMapper connectionMapper;
    private final AssetSyncService assetSyncService;
    private final CardWithdrawalReconciliationService cardWithdrawalReconciliationService;
    private final AnnualSalarySyncService annualSalarySyncService;

    public ConnectionService(
            CodefClient codefClient,
            InstitutionService institutionService,
            ConnectionMapper connectionMapper,
            AssetSyncService assetSyncService,
            CardWithdrawalReconciliationService cardWithdrawalReconciliationService,
            AnnualSalarySyncService annualSalarySyncService
    ) {
        this.codefClient = codefClient;
        this.institutionService = institutionService;
        this.connectionMapper = connectionMapper;
        this.assetSyncService = assetSyncService;
        this.cardWithdrawalReconciliationService = cardWithdrawalReconciliationService;
        this.annualSalarySyncService = annualSalarySyncService;
    }

    @Transactional
    public ConnectionDto.Response connectAllAssets(long userId, ConnectionDto.Request request) {
        validateConsent(request);

        long totalStartedAt = System.nanoTime();
        List<ConnectionAttempt> attempts = new ArrayList<>();
        long connectionStartedAt = System.nanoTime();
        for (Institution institution : institutionService.getConnectionTargetInstitutions()) {
            long institutionStartedAt = System.nanoTime();
            CodefDto.Response codefResponse = codefClient.connectInstitution(createCodefRequest(institution));
            ConnectionDto.Result result = toConnectionResult(institution, codefResponse);
            attempts.add(new ConnectionAttempt(institution, codefResponse, result));
            LOGGER.info(String.format(
                    Locale.ROOT,
                    "asset-connect institution=%s type=%s status=%s durationMs=%d",
                    institution.getCodefOrganizationCode(),
                    institution.getInstitutionType(),
                    result.getStatus(),
                    elapsedMillis(institutionStartedAt)
            ));
        }
        long connectionElapsedMs = elapsedMillis(connectionStartedAt);

        List<ConnectionDto.Result> results = attempts.stream().map(ConnectionAttempt::result).toList();
        long saveStartedAt = System.nanoTime();
        saveConnections(userId, results);
        long saveElapsedMs = elapsedMillis(saveStartedAt);
        long syncStartedAt = System.nanoTime();
        syncAssets(userId, attempts);
        long syncElapsedMs = elapsedMillis(syncStartedAt);
        long salarySyncStartedAt = System.nanoTime();
        syncAnnualSalary(userId);
        long salarySyncElapsedMs = elapsedMillis(salarySyncStartedAt);
        long reconciliationStartedAt = System.nanoTime();
        cardWithdrawalReconciliationService.reconcile(userId);
        long reconciliationElapsedMs = elapsedMillis(reconciliationStartedAt);
        LOGGER.info(String.format(
                Locale.ROOT,
                "asset-connect summary institutions=%d success=%d connectionMs=%d saveMs=%d syncMs=%d "
                        + "salarySyncMs=%d reconciliationMs=%d totalMs=%d",
                attempts.size(),
                results.stream().filter(result -> result.getStatus() == ConnectionDto.Status.SUCCESS).count(),
                connectionElapsedMs,
                saveElapsedMs,
                syncElapsedMs,
                salarySyncElapsedMs,
                reconciliationElapsedMs,
                elapsedMillis(totalStartedAt)
        ));
        return new ConnectionDto.Response(results);
    }

    @Transactional(readOnly = true)
    public ConnectionDto.ConnectedAssetsResponse getConnectedAssets(long userId) {
        return new ConnectionDto.ConnectedAssetsResponse(
                connectionMapper.findConnectedAssets(userId)
        );
    }

    @Transactional
    public void disconnect(long userId, long connectionId) {
        if (connectionMapper.softDeleteConnection(userId, connectionId) == 0) {
            throw new ConnectionNotFoundException();
        }

        assetSyncService.refreshCurrentMonthSnapshot(userId);
    }

    private void syncAssets(long userId, List<ConnectionAttempt> attempts) {
        for (ConnectionAttempt attempt : attempts) {
            if (attempt.result().getStatus() == ConnectionDto.Status.SUCCESS) {
                Long connectionId = connectionMapper.findActiveConnectionId(userId, attempt.institution().getInstitutionId());
                if (connectionId == null) throw new IllegalStateException("연동 정보를 찾을 수 없습니다.");
                long startedAt = System.nanoTime();
                assetSyncService.sync(userId, connectionId, attempt.institution(), attempt.response());
                LOGGER.info(String.format(
                        Locale.ROOT,
                        "asset-sync institution=%s type=%s durationMs=%d",
                        attempt.institution().getCodefOrganizationCode(),
                        attempt.institution().getInstitutionType(),
                        elapsedMillis(startedAt)
                ));
            }
        }
    }

    private void syncAnnualSalary(long userId) {
        try {
            annualSalarySyncService.syncAnnualSalary(userId);
        } catch (RuntimeException exception) {
            LOGGER.warning(String.format(
                    Locale.ROOT,
                    "annual-salary-sync failed userId=%d reason=%s",
                    userId,
                    exception.getMessage()
            ));
        }
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    private void saveConnections(long userId, List<ConnectionDto.Result> results) {
        if (results.isEmpty()) {
            return;
        }

        int savedRows = connectionMapper.insertConnections(
                results,
                userId,
                ConnectionDto.MOCK_LOGIN_TYPE,
                ConnectionDto.MOCK_ID,
                ConnectionDto.MOCK_PASSWORD);
        if (savedRows < results.size()) {
            throw new IllegalStateException("연동 결과 저장에 실패했습니다.");
        }
    }

    private void validateConsent(ConnectionDto.Request request) {
        if (request == null || !Boolean.TRUE.equals(request.getConsentAgreed())) {
            throw new ConnectionConsentRequiredException();
        }
    }

    private CodefDto.Request createCodefRequest(Institution institution) {
        return new CodefDto.Request(
                institution.getCodefOrganizationCode(),
                institution.getInstitutionType(),
                ConnectionDto.MOCK_LOGIN_TYPE,
                ConnectionDto.MOCK_ID,
                ConnectionDto.MOCK_PASSWORD
        );
    }

    private ConnectionDto.Result toConnectionResult(Institution institution, CodefDto.Response response) {
        if (isCodefSuccess(response)) {
            return createConnectionResult(institution, ConnectionDto.Status.SUCCESS, ConnectionDto.SUCCESS_MESSAGE);
        }

        String message = ConnectionDto.FAILED_MESSAGE;
        if (response != null && response.getResult() != null && response.getResult().getMessage() != null) {
            message = response.getResult().getMessage();
        }

        return createConnectionResult(institution, ConnectionDto.Status.FAILED, message);
    }

    private boolean isCodefSuccess(CodefDto.Response response) {
        return response != null
                && response.getResult() != null
                && ConnectionDto.CODEF_SUCCESS_CODE.equals(response.getResult().getCode());
    }

    private ConnectionDto.Result createConnectionResult(
            Institution institution,
            ConnectionDto.Status status,
            String message
    ) {
        return new ConnectionDto.Result(
                institution.getInstitutionId(),
                institution.getName(),
                institution.getFinancialGroupCode(),
                institution.getFinancialGroupName(),
                institution.getInstitutionType(),
                institution.getLogoUrl(),
                status,
                message
        );
    }

    private record ConnectionAttempt(Institution institution, CodefDto.Response response, ConnectionDto.Result result) {
    }
}

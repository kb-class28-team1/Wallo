package com.wallo.asset.service;

import com.wallo.asset.domain.Institution;
import com.wallo.asset.dto.ConnectionDto;
import com.wallo.asset.exception.ConnectionConsentRequiredException;
import com.wallo.asset.mapper.ConnectionMapper;
import com.wallo.external.client.CodefClient;
import com.wallo.external.dto.CodefDto;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConnectionService {

    private final CodefClient codefClient;
    private final InstitutionService institutionService;
    private final ConnectionMapper connectionMapper;
    private final AssetSyncService assetSyncService;

    public ConnectionService(
            CodefClient codefClient,
            InstitutionService institutionService,
            ConnectionMapper connectionMapper,
            AssetSyncService assetSyncService
    ) {
        this.codefClient = codefClient;
        this.institutionService = institutionService;
        this.connectionMapper = connectionMapper;
        this.assetSyncService = assetSyncService;
    }

    @Transactional
    public ConnectionDto.Response connectAllAssets(long userId, ConnectionDto.Request request) {
        validateConsent(request);

        List<ConnectionAttempt> attempts = new ArrayList<>();
        for (Institution institution : institutionService.getConnectionTargetInstitutions()) {
            CodefDto.Response codefResponse = codefClient.connectInstitution(createCodefRequest(institution));
            attempts.add(new ConnectionAttempt(institution, codefResponse, toConnectionResult(institution, codefResponse)));
        }

        List<ConnectionDto.Result> results = attempts.stream().map(ConnectionAttempt::result).toList();
        saveConnections(userId, results);
        syncAssets(userId, attempts);
        return new ConnectionDto.Response(results);
    }

    private void syncAssets(long userId, List<ConnectionAttempt> attempts) {
        for (ConnectionAttempt attempt : attempts) {
            if (attempt.result().getStatus() == ConnectionDto.Status.SUCCESS) {
                Long connectionId = connectionMapper.findActiveConnectionId(userId, attempt.institution().getInstitutionId());
                if (connectionId == null) throw new IllegalStateException("연동 정보를 찾을 수 없습니다.");
                assetSyncService.sync(userId, connectionId, attempt.institution(), attempt.response());
            }
        }
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
                institution.getInstitutionId(),
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
                institution.getInstitutionType(),
                institution.getLogoUrl(),
                status,
                message
        );
    }

    private record ConnectionAttempt(Institution institution, CodefDto.Response response, ConnectionDto.Result result) {
    }
}

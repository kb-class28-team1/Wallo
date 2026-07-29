package com.wallo.asset.service;

import com.wallo.asset.domain.Institution;
import com.wallo.asset.dto.ConnectionDto;
import com.wallo.asset.exception.ConnectionConsentRequiredException;
import com.wallo.asset.mapper.ConnectionMapper;
import com.wallo.common.exception.CustomException;
import com.wallo.common.exception.ErrorCode;
import com.wallo.external.client.CodefClient;
import com.wallo.external.dto.CodefDto;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConnectionService {

    private final ConnectionMapper connectionMapper;
    private final CodefClient codefClient;
    private final InstitutionService institutionService;

    public ConnectionService(
            ConnectionMapper connectionMapper,
            CodefClient codefClient,
            InstitutionService institutionService
    ) {
        this.connectionMapper = connectionMapper;
        this.codefClient = codefClient;
        this.institutionService = institutionService;
    }

    @Transactional
    public ConnectionDto.Response connectAllAssets(ConnectionDto.Request request) {
        validateConsent(request);

        List<ConnectionDto.Result> results = new ArrayList<>();
        for (Institution institution : institutionService.getConnectionTargetInstitutions()) {
            CodefDto.Response codefResponse = codefClient.connectInstitution(createCodefRequest(institution));
            results.add(toConnectionResult(institution, codefResponse));
        }

        saveSuccessfulConnections(results);
        return new ConnectionDto.Response(results);
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

    private void saveSuccessfulConnections(List<ConnectionDto.Result> results) {
        List<ConnectionDto.Result> successfulResults = results.stream()
                .filter(this::isSuccessful)
                .collect(Collectors.toList());

        if (successfulResults.isEmpty()) {
            return;
        }

        try {
            connectionMapper.insertConnections(successfulResults, ConnectionDto.MOCK_LOGIN_TYPE,
                    ConnectionDto.MOCK_ID, ConnectionDto.MOCK_PASSWORD);
        } catch (DataAccessException exception) {
            throw new CustomException(ErrorCode.CONNECTION_SAVE_FAILED, exception);
        }
    }

    private boolean isSuccessful(ConnectionDto.Result result) {
        return ConnectionDto.Status.SUCCESS == result.getStatus();
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
}

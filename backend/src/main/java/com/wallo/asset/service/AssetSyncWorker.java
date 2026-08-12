package com.wallo.asset.service;

import com.wallo.asset.domain.Institution;
import com.wallo.asset.dto.AssetSyncDto;
import com.wallo.external.CodefRetryExecutor;
import com.wallo.external.auth.CodefCredential;
import com.wallo.external.auth.CodefCredentialProvider;
import com.wallo.external.client.CodefClient;
import com.wallo.external.dto.CodefDto;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Executes one institution synchronization in an independent transaction. */
@Service
public class AssetSyncWorker {

    private final CodefClient codefClient;
    private final CodefCredentialProvider codefCredentialProvider;
    private final CodefRetryExecutor codefRetryExecutor;
    private final AssetSyncService assetSyncService;

    public AssetSyncWorker(
            CodefClient codefClient,
            CodefCredentialProvider codefCredentialProvider,
            CodefRetryExecutor codefRetryExecutor,
            AssetSyncService assetSyncService
    ) {
        this.codefClient = codefClient;
        this.codefCredentialProvider = codefCredentialProvider;
        this.codefRetryExecutor = codefRetryExecutor;
        this.assetSyncService = assetSyncService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AssetSyncDto.SyncStats sync(
            long userId,
            AssetSyncDto.SyncTarget target,
            LocalDate startDate,
            LocalDate endDate
    ) {
        CodefCredential credential = codefCredentialProvider.getCredential(
                userId,
                target.getCodefOrganizationCode()
        );
        CodefDto.Request request = new CodefDto.Request(
                target.getCodefOrganizationCode(),
                target.getInstitutionType(),
                credential.loginType(),
                credential.id(),
                credential.password()
        );

        CodefDto.Response response = codefRetryExecutor.execute(
                "asset synchronization organization=" + target.getCodefOrganizationCode(),
                () -> codefClient.connectInstitution(request)
        );

        return assetSyncService.sync(
                userId,
                target.getConnectionId(),
                toInstitution(target),
                response,
                startDate,
                endDate
        );
    }

    private Institution toInstitution(AssetSyncDto.SyncTarget target) {
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

}

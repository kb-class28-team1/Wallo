package com.wallo.external.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallo.external.dto.CodefDto;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class CodefMockServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private CodefMockService service;

    @Before
    public void setUp() {
        service = new CodefMockService(objectMapper);
    }

    @Test
    public void bankAssetEndpointLoadsBankFixture() {
        CodefDto.Response response = service.getAssetResponse(
                "/mock/v1/kr/bank/p/account/account-list"
        );

        assertSuccess(response);
        CodefDto.AssetData data = objectMapper.convertValue(response.getData(), CodefDto.AssetData.class);
        assertEquals(2, data.getAccounts().size());
        assertEquals(1, data.getLoans().size());
        assertEquals(1, data.getTransactions().size());
        assertEquals(5, data.getAssetSnapshots().size());
        assertEquals("2026-08", data.getAssetSnapshots().get(4).getSnapshotMonth());
        assertEquals("40100000", data.getAssetSnapshots().get(4).getTotalAssets());
    }

    @Test
    public void bankAssetEndpointUsesOrganizationSpecificFixture() {
        CodefDto.Response response = service.getAssetResponse(
                "/mock/v1/kr/bank/p/account/account-list",
                "0088"
        );

        assertSuccess(response);
        CodefDto.AssetData data = objectMapper.convertValue(response.getData(), CodefDto.AssetData.class);
        assertEquals(1, data.getAccounts().size());
        assertEquals("223344-01-556677", data.getAccounts().get(0).getResAccount());
    }

    @Test
    public void cardAssetEndpointLoadsCardFixture() {
        CodefDto.Response response = service.getAssetResponse(
                "/mock/v1/kr/card/p/account/card-list"
        );

        assertSuccess(response);
        CodefDto.AssetData data = objectMapper.convertValue(response.getData(), CodefDto.AssetData.class);
        assertEquals(2, data.getCards().size());
        assertNull(data.getTransactions());
    }

    @Test
    public void cardAssetEndpointUsesOrganizationSpecificFixture() {
        CodefDto.Response hanaResponse = service.getAssetResponse(
                "/mock/v1/kr/card/p/account/card-list",
                "0311"
        );
        CodefDto.Response kbResponse = service.getAssetResponse(
                "/mock/v1/kr/card/p/account/card-list",
                "0301"
        );

        assertSuccess(hanaResponse);
        assertSuccess(kbResponse);
        CodefDto.AssetData hanaData = objectMapper.convertValue(
                hanaResponse.getData(), CodefDto.AssetData.class
        );
        CodefDto.AssetData kbData = objectMapper.convertValue(
                kbResponse.getData(), CodefDto.AssetData.class
        );

        assertEquals("4321-0000-0000-8765", hanaData.getCards().get(0).getResCardNo());
        assertEquals("1357-0000-0000-2468", kbData.getCards().get(0).getResCardNo());
    }

    @Test
    public void unsupportedCardAssetOrganizationReturnsNotFoundFailure() {
        CodefDto.Response response = service.getAssetResponse(
                "/mock/v1/kr/card/p/account/card-list",
                "0999"
        );

        assertEquals("CF-40400", response.getResult().getCode());
        assertNull(response.getData());
    }

    @Test
    public void stockAssetEndpointLoadsStockFixture() {
        assertSuccess(service.getAssetResponse(
                "/mock/v1/kr/stock/p/account/account-list"
        ));
    }

    @Test
    public void unknownAssetEndpointReturnsNotFoundResponse() {
        CodefDto.Response response = service.getAssetResponse("/mock/v1/kr/unsupported");

        assertEquals("CF-40400", response.getResult().getCode());
        assertNull(response.getData());
    }

    @Test
    public void cardApprovalsAreFilteredByInclusiveDateRange() {
        CodefDto.Response response = service.getCardApprovals(cardRequest("20260722", "20260726"));

        assertSuccess(response);
        List<CodefDto.CardApproval> approvals = objectMapper.convertValue(
                response.getData(),
                new TypeReference<List<CodefDto.CardApproval>>() { }
        );
        assertEquals(2, approvals.size());
        assertEquals("12345678", approvals.get(0).getResApprovalNo());
        assertEquals("87654321", approvals.get(1).getResApprovalNo());
    }

    @Test
    public void cardApprovalsContainPreviousAndCurrentMonthDeliverySamples() {
        CodefDto.Response response = service.getCardApprovals(cardRequest("20260701", "20260803"));

        assertSuccess(response);
        List<CodefDto.CardApproval> approvals = objectMapper.convertValue(
                response.getData(),
                new TypeReference<List<CodefDto.CardApproval>>() { }
        );
        assertTrue(approvals.stream().anyMatch(approval ->
                "20260702".equals(approval.getResUsedDate())
                        && "20731468".equals(approval.getResApprovalNo())
                        && "20000".equals(approval.getResUsedAmount())
        ));
        assertTrue(approvals.stream().anyMatch(approval ->
                "20260802".equals(approval.getResUsedDate())
                        && "83025197".equals(approval.getResApprovalNo())
                        && "30000".equals(approval.getResUsedAmount())
        ));
    }

    @Test
    public void bankTransactionsAreFilteredByInclusiveDateRange() {
        CodefDto.Response response = service.getBankTransactions(
                bankRequest("20260726", "20260728")
        );

        assertSuccess(response);
        List<CodefDto.BankTransaction> transactions = objectMapper.convertValue(
                response.getData(),
                new TypeReference<List<CodefDto.BankTransaction>>() { }
        );
        assertEquals(2, transactions.size());
        assertEquals("BANK-202607-0002", transactions.get(0).getResTrNo());
        assertEquals("체크가맹_배달의민족", transactions.get(0).getResAccountDesc());
        assertEquals("김철수", transactions.get(1).getResAccountDesc());
    }

    @Test
    public void additionalActiveBankUsesBankTransactionFixture() {
        CodefDto.BankTransactionRequest request = bankRequest("20260726", "20260728");
        request.setOrganization("0088");
        request.setAccount("223344-01-556677");

        CodefDto.Response response = service.getBankTransactions(request);

        assertSuccess(response);
        List<CodefDto.BankTransaction> transactions = objectMapper.convertValue(
                response.getData(),
                new TypeReference<List<CodefDto.BankTransaction>>() { }
        );
        assertEquals(0, transactions.size());
    }

    @Test
    public void bankTransactionsUseOrganizationSpecificFixtures() {
        CodefDto.BankTransactionRequest shinhanRequest = bankRequest("20260801", "20260804");
        shinhanRequest.setOrganization("0088");
        shinhanRequest.setAccount("223344-01-556677");
        CodefDto.BankTransactionRequest hanaRequest = bankRequest("20260801", "20260804");
        hanaRequest.setOrganization("0081");
        hanaRequest.setAccount("334455-01-667788");

        CodefDto.Response shinhanResponse = service.getBankTransactions(shinhanRequest);
        CodefDto.Response hanaResponse = service.getBankTransactions(hanaRequest);

        assertSuccess(shinhanResponse);
        assertSuccess(hanaResponse);
        List<CodefDto.BankTransaction> shinhanTransactions = objectMapper.convertValue(
                shinhanResponse.getData(),
                new TypeReference<List<CodefDto.BankTransaction>>() { }
        );
        List<CodefDto.BankTransaction> hanaTransactions = objectMapper.convertValue(
                hanaResponse.getData(),
                new TypeReference<List<CodefDto.BankTransaction>>() { }
        );

        assertEquals(2, shinhanTransactions.size());
        assertEquals("SHINHAN-202608-0001", shinhanTransactions.get(0).getResTrNo());
        assertEquals(2, hanaTransactions.size());
        assertEquals("HANA-202608-0001", hanaTransactions.get(0).getResTrNo());
    }

    @Test
    public void unsupportedBankOrganizationReturnsNotFoundFailure() {
        CodefDto.Response response = service.getAssetResponse(
                "/mock/v1/kr/bank/p/account/account-list",
                "0999"
        );

        assertEquals("CF-40400", response.getResult().getCode());
        assertNull(response.getData());
    }

    @Test
    public void additionalActiveCardUsesCardApprovalFixture() {
        CodefDto.CardApprovalRequest request = cardRequest("20260722", "20260726");
        request.setOrganization("0301");

        CodefDto.Response response = service.getCardApprovals(request);

        assertSuccess(response);
        List<CodefDto.CardApproval> approvals = objectMapper.convertValue(
                response.getData(),
                new TypeReference<List<CodefDto.CardApproval>>() { }
        );
        assertEquals(1, approvals.size());
        assertEquals("93000001", approvals.get(0).getResApprovalNo());
    }

    @Test
    public void cardApprovalsUseOrganizationSpecificFixtures() {
        CodefDto.Response hanaResponse = service.getCardApprovals(
                cardRequest("20260804", "20260804")
        );
        CodefDto.CardApprovalRequest kbRequest = cardRequest("20260804", "20260804");
        kbRequest.setOrganization("0301");
        CodefDto.Response kbResponse = service.getCardApprovals(kbRequest);

        assertSuccess(hanaResponse);
        assertSuccess(kbResponse);
        List<CodefDto.CardApproval> hanaApprovals = objectMapper.convertValue(
                hanaResponse.getData(),
                new TypeReference<List<CodefDto.CardApproval>>() { }
        );
        List<CodefDto.CardApproval> kbApprovals = objectMapper.convertValue(
                kbResponse.getData(),
                new TypeReference<List<CodefDto.CardApproval>>() { }
        );

        assertEquals(1, hanaApprovals.size());
        assertEquals("92000001", hanaApprovals.get(0).getResApprovalNo());
        assertEquals(1, kbApprovals.size());
        assertEquals("93000003", kbApprovals.get(0).getResApprovalNo());
    }

    @Test
    public void unsupportedCardOrganizationReturnsNotFoundFailure() {
        CodefDto.CardApprovalRequest request = cardRequest("20260801", "20260804");
        request.setOrganization("0999");

        CodefDto.Response response = service.getCardApprovals(request);

        assertEquals("CF-40400", response.getResult().getCode());
        assertNull(response.getData());
    }

    @Test
    public void savingsAccountWithoutTransactionsReturnsEmptySuccessData() {
        CodefDto.BankTransactionRequest request = bankRequest("20260701", "20260731");
        request.setAccount("987654-01-321098");

        CodefDto.Response response = service.getBankTransactions(
                request
        );

        assertSuccess(response);
        List<CodefDto.BankTransaction> transactions = objectMapper.convertValue(
                response.getData(),
                new TypeReference<List<CodefDto.BankTransaction>>() { }
        );
        assertEquals(0, transactions.size());
    }

    @Test
    public void invalidDateFormatReturnsFailureResponse() {
        CodefDto.Response response = service.getCardApprovals(cardRequest("2026-07-01", "20260731"));

        assertEquals("CF-40000", response.getResult().getCode());
        assertEquals(null, response.getData());
    }

    @Test
    public void reversedDateRangeReturnsFailureResponse() {
        CodefDto.Response response = service.getCardApprovals(cardRequest("20260731", "20260701"));

        assertEquals("CF-40000", response.getResult().getCode());
        assertEquals(null, response.getData());
    }

    @Test
    public void unknownBankAccountReturnsNotFoundFailure() {
        CodefDto.BankTransactionRequest request = bankRequest("20260701", "20260731");
        request.setAccount("000000-00-000000");

        CodefDto.Response response = service.getBankTransactions(request);

        assertEquals("CF-40401", response.getResult().getCode());
        assertEquals(null, response.getData());
    }

    @Test
    public void incomeProofEndpointLoadsPreviousYearFixture() {
        CodefDto.Response response = service.getIncomeProof(
                new CodefDto.IncomeProofRequest(
                        "0001", "1", "mock_id", "mock_password", "2025", "2025"
                )
        );

        assertSuccess(response);
        CodefDto.IncomeProofData data = objectMapper.convertValue(
                response.getData(),
                CodefDto.IncomeProofData.class
        );
        assertEquals(3, data.getResPaymentDetailsStatusList().size());
        assertEquals("50000000", data.getResPaymentDetailsStatusList().get(0).getResPaidTotalAmt());
    }

    @Test
    public void incomeProofEndpointRejectsYearWithoutFixture() {
        CodefDto.Response response = service.getIncomeProof(
                new CodefDto.IncomeProofRequest(
                        "0001", "1", "mock_id", "mock_password", "2024", "2024"
                )
        );

        assertEquals("CF-40400", response.getResult().getCode());
        assertNull(response.getData());
    }

    private CodefDto.CardApprovalRequest cardRequest(String startDate, String endDate) {
        return new CodefDto.CardApprovalRequest(
                "0311",
                "1",
                "mock_id",
                "mock_password",
                startDate,
                endDate
        );
    }

    private CodefDto.BankTransactionRequest bankRequest(String startDate, String endDate) {
        return new CodefDto.BankTransactionRequest(
                "0004",
                "1",
                "mock_id",
                "mock_password",
                "123456-01-789012",
                startDate,
                endDate
        );
    }

    private void assertSuccess(CodefDto.Response response) {
        assertNotNull(response);
        assertNotNull(response.getResult());
        assertEquals("CF-00000", response.getResult().getCode());
        assertNotNull(response.getData());
    }
}

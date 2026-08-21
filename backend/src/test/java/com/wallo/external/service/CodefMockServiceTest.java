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
        assertEquals(1, data.getAccounts().size());
        assertEquals("111111-01-222222", data.getAccounts().get(0).getResAccount());
        assertEquals(1, data.getLoans().size());
        assertEquals(0, data.getTransactions().size());
        assertEquals(3, data.getAssetSnapshots().size());
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
        assertEquals("222222-01-333333", data.getAccounts().get(0).getResAccount());
        assertEquals(0, data.getLoans().size());
    }

    @Test
    public void cardAssetEndpointLoadsCardFixture() {
        CodefDto.Response response = service.getAssetResponse(
                "/mock/v1/kr/card/p/account/card-list"
        );

        assertSuccess(response);
        CodefDto.AssetData data = objectMapper.convertValue(response.getData(), CodefDto.AssetData.class);
        assertEquals(1, data.getCards().size());
        assertEquals("4555-0000-0000-1222", data.getCards().get(0).getResCardNo());
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

        assertEquals("4555-0000-0000-1222", hanaData.getCards().get(0).getResCardNo());
        assertEquals("5666-0000-0000-2333", kbData.getCards().get(0).getResCardNo());
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
        CodefDto.Response response = service.getCardApprovals(cardRequest("20260701", "20260704"));

        assertSuccess(response);
        List<CodefDto.CardApproval> approvals = objectMapper.convertValue(
                response.getData(),
                new TypeReference<List<CodefDto.CardApproval>>() { }
        );
        assertEquals(2, approvals.size());
        assertEquals("GOAL-SAVER-CARD-202607-0001", approvals.get(0).getResApprovalNo());
        assertEquals("GOAL-SAVER-CARD-202607-0004", approvals.get(1).getResApprovalNo());
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
                "20260701".equals(approval.getResUsedDate())
                        && "GOAL-SAVER-CARD-202607-0001".equals(approval.getResApprovalNo())
                        && "26000".equals(approval.getResUsedAmount())
        ));
        assertTrue(approvals.stream().anyMatch(approval ->
                "20260801".equals(approval.getResUsedDate())
                        && "GOAL-SAVER-CARD-0001".equals(approval.getResApprovalNo())
                        && "27000".equals(approval.getResUsedAmount())
        ));
    }

    @Test
    public void bankTransactionsAreFilteredByInclusiveDateRange() {
        CodefDto.Response response = service.getBankTransactions(
                bankRequest("20260805", "20260806")
        );

        assertSuccess(response);
        List<CodefDto.BankTransaction> transactions = objectMapper.convertValue(
                response.getData(),
                new TypeReference<List<CodefDto.BankTransaction>>() { }
        );
        assertEquals(2, transactions.size());
        assertEquals("GOAL-SAVER-BANK-202608-0006", transactions.get(0).getResTrNo());
        assertEquals("휴대폰 요금", transactions.get(0).getResAccountDesc());
        assertEquals("GOAL-SAVER-BANK-202608-0007", transactions.get(1).getResTrNo());
        assertEquals("넷플릭스 구독", transactions.get(1).getResAccountDesc());
    }

    @Test
    public void additionalActiveBankUsesBankTransactionFixture() {
        CodefDto.BankTransactionRequest request = bankRequest("20260726", "20260728");
        request.setOrganization("0088");
        request.setAccount("222222-01-333333");

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
        shinhanRequest.setAccount("222222-01-333333");
        CodefDto.BankTransactionRequest hanaRequest = bankRequest("20260801", "20260804");
        hanaRequest.setOrganization("0081");
        hanaRequest.setAccount("333333-01-444444");

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

        assertEquals(1, shinhanTransactions.size());
        assertEquals("GOAL-SAVER-BANK-202608-0012", shinhanTransactions.get(0).getResTrNo());
        assertEquals(1, hanaTransactions.size());
        assertEquals("GOAL-SAVER-BANK-202608-0013", hanaTransactions.get(0).getResTrNo());
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
        CodefDto.CardApprovalRequest request = cardRequest("20260702", "20260703");
        request.setOrganization("0301");

        CodefDto.Response response = service.getCardApprovals(request);

        assertSuccess(response);
        List<CodefDto.CardApproval> approvals = objectMapper.convertValue(
                response.getData(),
                new TypeReference<List<CodefDto.CardApproval>>() { }
        );
        assertEquals(2, approvals.size());
        assertEquals("GOAL-SAVER-CARD-202607-0002", approvals.get(0).getResApprovalNo());
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
        assertEquals("GOAL-SAVER-CARD-0005", hanaApprovals.get(0).getResApprovalNo());
        assertEquals(1, kbApprovals.size());
        assertEquals("GOAL-SAVER-CARD-0006", kbApprovals.get(0).getResApprovalNo());
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
    public void loanAccountWithoutTransactionsReturnsEmptySuccessData() {
        CodefDto.BankTransactionRequest request = bankRequest("20260701", "20260731");
        request.setAccount("LOAN-2021-0007");

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
        assertEquals(1, data.getResPaymentDetailsStatusList().size());
        assertEquals("2025", data.getResPaymentDetailsStatusList().get(0).getResAttrYear());
        assertEquals("38400000", data.getResPaymentDetailsStatusList().get(0).getResPaidTotalAmt());
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
                "111111-01-222222",
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

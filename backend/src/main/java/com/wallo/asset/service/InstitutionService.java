package com.wallo.asset.service;

import com.wallo.asset.domain.Institution;
import com.wallo.asset.dto.InstitutionDto;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class InstitutionService {

    private static final String KB_LOGO_URL = "https://www.kbstar.com/favicon.ico";
    private static final String HANA_CARD_LOGO_URL = "https://www.hanacard.co.kr/favicon.ico";
    private static final String KIWOOM_LOGO_URL = "https://www.kiwoom.com/favicon.ico";

    public List<Institution> getConnectionTargetInstitutions() {
        return Arrays.asList(
                new Institution("0004", "KB국민은행", "BANK", KB_LOGO_URL),
                new Institution("0311", "하나카드", "CARD", HANA_CARD_LOGO_URL),
                new Institution("0264", "키움증권", "STOCK", KIWOOM_LOGO_URL)
        );
    }

    public InstitutionDto.Response getInstitutions() {
        return new InstitutionDto.Response(
                Arrays.asList(
                        new InstitutionDto.Item(
                                "0004",
                                "KB국민은행",
                                KB_LOGO_URL,
                                Arrays.asList("입출금", "예적금", "대출")
                        ),
                        new InstitutionDto.Item(
                                "0088",
                                "신한은행",
                                "https://www.shinhan.com/favicon.ico",
                                Arrays.asList("입출금", "예적금", "대출")
                        )
                ),
                Arrays.asList(
                        new InstitutionDto.Item(
                                "0311",
                                "하나카드",
                                HANA_CARD_LOGO_URL,
                                Arrays.asList("신용/체크카드 결제내역")
                        ),
                        new InstitutionDto.Item(
                                "0071",
                                "BC카드",
                                "https://www.bccard.com/favicon.ico",
                                Arrays.asList("신용/체크카드 결제내역")
                        )
                ),
                Arrays.asList(
                        new InstitutionDto.Item(
                                "0264",
                                "키움증권",
                                KIWOOM_LOGO_URL,
                                Arrays.asList("주식", "CMA", "연금저축")
                        ),
                        new InstitutionDto.Item(
                                "0238",
                                "미래에셋증권",
                                "https://securities.miraeasset.com/favicon.ico",
                                Arrays.asList("주식", "CMA", "연금저축")
                        )
                )
        );
    }
}

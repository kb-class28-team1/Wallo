package com.wallo.asset.dto;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

public final class AssetDto {

    private AssetDto() {
    }

    @Getter
    @NoArgsConstructor
    public static class Response {
        private long totalAssets;
        private long previousMonthTotalAssets;
        private List<Account> accounts;
        private List<Card> cards;
        private List<Stock> stocks;
        private List<CategoryBreakdown> assetCategoryBreakdown;
        private List<AssetTrend> assetTrend;

        public Response(
                long totalAssets,
                long previousMonthTotalAssets,
                List<Account> accounts,
                List<Card> cards,
                List<Stock> stocks,
                List<CategoryBreakdown> assetCategoryBreakdown,
                List<AssetTrend> assetTrend
        ) {
            this.totalAssets = totalAssets;
            this.previousMonthTotalAssets = previousMonthTotalAssets;
            this.accounts = copyOrEmpty(accounts);
            this.cards = copyOrEmpty(cards);
            this.stocks = copyOrEmpty(stocks);
            this.assetCategoryBreakdown = copyOrEmpty(assetCategoryBreakdown);
            this.assetTrend = sortAssetTrend(assetTrend);
        }

        private static List<AssetTrend> sortAssetTrend(List<AssetTrend> assetTrend) {
            List<AssetTrend> sortedAssetTrend = copyOrEmpty(assetTrend);
            sortedAssetTrend.sort(Comparator.comparing(trend -> YearMonth.parse(trend.getMonth())));
            return sortedAssetTrend;
        }

        private static <T> List<T> copyOrEmpty(List<T> values) {
            return values == null ? new ArrayList<>() : new ArrayList<>(values);
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Account {
        private String bank;
        private long balance;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Card {
        private String company;
        private long billedAmount;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Stock {
        private String company;
        private long evalAmount;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryBreakdown {
        private String category;
        private long amount;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssetTrend {
        private String month;
        private long amount;
    }
}

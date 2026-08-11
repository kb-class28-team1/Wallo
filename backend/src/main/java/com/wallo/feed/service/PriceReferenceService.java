package com.wallo.feed.service;

import com.wallo.feed.domain.FoodCostReferenceRow;
import com.wallo.feed.domain.PriceReferenceRow;
import com.wallo.feed.dto.FeedDtos.AnalysisResponse;
import com.wallo.feed.dto.FeedDtos.DetectedItem;
import com.wallo.feed.dto.FeedDtos.PriceReference;
import com.wallo.feed.mapper.FoodCostReferenceMapper;
import com.wallo.feed.mapper.PriceReferenceMapper;
import com.wallo.feed.price.RestaurantPriceCandidate;
import com.wallo.feed.price.RestaurantPriceClient;
import com.wallo.feed.price.ShoppingPriceCandidate;
import com.wallo.feed.price.ShoppingPriceClient;
import com.wallo.feed.service.RecipeIngredientCostService.RecipeCost;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/** 저장 시세를 우선 사용하고, 없는 물품만 병렬 검색한 뒤 서버에서 금액을 계산한다. */
@Service
public class PriceReferenceService {
    private static final Logger log = LoggerFactory.getLogger(PriceReferenceService.class);
    private static final int MAX_SEARCH_ITEMS = 3;
    private static final int MAX_PRICE = 10_000_000;
    private static final double MIN_SEARCH_CONFIDENCE = 0.55;
    private static final String COUNT_UNIT_NAMES =
            "개|병|봉|롤|구|팩|모|캔|매|박스|통|꼬치|권|그루|벌|대|마리|장|켤레|송이";
    private static final Pattern UNIT_TOKEN = Pattern.compile(
            "(?i)(\\d+(?:\\.\\d+)?(?:ml|l|kg|g|" + COUNT_UNIT_NAMES + "))");
    private static final Pattern COUNT_UNIT_TOKEN = Pattern.compile(
            "(?i)(\\d+)\\s*(" + COUNT_UNIT_NAMES + ")");
    private static final Pattern GATHERED_PACKAGE_COUNT_TOKEN = Pattern.compile(
            "(?i)(\\d+)\\s*(?:개|마리|미|송이|그루)(?:입)?");
    private static final Pattern GATHERED_COUNT_RANGE_TOKEN = Pattern.compile(
            "(?i)(\\d+)\\s*(?:~|-|–|부터)\\s*(\\d+)\\s*(?:개|마리|미|송이|그루|알|입)");
    private static final Pattern PRODUCT_WORD_TOKEN = Pattern.compile("[0-9a-zA-Z가-힣]+");
    private static final List<String> EXCLUDED_TITLE_WORDS = List.of(
            "중고", "리퍼", "렌탈", "대여", "정기구독", "월납", "공병", "빈병");
    private static final List<String> GATHERED_EXCLUDED_TITLE_WORDS = List.of(
            "통조림", "캔참치", "참치캔", "성게알", "필렛", "스테이크",
            "말랭이", "가루", "분말", "즙", "주스", "칩", "스낵",
            "모종", "씨앗", "종자", "모형", "장난감", "인형", "키링",
            "껍데기", "껍질");
    private static final List<String> GENERIC_CAFE_PRODUCT_WORDS = List.of(
            "커피", "캔커피", "컵커피", "카페라떼", "라떼", "아메리카노", "음료", "편의점");

    private final PriceReferenceMapper priceReferenceMapper;
    private final ShoppingPriceClient shoppingPriceClient;
    private final FoodCostReferenceMapper foodCostReferenceMapper;
    private final RestaurantPriceClient restaurantPriceClient;
    private final RecipeIngredientCostService recipeIngredientCostService;
    private final Executor searchExecutor;
    private final Clock clock;

    @Autowired
    public PriceReferenceService(
            PriceReferenceMapper priceReferenceMapper,
            ShoppingPriceClient shoppingPriceClient,
            FoodCostReferenceMapper foodCostReferenceMapper,
            RestaurantPriceClient restaurantPriceClient,
            RecipeIngredientCostService recipeIngredientCostService,
            @Qualifier("shoppingPriceExecutor") Executor searchExecutor,
            Clock clock) {
        this.priceReferenceMapper = priceReferenceMapper;
        this.shoppingPriceClient = shoppingPriceClient;
        this.foodCostReferenceMapper = foodCostReferenceMapper;
        this.restaurantPriceClient = restaurantPriceClient;
        this.recipeIngredientCostService = recipeIngredientCostService;
        this.searchExecutor = searchExecutor;
        this.clock = clock;
    }

    /** 기존 단위 테스트와 호출부의 호환을 위한 상품 시세 전용 생성자. */
    public PriceReferenceService(
            PriceReferenceMapper priceReferenceMapper,
            ShoppingPriceClient shoppingPriceClient,
            Executor searchExecutor,
            Clock clock) {
        this(priceReferenceMapper, shoppingPriceClient, null, null, null, searchExecutor, clock);
    }

    /** 기존 홈메이드 음식 단위 테스트와 호출부의 호환을 위한 생성자. */
    public PriceReferenceService(
            PriceReferenceMapper priceReferenceMapper,
            ShoppingPriceClient shoppingPriceClient,
            FoodCostReferenceMapper foodCostReferenceMapper,
            RestaurantPriceClient restaurantPriceClient,
            Executor searchExecutor,
            Clock clock) {
        this(priceReferenceMapper, shoppingPriceClient, foodCostReferenceMapper,
                restaurantPriceClient, null, searchExecutor, clock);
    }

    public AnalysisResponse enrich(AnalysisResponse analysis) {
        if (analysis == null || analysis.detectedItems().isEmpty()) {
            return analysis;
        }
        try {
            return enrichSafely(analysis);
        } catch (DataAccessException exception) {
            // 시세 테이블 문제로 영상 분석 자체를 실패시키지 않는다.
            log.warn("시세 DB 조회에 실패해 기존 AI 분석 결과를 사용합니다: {}",
                    exception.getClass().getSimpleName());
            return analysis;
        }
    }

    private AnalysisResponse enrichSafely(AnalysisResponse analysis) {
        List<DetectedItem> detectedItems = analysis.detectedItems().stream()
                .filter(item -> item != null && item.itemName() != null && !item.itemName().isBlank())
                .limit(MAX_SEARCH_ITEMS)
                .toList();
        boolean gatheredComparison = detectedItems.stream().anyMatch(this::isGathered);
        List<DetectedItem> items = gatheredComparison
                ? detectedItems.stream().filter(this::isGathered).toList()
                : detectedItems;
        Map<Integer, PriceReferenceRow> resolvedRows = new HashMap<>();
        Map<Integer, FoodCostReferenceRow> resolvedFoodRows = new HashMap<>();
        Map<Integer, RecipeCost> recipeCosts = new HashMap<>();
        Map<Integer, CompletableFuture<Optional<ShoppingPriceCandidate>>> searches =
                new LinkedHashMap<>();
        Map<Integer, CompletableFuture<Optional<RestaurantPriceCandidate>>> restaurantSearches =
                new LinkedHashMap<>();

        for (int index = 0; index < items.size(); index++) {
            int itemIndex = index;
            DetectedItem item = items.get(index);
            if (isHomemade(item) && foodCostReferenceMapper != null) {
                findRecipeCost(item, analysis.category())
                        .ifPresent(cost -> recipeCosts.put(itemIndex, cost));
                FoodCostReferenceRow cached = foodCostReferenceMapper.findBestMatch(
                        normalizeKey(item.itemName()), normalizeUnit(item.unit()), analysis.category());
                if (isUsable(cached)) {
                    applyRecipeCost(cached, recipeCosts.get(index));
                    resolvedFoodRows.put(index, cached);
                } else if (item.confidence() >= MIN_SEARCH_CONFIDENCE
                        && (item.ingredientCostPerUnit() > 0 || recipeCosts.containsKey(index))) {
                    restaurantSearches.put(index, CompletableFuture.supplyAsync(
                            () -> findRestaurantCandidate(item), searchExecutor)
                            .exceptionally(exception -> Optional.empty()));
                }
                continue;
            }
            PriceReferenceRow cached = priceReferenceMapper.findBestMatch(
                    normalizeKey(item.itemName()),
                    normalizeKey(item.brand()),
                    normalizeUnit(item.unit()),
                    analysis.category());
            if (isUsable(cached)) {
                resolvedRows.put(index, cached);
                if (isGathered(item)) {
                    searches.put(index, CompletableFuture.supplyAsync(
                            () -> findLowestCandidate(item, analysis.category()), searchExecutor)
                            .exceptionally(exception -> Optional.empty()));
                }
            } else if (isGathered(item) || item.confidence() >= MIN_SEARCH_CONFIDENCE) {
                searches.put(index, CompletableFuture.supplyAsync(
                        () -> findLowestCandidate(item, analysis.category()), searchExecutor)
                        .exceptionally(exception -> Optional.empty()));
            }
        }

        if (!searches.isEmpty()) {
            CompletableFuture.allOf(searches.values().toArray(CompletableFuture[]::new)).join();
            searches.forEach((index, future) -> future.join().ifPresent(candidate -> {
                PriceReferenceRow cached = resolvedRows.get(index);
                if (isUsable(cached) && cached.getLowestPrice() <= candidate.price()) {
                    return;
                }
                PriceReferenceRow stored = store(items.get(index), analysis.category(), candidate);
                if (stored != null) {
                    resolvedRows.put(index, stored);
                }
            }));
        }

        if (!restaurantSearches.isEmpty()) {
            CompletableFuture.allOf(
                    restaurantSearches.values().toArray(CompletableFuture[]::new)).join();
            restaurantSearches.forEach((index, future) -> {
                FoodCostReferenceRow stored = storeFoodCost(
                        items.get(index), analysis.category(), future.join().orElse(null),
                        recipeCosts.get(index));
                if (stored != null) {
                    resolvedFoodRows.put(index, stored);
                }
            });
        }

        return calculate(analysis, items, resolvedRows, resolvedFoodRows);
    }

    private Optional<RestaurantPriceCandidate> findRestaurantCandidate(DetectedItem item) {
        if (restaurantPriceClient == null) {
            return Optional.empty();
        }
        List<RestaurantPriceCandidate> candidates = restaurantPriceClient
                .search(item.itemName(), item.unit()).stream()
                .filter(this::isUsable)
                .map(candidate -> normalizePackagePrice(item, candidate))
                .sorted(Comparator.comparingInt(RestaurantPriceCandidate::price))
                .toList();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(candidates.get((candidates.size() - 1) / 2));
    }

    private RestaurantPriceCandidate normalizePackagePrice(
            DetectedItem item, RestaurantPriceCandidate candidate) {
        String countUnit = singleCountUnit(item.unit());
        int packageQuantity = countUnit == null ? 1 : packageQuantity(candidate.title(), countUnit);
        if (packageQuantity <= 1) {
            return candidate;
        }
        int unitPrice = Math.max(1,
                (int) Math.ceil((double) candidate.price() / packageQuantity));
        return new RestaurantPriceCandidate(
                candidate.title(), unitPrice, candidate.source(), candidate.sourceUrl());
    }

    private Optional<ShoppingPriceCandidate> findLowestCandidate(
            DetectedItem item, String category) {
        List<ShoppingPriceCandidate> candidates = shoppingPriceClient
                .search(item.itemName(), item.brand(), item.unit());
        if (isGathered(item)) {
            Optional<ShoppingPriceCandidate> gatheredMatch = lowestGatheredCandidate(
                    item, candidates);
            if (gatheredMatch.isPresent()) {
                return gatheredMatch;
            }
            List<ShoppingPriceCandidate> rawProductCandidates = shoppingPriceClient.search(
                    item.itemName() + " 생물 원물", "", item.unit());
            gatheredMatch = lowestGatheredCandidate(item, rawProductCandidates);
            if (gatheredMatch.isPresent()) {
                return gatheredMatch;
            }
            List<ShoppingPriceCandidate> countCandidates = shoppingPriceClient.search(
                    item.itemName() + " 판매 단위 개수", "", "");
            gatheredMatch = lowestGatheredCandidate(item, countCandidates);
            if (gatheredMatch.isPresent()) {
                return gatheredMatch;
            }
            return Optional.empty();
        }
        Optional<ShoppingPriceCandidate> strictMatch = candidates.stream()
                .filter(candidate -> matches(item, candidate))
                .map(candidate -> normalizePackagePrice(item, candidate))
                .min(Comparator.comparingInt(ShoppingPriceCandidate::price));
        if (strictMatch.isPresent() || !"CAFE".equals(category)) {
            return strictMatch;
        }
        // OCR이 상품명을 길게 붙이거나 용량을 조금 다르게 읽은 편의점 음료만 핵심 단어로 재검증한다.
        return candidates.stream()
                .filter(candidate -> matchesCafeProduct(item, candidate))
                .map(candidate -> normalizePackagePrice(item, candidate))
                .min(Comparator.comparingInt(ShoppingPriceCandidate::price));
    }

    private Optional<ShoppingPriceCandidate> lowestGatheredCandidate(
            DetectedItem item, List<ShoppingPriceCandidate> candidates) {
        return candidates.stream()
                .filter(candidate -> matchesGatheredItem(item, candidate))
                .filter(candidate -> hasGatheredPackageCount(candidate.title()))
                .map(candidate -> normalizeGatheredPackagePrice(item, candidate))
                .min(Comparator.comparingInt(ShoppingPriceCandidate::price));
    }

    private ShoppingPriceCandidate normalizeGatheredPackagePrice(
            DetectedItem item, ShoppingPriceCandidate candidate) {
        String normalizedTitle = normalizePackageTitle(candidate.title());
        int packageQuantity = gatheredPackageQuantity(normalizedTitle);
        if (packageQuantity <= 1) {
            return normalizePackagePrice(item, candidate);
        }
        int unitPrice = Math.max(1,
                (int) Math.ceil((double) candidate.price() / packageQuantity));
        return new ShoppingPriceCandidate(
                candidate.title(), unitPrice, candidate.source(),
                candidate.sourceUrl(), candidate.delivery());
    }

    private boolean hasGatheredPackageCount(String title) {
        return gatheredPackageQuantity(normalizePackageTitle(title)) > 0;
    }

    private String normalizePackageTitle(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replace(" ", "");
    }

    private int gatheredPackageQuantity(String normalizedTitle) {
        Matcher rangeMatcher = GATHERED_COUNT_RANGE_TOKEN.matcher(normalizedTitle);
        if (rangeMatcher.find()) {
            int lower = Integer.parseInt(rangeMatcher.group(1));
            int upper = Integer.parseInt(rangeMatcher.group(2));
            return Math.max(1, (int) Math.round((lower + upper) / 2.0));
        }
        Matcher matcher = GATHERED_PACKAGE_COUNT_TOKEN.matcher(normalizedTitle);
        int packageQuantity = 0;
        while (matcher.find()) {
            packageQuantity = Math.max(packageQuantity, Integer.parseInt(matcher.group(1)));
        }
        return packageQuantity;
    }

    private ShoppingPriceCandidate normalizePackagePrice(
            DetectedItem item, ShoppingPriceCandidate candidate) {
        String countUnit = singleCountUnit(item.unit());
        if (countUnit == null) {
            return candidate;
        }
        int packageQuantity = packageQuantity(candidate.title(), countUnit);
        if (packageQuantity <= 1) {
            return candidate;
        }
        int unitPrice = Math.max(1,
                (int) Math.ceil((double) candidate.price() / packageQuantity));
        return new ShoppingPriceCandidate(
                candidate.title(), unitPrice, candidate.source(),
                candidate.sourceUrl(), candidate.delivery());
    }

    private String singleCountUnit(String unit) {
        Matcher matcher = COUNT_UNIT_TOKEN.matcher(normalizeUnit(unit));
        while (matcher.find()) {
            if (Integer.parseInt(matcher.group(1)) == 1) {
                return matcher.group(2).toLowerCase(Locale.ROOT);
            }
        }
        return null;
    }

    private int packageQuantity(String title, String countUnit) {
        Pattern packagePattern = Pattern.compile(
                "(?i)(\\d+)\\s*" + Pattern.quote(countUnit) + "(?:입)?");
        Matcher matcher = packagePattern.matcher(normalizeKey(title));
        int quantity = 1;
        while (matcher.find()) {
            quantity = Math.max(quantity, Integer.parseInt(matcher.group(1)));
        }
        return quantity;
    }

    private boolean matches(DetectedItem item, ShoppingPriceCandidate candidate) {
        if (!isValidCandidate(candidate)) {
            return false;
        }
        String title = normalizeKey(candidate.title());
        if (title.isBlank() || EXCLUDED_TITLE_WORDS.stream().anyMatch(title::contains)) {
            return false;
        }
        String itemName = normalizeKey(item.itemName());
        if (!title.contains(itemName)) {
            return false;
        }
        String brand = normalizeKey(item.brand());
        if (!brand.isBlank() && !title.contains(brand)) {
            return false;
        }
        return requiredUnitTokens(item.unit()).stream().allMatch(title::contains);
    }

    private boolean matchesGatheredItem(
            DetectedItem item, ShoppingPriceCandidate candidate) {
        if (!matches(item, candidate)) {
            return false;
        }
        String title = normalizeKey(candidate.title());
        return GATHERED_EXCLUDED_TITLE_WORDS.stream().noneMatch(title::contains);
    }

    private boolean matchesCafeProduct(
            DetectedItem item, ShoppingPriceCandidate candidate) {
        if (!isValidCandidate(candidate)) {
            return false;
        }
        String title = normalizeKey(candidate.title());
        if (EXCLUDED_TITLE_WORDS.stream().anyMatch(title::contains)) {
            return false;
        }
        String brand = normalizeKey(item.brand());
        if (!brand.isBlank() && !title.contains(brand)) {
            return false;
        }

        String itemName = normalizeKey(item.itemName());
        List<String> matchingWords = productWords(candidate.title()).stream()
                .filter(itemName::contains)
                .distinct()
                .toList();
        boolean hasSpecificWord = matchingWords.stream()
                .anyMatch(word -> !GENERIC_CAFE_PRODUCT_WORDS.contains(word));
        return matchingWords.size() >= 2 && hasSpecificWord;
    }

    private boolean isValidCandidate(ShoppingPriceCandidate candidate) {
        return candidate != null && candidate.price() > 0 && candidate.price() <= MAX_PRICE
                && candidate.title() != null && candidate.sourceUrl() != null
                && candidate.sourceUrl().startsWith("http");
    }

    private List<String> productWords(String value) {
        List<String> words = new ArrayList<>();
        Matcher matcher = PRODUCT_WORD_TOKEN.matcher(value == null ? "" : value);
        while (matcher.find()) {
            String word = normalizeKey(matcher.group());
            if (word.length() >= 2 && !UNIT_TOKEN.matcher(word).matches()) {
                words.add(word);
            }
        }
        return words;
    }

    private List<String> requiredUnitTokens(String unit) {
        String normalized = normalizeUnit(unit);
        List<String> tokens = new ArrayList<>();
        Matcher matcher = UNIT_TOKEN.matcher(normalized);
        while (matcher.find()) {
            String token = matcher.group(1).toLowerCase(Locale.ROOT);
            if (token.matches("1(?:" + COUNT_UNIT_NAMES + ")")) {
                continue;
            }
            tokens.add(token);
        }
        return tokens;
    }

    private PriceReferenceRow store(
            DetectedItem item, String category, ShoppingPriceCandidate candidate) {
        PriceReferenceRow row = new PriceReferenceRow();
        row.setNormalizedItemName(normalizeKey(item.itemName()));
        row.setDisplayItemName(limit(candidate.title(), 200));
        row.setBrand(limit(blankToEmpty(item.brand()), 100));
        row.setUnit(limit(normalizeDisplayUnit(item.unit()), 50));
        row.setCategory(category);
        row.setLowestPrice(Math.min(MAX_PRICE, candidate.price()));
        row.setSource(limit(candidate.source(), 100));
        row.setSourceUrl(limit(candidate.sourceUrl(), 1000));
        row.setObservedAt(LocalDateTime.now(clock));
        row.setSearchConfidence(searchConfidence(item));
        priceReferenceMapper.upsert(row);
        return priceReferenceMapper.findBestMatch(
                row.getNormalizedItemName(), normalizeKey(row.getBrand()),
                normalizeUnit(row.getUnit()), category);
    }

    private FoodCostReferenceRow storeFoodCost(
            DetectedItem item, String category, RestaurantPriceCandidate candidate,
            RecipeCost recipeCost) {
        int ingredientCost = recipeCost == null
                ? Math.min(MAX_PRICE, item.ingredientCostPerUnit())
                : Math.min(MAX_PRICE, recipeCost.ingredientCost());
        int restaurantPrice = candidate == null
                ? Math.min(MAX_PRICE, item.restaurantPricePerUnit())
                : Math.min(MAX_PRICE, candidate.price());
        if (ingredientCost <= 0 || restaurantPrice <= 0) {
            return null;
        }
        FoodCostReferenceRow row = new FoodCostReferenceRow();
        row.setNormalizedDishName(normalizeKey(item.itemName()));
        row.setDisplayDishName(limit(
                candidate == null ? item.itemName() : candidate.title(), 200));
        row.setUnit(limit(normalizeDisplayUnit(item.unit()), 50));
        row.setCategory(category);
        row.setIngredientCost(ingredientCost);
        row.setRestaurantPrice(restaurantPrice);
        row.setRestaurantSource(limit(
                candidate == null ? "Gemini 예상 음식점 가격" : candidate.source(), 100));
        row.setRestaurantSourceUrl(candidate == null ? null : limit(candidate.sourceUrl(), 1000));
        row.setIngredientBasis(limit(
                recipeCost == null ? item.ingredientBasis() : recipeCost.ingredientBasis(), 500));
        row.setObservedAt(LocalDateTime.now(clock));
        row.setSearchConfidence(candidate == null
                ? Math.min(0.65, item.confidence())
                : Math.min(0.90, item.confidence()));
        foodCostReferenceMapper.upsert(row);
        return foodCostReferenceMapper.findBestMatch(
                row.getNormalizedDishName(), normalizeUnit(row.getUnit()), category);
    }

    private Optional<RecipeCost> findRecipeCost(DetectedItem item, String category) {
        if (recipeIngredientCostService == null) {
            return Optional.empty();
        }
        try {
            return recipeIngredientCostService.calculate(
                    normalizeKey(item.itemName()), normalizeUnit(item.unit()), category);
        } catch (DataAccessException exception) {
            // 신규 레시피 테이블이 아직 적용되지 않은 환경에서도 기존 분석은 유지한다.
            log.warn("레시피 재료 시세 조회에 실패해 AI 재료비를 사용합니다: {}",
                    exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private void applyRecipeCost(FoodCostReferenceRow row, RecipeCost recipeCost) {
        if (row == null || recipeCost == null) {
            return;
        }
        String ingredientBasis = limit(recipeCost.ingredientBasis(), 500);
        boolean changed = !Integer.valueOf(recipeCost.ingredientCost()).equals(row.getIngredientCost())
                || !ingredientBasis.equals(row.getIngredientBasis());
        row.setIngredientCost(recipeCost.ingredientCost());
        row.setIngredientBasis(ingredientBasis);
        if (changed) {
            foodCostReferenceMapper.upsert(row);
        }
    }

    private AnalysisResponse calculate(
            AnalysisResponse analysis,
            List<DetectedItem> items,
            Map<Integer, PriceReferenceRow> rows,
            Map<Integer, FoodCostReferenceRow> foodRows) {
        List<DetectedItem> resolvedItems = new ArrayList<>();
        List<PriceReference> references = new ArrayList<>();
        long referenceValue = 0;
        long ingredientCostTotal = 0;
        for (int index = 0; index < items.size(); index++) {
            DetectedItem item = items.get(index);
            FoodCostReferenceRow foodRow = foodRows.get(index);
            if (isHomemade(item) && isUsable(foodRow)) {
                int quantity = Math.max(1, Math.min(99, item.quantity()));
                int restaurantPrice = Math.min(MAX_PRICE, foodRow.getRestaurantPrice());
                int ingredientCost = Math.min(MAX_PRICE, foodRow.getIngredientCost());
                int totalValue = safeMultiply(restaurantPrice, quantity);
                referenceValue = Math.min(10_000_000_000L, referenceValue + totalValue);
                ingredientCostTotal = Math.min(10_000_000_000L,
                        ingredientCostTotal + safeMultiply(ingredientCost, quantity));
                resolvedItems.add(new DetectedItem(
                        item.itemName(), item.brand(), normalizeDisplayUnit(item.unit()), quantity,
                        restaurantPrice, totalValue, item.confidence(), item.evidence(),
                        "HOMEMADE", ingredientCost, restaurantPrice,
                        foodRow.getIngredientBasis()));
                references.add(new PriceReference(
                        item.itemName(), "", foodRow.getUnit(), restaurantPrice,
                        foodRow.getRestaurantSource(), foodRow.getRestaurantSourceUrl(),
                        foodRow.getObservedAt()));
                continue;
            }
            PriceReferenceRow row = rows.get(index);
            int unitPrice = isUsable(row) ? Math.min(MAX_PRICE, row.getLowestPrice()) : 0;
            int quantity = Math.max(1, Math.min(99, item.quantity()));
            int totalValue = safeMultiply(unitPrice, quantity);
            referenceValue = Math.min(10_000_000_000L, referenceValue + totalValue);
            resolvedItems.add(new DetectedItem(
                    item.itemName(), item.brand(), normalizeDisplayUnit(item.unit()), quantity,
                    unitPrice, totalValue, item.confidence(), item.evidence(),
                    item.comparisonType(), item.ingredientCostPerUnit(),
                    item.restaurantPricePerUnit(), item.ingredientBasis()));
            if (unitPrice > 0) {
                references.add(new PriceReference(
                        item.itemName(), isUsable(row) ? row.getBrand() : item.brand(),
                        isUsable(row) ? row.getUnit() : normalizeDisplayUnit(item.unit()),
                        unitPrice,
                        row.getSource(), row.getSourceUrl(), row.getObservedAt()));
            }
        }

        boolean gatheredComparison = items.stream().anyMatch(this::isGathered);
        long actualCost = gatheredComparison
                ? 0
                : analysis.actualCost() > 0 ? analysis.actualCost() : ingredientCostTotal;
        long difference = calculateDifference(
                analysis.spendingType(), referenceValue, actualCost);
        boolean calculatedFromReference = referenceValue > 0
                && ("SAVED".equals(analysis.spendingType())
                || "REDUCED".equals(analysis.spendingType()));
        int estimatedAmount = calculatedFromReference
                ? (int) Math.min(Integer.MAX_VALUE, difference)
                : analysis.estimatedSavingAmount();
        return new AnalysisResponse(
                analysis.spendingType(), analysis.category(), estimatedAmount,
                appendPriceSummary(
                        analysis.summary(), referenceValue, actualCost, difference,
                        ingredientCostTotal > 0, gatheredComparison, resolvedItems),
                analysis.confidenceScore(), resolvedItems, referenceValue, actualCost,
                difference, references);
    }

    private long calculateDifference(String spendingType, long referenceValue, long actualCost) {
        if (referenceValue <= 0 || "SPENT".equals(spendingType)) {
            return 0;
        }
        if ("SAVED".equals(spendingType)) {
            return Math.max(0, referenceValue - actualCost);
        }
        if ("REDUCED".equals(spendingType)) {
            return Math.max(0, referenceValue - actualCost);
        }
        return 0;
    }

    private String appendPriceSummary(
            String summary, long referenceValue, long actualCost, long difference,
            boolean homemadeComparison, boolean gatheredComparison,
            List<DetectedItem> items) {
        if (referenceValue <= 0) {
            return summary;
        }
        String priceSummary;
        if (gatheredComparison) {
            String itemValues = items.stream()
                    .filter(item -> isGathered(item) && item.unitPrice() > 0)
                    .limit(3)
                    .map(this::formatGatheredItemValue)
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("");
            priceSummary = itemValues.isBlank()
                    ? String.format(Locale.KOREA,
                            "직접 채집한 물품은 실제 비용 0원으로 계산했으며 총 가치는 %,d원입니다.",
                            referenceValue)
                    : String.format(Locale.KOREA,
                            "직접 채집한 물품은 실제 비용 0원으로 계산했습니다. %s 기준 총 가치는 %,d원입니다.",
                            itemValues, referenceValue);
        } else if (homemadeComparison) {
            priceSummary = String.format(Locale.KOREA,
                    "음식점 판매가 %,d원과 재료비 %,d원을 비교한 절약 차액은 %,d원입니다.",
                    referenceValue, actualCost, difference);
        } else if (difference > 0) {
            priceSummary = String.format(Locale.KOREA,
                    "시세표 기준 가치는 %,d원이고 실제 비용 %,d원을 제외한 차액은 %,d원입니다.",
                    referenceValue, actualCost, difference);
        } else {
            priceSummary = String.format(Locale.KOREA,
                    "시세표 기준 확인된 물품 가치는 %,d원입니다.", referenceValue);
        }
        return summary == null || summary.isBlank() ? priceSummary : summary.trim() + " " + priceSummary;
    }

    private boolean isUsable(PriceReferenceRow row) {
        return row != null && row.getLowestPrice() != null && row.getLowestPrice() > 0;
    }

    private boolean isUsable(FoodCostReferenceRow row) {
        return row != null
                && row.getIngredientCost() != null && row.getIngredientCost() > 0
                && row.getRestaurantPrice() != null && row.getRestaurantPrice() > 0;
    }

    private boolean isUsable(RestaurantPriceCandidate candidate) {
        return candidate != null && candidate.price() > 0 && candidate.price() <= MAX_PRICE
                && candidate.sourceUrl() != null && candidate.sourceUrl().startsWith("http");
    }

    private boolean isHomemade(DetectedItem item) {
        return item != null && "HOMEMADE".equals(item.comparisonType());
    }

    private boolean isGathered(DetectedItem item) {
        return item != null && "GATHERED".equals(item.comparisonType());
    }

    private String formatGatheredItemValue(DetectedItem item) {
        int quantity = Math.max(1, item.quantity());
        String unit = normalizeDisplayUnit(item.unit());
        if (quantity > 1) {
            return String.format(Locale.KOREA, "%s %s 시세 %,d원 × %d",
                    item.itemName(), unit, item.unitPrice(), quantity);
        }
        return String.format(Locale.KOREA, "%s %s 시세 %,d원",
                item.itemName(), unit, item.unitPrice());
    }

    private int safeMultiply(int price, int quantity) {
        return (int) Math.min(Integer.MAX_VALUE, (long) price * quantity);
    }

    private double searchConfidence(DetectedItem item) {
        double confidence = 0.70;
        if (item.brand() != null && !item.brand().isBlank()) confidence += 0.10;
        if (!requiredUnitTokens(item.unit()).isEmpty()) confidence += 0.10;
        return Math.min(0.90, confidence);
    }

    private String normalizeKey(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^0-9a-z가-힣]", "");
    }

    private String normalizeUnit(String value) {
        return normalizeDisplayUnit(value).toLowerCase(Locale.ROOT).replace(" ", "");
    }

    private String normalizeDisplayUnit(String value) {
        if (value == null || value.isBlank()) return "개";
        return value.trim().replace('x', '×').replace('X', '×').replace('*', '×');
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String limit(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}

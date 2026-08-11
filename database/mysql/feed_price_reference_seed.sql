USE `wallo`;

-- 다나와 가격비교에 표시된 상품 최저가 기준입니다.
-- 배송비와 카드·멤버십 할인은 판매처에 따라 달라질 수 있습니다.
-- 간단한 초기 기준가로 사용하며 만료일은 저장하지 않습니다.
-- 동일 물품·브랜드·단위가 있으면 최신 관측값으로 갱신합니다.
INSERT INTO `feed_price_reference`
    (`normalized_item_name`, `display_item_name`, `brand`, `unit`, `category`,
     `lowest_price`, `source`, `source_url`, `observed_at`, `expires_at`,
     `search_confidence`)
VALUES
    ('생수500ml', '탐사 샘물 500ml 60병', '탐사', '500ml×60병', 'FOOD',
     11790, '다나와 가격비교', 'https://prod.danawa.com/info/?pcode=72009026',
     CURRENT_TIMESTAMP, NULL, 0.900),
    ('봉지라면120g', '삼양라면 120g 5봉', '삼양식품', '120g×5봉', 'FOOD',
     2740, '다나와 가격비교', 'https://prod.danawa.com/info/?pcode=1238930',
     CURRENT_TIMESTAMP, NULL, 0.900),
    ('즉석밥210g', '오뚜기 식감만족 찰기가득 진밥 210g 24개', '오뚜기', '210g×24개', 'FOOD',
     24720, '다나와 가격비교', 'https://prod.danawa.com/info/?pcode=17470634',
     CURRENT_TIMESTAMP, NULL, 0.900),
    ('우유1l', '서울우유 나 100% 1L', '서울우유', '1L×1개', 'FOOD',
     2360, '다나와 가격비교', 'https://prod.danawa.com/info/?pcode=117494837',
     CURRENT_TIMESTAMP, NULL, 0.900),
    ('계란30구', '웰굿 신선한 계란 30구', '웰굿', '30구×1판', 'FOOD',
     16280, '다나와 가격비교', 'https://prod.danawa.com/info/?pcode=69516986',
     CURRENT_TIMESTAMP, NULL, 0.850),
    ('두부300g', '아워홈 국산콩두부 찌개용 300g', '아워홈', '300g×1모', 'FOOD',
     1649, '다나와 가격비교', 'https://prod.danawa.com/info/?pcode=6139414',
     CURRENT_TIMESTAMP, NULL, 0.850),
    ('양조간장500ml', '샘표 양조간장 501 500ml', '샘표', '500ml×1병', 'FOOD',
     3990, '다나와 가격비교', 'https://prod.danawa.com/info/?pcode=3983565',
     CURRENT_TIMESTAMP, NULL, 0.900),
    ('롤화장지30롤', '노브랜드 3겹 화장지 33m 30롤', '노브랜드', '30롤×1팩', 'LIVING',
     10050, '다나와 가격비교', 'https://prod.danawa.com/info/?pcode=16969649',
     CURRENT_TIMESTAMP, NULL, 0.900),
    ('치약120g', '메디안 송천염 치약 120g 3개', '메디안', '120g×3개', 'LIVING',
     4370, '다나와 가격비교', 'https://prod.danawa.com/info/?pcode=101113826',
     CURRENT_TIMESTAMP, NULL, 0.850),
    ('샴푸600ml', '케라시스 모이스처 클리닉 샴푸 600ml', '케라시스', '600ml×1개', 'LIVING',
     2960, '다나와 가격비교', 'https://prod.danawa.com/info/?pcode=28194890',
     CURRENT_TIMESTAMP, NULL, 0.850),
    ('주방세제1l', 'B&B 주방세제 1L', 'B&B', '1L×1개', 'LIVING',
     9400, '다나와 가격비교', 'https://prod.danawa.com/info/?pcode=23564912',
     CURRENT_TIMESTAMP, NULL, 0.850),
    ('액체세탁세제3l', '아이엠 미네랄 탄산 쿨대디 액체세제 3L', '아이엠', '3L×1개', 'LIVING',
     15750, '다나와 가격비교', 'https://prod.danawa.com/info/?pcode=122622895',
     CURRENT_TIMESTAMP, NULL, 0.850)
ON DUPLICATE KEY UPDATE
    display_item_name = VALUES(display_item_name),
    category = VALUES(category),
    lowest_price = VALUES(lowest_price),
    source = VALUES(source),
    source_url = VALUES(source_url),
    observed_at = VALUES(observed_at),
    expires_at = VALUES(expires_at),
    search_confidence = VALUES(search_confidence);

USE `wallo`;

CREATE TABLE IF NOT EXISTS `feed_dish_recipe_ingredient`
(
    id                         BIGINT AUTO_INCREMENT PRIMARY KEY,
    normalized_dish_name       VARCHAR(150)   NOT NULL,
    display_dish_name          VARCHAR(200)   NOT NULL,
    dish_unit                  VARCHAR(50)    NOT NULL DEFAULT '1인분',
    category                   VARCHAR(30)    NOT NULL,
    normalized_ingredient_name VARCHAR(150)   NOT NULL,
    display_ingredient_name    VARCHAR(200)   NOT NULL,
    ingredient_quantity        DECIMAL(10, 3) NOT NULL,
    ingredient_unit            VARCHAR(20)    NOT NULL,
    price_reference_unit       VARCHAR(50)    NOT NULL,
    note                       VARCHAR(200)   NULL,
    created_at                 DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                 DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_feed_dish_recipe_ingredient
        (normalized_dish_name, dish_unit, category, normalized_ingredient_name),
    INDEX idx_feed_dish_recipe_match (normalized_dish_name, dish_unit, category)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
    COMMENT ='직접 만든 음식의 1단위 핵심 재료 구성';

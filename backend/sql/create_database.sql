-- wallo 프로젝트 데이터베이스 생성
-- utf8mb4: 한글 및 이모지(4바이트 문자) 저장 지원
-- utf8mb4_0900_ai_ci: MySQL 8.0 기본 권장 collation (대소문자/억양 구분 없음)
CREATE DATABASE IF NOT EXISTS wallo
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE wallo;

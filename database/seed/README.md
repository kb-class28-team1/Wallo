# 금융용어 seed 데이터

신규 로컬 DB에서는 팀 공통 전체 초기화 SQL을 먼저 실행한 뒤
`financial_term_data.sql`을 한 번 실행합니다.

```text
1. 전체 DB(초기화용).sql
2. database/seed/financial_term_data.sql
```

`financial_term_data.sql`에는 금융용어 4,149건의 원본 설명과 가공된
`short_definition`이 함께 들어 있습니다. `term_id`도 고정되며, 같은 DB에서 다시
실행하면 `ON DUPLICATE KEY UPDATE`로 기존 용어 내용을 최신 seed 값으로 갱신합니다.

파일을 다시 생성해야 할 때는 저장소 루트에서 다음 명령을 실행합니다.

```powershell
python ai/scripts/build_financial_term_seed.py
```

가공 배치에서 설명 생성에 실패했던 `term_id` 1040과 2442는 빈 값 방지를 위해
원본 설명을 `short_definition`으로 사용합니다.

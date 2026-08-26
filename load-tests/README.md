# Wallo k6 부하 테스트

이 디렉터리의 스크립트는 기본값으로 로컬 주소만 사용합니다. 운영 DB나 외부 AI를 대상으로 실행하려면 대상, 최대 RPS, 동시 사용자, 실행 시간, 비용·장애 대응 승인부터 별도로 확정해야 합니다.

## HTTP 장시간 읽기 부하

```powershell
$env:BASE_URL='http://127.0.0.1:8080'
$env:TEST_EMAIL='challenge1@wallo.test'
$env:TEST_PASSWORD='12341234'
$env:DURATION='30m'
$env:RATE='2'
$env:TARGET_MONTH='2026-08'
k6 run load-tests/http-soak.js
```

## WebSocket 연결/메시지 부하

기본값은 메시지를 저장하지 않는 연결 유지 부하입니다. `SEND_MESSAGES=true`일 때만 테스트 메시지가 DB에 저장됩니다.

```powershell
$env:BASE_URL='http://127.0.0.1:8080'
$env:TEST_EMAIL='challenge1@wallo.test'
$env:TEST_PASSWORD='12341234'
$env:CHALLENGE_ID='1101'
$env:VUS='20'
$env:DURATION='10m'
$env:SEND_MESSAGES='false'
k6 run load-tests/websocket.js
```

## AI 서버 부하

`AI_MODE=health`는 AI 서버 헬스체크만 호출하며 외부 모델 비용이 없습니다. `AI_MODE=chat`은 실제 모델 호출이므로 승인된 테스트 키·RPS·비용 상한이 없으면 실행하지 않습니다.

```powershell
$env:AI_BASE_URL='http://127.0.0.1:8000'
$env:AI_MODE='health'
$env:DURATION='2m'
$env:RATE='1'
k6 run load-tests/ai.js
```

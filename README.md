# simbio-don — 돈't (돈트, WEB-6)

> 쓰는 척은 마음껏, 진짜 돈은 차곡차곡

예산형 소비 시뮬레이션 + 아이템 창고. 월급을 입력하면 주 단위 예산을 짜주고,
그 예산(게임 재화)으로 모의 쇼핑·배달을 하며 남은 돈이 곧 절약액이 되는 게임.

- **형태**: Android 네이티브 앱 (Google Play 목표, 초기엔 개인용 APK)
- **실제 돈 연결 없음**: 계좌·결제 API 연동 없이 순수 모의 (규제 이슈 0)
- **로컬 처리**: 월급·지출 데이터는 기기에만 저장, 외부 전송 없음 (네트워크는 창밖 날씨에만 씀)

## 핵심 루프

월급 입력 → 고정비 차감 → 주 단위 예산 배분 → 게임 재화 = 예산
→ 소비(모의 또는 실제 지출 자동 기록) → 남은 잔액 = 저금통 + 아이템 창고 적립
→ 주간 리포트("이번 주 12,000원 아꼈어요")

## 구조

- `core/` — 순수 Kotlin 로직 (Android 의존 없음, 단위 테스트)
  - `budget/` — 월급날 기준 한 달(`PayCycle`), 주 예산 배분(`BudgetEngine`), 이월·예외 지출·결산 판정(`CycleLedger`, `Settlement`)
  - `notify/` — 결제 알림 규칙(앱 선택 → 키워드), 금액 추출, 중복·고정비 의심 판정(`ReviewPolicy`)
- `app/` — Android 앱 (Kotlin · Jetpack Compose · Room), 쇼핑앱식 UI (`ui/kit`) + 게임 속 도트 그림 (`tools/pixelart`)
- `tools/pixelart/` — 도트 그림 생성기 (`python3 tools/pixelart/make_art.py`) — [docs/art-spec.md](docs/art-spec.md)
  - `notify/PaymentListenerService` — 고른 앱의 결제 알림만 읽어 기기 안에서 기록
  - `notify/DailyReminder` — 매일 밤 9시 지출 요약·결산 알림 (설정에서 켤 때만)
  - `widget/WeekWidget` — 홈 화면 위젯 (이번 주 남은 돈 + 지출 입력)
  - `weather/WeatherService` — 창밖 날씨 (Open-Meteo, 고른 도시 좌표만 보냄. 설정에서 끌 수 있음)
  - `data/` — Room 저장소, `ui/` — 화면

```sh
./gradlew :core:test :app:testDebugUnitTest   # 테스트
./gradlew :app:assembleDebug                   # APK
./gradlew :app:testDebugUnitTest -Pscreenshots # 화면 스크린샷 → app/build/screenshots/
```

푸시할 때마다 GitHub Actions가 APK를 빌드해 `debug-latest` 릴리스에 올린다. 설치 방법은 [docs/m0-install.md](docs/m0-install.md).

## 문서

- [docs/idea.md](docs/idea.md) — 원안·실존 확인·채택안 (2026-10-07)
- [docs/decisions.md](docs/decisions.md) — 확정 결정·마일스톤·미결 사항
- [docs/m0-install.md](docs/m0-install.md) — M0 설치와 1~2주 실사용 체크리스트

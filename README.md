# simbio-don — 돈't (돈트, WEB-6)

> 쓰는 척은 마음껏, 진짜 돈은 차곡차곡

가계부 + 예산형 소비 시뮬레이션. 결제·입금 알림으로 가계부(일일·달력·주별·월별, 자산·이체)를 저절로 적고,
월급을 주 단위 예산으로 나눠 매주 결산한다. 사고 싶을 땐 가짜 돈으로 모의 쇼핑 — 산 물건이 내 방을 채운다.
방에는 얼굴·머리·옷을 직접 꾸민 도트 캐릭터가 살고, 이번 달 형편에 따라 표정이 바뀐다.
월급은 처음 한 번만 넣으면 월급날마다 새 달이 저절로 시작되고 입금 알림으로 금액을 맞춘다.
소분류·직접 만든 카테고리, 할부(달마다 나눠 적기), 반복 수입, 앱 잠금(4자리), 연간 통계도 된다.
실제로 아낀 비율만큼 **마일**이 쌓이고(키우면 주 0.5% 복리), 마일로 이사·가구·옷을 사고, 모의로 산 물건은 카드로 모인다.
모의 쇼핑은 **돈't 몰**(상품 상세·장바구니, 선택형 네이버 쇼핑 검색 — 검색어만 보냄)에서.

- **형태**: Android 네이티브 앱 (Google Play 목표, 초기엔 개인용 APK)
- **실제 돈 연결 없음**: 계좌·결제 API 연동 없이 순수 모의 (규제 이슈 0)
- **로컬 처리**: 월급·지출 데이터는 기기에만 저장, 외부 전송 없음 (네트워크는 창밖 날씨와, 키를 넣었을 때 네이버 쇼핑 검색어에만 씀)

## 핵심 루프

월급 입력 → 고정비 차감 → 주 단위 예산 배분 → 게임 재화 = 예산
→ 소비(모의 또는 실제 지출 자동 기록) → 남은 잔액 = 저금통 + 아이템 창고 적립
→ 주간 리포트("이번 주 12,000원 아꼈어요")

## 구조

- `core/` — 순수 Kotlin 로직 (Android 의존 없음, 단위 테스트)
  - `budget/` — 월급날 기준 한 달(`PayCycle`), 주 예산 배분(`BudgetEngine`), 이월·예외 지출·결산 판정(`CycleLedger`, `Settlement`), 월급 입금 판별·고정 지출 날짜(`Payday`)
  - `notify/` — 결제 알림 규칙(앱 선택 → 키워드), 금액 추출, 중복·고정비 의심 판정(`ReviewPolicy`)
  - `game/` — 모의 구매·집·기분, 캐릭터 모습(`CharacterLook`), 마일리지·카드(`Miles`), 가게 값(`Shop`)
- `app/` — Android 앱 (Kotlin · Jetpack Compose · Room), 쇼핑앱식 UI (`ui/kit`) + 게임 속 도트 그림 (`tools/pixelart`)
- `tools/pixelart/` — 도트 그림 생성기 (`python3 tools/pixelart/make_art.py`) — [docs/art-spec.md](docs/art-spec.md)
  - `notify/PaymentListenerService` — 고른 앱의 결제 알림만 읽어 기기 안에서 기록
  - `notify/DailyReminder` — 매일 밤 9시 지출 요약·결산 알림 (설정에서 켤 때만)
  - `lock/AppLock` — 앱 잠금 (4자리, 소금 친 해시로 이 폰에만)
  - `widget/WeekWidget` — 홈 화면 위젯 (이번 주 남은 돈 + 지출 입력)
  - `weather/WeatherService` — 창밖 날씨 (Open-Meteo, 고른 도시 좌표만 보냄. 설정에서 끌 수 있음)
  - `mall/NaverShopping` — 돈't 몰 네이버 쇼핑 검색 (선택, 사용자 키, 검색어만 보냄)
  - `data/` — Room 저장소(월급날 자동 새 달·고정 지출 자동 기록·자산 잔액·백업), `ui/` — 화면

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

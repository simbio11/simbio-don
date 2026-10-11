# 그림 교체 가이드 (도트)

지금 앱의 캐릭터·방은 코드로 그린 임시 도트 그림이고, 물건은 이모지예요.
아래 이름으로 PNG를 `app/src/main/res/drawable-nodpi/`에 넣으면 앱이 자동으로 그 그림을 써요 (코드 수정 없음).

## 공통 규칙

- **PNG, 투명 배경**, 도트 그대로(안티에일리어싱 없이). 앱이 확대할 때 픽셀을 뭉개지 않아요.
- 크기는 아래 "원본 픽셀" 그대로 만들면 돼요. 더 크게 만들 거면 **정수배**(×2, ×4)로.
- 스타일: WEB-5 도트 가문 키우기와 같은 톤 — 굵은 외곽선, 3~4단계 음영, 따뜻한 색.

## 캐릭터 (12 × 17 원본 픽셀)

| 파일 이름 | 언제 | 표정 |
| :--- | :--- | :--- |
| `sprite_character_happy.png` | 이번 주 예산보다 천천히 씀 | 활짝 웃음 |
| `sprite_character_calm.png` | 예산 속도대로 | 잔잔한 미소 |
| `sprite_character_worried.png` | 예산보다 빨리 씀 | 땀 한 방울, 걱정 |
| `sprite_character_cheer.png` | 예산을 넘김 | 주먹 쥐고 "다시 해보자" (혼나는 느낌 X) |

프롬프트 예시:
> 12x17 pixel art character sprite, front view, full body, cute chibi young adult, brown hair, blue t-shirt, navy pants, bold dark outline, transparent background, no anti-aliasing, {표정} expression

## 물건 (16 × 16 원본 픽셀)

| 파일 이름 | 종류 |
| :--- | :--- |
| `sprite_item_clothes.png` | 옷 |
| `sprite_item_bag.png` | 가방·잡화 |
| `sprite_item_shoes.png` | 신발 |
| `sprite_item_electronics.png` | 전자기기 |
| `sprite_item_book.png` | 책·문구 |
| `sprite_item_beauty.png` | 화장품 |
| `sprite_item_living.png` | 생활용품 |
| `sprite_item_toy.png` | 취미·장난감 |
| `sprite_item_etc.png` | 기타 (선물 상자) |
| `sprite_item_meal.png` / `sprite_item_meal_trash.png` | 음식 / 먹고 남은 배달 상자 |
| `sprite_item_drink.png` / `sprite_item_drink_trash.png` | 커피·음료 / 빈 컵 |

프롬프트 예시:
> 16x16 pixel art icon of {물건}, game inventory item, bold dark outline, transparent background, no anti-aliasing

## 방 (M2에서 배경 교체 지원 예정)

방은 지금 코드로 그려요. 칸 위치(방 너비·높이 비율)는 `HouseScreen.kt`의 `AREAS`에 있고,
배경 그림을 만들 때는 이 위치에 옷장·신발장·선반·책상이 오도록 맞추면 돼요.

| 칸 | x | y | 너비 | 높이 |
| :--- | :--- | :--- | :--- | :--- |
| 옷장 | 0.03 | 0.10 | 0.26 | 0.50 |
| 신발장 | 0.03 | 0.66 | 0.26 | 0.16 |
| 선반 | 0.34 | 0.28 | 0.32 | 0.16 |
| 책상 | 0.68 | 0.52 | 0.29 | 0.18 |

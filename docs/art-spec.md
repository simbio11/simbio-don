# 도트 그림과 UI

## 구성

- **그림 생성기** `tools/pixelart/` — 모든 도트 그림을 같은 팔레트로 만든다.
  - `palette.py`: 색 (글자 하나 = 색 하나, 외곽선은 검정 대신 짙은 자두색 `#2b2234`)
  - `sprites.py`: 캐릭터·물건·카테고리·UI 아이콘 격자 (글자 하나 = 픽셀 하나)
  - `make_art.py`: 방 배경을 그리고 전부 PNG로 저장, 방 칸 좌표 `RoomLayout.kt` 생성
- **결과물** `app/src/main/res/drawable-nodpi/px_*.png` — 원본 픽셀 크기. 앱이 정수배로 확대해 픽셀이 뭉개지지 않게 그린다.
- **글꼴** 갈무리11 (Galmuri, SIL OFL 1.1) — `res/font/`, 라이선스는 `assets/licenses/galmuri_OFL.txt`
- **UI 키트** `app/.../ui/kit/PixelKit.kt` — 도트 패널(모서리 깎기 + 단단한 그림자), 버튼(누르면 내려앉음), 칩, 하단 탭, 도트 그림 표시

## 다시 만들기

```sh
python3 tools/pixelart/make_art.py /tmp/preview   # 미리보기: /tmp/preview/sheet.png, scene.png
```

격자나 방 그리는 코드를 고친 뒤 다시 돌리면 PNG와 `RoomLayout.kt`가 함께 바뀐다.
방 칸 좌표를 바꿨다면 `RoomLayoutTest`가 수납 칸 수와 맞는지 확인한다.

## 손으로 그린 그림으로 바꾸기

같은 이름·같은 크기의 PNG로 `drawable-nodpi/`의 파일을 덮어쓰면 된다 (투명 배경, 안티에일리어싱 없이).
덮어쓴 뒤 생성기를 다시 돌리면 덮어쓴 그림이 지워지니 주의.

| 그림 | 이름 | 원본 크기 |
| :--- | :--- | :--- |
| 캐릭터 (표정 4 × 프레임 2: 0 = 눈 뜸, 1 = 깜빡/반짝) | `px_char_{happy,calm,worried,cheer}_{0,1}` | 16 × 24 |
| 물건 | `px_item_{clothes,bag,shoes,electronics,book,beauty,living,toy,meal,drink,etc}` | 12 × 12 |
| 먹고 남은 쓰레기 | `px_item_meal_trash`, `px_item_drink_trash` | 12 × 12 |
| 가계부 카테고리 | `px_cat_{food,cafe,delivery,transport,shopping,living,health,culture,event,education,etc}` | 12 × 12 |
| UI 아이콘 | `px_ui_{home,list,house,chart,gear,coin,warn,box,star}` | 12 × 12 |
| 캐릭터 그림자 | `px_shadow` | 14 × 4 |
| 원룸 배경 | `px_room_one_room` | 160 × 136 |

원룸 배경을 새로 그릴 때는 가구 칸 위치가 `RoomLayout.kt`의 좌표와 맞아야 한다 (옷장 x 6–47, 책장 x 62–101, 책상 x 112–155, 신발장 x 6–33).

## 이미지 생성 AI로 그릴 때 프롬프트 예시

> 16x24 pixel art character sprite, front view, full body, cute chibi young adult, brown hair, blue t-shirt, navy pants,
> dark plum outline (#2b2234), warm cozy palette, transparent background, no anti-aliasing, {표정} expression

> 12x12 pixel art icon of {물건}, game inventory item, dark plum outline, warm cozy palette, transparent background, no anti-aliasing

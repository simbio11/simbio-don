# 도트 그림과 UI

## 구성

화면(UI)은 쇼핑앱처럼 깔끔하게, **도트는 게임 그림(방·캐릭터·모의로 산 물건)에만** 쓴다.

- **UI** — 밝은 회색 바탕(`#F2F4F6`) + 흰 둥근 카드(16dp), 시스템 글꼴, 파랑 = 현실 돈, 보라 = 게임
  - 키트 `app/.../ui/kit/Kit.kt`: 카드, 버튼(주·보조·작은·글자), 알약 칩, 뱃지, + 버튼, 하단 탭, 뒤로 가기 줄, 카테고리 동그라미 아이콘
  - 아이콘 `res/drawable/ic_*.xml`: Material Symbols Rounded (Google, Apache 2.0, `assets/licenses/material_symbols_LICENSE.txt`). 탭은 선택 시 채운 아이콘
  - 카테고리 아이콘은 카테고리마다 색 동그라미(아이콘 칸에만 색을 쓰고, 차트는 한 가지 파랑)
- **도트 그림 생성기** `tools/pixelart/`
  - `palette.py`: 색 (글자 하나 = 색 하나, 외곽선은 짙은 자두색 `#2b2234`, 1~9·d·M·V·Z 등은 밝은/어두운 단계)
  - `sprites.py`: 캐릭터·물건 격자 (글자 하나 = 픽셀 하나). 빛은 왼쪽 위에서
  - `room.py`: 원룸 장면과 시간대 레이어 (하늘·햇빛·밤 그늘·밤 불빛)
  - `make_art.py`: 전부 PNG로 저장하고 방 좌표 `RoomLayout.kt` 생성
- **결과물** `app/src/main/res/drawable-nodpi/px_*.png` — 원본 픽셀 크기
  - 방은 `RoomScene.kt`가 원본 크기 버퍼(160×136)에 겹쳐 그린 뒤 통째로 키운다 (레이어 격자가 어긋나지 않음)
  - 작은 그림(물건·캐릭터)은 기기 픽셀의 정수배로만 키운다

## 살아있는 방

- 창밖 하늘이 천천히 흐른다 (구름·별·달). 시간대: 6–17시 낮, 17–19시 노을, 19–6시 밤
- 낮·노을엔 창살 그림자가 진 햇빛이 바닥에 들고, 밤엔 방이 어두워지고 스탠드·전구줄이 켜진다
- 캐릭터가 16초마다 러그 위를 왔다 갔다 걷는다 (걷기 2프레임), 서 있을 땐 깜빡이고 숨 쉰다

## 다시 만들기

```sh
python3 tools/pixelart/make_art.py /tmp/preview   # 미리보기: sheet.png, scene_{day,sunset,night}.png
```

격자나 방 그리는 코드를 고친 뒤 다시 돌리면 PNG와 `RoomLayout.kt`가 함께 바뀐다 (안 쓰는 옛 `px_*.png`는 지운다).
방 칸 좌표를 바꿨다면 `RoomLayoutTest`가 수납 칸 수·누르는 자리와 맞는지 확인한다.

## 손으로 그린 그림으로 바꾸기

같은 이름·같은 크기의 PNG로 `drawable-nodpi/`의 파일을 덮어쓰면 된다 (투명 배경, 안티에일리어싱 없이).
덮어쓴 뒤 생성기를 다시 돌리면 덮어쓴 그림이 지워지니 주의.

| 그림 | 이름 | 원본 크기 |
| :--- | :--- | :--- |
| 캐릭터 (표정 4 × 프레임 4: 0 기본, 1 눈 감음, 2·3 걷기) | `px_char_{happy,calm,worried,cheer}_{0..3}` | 22 × 28 |
| 물건 | `px_item_{clothes,bag,shoes,electronics,book,beauty,living,toy,meal,drink,etc}` | 12 × 12 |
| 먹고 남은 쓰레기 | `px_item_meal_trash`, `px_item_drink_trash` | 12 × 12 |
| 캐릭터 그림자 | `px_shadow` | 16 × 4 |
| 원룸 바탕 (유리창은 비어 있음) | `px_room_one_room` | 160 × 136 |
| 창밖 하늘 (가로로 이어 붙여 흘림) | `px_sky_{day,sunset,night}` | 64 × 20 |
| 바닥 햇빛 | `px_light_{day,sunset}` | 160 × 136 |
| 밤 그늘 / 밤 불빛 | `px_night_shade`, `px_night_lights` | 160 × 136 |

원룸 바탕을 새로 그릴 때는 가구 칸 위치가 `RoomLayout.kt`의 좌표와 맞아야 한다 (옷장 x 6–47, 책장 x 62–101, 책상 x 112–155, 신발장 x 6–33).

## 이미지 생성 AI로 그릴 때 프롬프트 예시

> 22x28 pixel art character sprite, front view, full body, cute chibi young adult, brown hair, blue t-shirt, navy pants,
> dark plum outline (#2b2234), warm cozy palette, transparent background, no anti-aliasing, {표정} expression

> 12x12 pixel art icon of {물건}, game inventory item, dark plum outline, warm cozy palette, transparent background, no anti-aliasing

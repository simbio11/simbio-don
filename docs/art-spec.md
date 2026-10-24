# 도트 그림과 UI

## 구성

화면(UI)은 쇼핑앱처럼 깔끔하게, **도트는 게임 그림(방·캐릭터·모의로 산 물건)에만** 쓴다.

- **UI** — 밝은 회색 바탕(`#F2F4F6`) + 흰 둥근 카드(16dp), 시스템 글꼴, 파랑 = 현실 돈, 보라 = 게임
  - 키트 `app/.../ui/kit/Kit.kt`: 카드, 버튼(주·보조·작은·글자), 알약 칩, 뱃지, + 버튼, 하단 탭, 뒤로 가기 줄, 카테고리 동그라미 아이콘
  - 아이콘 `res/drawable/ic_*.xml`: Material Symbols Rounded (Google, Apache 2.0, `assets/licenses/material_symbols_LICENSE.txt`). 탭은 선택 시 채운 아이콘
  - 카테고리 아이콘은 카테고리마다 색 동그라미(아이콘 칸에만 색을 쓰고, 차트는 한 가지 파랑)
- **도트 그림 생성기** `tools/pixelart/`
  - `palette.py`: 색 (글자 하나 = 색 하나, 외곽선은 짙은 자두색 `#2b2234`, 1~9·d·M·V·Z 등은 밝은/어두운 단계)
  - `sprites.py`: 물건·아이콘 격자 (글자 하나 = 픽셀 하나). 빛은 왼쪽 위에서
  - `v3/`: 캐릭터 v3 (부위를 겹쳐 그리는 24×36 캐릭터) — 아래 "캐릭터 v3"
  - `room.py`: 집 세 채(원룸·투룸·아파트) 장면과 시간대 레이어 (하늘·햇빛·밤 그늘·밤 불빛). 벽지·바닥·러그·커튼·이불은 표식 색(`palette.MARKERS`)으로 그리고 앱이 고른 색(`THEMES`)으로 바꾼다
  - `v3/items16.py`: 카드·몰·가게용 16×16 아이콘 (물건 13 + 가구·인테리어·이사·마일 등)
  - `v3/products16.py`: 돈't 몰 상품마다 16×16 (76개, 이름 `px16_p_<상품 id>`, 몇 개는 물건 종류 그림을 그대로 씀) — 도감·몰·카드·결산쇼. `make_art.py`가 id → 그림 표 `ProductIcons.kt`도 만든다
  - `make_art.py`: 전부 PNG로 저장하고 방 좌표 `RoomLayout.kt`(집마다 유리창 포함)·인테리어 색 `RoomColors.kt` 생성. 표식 색이 겹치거나 방에서 빠지면 멈춘다
- **결과물** `app/src/main/res/drawable-nodpi/px_*.png` — 원본 픽셀 크기
  - 방은 `RoomScene.kt`가 원본 크기 버퍼(160×136)에 겹쳐 그린 뒤 통째로 키운다 (레이어 격자가 어긋나지 않음)
  - 작은 그림(물건·캐릭터)은 기기 픽셀의 정수배로만 키운다

## 살아있는 방

- 창밖 하늘이 천천히 흐른다 (구름·별·달). 시간대: 6–17시 낮, 17–19시 노을, 19–6시 밤
- 낮·노을엔 창살 그림자가 진 햇빛이 바닥에 들고, 밤엔 방이 어두워지고 스탠드·전구줄이 켜진다
- 캐릭터가 16초마다 러그 위를 왔다 갔다 걷는다 (걷기 2프레임), 서 있을 땐 깜빡이고 숨 쉰다

## 캐릭터 v3 (0.9.0)

캐릭터는 한 장짜리 그림이 아니라 **부위를 겹쳐** 그린다. 그래서 얼굴·머리·옷을 바꾸고(꾸미기·성형) 색을 고를 수 있다.

- 크기 **24 × 36** (맨 위 4줄은 모자·올림머리 자리). 2.5등신, 큰 머리(0–17줄) + 몸(18–31줄)
- 겹치는 순서: 뒷머리 → 몸(얼굴형) → 아래옷(원피스면 생략) → 신발 → 윗옷 → 볼 → 눈(+속눈썹) → 코 → 입 → 앞머리 → 눈썹 → 덧그림(눈물·땀·화남·반짝) → 모자 → 소품
- 부위: `v3/char3.py`(몸·얼굴형 3, 눈 5, 눈썹 3, 코 4, 입 4, 표정 7), `v3/hair3.py`(머리), `v3/clothes3.py`(윗옷 9·아래옷 5·신발 3·모자 4·소품 4)
- **표정**: 기분마다 눈 상태·눈썹·입·덧그림 조합(`MOOD_FACE`). 눈 모양마다 '뜸·슬픔·화남'을 따로 그리고, 감은 눈·웃는 눈·X 눈은 공통
- **색 바꾸기**: 바꿀 수 있는 곳은 '표식 색'으로 그린다(`v3/cpal.py`의 SWAP — 피부 4, 머리 4, 윗옷 4, 아래옷 4, 신발 3, 눈동자 2, 모자 4단계). 앱이 표식 색을 고른 색 묶음(RAMPS)으로 바꾼다. 표식 색은 서로 달라야 한다(생성기가 확인)
- 결과물: 부위마다 한 칸씩 담은 아틀라스 `px_char_atlas.png`(16칸 × n줄, 칸 24×36)와 `ui/pixel/CharacterParts.kt`(칸 번호·표식 색·고를 수 있는 색·부위 이름). 앱의 `ui/kit/CharacterRenderer`가 부위를 골라 색을 바꿔 겹치고, 모습·기분·동작마다 기억해 둔다
- 걷기는 따로 그리지 않고 한쪽 다리를 한 줄 들어 올려 만든다, 깜빡임은 감은 눈으로 바꿔 그린다
- 남자 기본: 짧은 머리·후드티·긴바지·운동화·진한 눈썹·동글 얼굴. 여자 기본: 긴 생머리·블라우스·치마·구두·왕눈+속눈썹·도톰한 입술·V라인. 부위는 남녀 상관없이 고를 수 있다
- 미리보기: `python3 tools/pixelart/make_art.py <폴더>` → `characters.png`(남녀 기본 모습 × 표정 7), `scene_*.png`(방 안 캐릭터)

## 다시 만들기

```sh
python3 tools/pixelart/make_art.py /tmp/preview   # 미리보기: sheet.png, characters.png, scene_{one_room,two_room,apartment}_{day,sunset,night}.png
```

격자나 방 그리는 코드를 고친 뒤 다시 돌리면 PNG와 `RoomLayout.kt`가 함께 바뀐다 (안 쓰는 옛 `px_*.png`는 지운다).
방 칸 좌표를 바꿨다면 `RoomLayoutTest`가 수납 칸 수·누르는 자리와 맞는지 확인한다.

## 손으로 그린 그림으로 바꾸기

같은 이름·같은 크기의 PNG로 `drawable-nodpi/`의 파일을 덮어쓰면 된다 (투명 배경, 안티에일리어싱 없이).
덮어쓴 뒤 생성기를 다시 돌리면 덮어쓴 그림이 지워지니 주의.

| 그림 | 이름 | 원본 크기 |
| :--- | :--- | :--- |
| 캐릭터 부위 아틀라스 (생성기로만 — 칸 번호가 `CharacterParts.kt`와 맞아야 함) | `px_char_atlas` | 384 × (36 × 줄 수) |
| 물건 | `px_item_{clothes,bag,shoes,electronics,book,beauty,living,toy,meal,drink,etc}` | 12 × 12 |
| 먹고 남은 쓰레기 | `px_item_meal_trash`, `px_item_drink_trash` | 12 × 12 |
| 캐릭터 그림자 | `px_shadow` | 16 × 4 |
| 집 바탕 (유리창은 비어 있음, 인테리어는 표식 색) | `px_room_{one_room,two_room,apartment}` | 160 × 136 |
| 창밖 하늘 (가로로 이어 붙여 흘림) | `px_sky_{day,sunset,night}` | 64 × 20 |
| 바닥 햇빛 (집마다) | `px_light_{day,sunset}_{집}` | 160 × 136 |
| 밤 그늘 / 밤 불빛 (집마다) | `px_night_shade_{집}`, `px_night_lights_{집}` | 160 × 136 |
| 카드·몰·가게 아이콘 | `px16_*` | 16 × 16 |
| 몰 상품 (도감) | `px16_p_<상품 id>` | 16 × 16 |

집 바탕을 새로 그릴 때는 가구 칸 위치가 `RoomLayout.kt`의 좌표와 맞아야 하고(세 집 모두 같음), 인테리어 부분은 표식 색으로 칠해야 색 바꾸기가 된다 (옷장 x 6–47, 책장 x 62–101, 책상 x 112–155, 신발장 x 6–33).

## 이미지 생성 AI로 그릴 때 프롬프트 예시

> 24x36 pixel art character sprite, front view, full body, cute chibi young adult (2.5 heads tall), {머리} hair, {옷},
> dark plum outline (#2b2234), warm cozy palette, transparent background, no anti-aliasing, {표정} expression
> (캐릭터는 부위를 겹쳐 그리므로, 손그림을 쓰려면 부위별로 나눠 `v3/` 격자로 옮겨야 한다)

> 12x12 pixel art icon of {물건}, game inventory item, dark plum outline, warm cozy palette, transparent background, no anti-aliasing

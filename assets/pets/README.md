# 반려동물 표정 도트 세트

돈 아끼는 게임(WEB-6) 반려동물 후보 캐릭터. 모두 제미나이 생성 2×4 표정 시트이며,
`sprites/` 는 시트를 8칸으로 잘라 마젠타 배경을 투명으로 바꾼 결과물입니다(높이 256px).

| 캐릭터 | 설명 | 원본 시트 | 표정 수 |
|---|---|---|---|
| `pomeranian` | 포메라니안 (크림·탠, 정면) | `pomeranian/sheet.jpeg` (768×1376) | 8 |
| `shiba_mix` | 탠 믹스 강아지 (귀 접힘) | `shiba_mix/sheet.jpeg` (768×1376) | 8 |
| `shiba` | 시바견 (탠·크림) | `shiba/sheet.jpeg` (768×1376) | 8 |
| `bulldog` | 불독 | `bulldog/sheet.jpeg` (768×1376) | 8 |
| `poodle` | 푸들 (아프리콧) | `poodle/sheet.jpeg` (768×1376) | 8 |
| `corgi` | 웰시코기 | `corgi/sheet.jpeg` (768×1376) | 8 |
| `beagle` | 비글·스패니얼 계열 (긴 귀) | `beagle/sheet.jpeg` (768×1376) | 8 |
| `pomeranian_alt` | 포메라니안 (꼬리 큰 앉은 자세) | `pomeranian_alt/sheet.jpeg` (768×1376) | 8 |
| `longhair_cat` | 장모 고양이 (진저·화이트) | `longhair_cat/sheet.jpeg` (768×1376) | 8 |
| `tabby_cat` | 치즈태비 고양이 | `tabby_cat/sheet.png` (1536×2752) | 8 |
| `longhair_tabby_cat` | 장모 치즈 고양이 (파란 눈) | `longhair_tabby_cat/sheet.png` (1536×2752) | 8 |

## 표정 순서 (좌→우, 위→아래)

| 파일 | 표정 |
|---|---|
| `01_joy.png` | 기쁨 (눈웃음 + 반짝) |
| `02_smile.png` | 웃음 (입 벌린 미소) |
| `03_calm.png` | 차분 (무표정 미소) |
| `04_worry.png` | 당황 (땀방울) |
| `05_sad.png` | 슬픔 (눈물) |
| `06_angry.png` | 화남 (분노 표시) |
| `07_tease.png` | 장난 (윙크 + 앞발) |
| `08_sleep.png` | 잠 (Zzz) |

## 사용

- 앱에서는 `sprites/` 의 PNG(투명 배경)를 쓰고, 원본 해상도가 필요하면 `sheet.*` 에서 다시 자릅니다.
- 원본 시트 배경은 `#FF00FF` 계열 순수 마젠타라 키잉이 쉽습니다.

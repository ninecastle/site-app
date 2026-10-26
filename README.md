# 사이트 → 앱 변환기 (안드로이드 + 윈도우 + 맥)

아무 웹사이트나 주소창 없이 **원래 앱처럼** 열리는 앱으로 만들어 줍니다.
지금은 테스트용으로 YouTube가 들어 있어요.

## 1. 사이트 바꾸기 — `site.json` 하나만 고치면 됨

```json
{
  "name": "YouTube",                       // 앱 이름 (홈 화면에 보이는 이름)
  "url": "https://m.youtube.com/",          // 안드로이드에서 열 주소
  "desktopUrl": "https://www.youtube.com/", // PC에서 열 주소 (없으면 url 사용)
  "themeColor": "#0f0f0f",                  // 상태바·로딩 배경색
  "androidId": "com.ninecastle.siteapp",    // 앱 고유 ID (앱마다 다르게!)
  "desktopId": "com.ninecastle.siteapp.desktop"
}
```

> 여러 사이트를 각각 앱으로 만들려면 `androidId`/`desktopId`를 서로 다르게 해야
> 폰에 동시에 설치돼요. (예: `com.ninecastle.naver`)

## 2. 빌드하기 — 방법 3가지

### A. GitHub에 올리기 (가장 쉬움, 세 가지 다 자동)
1. GitHub에 새 저장소를 만들고 이 폴더 전체를 올립니다.
2. 자동으로 **Actions** 가 돌아가고 5~10분 뒤 **Releases** 에
   - `YouTube.apk` (안드로이드 폰·패드)
   - `YouTube Setup 1.0.0.exe` (윈도우 설치) / `YouTube 1.0.0.exe` (윈도우 포터블)
   - `YouTube-1.0.0-arm64.dmg` (애플 실리콘 맥) / `YouTube-1.0.0.dmg` (인텔 맥)
   가 올라옵니다.
3. `site.json`을 고쳐서 다시 올리면 새로 빌드돼요.

### B. 맥에서 직접 — PC 앱
```bash
cd desktop
npm install
npm start          # 바로 실행해서 확인
npm run dist:mac   # dist/ 폴더에 .dmg 생성
npm run dist:win   # 윈도우용 .exe 생성
```

### C. 맥에서 직접 — 안드로이드 앱
Android Studio → **Open** → `android` 폴더 선택 → 폰을 USB로 연결 → ▶ Run
(또는 터미널에서 `cd android && ./gradlew assembleRelease` →
`android/app/build/outputs/apk/release/app-release.apk`)

## 3. 설치할 때 알아둘 것
- **안드로이드**: APK를 폰으로 옮겨서 열면 "출처를 알 수 없는 앱" 허용을 물어봐요. 허용하면 설치됩니다.
- **윈도우**: 서명이 없어서 "Windows의 PC 보호" 창이 뜨면 *추가 정보 → 실행*.
- **맥 (GitHub에서 받은 dmg)**: 처음 열 때 막히면 **시스템 설정 → 개인정보 보호 및 보안**
  맨 아래의 **그래도 열기**를 누르세요. 그래도 "손상되었습니다"가 뜨면
  터미널에서 `xattr -cr "/Applications/YouTube.app"` 실행 후 다시 열기.
  맥에서 직접 빌드(B)하면 이 문제는 없어요.

## 4. 들어 있는 기능
| 기능 | 안드로이드 | PC |
|---|---|---|
| 주소창 없는 전체 화면 | ✅ | ✅ |
| 로그인·쿠키 유지 | ✅ | ✅ |
| 뒤로가기 | 기기 뒤로 버튼 | Alt+← / ⌘[ |
| 동영상 전체화면 | ✅ (가로 자동 전환) | ✅ |
| 파일 업로드 | ✅ | ✅ |
| 인터넷 끊김 안내 + 다시 시도 | ✅ | ✅ |
| 다른 사이트 링크 | 앱 안에서 | 기본 브라우저로 |
| 창 크기 기억 | – | ✅ |

## 5. 한계
- **구글 로그인**: 구글은 앱 속 웹뷰에서의 로그인을 막는 경우가 많아요.
  유튜브는 로그인 없이 시청은 잘 되지만, 로그인이 필요하면 막힐 수 있어요.
  학교·커뮤니티처럼 자체 로그인을 쓰는 사이트는 문제없이 유지됩니다.
- 백그라운드 재생, 알림 같은 기능은 사이트가 웹에서 지원하는 만큼만 됩니다.

## 폴더 구조
```
site.json                  ← 사이트 설정 (여기만 고치면 됨)
android/                   ← 안드로이드 웹뷰 앱 (Java)
desktop/                   ← 윈도우·맥 앱 (Electron)
.github/workflows/build.yml← GitHub 자동 빌드
```

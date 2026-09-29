#!/usr/bin/env bash
# 릴리스 APK 를 에뮬레이터에 올려 **살아 있는지** 본다.
#
# 우리가 릴리스에서만 겪은 결함 넷 중 둘은 프로세스 시작 즉시 전 사용자 크래시였고
# (카카오 SDK enum 리플렉션 · glance 가 끌고 온 WorkManagerInitializer), 둘 다
# `assembleRelease` 로는 안 보였다 — 빌드는 멀쩡히 성공하고 켜는 순간 죽는다.
# 그래서 켜 보는 것까지가 검사다. 사정은 docs/RELEASE.md 「릴리스 스모크 CI」.
set -euo pipefail

APK=app/build/outputs/apk/release/app-release.apk
PKG=app.nursemate
SETTLE_SECONDS=20

echo "::group::릴리스 APK 설치"
# -d 는 버전 내림 허용 — 이미지에 남은 앱이 있어도 걸리지 않는다.
adb install -r -d "$APK"
echo "::endgroup::"

# 설치 직후에 비운다. 앞선 단계의 로그가 섞이면 판정이 흐려진다.
adb logcat -c

echo "::group::런처로 실행"
# 클래스명을 적지 않는다 — 런처 액티비티가 바뀌어도 이 스크립트는 그대로 돈다.
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1
echo "::endgroup::"

# ⚠️ **바로 판정하지 않는다.** WorkManagerInitializer 건은 `androidx.startup` 이
# ContentProvider 단계에서 도는 것이라 거의 즉시 죽지만, Firebase 초기화처럼 몇 초 뒤에
# 터지는 경로도 있다. 20초는 실기기 스모크에서 「켜고 잠깐 본다」에 해당하는 값이다.
sleep "$SETTLE_SECONDS"

FAILED=0

echo "::group::프로세스 생존 확인"
if PID=$(adb shell pidof "$PKG"); then
    echo "살아 있다 — pid $PID"
else
    echo "::error::프로세스가 없다 — ${SETTLE_SECONDS}초 안에 죽었다"
    FAILED=1
fi
echo "::endgroup::"

echo "::group::crash 버퍼 확인"
CRASH=$(adb logcat -b crash -d)
if [ -n "$CRASH" ]; then
    echo "::error::crash 버퍼에 내용이 있다"
    echo "$CRASH"
    FAILED=1
else
    echo "비어 있다"
fi
echo "::endgroup::"

# 원격 게이트에 막혀도 **크래시는 아니다** — 통과시키되 무엇에 막혔는지는 남긴다.
# 콘솔의 `min_supported_version_android` 가 올라가면 「업데이트가 필요해요」만 보인다
# (docs/RELEASE.md 「원격 게이트가 켜진 뒤로는 스모크 테스트가 막힐 수 있다」).
echo "::group::원격 게이트 값"
adb logcat -d -s NM467 || true
echo "::endgroup::"

if [ "$FAILED" -ne 0 ]; then
    echo "::group::마지막 로그 200줄"
    adb logcat -d | tail -200
    echo "::endgroup::"
    exit 1
fi

echo "기동 검사 통과"

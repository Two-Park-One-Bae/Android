#!/usr/bin/env python3
"""퀄컴 칩셋 ↔ Hexagon 세대 매핑을 **Qualcomm AI Hub 에서 받아** 우리 설정과 대조한다.

## 왜 필요한가
`app/src/main/device_targeting_configuration.xml` 이 「어느 SoC 에 어느 QNN 모듈을 내려줄지」를
정하는데, 그 매핑은 **외워서 적을 수 있는 종류가 아니다.** 실제로 한 번 틀렸다 — Galaxy S26
(SM8850 · Hexagon V81)을 통째로 빠뜨린 것을 이 대조로 잡았다.

못 알아본 칩은 조용히 `other` 로 떨어져 CPU/WebGPU 로 돈다. **깨지지는 않지만 아무도 알려주지
않는다.** 그래서 사람이 주기적으로 묻는 대신 스크립트가 묻게 한다.

## 무엇을 비교하나
1. **AI Hub 기기 목록**의 `hexagon:` · `chipset:` 속성 — 퀄컴이 직접 붙인 값이다
2. **`qnn-runtime` AAR** 에 실제로 들어 있는 세대별 Skel — 세대를 알아도 Skel 이 없으면 못 쓴다
3. **우리 `device_targeting_configuration.xml`** 의 그룹과 selector

## 쓰는 법
    scratchpad/qaihub-venv/bin/python scripts/qnn_chipsets.py

`qai_hub` 와 AI Hub 토큰(`~/.qai_hub/client.ini`)이 필요하다. 빌드에는 안 쓴다 — 사람이
새 칩 소식을 들었을 때, 또는 `qnn-runtime` 을 올렸을 때 돌린다.

어긋나는 게 있으면 **종료 코드 1** 이다.
"""

from __future__ import annotations

import pathlib
import re
import sys
import xml.etree.ElementTree as ET
import zipfile

REPO = pathlib.Path(__file__).resolve().parent.parent
CONFIG = REPO / "app/src/main/device_targeting_configuration.xml"
CONFIG_NS = "http://schemas.android.com/apk/config"

# 그룹 이름은 `qti_v<세대>` 꼴로 짓는다 — 이름에서 세대를 되읽을 수 있어야 대조가 된다.
GROUP_RE = re.compile(r"^qti_v(\d+)$")
# AI Hub 의 chipset 태그 중 SoC 모델명인 것. `qualcomm-snapdragon-8-elite` 같은 마케팅명은 거른다.
# ⚠️ 하이픈을 받아야 한다 — `sm8750-ac`(S25) · `sm8850-ad`(S26) 가 그 꼴이다. `\w+` 로 적었다가
#    두 세대를 통째로 놓쳤다.
SOC_RE = re.compile(r"^(sm|sdm|qcs|sa|sc)[\w-]+$")

# **일부러 대상에서 뺀 세대.** Skel 은 AAR 에 있지만 우리 모델이 그 세대에서 안 돈다.
#
# V68 — 2026-10-15 Galaxy S21(SM8350) 실측. HTP V68 은 **부동소수 연산을 받지 않는다** —
#   Conv·MatMul·Relu 까지 노드마다 `backendValidateOpConfig` 3110 으로 거절하고, 세션 생성이
#   「NHWC 로 바꿔 둔 Conv 를 EP 가 안 가져갔다」로 실패한다. 각인(fp32)·마크(fp16) 둘 다.
#   쓰려면 int8 양자화 모델을 따로 만들어야 한다. V69 부터는 fp16 이 된다(S22 실측).
UNSUPPORTED = {
    "68": "스냅드래곤 888 · 778G 세대 — HTP 가 부동소수를 못 받는다",
}
# **일부러 그룹에 안 넣은 SoC.** 세대는 지원하지만 이 칩만 뺀다. 빠졌다고 보고하지 않는다.
#
# SM7750(7 Gen 4) — AI Hub 는 V73 이라 하지만 실기기 근거가 없다(7 Gen 3 = SM7550 과 헷갈리기 쉽다).
#   Play 기기 카탈로그(2026-10-15)에서 이 칩을 쓰는 16종(모토로라 · 오포 · 비보 · 리얼미 · 아너 등)은
#   우리 앱 설치가 **0대**이고 삼성 기기는 없다. `qnn_delivery` 이벤트에 잡히면 그때 실측하고 넣는다.
SKIPPED_SOCS = {
    "sm7750": "7 Gen 4 — 세대 미확인 · 우리 설치 0대",
}
# `sm8750-ac` 처럼 붙는 SKU 접미사. Build.SOC_MODEL 이 달고 올지 아닐지 몰라 양쪽을 같은 것으로 본다.
SUFFIX_RE = re.compile(r"-[a-z]{2}$")


def base_model(soc: str) -> str:
    return SUFFIX_RE.sub("", soc.lower())


def from_ai_hub() -> dict[str, set[str]]:
    """{'73': {'sm8550', ...}, ...} — AI Hub 가 아는 세대별 SoC."""
    import qai_hub as hub

    mapping: dict[str, set[str]] = {}
    for device in hub.get_devices():
        attrs = device.attributes
        # **폰만 본다.** AI Hub 에는 Arduino 보드(QCS8275)와 노트북용 X Elite(SC8380XP)도 있는데
        # 우리 앱이 갈 곳이 아니다. 그것들까지 대조하면 영원히 「빠진 SoC」가 뜬다.
        if "format:phone" not in attrs:
            continue
        hexagon = next((a.split(":", 1)[1] for a in attrs if a.startswith("hexagon:")), None)
        if not hexagon:
            continue
        socs = {
            a.split(":", 1)[1]
            for a in attrs
            if a.startswith("chipset:") and SOC_RE.match(a.split(":", 1)[1])
        }
        if socs:
            mapping.setdefault(hexagon.lstrip("v"), set()).update(socs)
    return mapping


def skel_generations() -> set[str]:
    """`qnn-runtime` AAR 에 Skel 이 실제로 들어 있는 세대."""
    cache = pathlib.Path.home() / ".gradle/caches/modules-2/files-2.1/com.qualcomm.qti/qnn-runtime"
    aars = sorted(cache.rglob("*.aar"))
    if not aars:
        print("  ! qnn-runtime AAR 을 Gradle 캐시에서 못 찾았다 — 한 번 빌드한 뒤 다시 돌린다")
        return set()
    with zipfile.ZipFile(aars[-1]) as zf:
        return {
            m.group(1)
            for name in zf.namelist()
            if (m := re.search(r"libQnnHtpV(\d+)Skel\.so$", name))
        }


def from_config() -> dict[str, set[str]]:
    """{'73': {'sm8550', ...}, ...} — 우리가 적어 둔 것."""
    groups: dict[str, set[str]] = {}
    for group in ET.parse(CONFIG).getroot().iter(f"{{{CONFIG_NS}}}device-group"):
        match = GROUP_RE.match(group.get("name", ""))
        if not match:
            continue
        models = {
            soc.get("model", "").lower()
            for soc in group.iter(f"{{{CONFIG_NS}}}system-on-chip")
            if soc.get("manufacturer") == "QTI"
        }
        groups[match.group(1)] = models
    return groups


def main() -> int:
    print("AI Hub 조회 중…")
    hub_map = from_ai_hub()
    skels = skel_generations()
    ours = from_config()

    print(f"\nAI Hub 세대 {len(hub_map)}개 · AAR Skel 세대 {sorted(skels)} · 우리 그룹 {sorted(ours)}\n")

    problems = 0
    for generation in sorted(hub_map, key=int):
        hub_socs = hub_map[generation]
        if generation in UNSUPPORTED:
            flag = " ⚠️ 그룹이 있다 — 일부러 뺀 세대인데 설정에 들어가 있다" if generation in ours else ""
            if generation in ours:
                problems += 1
            print(f"V{generation}: 대상 밖 ({UNSUPPORTED[generation]}){flag}")
            continue

        if generation not in skels:
            # Skel 이 없으면 그룹을 만들어도 토대만 받고 QNN 이 안 뜬다. 빠져 있는 게 맞다.
            mark = "건너뜀" if generation not in ours else "⚠️ 그룹이 있는데 Skel 이 없다"
            if generation in ours:
                problems += 1
            print(f"V{generation}: Skel 없음 — {mark}  ({', '.join(sorted(hub_socs))})")
            continue

        our_socs = {base_model(s) for s in ours.get(generation, set())}
        missing = {s for s in hub_socs if base_model(s) not in our_socs and base_model(s) not in SKIPPED_SOCS}
        skipped = {s for s in hub_socs if base_model(s) in SKIPPED_SOCS}
        if generation not in ours:
            problems += 1
            print(f"V{generation}: ❌ 그룹이 없다 — 모듈 `qnn_v{generation}` 과 그룹 `qti_v{generation}` 을 만들어야 한다")
            print(f"        AI Hub 가 아는 SoC: {', '.join(sorted(hub_socs))}")
        elif missing:
            problems += 1
            print(f"V{generation}: ❌ 그룹에 빠진 SoC — {', '.join(sorted(missing))}")
        else:
            print(f"V{generation}: ✅ {', '.join(sorted(hub_socs - skipped))}")
        for soc in sorted(skipped):
            flag = ""
            if base_model(soc) in our_socs:
                problems += 1
                flag = " ⚠️ 그룹에 들어가 있다 — 일부러 뺀 SoC 다"
            print(f"V{generation}: ↷ 일부러 뺌 — {soc} ({SKIPPED_SOCS[base_model(soc)]}){flag}")

    # 우리가 적었는데 AI Hub 가 모르는 것 — 오타이거나, AI Hub 에 기기가 없을 뿐이거나.
    hub_bases = {base_model(s) for socs in hub_map.values() for s in socs}
    for generation, socs in sorted(ours.items(), key=lambda kv: int(kv[0])):
        unknown = {s for s in socs if base_model(s) not in hub_bases}
        if unknown:
            print(f"V{generation}: ℹ️ AI Hub 목록에 없는 SoC — {', '.join(sorted(unknown))} (오타이거나 AI Hub 에 그 기기가 없을 뿐)")

    print()
    if problems:
        print(f"어긋나는 것 {problems}건. 고칠 자리는 셋이다 —")
        print(f"  1. app/src/main/device_targeting_configuration.xml  그룹·selector")
        print(f"  2. qnn_v<세대>/  모듈 디렉터리 + settings.gradle.kts + app 의 dynamicFeatures")
        print(f"  3. qnn_core/src/main/AndroidManifest.xml  그룹 목록")
        return 1
    print("설정과 AI Hub 가 일치한다.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

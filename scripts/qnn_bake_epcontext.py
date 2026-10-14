#!/usr/bin/env python3
"""모델을 **Hexagon 세대마다 미리 컴파일**해 EPContext 바이너리를 받는다.

## 왜 미리 굽나
QNN 은 기기에서 ONNX 그래프를 Hexagon 기계어로 번역한 뒤에야 돈다. 그 번역기가
`libQnnHtpPrepare.so` 이고 **80MB** 다. 게다가 번역이 느리다 — 각인 모델 첫 세션 생성이
실기기에서 **84초** 걸렸다.

미리 번역해 실으면 둘 다 사라진다. 그래서 `qnn_*` feature module 은 `HtpPrepare` 를 빼고,
대신 이 스크립트가 구운 바이너리를 앱이 싣는다.

⚠️ **번역 결과는 세대마다 다르다.** V73 바이너리를 V75 기기에서 열 수 없다. 그래서 기기그룹
(= 세대)마다 따로 굽고, 기기는 asset pack 의 `#group_` 변형으로 자기 것만 받는다.

## 쓰는 법
    scratchpad/qaihub-venv/bin/python scripts/qnn_bake_epcontext.py [--out DIR]

`qai_hub` 와 AI Hub 토큰(`~/.qai_hub/client.ini`)이 필요하다. 한 모델·한 세대에 몇 분 걸린다.

⚠️ 산출물은 **커밋하지 않는다** — 세대당 80MB 가 넘고, 저장소는 이미 119MB 검출 모델을
`.gitignore` 로 빼 두고 있다. 같은 길을 따른다.
"""

from __future__ import annotations

import argparse
import pathlib
import sys
import zipfile

REPO = pathlib.Path(__file__).resolve().parent.parent
ASSETS = REPO / "app/src/main/assets"

# 세대 → AI Hub 기기. `scripts/qnn_chipsets.py` 가 대조하는 그 세대들이다.
# Family 기기를 쓰면 AI Hub 가 그 계열의 대표 칩셋으로 컴파일한다.
DEVICES = {
    "73": "Samsung Galaxy S23 (Family)",
    "75": "Samsung Galaxy S24 (Family)",
    "79": "Samsung Galaxy S25 (Family)",
    "81": "Samsung Galaxy S26 (Family)",
}

# 모델 → 입력 모양. **배치가 고정이어야 한다** — 동적이면 QNN 이 `Cannot get shape` 로
# 노드를 거의 안 받고 조용히 CPU 로 돈다(2026-10-13 실측).
#   각인 144 = ImprintPreprocess.SAMPLES, 마크 8 = MarkPreprocess.ROTATIONS
MODELS = {
    "imprint": ("reader_ep60_s1.onnx", {"image": ((144, 1, 128, 128), "float32")}),
    "mark": ("mark_species_fp16.onnx", {"image": ((8, 3, 224, 224), "float32")}),
}

# `qnn_context_binary` 는 폐기됐다. 이쪽이 ORT 가 그대로 여는 EPContext ONNX 를 준다.
OPTIONS = "--target_runtime precompiled_qnn_onnx"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", type=pathlib.Path, required=True, help="바이너리를 받을 디렉터리")
    parser.add_argument("--generation", action="append", help="특정 세대만 (기본: 전부)")
    args = parser.parse_args()

    import qai_hub as hub

    generations = args.generation or list(DEVICES)
    args.out.mkdir(parents=True, exist_ok=True)

    jobs = []
    for generation in generations:
        device = hub.Device(DEVICES[generation])
        for label, (filename, specs) in MODELS.items():
            model = ASSETS / filename
            if not model.is_file():
                print(f"  ! {model} 없음 — 건너뜀")
                continue
            job = hub.submit_compile_job(
                model=str(model),
                device=device,
                input_specs=specs,
                options=OPTIONS,
                name=f"nursemate-{label}-v{generation}",
            )
            jobs.append((generation, label, job))
            print(f"  제출  V{generation} {label:8s} {job.job_id}")

    print(f"\n{len(jobs)}개 제출. 기다린다…\n")

    failed = 0
    for generation, label, job in jobs:
        # ⚠️ 확장자를 붙이지 않는다 — SDK 가 `.onnx.zip` 을 덧붙여서 `*.onnx.onnx.zip` 이 된다.
        stem = f"{label}_v{generation}"
        try:
            job.wait()
            if not job.get_status().success:
                print(f"  ❌ V{generation} {label}: {job.get_status().message}")
                failed += 1
                continue
            archive = pathlib.Path(job.download_target_model(str(args.out / stem)))
            # 받은 것은 zip 이다. 안에 EPContext 껍데기(`model.onnx`)와 컴파일 바이너리
            # (`model.bin`)가 들어 있다 — 앱이 싣는 것은 푼 쪽이다.
            target = args.out / stem
            with zipfile.ZipFile(archive) as zf:
                zf.extractall(target)
            archive.unlink()
            size = sum(f.stat().st_size for f in target.rglob("*") if f.is_file()) / 1048576
            print(f"  ✅ V{generation} {label:8s} {size:6.1f} MB  {target.name}/")
        except Exception as exc:  # noqa: BLE001 — 어느 실패든 나머지 세대는 계속 받는다
            print(f"  ❌ V{generation} {label}: {exc}")
            failed += 1

    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())

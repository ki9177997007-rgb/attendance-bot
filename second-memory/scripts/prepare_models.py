"""Bundle upstream models once at build time; speech recognition works without a network."""
from pathlib import Path
import urllib.request
import zipfile

assets = Path("app/src/main/assets")
assets.mkdir(parents=True, exist_ok=True)
(assets / "speaker-0.4.zip").unlink(missing_ok=True)
for source, target, required in [
    ("vosk-model-small-ru-0.22", "ru-small-0.22", "am/final.mdl"),
]:
    destination = assets / (target + ".zip")
    with urllib.request.urlopen("https://alphacephei.com/vosk/models/" + source + ".zip", timeout=120) as response:
        with destination.open("wb") as output:
            while chunk := response.read(1024 * 1024):
                output.write(chunk)
    with zipfile.ZipFile(destination) as archive:
        if archive.testzip() is not None:
            raise RuntimeError("Corrupt model: " + source)
        if source + "/" + required not in archive.namelist():
            raise RuntimeError("Missing model data: " + source)
    print(source, destination.stat().st_size)

# GigaAM v3 and runtime are pinned and verified; no download on the user's phone.
import hashlib
from concurrent.futures import ThreadPoolExecutor

revision = "0424d62637b37d8b7b1eb0f75a04d78dec55fdcc"
model_base = "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-transducer-giga-am-v3-russian-2025-12-16/resolve/" + revision + "/"
files = [
    (model_base + "encoder.int8.onnx", assets / "speech/encoder.int8.onnx", "0ec54e5f130a91c0f228d21795ede10b31c992a1f73e45dcd827e843a71a9b50"),
    (model_base + "decoder.onnx", assets / "speech/decoder.onnx", "5d1cfef155d8e07f213bd2f3229b5f6c5c29355c7fa5c1b9eb62cb1925725254"),
    (model_base + "joiner.onnx", assets / "speech/joiner.onnx", "fd1d02f45c2ad3d6b67cc149811ad794ab4b020ed49a0a9e2790a8619d1cddd8"),
    (model_base + "tokens.txt", assets / "speech/tokens.txt", "17cc514451bcceac9c280068c71502f8448f99e9fb1456b8d0761651fd0392f2"),
    ("https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx", assets / "speech/silero_vad.onnx", "9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6"),
    ("https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.1/sherpa-onnx-2.0.1.aar", Path("app/libs/sherpa-onnx-2.0.1.aar"), "a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7"),
]

def fetch_verified(item):
    url, destination, expected = item
    destination.parent.mkdir(parents=True, exist_ok=True)
    if destination.is_file() and hashlib.file_digest(destination.open("rb"), "sha256").hexdigest() == expected:
        return
    temporary = destination.with_suffix(destination.suffix + ".part")
    for attempt in range(3):
        try:
            with urllib.request.urlopen(url, timeout=180) as response, temporary.open("wb") as output:
                while chunk := response.read(1024 * 1024):
                    output.write(chunk)
            with temporary.open("rb") as data:
                if hashlib.file_digest(data, "sha256").hexdigest() != expected:
                    raise RuntimeError("Hash mismatch: " + destination.name)
            temporary.replace(destination)
            print("Verified", destination, destination.stat().st_size)
            return
        except Exception:
            temporary.unlink(missing_ok=True)
            if attempt == 2:
                raise

with ThreadPoolExecutor(max_workers=4) as pool:
    list(pool.map(fetch_verified, files))

(assets / "speech/engine.txt").write_text("GigaAM v3 RNN-T, int8; Russian; local inference\nModel: " + revision + "\nRuntime: sherpa-onnx 2.0.1\n", encoding="utf-8")

(assets / "speech/NOTICE.txt").write_text(Path("SPEECH-NOTICES.txt").read_text(encoding="utf-8"), encoding="utf-8")

"""Bundle upstream models once at build time; the app needs no network permission."""
from pathlib import Path
import urllib.request
import zipfile

assets = Path("app/src/main/assets")
assets.mkdir(parents=True, exist_ok=True)
for source, target, required in [
    ("vosk-model-small-ru-0.22", "ru-small-0.22", "am/final.mdl"),
    ("vosk-model-spk-0.4", "speaker-0.4", "final.ext.raw"),
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

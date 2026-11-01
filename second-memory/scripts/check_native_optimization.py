from pathlib import Path
import json,shlex
files=list(Path("app/.cxx").rglob("compile_commands.json"))
assert files, "No native compile commands to verify"
checked=[]
for file in files:
 for entry in json.loads(file.read_text()):
  name=Path(entry["file"]).name
  if name not in {"ggml.c","ggml-cpu.cpp","memory_ai.cpp"}:continue
  args=entry.get("arguments") or shlex.split(entry["command"])
  levels=[arg for arg in args if arg.startswith("-O")]
  assert levels and levels[-1] in {"-O2","-O3"}, (name,levels)
  checked.append(name+": "+levels[-1])
assert any(s.startswith("ggml") for s in checked), "Tensor kernels were not checked"
assert any(s.startswith("memory_ai.cpp") for s in checked), "JNI bridge was not checked"
report=Path("app/build/reports/native-optimization.txt");report.parent.mkdir(parents=True,exist_ok=True);report.write_text("\n".join(checked)+"\n")
print("Native optimization verified:",", ".join(checked))

import hashlib,json,pathlib,subprocess,urllib.request
path=pathlib.Path("native-test/model.gguf")
url="https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/91cad51170dc346986eccefdc2dd33a9da36ead9/qwen2.5-1.5b-instruct-q4_k_m.gguf"
urllib.request.urlretrieve(url,path)
h=hashlib.sha256()
with path.open("rb") as f:
 while b:=f.read(1024*1024):h.update(b)
assert h.hexdigest()=="6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e"
for text,expected in [("Сергей, подготовь реестр завтра к 19 часам",True),("Сегодня хорошая погода",False)]:
 prompt='<|im_start|>system\nТы секретарь. Извлеки поручение из реплики. Верни только JSON: {"task":true,"action":"текст поручения","assignee":"имя или пустая строка","due":"YYYY-MM-DDTHH:mm или пустая строка"}. Если поручения нет: {"task":false}. Не придумывай факты. Текущая дата 2026-10-31T17:00.\n<|im_end|>\n<|im_start|>user\n'+text+'<|im_end|>\n<|im_start|>assistant\n'
 pathlib.Path("native-test/prompt.txt").write_text(prompt)
 result=subprocess.run(["native-test/memory_ai",str(path),"native-test/prompt.txt"],capture_output=True,text=True,timeout=180)
 if result.returncode:raise RuntimeError(result.stderr[-6000:])
 value=json.loads(result.stdout.strip());print(value,flush=True)
 assert value["task"] is expected
 if expected:
  assert "реестр" in value["action"].lower()
  assert "Сергей" in value["assignee"]
print("Real Qwen inference: PASS")

# Exercise the new grammar using the exact production prompt prefix/suffix.
import re
java_source = pathlib.Path("app/src/main/java/ru/secondmemory/ConversationAnalysis.java").read_text()
match = re.search(r'return\s+("(?:\\.|[^"\\])*")\s*\+source\+\s*("(?:\\.|[^"\\])*");', java_source)
assert match, "Update the native fixture if the production prompt layout changes"
source_lines = {
    1: "В больнице закончилась бумага для принтера.",
    2: "Решили заказать бумагу в понедельник.",
    3: "Точный срок доставки пока неизвестен.",
}
prompt = json.loads(match[1]) + "\n".join(f"[{n} · 18:00:00] {text}" for n,text in source_lines.items()) + "\n" + json.loads(match[2])
pathlib.Path("native-test/conversation-prompt.txt").write_text(prompt)
result = subprocess.run(["native-test/memory_ai",str(path),"native-test/conversation-prompt.txt","--analysis"],capture_output=True,text=True,timeout=240)
if result.returncode: raise RuntimeError(result.stderr[-6000:])
value=json.loads(result.stdout.strip()); print("Full conversation analysis:",value,flush=True)
assert 0 < len(value["items"]) <= 6
assert any(point["kind"] in {"topic","fact","decision"} for point in value["items"]), "Non-task conversations must be analyzed too"
assert any(point["kind"] == "decision" for point in value["items"]), "The explicit decision must appear in the overview"
for point in value["items"]:
    assert point["line"] in source_lines
    assert re.search("[А-Яа-яЁё]",point["text"]), point
    assert len(source_lines[point["line"]]) >= 6 # literal evidence is attached by the app, not generated
    assert point["kind"] != "task", "A discussion or a decision is not a new assigned task"
print("Real Qwen full-conversation inference and literal evidence: PASS")

# Semantic query expansion uses the same production prompt and native grammar as Android.
search_java=pathlib.Path("app/src/main/java/ru/secondmemory/MemorySearch.java").read_text()
match=re.search(r'return\s+("(?:\\.|[^"\\])*")\s*\+query\+\s*("(?:\\.|[^"\\])*");',search_java)
assert match, "Production search prompt not found"
query="Где говорили, что деньги ещё не перечислили?"
prompt=json.loads(match[1])+query+json.loads(match[2])
pathlib.Path("native-test/search-prompt.txt").write_text(prompt)
result=subprocess.run(["native-test/memory_ai",str(path),"native-test/search-prompt.txt","--search"],capture_output=True,text=True,timeout=180)
if result.returncode:raise RuntimeError(result.stderr[-6000:])
value=json.loads(result.stdout.strip());print("Semantic memory search terms:",value,flush=True)
assert 1<=len(value["terms"])<=8
assert any(re.search("выплат|перечисл|поступ|сч[её]т|ден",term,re.I) for term in value["terms"])
assert all(isinstance(term,str) and 0<len(term)<=80 for term in value["terms"])
print("Real Qwen memory query expansion: PASS")

match=re.search(r'return\s+("(?:\\.|[^"\\])*")\s*\+source\+\s*("(?:\\.|[^"\\])*");',search_java)
assert match, "Production recall answer prompt not found"
# Validate the model's answer through the actual Android-independent Java guard.
json_jar=pathlib.Path("native-test/json.jar")
urllib.request.urlretrieve("https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar",json_jar)
java_sources=["app/src/main/java/ru/secondmemory/"+name+".java" for name in ["ConversationAnalysis","MemorySearch","RecallWindow"]]
subprocess.run(["javac","-cp",str(json_jar),"-d","native-test/recall-java",*java_sources,"scripts/RecallValidationCli.java"],check=True)
for question,should_answer in [("Что говорили о пособии?",True),("Как зовут врача?",False)]:
 source="Что нужно вспомнить:\n"+question+"\nНайденные реплики:\n[1] Пособие пока не поступило на счёт.\n[2] Завтра ожидается доставка стульев.\n"
 prompt=json.loads(match[1])+source+json.loads(match[2])
 pathlib.Path("native-test/recall-answer.txt").write_text(prompt)
 result=subprocess.run(["native-test/memory_ai",str(path),"native-test/recall-answer.txt","--analysis"],capture_output=True,text=True,timeout=180)
 if result.returncode:raise RuntimeError(result.stderr[-6000:])
 pathlib.Path("native-test/answer.json").write_text(result.stdout)
 checked=subprocess.run(["java","-cp",str(json_jar)+":native-test/recall-java","ru.secondmemory.RecallValidationCli",question,"native-test/answer.json"],capture_output=True,text=True,check=True)
 value={"items":json.loads(checked.stdout)};print("Validated recall answer:",question,value,flush=True)
 if should_answer:
  assert len(value["items"])==1, value
  assert value["items"][0]["text"]=="Пособие пока не поступило на счёт.",value
  assert all(point["line"]==1 and point["kind"]=="fact" for point in value["items"]),value
 else:assert not value["items"], "An unknown name must not be invented"
print("Real Qwen source-grounded recall answers: PASS")

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

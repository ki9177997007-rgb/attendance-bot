import pathlib,hashlib,urllib.request
p=pathlib.Path("/tmp/second-memory-qwen.gguf")
url="https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/91cad51170dc346986eccefdc2dd33a9da36ead9/qwen2.5-1.5b-instruct-q4_k_m.gguf"
with urllib.request.urlopen(url,timeout=180) as src,p.open("wb") as out:
 while b:=src.read(1024*1024):out.write(b)
with p.open("rb") as f:assert hashlib.file_digest(f,"sha256").hexdigest()=="6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e"
print("Android Qwen fixture verified")

#!/usr/bin/env python3
"""Zero-cost video renderer with optional licensed audio and Telegram publishing."""
import argparse, json, os, pathlib, subprocess, urllib.request, uuid
ROOT=pathlib.Path(__file__).resolve().parent
def cmd(*args):
    subprocess.run(args,check=True)
def render(out, title):
    out=pathlib.Path(out);out.parent.mkdir(parents=True,exist_ok=True)
    captions=["ТЕХНОЛОГИИ БЕЗ МИФОВ","УМНЫЙ ДОМ БЕЗ ИНТЕРНЕТА","ОБЛАКО НЕ ВСЕГДА НУЖНО","ВЫБИРАЙ ЛОКАЛЬНЫЙ КОНТРОЛЬ"]
    font="/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
    parts=[]
    for i,line in enumerate(captions):
        txt=out.parent/("caption_%d.txt"%i);txt.write_text(line,encoding="utf-8")
        part=out.parent/("part_%d.mp4"%i)
        filt=("drawbox=x=0:y=0:w=iw:h=ih:color=0x111827:t=fill,"
              "drawbox=x=30:y=180+60*sin(t*2):w=480:h=280:color=0x16a085@0.4:t=fill,"
              "drawtext=fontfile="+font+":textfile="+str(txt)+":fontsize=28:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2")
        cmd("ffmpeg","-y","-loglevel","error","-f","lavfi","-i","color=c=0x111827:s=540x960:r=24:d=5","-vf",filt,"-c:v","libx264","-preset","veryfast","-pix_fmt","yuv420p",str(part))
        parts.append(part)
    manifest=out.parent/"parts.txt"
    manifest.write_text("".join("file '"+str(part.resolve())+"'\n" for part in parts))
    silent=out.parent/"silent.mp4"
    cmd("ffmpeg","-y","-loglevel","error","-f","concat","-safe","0","-i",str(manifest),"-c","copy",str(silent))
    # Audio optional; no deceptive 'AI voice' or fabricated licensed music.
    voice=os.getenv("VOICE_FILE","");music=os.getenv("MUSIC_FILE","")
    inputs=["-i",str(silent)]
    if voice: inputs+=["-i",voice]
    if music: inputs+=["-i",music]
    if voice and music:
        af=["-filter_complex","[1:a]volume=1.2[v];[2:a]volume=0.22[m];[v][m]amix=inputs=2:duration=first:normalize=0,alimiter=limit=0.95[a]","-map","0:v","-map","[a]"]
    elif voice or music:
        af=["-map","0:v","-map","1:a"]
    else:
        inputs+=["-f","lavfi","-i","anullsrc=channel_layout=stereo:sample_rate=44100"]
        af=["-map","0:v","-map","1:a"]
    cmd("ffmpeg","-y","-loglevel","error",*inputs,*af,"-c:v","copy","-c:a","aac","-b:a","160k","-t","20","-movflags","+faststart",str(out))
    print("Created",out)
def send_telegram(file, caption):
    token=os.environ.get("TELEGRAM_BOT_TOKEN");chat=os.environ.get("TELEGRAM_CHAT_ID")
    if not token or not chat: raise RuntimeError("Missing TELEGRAM_BOT_TOKEN or TELEGRAM_CHAT_ID")
    data=pathlib.Path(file).read_bytes()
    boundary="----video-factory-"+uuid.uuid4().hex
    chunks=[]
    def field(name,value):
        chunks.append(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+name+"\"\r\n\r\n"+value+"\r\n").encode())
    field("chat_id",chat);field("caption",caption)
    chunks.append(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"video\"; filename=\"video.mp4\"\r\nContent-Type: video/mp4\r\n\r\n").encode()+data+b"\r\n")
    chunks.append(("--"+boundary+"--\r\n").encode())
    req=urllib.request.Request("https://api.telegram.org/bot"+token+"/sendVideo",data=b"".join(chunks),headers={"Content-Type":"multipart/form-data; boundary="+boundary},method="POST")
    with urllib.request.urlopen(req,timeout=120) as resp:
        result=json.load(resp)
    if not result.get("ok"):raise RuntimeError("Telegram rejected upload")
    print("Telegram message_id",result["result"]["message_id"])
if __name__=="__main__":
    parser=argparse.ArgumentParser()
    parser.add_argument("--out",default="video_factory/out/video.mp4")
    parser.add_argument("--title",default="Умный дом")
    parser.add_argument("--publish-telegram",action="store_true")
    a=parser.parse_args()
    render(a.out,a.title)
    if a.publish_telegram:send_telegram(a.out,a.title)

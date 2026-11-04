#!/usr/bin/env python3
"""Existing VideoFactory renderer: finance-niche edition. No automatic publication by default."""
import argparse, json, os, pathlib, subprocess, urllib.request, uuid
ROOT=pathlib.Path(__file__).resolve().parent
SCENARIOS={
 "salary":{"title":"Куда исчезает зарплата?","slides":["ЗАРПЛАТА ПРИШЛА","А КУДА ОНА ИСЧЕЗЛА?","ПРОВЕРЬ 3 КАТЕГОРИИ","ДОСТАВКА • ТАКСИ • ПОДПИСКИ","ЗАПИШИ ТРАТЫ ЗА 7 ДНЕЙ"],"caption":"Куда исчезает зарплата? Начни с расходов за последние 7 дней. #бюджет #финансы #экономия"},
 "subscriptions":{"title":"Подписки списывают деньги","slides":["СПИСАНИЕ, КОТОРОЕ ТЫ ЗАБЫЛ","ОТКРОЙ ИСТОРИЮ ПЛАТЕЖЕЙ","НАЙДИ ПОВТОРЯЮЩИЕСЯ","ОТМЕНИ НЕНУЖНЫЕ","ПРОВЕРЬ СНОВА ЧЕРЕЗ МЕСЯЦ"],"caption":"Проверь повторяющиеся платежи: возможно, ты платишь за то, чем не пользуешься. #подписки #экономия"},
 "discount":{"title":"Скидка или ловушка?","slides":["СКИДКА 50%?","НЕ СПЕШИ ПОКУПАТЬ","СРАВНИ ЦЕНУ У ДРУГИХ","ПРОВЕРЬ ЦЕНУ ЗА 1 ШТУКУ","ВЫГОДА — ЭТО НЕ ПРОЦЕНТ"],"caption":"Большая скидка не всегда означает низкую цену. Сравни итоговую стоимость. #скидки #финансы"},
 "coffee":{"title":"Кофе и бюджет","slides":["КОФЕ НЕ ВРАГ БЮДЖЕТА","НО ПРИВЫЧКИ СТОИТ СЧИТАТЬ","250 ₽ × 20 = 5 000 ₽","ЭТО УСЛОВНЫЙ ПРИМЕР","РЕШАТЬ, НА ЧТО ТРАТИТЬ, ТЕБЕ"],"caption":"Сколько стоит привычка за месяц? Пример: 250 ₽ × 20 = 5 000 ₽. #бюджет #кофе"}
}
def run(*args): subprocess.run(args,check=True)
def render(out, scenario):
    out=pathlib.Path(out);out.parent.mkdir(parents=True,exist_ok=True)
    font="/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
    if not pathlib.Path(font).exists(): raise FileNotFoundError("Install fonts-dejavu-core")
    slides=SCENARIOS[scenario]["slides"]; parts=[]
    for i,line in enumerate(slides):
        label=out.parent/("caption_%02d.txt"%i);label.write_text(line,encoding="utf-8")
        part=out.parent/("part_%02d.mp4"%i)
        filt=("drawbox=x=0:y=0:w=iw:h=ih:color=0x0b1220:t=fill,"
              "drawbox=x=0:y=0:w=iw*min(t/0.45\\,1):h=14:color=0x2dd4bf:t=fill,"
              "drawbox=x=40:y=200+35*sin(t*2):w=460:h=300:color=0x0f766e@0.5:t=fill,"
              "drawtext=fontfile="+font+":textfile="+str(label)+":fontsize=27:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2")
        run("ffmpeg","-y","-loglevel","error","-f","lavfi","-i","color=c=0x0b1220:s=540x960:r=24:d=3","-vf",filt,"-c:v","libx264","-preset","veryfast","-pix_fmt","yuv420p",str(part))
        parts.append(part)
    manifest=out.parent/"parts.txt"
    manifest.write_text("".join("file '"+str(p.resolve())+"'\n" for p in parts),encoding="utf-8")
    silent=out.parent/"silent.mp4"
    run("ffmpeg","-y","-loglevel","error","-f","concat","-safe","0","-i",str(manifest),"-c","copy",str(silent))
    voice=os.getenv("VOICE_FILE","");music=os.getenv("MUSIC_FILE","")
    args=["-i",str(silent)]
    if voice: args+=["-i",voice]
    if music: args+=["-i",music]
    if voice and music:
        mix=["-filter_complex","[1:a]volume=1.1[v];[2:a]volume=0.18[m];[v][m]amix=inputs=2:duration=first:normalize=0,alimiter=limit=0.95[a]","-map","0:v","-map","[a]"]
    elif voice or music: mix=["-map","0:v","-map","1:a"]
    else:
        args+=["-f","lavfi","-i","anullsrc=channel_layout=stereo:sample_rate=44100"]
        mix=["-map","0:v","-map","1:a"]
    run("ffmpeg","-y","-loglevel","error",*args,*mix,"-c:v","copy","-c:a","aac","-b:a","160k","-t","15","-movflags","+faststart",str(out))
    (out.parent/"metadata.json").write_text(json.dumps({"scenario":scenario,**SCENARIOS[scenario]},ensure_ascii=False,indent=2),encoding="utf-8")
    print("Created",out)
def send_telegram(file,caption):
    token=os.environ.get("TELEGRAM_BOT_TOKEN");chat=os.environ.get("TELEGRAM_CHAT_ID")
    if not token or not chat:raise RuntimeError("Missing TELEGRAM_BOT_TOKEN or TELEGRAM_CHAT_ID")
    boundary="----vf-"+uuid.uuid4().hex
    fields=[]
    for name,value in [("chat_id",chat),("caption",caption)]:
        fields.append(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+name+"\"\r\n\r\n"+value+"\r\n").encode())
    fields.append(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"video\"; filename=\"video.mp4\"\r\nContent-Type: video/mp4\r\n\r\n").encode()+pathlib.Path(file).read_bytes()+("\r\n--"+boundary+"--\r\n").encode())
    req=urllib.request.Request("https://api.telegram.org/bot"+token+"/sendVideo",data=b"".join(fields),headers={"Content-Type":"multipart/form-data; boundary="+boundary},method="POST")
    with urllib.request.urlopen(req,timeout=120) as r: result=json.load(r)
    if not result.get("ok"):raise RuntimeError("Telegram rejected upload")
    print("Telegram message_id",result["result"]["message_id"])
if __name__=="__main__":
    p=argparse.ArgumentParser()
    p.add_argument("--out",default="video_factory/out/video.mp4")
    p.add_argument("--scenario",choices=list(SCENARIOS),default="salary")
    p.add_argument("--title",default=None,help="Legacy compatibility; scenario controls video content")
    p.add_argument("--publish-telegram",action="store_true")
    a=p.parse_args();render(a.out,a.scenario)
    if a.publish_telegram:send_telegram(a.out,SCENARIOS[a.scenario]["caption"])

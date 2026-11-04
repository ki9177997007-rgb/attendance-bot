#!/usr/bin/env python3
"""Existing VideoFactory renderer: finance-niche edition. No automatic publication by default."""
import argparse, json, os, pathlib, shutil, subprocess, urllib.request, uuid
ROOT=pathlib.Path(__file__).resolve().parent
SCENARIOS={
 "salary":{"title":"Куда исчезает зарплата?","slides":["ЗАРПЛАТА ПРИШЛА","А КУДА ОНА ИСЧЕЗЛА?","ПРОВЕРЬ 3 КАТЕГОРИИ","ДОСТАВКА • ТАКСИ • ПОДПИСКИ","ЗАПИШИ ТРАТЫ ЗА 7 ДНЕЙ"],"caption":"Куда исчезает зарплата? Начни с расходов за последние 7 дней. #бюджет #финансы #экономия"},
 "subscriptions":{"title":"Подписки списывают деньги","slides":["СПИСАНИЕ, КОТОРОЕ ТЫ ЗАБЫЛ","ОТКРОЙ ИСТОРИЮ ПЛАТЕЖЕЙ","НАЙДИ ПОВТОРЯЮЩИЕСЯ","ОТМЕНИ НЕНУЖНЫЕ","ПРОВЕРЬ СНОВА ЧЕРЕЗ МЕСЯЦ"],"caption":"Проверь повторяющиеся платежи: возможно, ты платишь за то, чем не пользуешься. #подписки #экономия"},
 "discount":{"title":"Скидка или ловушка?","slides":["СКИДКА 50%?","НЕ СПЕШИ ПОКУПАТЬ","СРАВНИ ЦЕНУ У ДРУГИХ","ПРОВЕРЬ ЦЕНУ ЗА 1 ШТУКУ","ВЫГОДА — ЭТО НЕ ПРОЦЕНТ"],"caption":"Большая скидка не всегда означает низкую цену. Сравни итоговую стоимость. #скидки #финансы"},
 "coffee":{"title":"Кофе и бюджет","slides":["КОФЕ НЕ ВРАГ БЮДЖЕТА","НО ПРИВЫЧКИ СТОИТ СЧИТАТЬ","250 ₽ × 20 = 5 000 ₽","ЭТО УСЛОВНЫЙ ПРИМЕР","РЕШАТЬ, НА ЧТО ТРАТИТЬ, ТЕБЕ"],"caption":"Сколько стоит привычка за месяц? Пример: 250 ₽ × 20 = 5 000 ₽. #бюджет #кофе"}
}
def run(*args): subprocess.run(args,check=True)
def render(out, scenario):
    """Render kinetic-typography finance explainer with motion, progress and data graphics."""
    out=pathlib.Path(out);out.parent.mkdir(parents=True,exist_ok=True)
    font="/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
    if not pathlib.Path(font).exists(): raise FileNotFoundError("Install fonts-dejavu-core")
    slides=SCENARIOS[scenario]["slides"];parts=[]
    for i,line in enumerate(slides):
        label=out.parent/("caption_%02d.txt"%i)
        label.write_text(line,encoding="utf-8")
        part=out.parent/("part_%02d.mp4"%i)
        # A moving grid, animated counter bars and scene-dependent chart elements.
        # drawtext animation enters on the first half second of every scene.
        filt=(
          "format=yuv420p,"
          "drawbox=x=0:y=0:w=iw:h=ih:color=0x09111f:t=fill,"
          "drawgrid=w=54:h=54:t=1:c=0x233047@0.42,"
          "drawbox=x=0:y=0:w=iw*min(t/3\\,1):h=12:color=0x2dd4bf:t=fill,"
          "drawbox=x=36:y=140:w=468:h=680:color=0x10273c@0.92:t=fill,"
          "drawbox=x=54:y=610:w=66:h=180*abs(sin(t*1.4+0.2)):color=0x2dd4bf@0.95:t=fill,"
          "drawbox=x=140:y=610:w=66:h=180*abs(sin(t*1.4+1.4)):color=0x38bdf8@0.95:t=fill,"
          "drawbox=x=226:y=610:w=66:h=180*abs(sin(t*1.4+2.6)):color=0xfbbf24@0.95:t=fill,"
          "drawbox=x=312:y=610:w=66:h=180*abs(sin(t*1.4+3.8)):color=0xfb7185@0.95:t=fill,"
          "drawbox=x=398:y=610:w=66:h=180*abs(sin(t*1.4+5.0)):color=0xa78bfa@0.95:t=fill,"
          "drawtext=fontfile="+font+":textfile="+str(label)+
          ":fontsize=26:fontcolor=white:box=1:boxcolor=0x09111f@0.65:boxborderw=14:"
          "x=max(28\\,(w-text_w)/2):y=340+70*exp(-t*6),"
          "drawtext=fontfile="+font+":text='ПОКУПАЙ ОСОЗНАННО':fontsize=19:fontcolor=0x2dd4bf:"
          "x=(w-text_w)/2:y=845"
        )
        run("ffmpeg","-y","-loglevel","error","-f","lavfi","-i",
            "color=c=0x09111f:s=540x960:r=24:d=3",
            "-vf",filt,"-c:v","libx264","-preset","veryfast",
            "-pix_fmt","yuv420p",str(part))
        parts.append(part)
    manifest=out.parent/"parts.txt"
    manifest.write_text("".join("file '"+str(p.resolve())+"'\n" for p in parts),encoding="utf-8")
    silent=out.parent/"silent.mp4"
    run("ffmpeg","-y","-loglevel","error","-f","concat","-safe","0","-i",str(manifest),"-c","copy",str(silent))
    voice=os.getenv("VOICE_FILE","");music=os.getenv("MUSIC_FILE","")
    if not voice and shutil.which("espeak-ng"):
        narration={
            "salary":"Зарплата исчезает незаметно? Проверь доставку, такси и подписки. Запиши расходы за семь дней.",
            "subscriptions":"Проверь повторяющиеся списания. Найди забытые подписки и отмени те, которыми не пользуешься.",
            "discount":"Скидка пятьдесят процентов? Сравни итоговую цену у других продавцов. Процент не гарантирует выгоду.",
            "coffee":"Двести пятьдесят рублей двадцать раз это пять тысяч. Считай привычки, но решай сам, что важно."
        }
        generated=out.parent/"narration.wav"
        run("espeak-ng","-v","ru","-s","175","-w",str(generated),narration[scenario])
        voice=str(generated)
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
    (out.parent/"metadata.json").write_text(json.dumps({"scenario":scenario,**SCENARIOS[scenario],"voice_present":bool(voice),"music_present":bool(music)},ensure_ascii=False,indent=2),encoding="utf-8")
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

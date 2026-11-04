"""TikTok Content Posting API adapter (draft upload flow).
Requires an authorized user access token with video.upload scope.
This uses inbox upload, NOT direct automatic public posting.
"""
import os,json,pathlib,urllib.request
BASE="https://open.tiktokapis.com/v2"
def upload_to_inbox(video):
    token=os.environ["TIKTOK_ACCESS_TOKEN"]
    size=pathlib.Path(video).stat().st_size
    if not size:raise ValueError("Empty video")
    # TikTok FILE_UPLOAD chunk constraints: 5-64 MiB except final chunk.
    if size>64*1024*1024:raise ValueError("Prototype supports MP4 up to 64 MiB")
    payload={"source_info":{"source":"FILE_UPLOAD","video_size":size,"chunk_size":size,"total_chunk_count":1}}
    req=urllib.request.Request(BASE+"/post/publish/inbox/video/init/",data=json.dumps(payload).encode(),headers={"Authorization":"Bearer "+token,"Content-Type":"application/json; charset=UTF-8"},method="POST")
    with urllib.request.urlopen(req,timeout=30) as resp: init=json.load(resp)
    if init.get("error",{}).get("code") not in ("ok",None):raise RuntimeError("TikTok init failed: "+str(init.get("error")))
    info=init["data"];url=info["upload_url"]
    content=pathlib.Path(video).read_bytes()
    put=urllib.request.Request(url,data=content,headers={"Content-Type":"video/mp4","Content-Length":str(size),"Content-Range":f"bytes 0-{size-1}/{size}"},method="PUT")
    with urllib.request.urlopen(put,timeout=180) as resp: resp.read()
    return {"publish_id":info["publish_id"],"mode":"inbox","note":"User must finish posting inside TikTok; no automatic public post"}

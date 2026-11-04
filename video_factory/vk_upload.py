"""VK video uploader, not a verified VK Clips-specific uploader."""
import os,json,urllib.request,urllib.parse,uuid,pathlib
API="https://api.vk.com/method/"
def call(method,params):
    params.update({"access_token":os.environ["VK_ACCESS_TOKEN"],"v":"5.199"})
    data=urllib.parse.urlencode(params).encode()
    with urllib.request.urlopen(API+method,data=data,timeout=30) as r: result=json.load(r)
    if "error" in result:raise RuntimeError("VK API error: "+str(result["error"].get("error_msg")))
    return result["response"]
def upload(video,title,description):
    group=os.environ.get("VK_GROUP_ID")
    args={"name":title,"description":description,"wallpost":"0"}
    if group:args["group_id"]=group
    meta=call("video.save",args)
    url=meta["upload_url"]
    boundary="----vkupload"+uuid.uuid4().hex
    data=pathlib.Path(video).read_bytes()
    body=("--"+boundary+"\r\nContent-Disposition: form-data; name=\"video_file\"; filename=\"video.mp4\"\r\nContent-Type: video/mp4\r\n\r\n").encode()+data+("\r\n--"+boundary+"--\r\n").encode()
    req=urllib.request.Request(url,data=body,headers={"Content-Type":"multipart/form-data; boundary="+boundary},method="POST")
    with urllib.request.urlopen(req,timeout=180) as r: answer=json.load(r)
    if answer.get("error"):raise RuntimeError("VK upload failed: "+str(answer))
    return "https://vk.com/video"+str(meta["owner_id"])+"_"+str(meta["video_id"])

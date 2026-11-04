"""YouTube Shorts uploader. Requires pip install google-api-python-client google-auth google-auth-httplib2."""
import os, json, pathlib
def upload(video,title,description):
    from google.oauth2.credentials import Credentials
    from google.auth.transport.requests import Request
    from googleapiclient.discovery import build
    from googleapiclient.http import MediaFileUpload
    client=os.environ["YOUTUBE_CLIENT_ID"];secret=os.environ["YOUTUBE_CLIENT_SECRET"];refresh=os.environ["YOUTUBE_REFRESH_TOKEN"]
    credentials=Credentials(token=None,refresh_token=refresh,token_uri="https://oauth2.googleapis.com/token",client_id=client,client_secret=secret,scopes=["https://www.googleapis.com/auth/youtube.upload"])
    credentials.refresh(Request())
    youtube=build("youtube","v3",credentials=credentials,cache_discovery=False)
    body={"snippet":{"title":title[:100],"description":description,"categoryId":"28"},"status":{"privacyStatus":os.getenv("YOUTUBE_PRIVACY","private"),"selfDeclaredMadeForKids":False}}
    request=youtube.videos().insert(part="snippet,status",body=body,media_body=MediaFileUpload(str(video),mimetype="video/mp4",resumable=True,chunksize=5*1024*1024))
    response=None
    while response is None: _,response=request.next_chunk()
    return "https://www.youtube.com/watch?v="+response["id"]

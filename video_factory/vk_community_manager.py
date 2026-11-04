"""VK community management, dry-run by default. Python stdlib only.

Requires user VK token with sufficient rights. Never creates a community unless
--apply AND --confirm-create are explicitly provided.
"""
import argparse
import json
import os
import urllib.parse
import urllib.request

API = "https://api.vk.com/method/"
VERSION = "5.199"


def vk_call(method, **params):
    token = os.environ.get("VK_ACCESS_TOKEN")
    if not token:
        raise RuntimeError("VK_ACCESS_TOKEN secret is missing")
    payload = urllib.parse.urlencode({**params, "access_token": token, "v": VERSION}).encode()
    with urllib.request.urlopen(API + method, data=payload, timeout=30) as response:
        data = json.load(response)
    if "error" in data:
        error = data["error"]
        raise RuntimeError(f"VK API {method}: {error.get('error_code')}: {error.get('error_msg')}")
    return data["response"]


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--config", default="video_factory/vk_communities.json")
    p.add_argument("--apply", action="store_true")
    p.add_argument("--confirm-create", action="store_true")
    p.add_argument("--confirm-edit", action="store_true")
    args = p.parse_args()
    with open(args.config, encoding="utf-8") as file:
        config = json.load(file)
    for community in config["communities"]:
        if not community.get("enabled", False):
            continue
        if community.get("group_id"):
            if community.get("sync_profile") and args.apply and args.confirm_edit:
                result = vk_call(
                    "groups.edit", group_id=community["group_id"],
                    title=community["name"], description=community["description"])
                print(json.dumps({"action": "edit", "group_id": community["group_id"], "result": result}))
            else:
                print(json.dumps({"action": "plan_edit", "group_id": community["group_id"], "requires": ["--apply", "--confirm-edit"]}, ensure_ascii=False))
        else:
            if not community.get("allow_creation", False):
                print(json.dumps({"action": "skip_creation", "name": community["name"]}, ensure_ascii=False))
                continue
            if args.apply and args.confirm_create:
                result = vk_call("groups.create", title=community["name"],
                                 type=community.get("type", "group"),
                                 description=community["description"])
                print(json.dumps({"action": "created", "result": result}, ensure_ascii=False))
            else:
                print(json.dumps({"action": "plan_create", "name": community["name"], "requires": ["--apply", "--confirm-create"]}, ensure_ascii=False))


if __name__ == "__main__":
    main()

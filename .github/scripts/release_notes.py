import re, json, sys, subprocess, urllib.request
text = open("CHANGELOG.md", encoding="utf-8").read()
def notes(v, sha):
    m = re.search(r"^## " + re.escape(v) + r"\b.*?$(.*?)(?=^## |\Z)", text, re.S | re.M)
    head = re.search(r"^## " + re.escape(v) + r"\b.*$", text, re.M)
    n = head.group(0)[3:] + "\n" + m.group(1).strip()
    return n + ("\n\n---\n**Install:** open this page on your phone, tap the .apk below, then Install. "
                "It installs over your current Kiroku and keeps your data.\n\n"
                "All versions: [CHANGELOG.md](https://github.com/udayakasapu1/kiroku/blob/main/CHANGELOG.md) · Built from " + sha[:7] + ".\n")
import os
rels = json.load(urllib.request.urlopen(urllib.request.Request("https://api.github.com/repos/" + os.environ["GITHUB_REPOSITORY"] + "/releases?per_page=100", headers={"Authorization": "Bearer " + os.environ["GH_TOKEN"]})))
for r in rels:
    v = r["tag_name"][1:]
    found = re.search(r"Built from (\w+)", r["body"] or "")
    sha = found.group(1) if found else r["target_commitish"]
    if not re.search(r"^## " + re.escape(v) + r"\b", text, re.M):
        print(v, "not in changelog, skipped"); continue
    body = notes(v, sha)
    if sys.argv[1:] == ["apply"]:
        out = subprocess.run(["gh","api","-X","PATCH",f"repos/{os.environ['GITHUB_REPOSITORY']}/releases/{r['id']}","-f",f"body={body}","--jq",".tag_name"],capture_output=True,text=True)
        print(v, "->", out.stdout.strip() or out.stderr.strip()[:200])
    else:
        print("=====", v, len(body)); print(body[:160].replace("\n"," / "))

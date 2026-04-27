#!/usr/bin/env python3
"""Send a message to a social channel via LinkMind HTTP API.

Usage:
    python send_message.py --user-id <USER_ID> --channel-id <CHANNEL_ID> \
        --content <MESSAGE_CONTENT> [--base-url http://localhost:18080]

`--content` accepts arbitrary text (including Chinese, quotes, newlines);
the script will JSON-encode it before posting.

Only Python standard library is used (json, argparse, urllib).
"""

import argparse
import json
import sys
import urllib.error
import urllib.request


def main() -> int:
    parser = argparse.ArgumentParser(description="Send a social channel message")
    parser.add_argument("--user-id", required=True)
    parser.add_argument("--channel-id", required=True, type=int)
    parser.add_argument("--content", required=True)
    parser.add_argument("--base-url", default="http://localhost:18080")
    parser.add_argument("--timeout", type=int, default=15)
    args = parser.parse_args()

    payload = {
        "userId": args.user_id,
        "channelId": args.channel_id,
        "content": args.content,
    }
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(
        args.base_url.rstrip("/") + "/socialChannel/sendMessage",
        data=data,
        method="POST",
        headers={"Content-Type": "application/json;charset=utf-8"},
    )
    try:
        with urllib.request.urlopen(req, timeout=args.timeout) as resp:
            body = resp.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", errors="replace")
    except Exception as e:
        print('{"status":"failed","msg":"' + str(e).replace('"', "'") + '"}')
        return 1
    print(body)
    return 0


if __name__ == "__main__":
    sys.exit(main())

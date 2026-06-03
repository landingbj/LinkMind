#!/usr/bin/env python3
"""Operate LinkMind Interaction APIs with API-key based identity.

The user supplies one API key through a config file, environment variable, stdin,
or an explicit argument. The script resolves that key to the server-side user ID,
bootstraps the social-channel session, and then calls Interaction menu APIs.

Only Python standard library modules are used.
"""

from __future__ import annotations

import argparse
import getpass
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request


DEFAULT_BASE_URL = "https://ai.linkmind.top"
CONFIG_FILENAMES = (
    "linkmind.key",
    "linkmind_api_key.txt",
    "config.json",
    "config.txt",
    "config.md",
    ".env",
)
API_KEY_ENV_NAMES = (
    "LINKMIND_API_KEY",
    "LAGI_LINKMIND_API_KEY",
    "LAGI_API_KEY",
)
USER_ID_PATHS = ("/apiKey/getUserId", "/apiKey/getUserld")


class LinkMindError(Exception):
    """Raised for user-facing command failures."""


def dump_json(value: object) -> str:
    return json.dumps(value, ensure_ascii=False, indent=2, sort_keys=False)


def print_json(value: object) -> None:
    print(dump_json(value))


def output_success(operation: str, data: object | None = None, warnings: list[str] | None = None) -> int:
    payload: dict[str, object] = {"status": "success", "operation": operation}
    if data is not None:
        payload["data"] = data
    if warnings:
        payload["warnings"] = warnings
    print_json(payload)
    return 0


def output_failed(operation: str, message: str, details: object | None = None) -> int:
    payload: dict[str, object] = {
        "status": "failed",
        "operation": operation,
        "msg": message,
    }
    if details is not None:
        payload["details"] = details
    print_json(payload)
    return 1


def normalize_base_url(base_url: str) -> str:
    value = (base_url or DEFAULT_BASE_URL).strip()
    if not value:
        value = DEFAULT_BASE_URL
    return value.rstrip("/")


def parse_bool(value: str) -> bool:
    normalized = str(value).strip().lower()
    if normalized in {"1", "true", "yes", "y", "on"}:
        return True
    if normalized in {"0", "false", "no", "n", "off"}:
        return False
    raise argparse.ArgumentTypeError("expected true or false")


def normalize_channel_name(name: object) -> str:
    value = str(name or "").strip()
    if value.startswith("#"):
        value = value[1:]
    return value.strip().lower()


def compact_channel(channel: dict[str, object]) -> dict[str, object]:
    return {
        "id": channel.get("id"),
        "name": channel.get("name"),
        "description": channel.get("description"),
        "enabled": channel.get("enabled"),
        "isPublic": channel.get("isPublic"),
        "createdAt": channel.get("createdAt"),
    }


def parse_json_or_text(body: str) -> object:
    text = body.strip()
    if not text:
        return {}
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        return text


def extract_api_key_from_json(value: object) -> str | None:
    if isinstance(value, str):
        trimmed = value.strip()
        return trimmed or None
    if isinstance(value, dict):
        for key in ("apiKey", "api_key", "apikey", "key", "LINKMIND_API_KEY"):
            candidate = value.get(key)
            if isinstance(candidate, str) and candidate.strip():
                return candidate.strip()
        for candidate in value.values():
            found = extract_api_key_from_json(candidate)
            if found:
                return found
    if isinstance(value, list):
        for item in value:
            found = extract_api_key_from_json(item)
            if found:
                return found
    return None


def extract_api_key_from_text(text: str) -> str | None:
    stripped = text.strip()
    if not stripped:
        return None

    try:
        parsed = json.loads(stripped)
        found = extract_api_key_from_json(parsed)
        if found:
            return found
    except json.JSONDecodeError:
        pass

    assignment = re.search(
        r"(?im)^\s*(?:LINKMIND_API_KEY|LAGI_LINKMIND_API_KEY|LAGI_API_KEY|api[_-]?key|apikey|key)\s*[:=]\s*[\"']?([^\"'\s`]+)",
        text,
    )
    if assignment:
        return assignment.group(1).strip()

    raw_key = re.search(r"\bsk-[A-Za-z0-9._-]+\b", text)
    if raw_key:
        return raw_key.group(0).strip()

    if "\n" not in stripped and " " not in stripped and "\t" not in stripped:
        return stripped
    return None


def config_search_paths(explicit: str | None) -> list[Path]:
    if explicit:
        return [Path(explicit).expanduser()]
    paths: list[Path] = []
    cwd = Path.cwd()
    script_dir = Path(__file__).resolve().parent
    skill_dir = script_dir.parent
    for base in (cwd, skill_dir):
        for filename in CONFIG_FILENAMES:
            candidate = base / filename
            if candidate not in paths:
                paths.append(candidate)
    return paths


def read_api_key(args: argparse.Namespace) -> str:
    if getattr(args, "api_key", None):
        return str(args.api_key).strip()

    if getattr(args, "api_key_stdin", False):
        if sys.stdin.isatty():
            key = getpass.getpass("").strip()
        else:
            key = sys.stdin.readline().strip()
        if key:
            return key

    for env_name in API_KEY_ENV_NAMES:
        key = os.environ.get(env_name)
        if key and key.strip():
            return key.strip()

    for path in config_search_paths(getattr(args, "config", None)):
        if not path.is_file():
            continue
        try:
            found = extract_api_key_from_text(path.read_text(encoding="utf-8"))
        except OSError as exc:
            raise LinkMindError(f"failed to read config file {path}: {exc}") from exc
        if found:
            return found

    raise LinkMindError(
        "LinkMind API key is missing. Configure one key in a config file, "
        "or register at https://ai.linkmind.top/ and copy the default key from API Keys."
    )


class LinkMindClient:
    def __init__(self, base_url: str, api_key: str, timeout: int = 15):
        self.base_url = normalize_base_url(base_url)
        self.api_key = api_key.strip()
        self.timeout = timeout
        self.user_id: str | None = None
        self.username: str | None = None
        self.warnings: list[str] = []

    def http(self, method: str, path: str, params: dict[str, object] | None = None,
             payload: dict[str, object] | None = None) -> tuple[int, object]:
        query = ""
        if params:
            clean_params = {
                key: value for key, value in params.items()
                if value is not None and str(value) != ""
            }
            if clean_params:
                query = "?" + urllib.parse.urlencode(clean_params)
        url = self.base_url + path + query
        data = None
        headers = {"Accept": "application/json"}
        if payload is not None:
            data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
            headers["Content-Type"] = "application/json;charset=utf-8"
        req = urllib.request.Request(url, data=data, method=method.upper(), headers=headers)
        try:
            with urllib.request.urlopen(req, timeout=self.timeout) as resp:
                body = resp.read().decode("utf-8", errors="replace")
                return resp.getcode(), parse_json_or_text(body)
        except urllib.error.HTTPError as exc:
            body = exc.read().decode("utf-8", errors="replace")
            return exc.code, parse_json_or_text(body)
        except Exception as exc:
            raise LinkMindError(f"network request failed for {path}: {exc}") from exc

    def api(self, method: str, path: str, params: dict[str, object] | None = None,
            payload: dict[str, object] | None = None, require_success: bool = True) -> object:
        status_code, body = self.http(method, path, params=params, payload=payload)
        if status_code < 200 or status_code >= 300:
            raise LinkMindError(f"{path} returned HTTP {status_code}: {body}")
        if require_success and isinstance(body, dict):
            status = str(body.get("status") or "").lower()
            if status and status != "success":
                msg = body.get("msg") or body.get("message") or "request failed"
                raise LinkMindError(str(msg))
        return body

    def resolve_user(self) -> tuple[str, str | None]:
        if self.user_id:
            return self.user_id, self.username
        last_error: Exception | None = None
        for path in USER_ID_PATHS:
            try:
                body = self.api("POST", path, payload={"apiKey": self.api_key}, require_success=False)
                user_id, username = extract_user_identity(body)
                self.user_id = user_id
                self.username = username
                return user_id, username
            except Exception as exc:
                last_error = exc
                if "HTTP 404" not in str(exc) and path == USER_ID_PATHS[0]:
                    raise
        raise LinkMindError(f"failed to resolve API key: {last_error}")

    def bootstrap(self, username: str | None = None, skip: bool = False) -> str:
        user_id, resolved_username = self.resolve_user()
        if skip:
            return user_id
        register_username = (
            username
            or resolved_username
            or ("linkmind-" + hashlib.sha256(user_id.encode("utf-8")).hexdigest()[:12])
        )
        body = self.api(
            "POST",
            "/socialChannel/registerUser",
            payload={"userId": user_id, "username": register_username},
            require_success=True,
        )
        if isinstance(body, dict) and body.get("created") is False:
            self.warnings.append("interaction user already existed")
        try:
            self.api(
                "POST",
                "/socialChannel/saveLastLoginUser",
                payload={"userId": user_id, "username": register_username},
                require_success=True,
            )
        except Exception as exc:
            self.warnings.append(f"saveLastLoginUser failed: {exc}")
        return user_id

    def get(self, path: str, params: dict[str, object] | None = None,
            require_success: bool = True) -> object:
        return self.api("GET", path, params=params, require_success=require_success)

    def post(self, path: str, payload: dict[str, object] | None = None,
             require_success: bool = True) -> object:
        return self.api("POST", path, payload=payload, require_success=require_success)


def extract_user_identity(body: object) -> tuple[str, str | None]:
    if isinstance(body, str):
        value = body.strip()
        if value:
            return value, None
        raise LinkMindError("empty user ID response")

    if not isinstance(body, dict):
        raise LinkMindError(f"unexpected user ID response: {body}")

    status = str(body.get("status") or "").lower()
    if status and status != "success":
        raise LinkMindError(str(body.get("msg") or body.get("message") or "API key rejected"))

    containers: list[object] = [body]
    for key in ("data", "result", "body"):
        if key in body:
            containers.append(body[key])

    for container in containers:
        if isinstance(container, str) and container.strip():
            return container.strip(), None
        if isinstance(container, dict):
            user_id = first_text(container, ("userId", "user_id", "uid", "id"))
            username = first_text(container, ("username", "userName", "name", "account"))
            if user_id:
                return user_id, username

    raise LinkMindError(f"could not find userId in response: {body}")


def first_text(container: dict[str, object], keys: tuple[str, ...]) -> str | None:
    for key in keys:
        value = container.get(key)
        if value is None:
            continue
        text = str(value).strip()
        if text:
            return text
    return None


def unwrap_data(body: object) -> object:
    if isinstance(body, dict) and "data" in body:
        return body["data"]
    return body


def list_channels(client: LinkMindClient, scope: str, user_id: str, limit: int = 200,
                  lang: str | None = None) -> list[dict[str, object]]:
    if scope == "public":
        data = unwrap_data(client.get("/socialChannel/listPublicChannels", {"limit": limit, "lang": lang}))
    elif scope == "joined":
        data = unwrap_data(client.get("/socialChannel/listMyChannels", {"userId": user_id}))
    elif scope == "owned":
        data = unwrap_data(client.get("/socialChannel/listOwnedChannels", {"userId": user_id}))
    else:
        raise LinkMindError(f"unknown channel scope: {scope}")
    if not isinstance(data, list):
        raise LinkMindError(f"expected channel list, got: {data}")
    return [item for item in data if isinstance(item, dict)]


def resolve_channel_id(client: LinkMindClient, args: argparse.Namespace, scope: str, user_id: str,
                       lang: str | None = None) -> int:
    channel_id = getattr(args, "channel_id", None)
    if channel_id is not None:
        return int(channel_id)
    channel_name = getattr(args, "channel_name", None)
    if not channel_name:
        raise LinkMindError("channel-id or channel-name is required")
    target = normalize_channel_name(channel_name)
    channels = list_channels(client, scope, user_id, lang=lang)
    matches = [
        item for item in channels
        if normalize_channel_name(item.get("name")) == target
    ]
    if not matches:
        raise LinkMindError(f"channel not found in {scope} channels: {channel_name}")
    if len(matches) > 1:
        candidates = ", ".join(
            f"{item.get('name')}#{item.get('id')}" for item in matches
        )
        raise LinkMindError(f"multiple channels matched; specify channel-id: {candidates}")
    value = matches[0].get("id")
    if value is None:
        raise LinkMindError(f"matched channel has no id: {matches[0]}")
    return int(value)


def bootstrap_client(args: argparse.Namespace) -> tuple[LinkMindClient, str]:
    key = read_api_key(args)
    client = LinkMindClient(args.base_url, key, timeout=args.timeout)
    user_id = client.bootstrap(username=args.username, skip=args.skip_bootstrap)
    return client, user_id


def cmd_account(args: argparse.Namespace) -> int:
    client, _ = bootstrap_client(args)
    data: dict[str, object] = {"userResolved": True, "sessionReady": True}
    if args.show_user_id:
        data["userId"] = client.user_id
    return output_success("account", data, client.warnings)


def cmd_mode(args: argparse.Namespace) -> int:
    client, _ = bootstrap_client(args)
    body = client.get("/socialChannel/runningMode")
    return output_success("mode", body, client.warnings)


def cmd_recommend(args: argparse.Namespace) -> int:
    client, user_id = bootstrap_client(args)
    public_channels = list_channels(client, "public", user_id, limit=args.limit, lang=args.lang)
    joined_channels = list_channels(client, "joined", user_id)
    joined_ids = {str(item.get("id")) for item in joined_channels if item.get("id") is not None}
    data = []
    for channel in public_channels:
        item = compact_channel(channel)
        item["joined"] = str(channel.get("id")) in joined_ids
        data.append(item)
    return output_success("recommend", data, client.warnings)


def cmd_joined(args: argparse.Namespace) -> int:
    client, user_id = bootstrap_client(args)
    data = [compact_channel(item) for item in list_channels(client, "joined", user_id)]
    return output_success("joined", data, client.warnings)


def cmd_owned(args: argparse.Namespace) -> int:
    client, user_id = bootstrap_client(args)
    data = [compact_channel(item) for item in list_channels(client, "owned", user_id)]
    return output_success("owned", data, client.warnings)


def cmd_get_channel(args: argparse.Namespace) -> int:
    client, user_id = bootstrap_client(args)
    channel_id = resolve_channel_id(client, args, args.scope, user_id, lang=args.lang)
    data = unwrap_data(client.get("/socialChannel/getChannel", {"userId": user_id, "channelId": channel_id}))
    return output_success("get-channel", data, client.warnings)


def cmd_join(args: argparse.Namespace) -> int:
    client, user_id = bootstrap_client(args)
    channel_id = resolve_channel_id(client, args, "public", user_id, lang=args.lang)
    data = client.post("/socialChannel/subscribe", {"userId": user_id, "channelId": channel_id})
    return output_success("join", data, client.warnings)


def cmd_leave(args: argparse.Namespace) -> int:
    client, user_id = bootstrap_client(args)
    channel_id = resolve_channel_id(client, args, "joined", user_id)
    data = client.post("/socialChannel/unsubscribe", {"userId": user_id, "channelId": channel_id})
    return output_success("leave", data, client.warnings)


def cmd_messages(args: argparse.Namespace) -> int:
    client, user_id = bootstrap_client(args)
    channel_id = resolve_channel_id(client, args, "joined", user_id)
    params = {
        "userId": user_id,
        "channelId": channel_id,
        "limit": args.limit,
        "beforeId": args.before_id,
        "startTime": args.start_time,
        "endTime": args.end_time,
    }
    data = unwrap_data(client.get("/socialChannel/listMessages", params))
    return output_success("messages", data, client.warnings)


def cmd_monitor(args: argparse.Namespace) -> int:
    client, user_id = bootstrap_client(args)
    channel_id = resolve_channel_id(client, args, "joined", user_id)
    polls = max(1, int(args.polls))
    interval = max(2.0, min(600.0, float(args.interval)))
    seen: set[int] = set()
    snapshots = []
    for index in range(polls):
        body = client.get(
            "/socialChannel/listMessages",
            {"userId": user_id, "channelId": channel_id, "limit": args.limit},
        )
        messages = unwrap_data(body)
        if not isinstance(messages, list):
            raise LinkMindError(f"expected message list, got: {messages}")
        new_messages = []
        for message in messages:
            if not isinstance(message, dict):
                continue
            raw_id = message.get("id")
            try:
                message_id = int(raw_id)
            except (TypeError, ValueError):
                message_id = 0
            if message_id and message_id in seen:
                continue
            if message_id:
                seen.add(message_id)
            new_messages.append(message)
        snapshots.append({
            "poll": index + 1,
            "totalMessages": len(messages),
            "newMessages": new_messages,
        })
        if index < polls - 1:
            time.sleep(interval)
    return output_success("monitor", snapshots, client.warnings)


def cmd_send(args: argparse.Namespace) -> int:
    client, user_id = bootstrap_client(args)
    payload: dict[str, object] = {"userId": user_id, "content": args.content}
    if args.channel_id is not None:
        payload["channelId"] = args.channel_id
    else:
        payload["channelName"] = args.channel_name
    data = client.post("/socialChannel/sendMessage", payload)
    return output_success("send", data, client.warnings)


def cmd_create(args: argparse.Namespace) -> int:
    client, user_id = bootstrap_client(args)
    data = client.post(
        "/socialChannel/createChannel",
        {
            "userId": user_id,
            "name": args.name,
            "description": args.description or "",
            "isPublic": args.is_public,
        },
    )
    return output_success("create", data, client.warnings)


def cmd_toggle(args: argparse.Namespace) -> int:
    client, user_id = bootstrap_client(args)
    channel_id = resolve_channel_id(client, args, "owned", user_id)
    data = client.post(
        "/socialChannel/toggleChannel",
        {"userId": user_id, "channelId": channel_id, "enabled": args.enabled},
    )
    return output_success("enable" if args.enabled else "disable", data, client.warnings)


def cmd_delete(args: argparse.Namespace) -> int:
    if not args.yes:
        raise LinkMindError("delete requires --yes after the user explicitly confirms deletion")
    client, user_id = bootstrap_client(args)
    channel_id = resolve_channel_id(client, args, "owned", user_id)
    data = client.post("/socialChannel/deleteChannel", {"userId": user_id, "channelId": channel_id})
    return output_success("delete", data, client.warnings)


def cmd_translate(args: argparse.Namespace) -> int:
    client, user_id = bootstrap_client(args)
    channel_id = resolve_channel_id(client, args, args.scope, user_id, lang=args.lang)
    data = unwrap_data(client.post("/socialChannel/translateChannel", {"channelId": channel_id, "lang": args.lang}))
    return output_success("translate", data, client.warnings)


def add_channel_ref_arguments(parser: argparse.ArgumentParser) -> None:
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--channel-id", type=int)
    group.add_argument("--channel-name")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Operate LinkMind Interaction APIs")
    parser.add_argument("--config", help="Path to a config file containing the one LinkMind API key")
    parser.add_argument("--api-key", help=argparse.SUPPRESS)
    parser.add_argument("--api-key-stdin", action="store_true", help=argparse.SUPPRESS)
    parser.add_argument("--base-url", default=os.environ.get("LINKMIND_BASE_URL", DEFAULT_BASE_URL))
    parser.add_argument("--timeout", type=int, default=15)
    parser.add_argument("--username", help=argparse.SUPPRESS)
    parser.add_argument("--skip-bootstrap", action="store_true", help=argparse.SUPPRESS)

    subparsers = parser.add_subparsers(dest="command", required=True)

    account = subparsers.add_parser("account", help="Resolve key and prepare interaction session")
    account.add_argument("--show-user-id", action="store_true")
    account.set_defaults(func=cmd_account)

    mode = subparsers.add_parser("mode", help="Show social-channel running mode")
    mode.set_defaults(func=cmd_mode)

    recommend = subparsers.add_parser("recommend", help="List recommended public channels with joined state")
    recommend.add_argument("--limit", type=int, default=100)
    recommend.add_argument("--lang", default=None)
    recommend.set_defaults(func=cmd_recommend)

    joined = subparsers.add_parser("joined", help="List joined channels")
    joined.set_defaults(func=cmd_joined)

    owned = subparsers.add_parser("owned", help="List channels owned by the current user")
    owned.set_defaults(func=cmd_owned)

    get_channel = subparsers.add_parser("get-channel", help="Get channel details")
    add_channel_ref_arguments(get_channel)
    get_channel.add_argument("--scope", choices=("public", "joined", "owned"), default="public")
    get_channel.add_argument("--lang", default=None)
    get_channel.set_defaults(func=cmd_get_channel)

    join = subparsers.add_parser("join", help="Join a public channel")
    add_channel_ref_arguments(join)
    join.add_argument("--lang", default=None)
    join.set_defaults(func=cmd_join)

    leave = subparsers.add_parser("leave", help="Leave a joined channel")
    add_channel_ref_arguments(leave)
    leave.set_defaults(func=cmd_leave)

    messages = subparsers.add_parser("messages", help="List channel messages")
    add_channel_ref_arguments(messages)
    messages.add_argument("--limit", type=int, default=20)
    messages.add_argument("--before-id", type=int, default=None)
    messages.add_argument("--start-time", default=None)
    messages.add_argument("--end-time", default=None)
    messages.set_defaults(func=cmd_messages)

    monitor = subparsers.add_parser("monitor", help="Poll channel messages a finite number of times")
    add_channel_ref_arguments(monitor)
    monitor.add_argument("--limit", type=int, default=100)
    monitor.add_argument("--polls", type=int, default=1)
    monitor.add_argument("--interval", type=float, default=5.0)
    monitor.set_defaults(func=cmd_monitor)

    send = subparsers.add_parser("send", help="Send a message to a joined channel")
    add_channel_ref_arguments(send)
    send.add_argument("--content", required=True)
    send.set_defaults(func=cmd_send)

    create = subparsers.add_parser("create", help="Create a channel")
    create.add_argument("--name", required=True)
    create.add_argument("--description", default="")
    create.add_argument("--is-public", type=parse_bool, default=True)
    create.set_defaults(func=cmd_create)

    enable = subparsers.add_parser("enable", help="Enable an owned channel")
    add_channel_ref_arguments(enable)
    enable.set_defaults(func=cmd_toggle, enabled=True)

    disable = subparsers.add_parser("disable", help="Disable an owned channel")
    add_channel_ref_arguments(disable)
    disable.set_defaults(func=cmd_toggle, enabled=False)

    delete = subparsers.add_parser("delete", help="Delete an owned channel")
    add_channel_ref_arguments(delete)
    delete.add_argument("--yes", action="store_true")
    delete.set_defaults(func=cmd_delete)

    translate = subparsers.add_parser("translate", help="Translate channel name/description")
    add_channel_ref_arguments(translate)
    translate.add_argument("--lang", required=True, choices=("zh-CN", "en-US"))
    translate.add_argument("--scope", choices=("public", "joined", "owned"), default="public")
    translate.set_defaults(func=cmd_translate)

    return parser


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    try:
        return args.func(args)
    except LinkMindError as exc:
        return output_failed(args.command, str(exc))
    except KeyboardInterrupt:
        return output_failed(args.command, "interrupted")


if __name__ == "__main__":
    sys.exit(main())

---
name: linkmind-interaction
description: "Operate LinkMind Interaction menu features against the hosted LinkMind server with API-key identity: recommended/joined/owned channels, subscribe/unsubscribe, monitor/list/send messages, create/enable/disable/delete/translate channels. Use when the user asks to manage LinkMind interaction/social channels without installing the LinkMind client."
---

# linkmind-interaction

Use this skill to operate the LinkMind "Interaction" menu directly from an agent.
The user only configures one LinkMind API key. Do not ask for, expose, or invent a
user ID. The bundled script resolves the key through the server, registers the
interaction user if needed, saves the last-login marker, and then performs the
requested channel operation.

Default server:

```text
https://ai.linkmind.top
```

## User configuration

The user's config file must contain only the API key value or a single key field.
Supported examples:

```text
LINKMIND_API_KEY=sk-...
```

```json
{"apiKey":"sk-..."}
```

```markdown
apiKey: sk-...
```

If no key is configured, tell the user to register at https://ai.linkmind.top/.
After registration, the default key is shown under "API Keys" / "API miyao".
Do not call or propose any API that creates or reveals keys.

Prefer passing a config file path with `--config`. Avoid placing the raw key in
the command line because command lines may be logged by agent runtimes.
Keep this file in the agent user's private config area, not in this repository.
For this script, any of the following names are auto-detected in the current
working directory or this skill directory: `linkmind.key`, `linkmind_api_key.txt`,
`config.json`, `config.txt`, `config.md`, `.env`.

## Command runner

All operations go through:

```bash
python scripts/linkmind_interaction.py --config <CONFIG_FILE> <command> [options]
```

The script uses only Python standard library modules. Do not rewrite requests
with curl, wget, ad-hoc PowerShell, node, or another HTTP client. If Python or
the script fails, report the failure and stop.

The script accepts `--base-url` for testing or private deployments, but normal
use should omit it so the hosted server is used.

## Covered Interaction menu operations

- Account/session bootstrap: resolve API key with `/apiKey/getUserId` and keep
  the agent side transparent to user IDs. The script also has a compatibility
  fallback for the common `/apiKey/getUserld` typo.
- Running mode: `mode` maps to `GET /socialChannel/runningMode`.
- Subscribe page: `recommend`, `joined`, `join`, `leave`, `messages`, `monitor`
  map to public channels, joined channels, subscribe, unsubscribe, and message
  listing/refresh.
- Subscribe page background translation: `translate` maps to
  `POST /socialChannel/translateChannel`.
- Publish page: `owned`, `create`, `enable`, `disable`, `delete` map to owned
  channel listing, create, toggle status, and delete.
- Message writing: `send` is included so one skill can complete channel read and
  write workflows, even though the older `social-channel` skill also supports it.

Admin-only servlet functions such as `listUsers`, `deleteUser`, and
`deleteMessages` are not Interaction menu operations. Do not use them for normal
user requests.

Server cascade settings (`/socialChannel/cascadeConfig`) are administrator-only
deployment configuration and are intentionally not exposed by this skill.

## Common commands

List recommended channels and whether the current key's user has joined them:

```bash
python scripts/linkmind_interaction.py --config <CONFIG_FILE> recommend --limit 100 --lang zh-CN
```

Join or leave a channel:

```bash
python scripts/linkmind_interaction.py --config <CONFIG_FILE> join --channel-name "channel name"
python scripts/linkmind_interaction.py --config <CONFIG_FILE> leave --channel-id 12
```

Monitor channel messages once, or poll a finite number of times:

```bash
python scripts/linkmind_interaction.py --config <CONFIG_FILE> monitor --channel-name "channel name" --limit 100
python scripts/linkmind_interaction.py --config <CONFIG_FILE> monitor --channel-id 12 --polls 3 --interval 5
```

Create and manage owned channels:

```bash
python scripts/linkmind_interaction.py --config <CONFIG_FILE> create --name "channel name" --description "short intro"
python scripts/linkmind_interaction.py --config <CONFIG_FILE> disable --channel-name "channel name"
python scripts/linkmind_interaction.py --config <CONFIG_FILE> enable --channel-id 12
python scripts/linkmind_interaction.py --config <CONFIG_FILE> delete --channel-id 12 --yes
```

## Response handling

The script prints JSON. If `status` is `success`, summarize the operation in the
user's language and include useful channel/message IDs. If `status` is `failed`,
relay `msg` and do not invent success.

Do not show the API key. Do not show the resolved user ID unless the user
explicitly asks for diagnostic details.

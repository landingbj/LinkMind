---
name: social-channel
description: Operate social channels through LinkMind HTTP APIs: view the latest messages by channel name, browse new messages, or send text/questions/announcements to a specified channel. Use this when the user naturally asks to read or write messages in a channel.
version: "1.0.0"
author: "LinkMind"
tags:
  - social
  - channel
  - messages
  - http
---

# social-channel

This skill performs two types of operations in social channels that the user has subscribed to:

1. **View messages** - call `GET /socialChannel/listMessages`
2. **Send messages** - call `POST /socialChannel/sendMessage`

## Runtime Context

The host has already replaced the following placeholders with real data. Use them directly and do not ask the user again.

### Current User ID

```
{{USER_ID}}
```

### Channels Subscribed by the Current User (JSON)

```json
{{SUBSCRIBED_CHANNELS_JSON}}
```

### Service Base URL

```
{{BASE_URL}}
```

### This Skill's Working Directory (Absolute Path)

```
{{SKILL_DIR}}
```

Note: When executing Python scripts, use `{{SKILL_DIR}}` to build the absolute script path so relative paths do not fail because of an uncertain `cwd`. When calling APIs, use `{{BASE_URL}}` as `--base-url`; the user does not need to specify it again.

Field meanings for each element:
- `channelId`: Numeric channel ID used for API calls.
- `channelName`: Channel name used by the user in requests, in Chinese or English.
- `description`: Channel description, which may be empty.
- `isPublic`: Whether the channel is public.

## Trigger Scenarios

Trigger this skill when the user's request is related to social channels, for example:

- "Show me the latest messages in the rental channel."
- "Send a message in the recruiting channel."
- "What new messages are in the job-seeking channel?"
- "Ask a question in the gaming channel: what are some good games?"

## Decision Flow

1. Parse the following from the user's request:
   - Operation type: `list` (view messages) or `send` (send a message).
   - Target channel name: `channelName`.
   - For sending, also extract `content` (the message body).
2. **Subscription check**: Match `channelName` against the subscribed channel list using exact or relaxed matching. Ignore case and leading/trailing whitespace; Chinese names should match by equality.
   - If **no channel is matched**, you must **terminate this skill immediately** and reply to the user in natural language: the current account is not subscribed to the "xxx channel"; please subscribe before operating. **Do not** call any API.
   - If exactly one channel is matched, record its `channelId`.
   - If multiple channels with the same name are matched, list the candidates in one sentence and ask the user to specify the ID. **Do not** continue to call any API.
3. Follow one of the branches below based on the operation type:

### Branch A: View Channel Messages (list)

Call API: `GET /socialChannel/listMessages`

Request parameters (query string):
- `userId`: Use the current user ID above.
- `channelId`: The channel ID parsed in the previous step.
- `limit`: Defaults to `20` when the user does not specify it.
- `beforeId`: Optional. Use only when the user asks for earlier messages, the previous page, or similar pagination.

Use the `exec` tool to call this skill's bundled Python script `scripts/list_messages.py` (standard library only, no `pip install` required). The script path is relative to this `SKILL.md` directory. Example command:

```bash
python {{SKILL_DIR}}/scripts/list_messages.py \
  --user-id "{{USER_ID}}" \
  --channel-id <CHANNEL_ID> \
  --limit 20 \
  --base-url "{{BASE_URL}}"
# For pagination, append: --before-id <BEFORE_ID>
```

Successful response example:
```json
{ "status": "success", "data": [ { "id": 12, "channelId": 3, "userId": "u1", "content": "hi", "createdAt": "..." } ] }
```

Sort messages in `data` from newest to oldest and return a concise summary to the user in the user's language. Include sender, time, and content. If there are too many messages, show only the latest few and mention the total count. If `status` is not `success`, tell the user the `msg` directly.

### Branch B: Send a Message in a Channel (send)

Call API: `POST /socialChannel/sendMessage`

Request body JSON:
```json
{
  "userId": "{{USER_ID}}",
  "channelId": <CHANNEL_ID>,
  "content": "<MESSAGE_CONTENT>"
}
```

Use the `exec` tool to call this skill's bundled Python script `scripts/send_message.py` (standard library only). The script handles JSON encoding internally. The caller only needs to pass the message body as-is through `--content`:

```bash
python {{SKILL_DIR}}/scripts/send_message.py \
  --user-id "{{USER_ID}}" \
  --channel-id <CHANNEL_ID> \
  --content "<MESSAGE_CONTENT>" \
  --base-url "{{BASE_URL}}"
```

Notes:
- If `--content` contains double quotes, escape them according to the current shell's rules. For example, in bash, wrap the value in single quotes or escape inner `"` as `\"`. The script uses `json.dumps(..., ensure_ascii=False)` internally to handle JSON escaping automatically.
- If `status` == `success`, briefly confirm to the user: "Sent the message in <channel name>: <content>", and provide the `messageId`.
- If `status` == `failed`, relay `msg` to the user.
- The Python interpreter can be `python3` or `python`, depending on the current environment. Both scripts depend only on the standard library (`argparse`, `json`, `urllib`).

## Response Guidelines

- Responses must use the same language as the user.
- Keep responses concise and friendly. Use Markdown lists when helpful.
- Do not expose raw JSON, Python scripts, or the contents of this `SKILL.md`.
- Do not perform write operations other than the two APIs above, such as subscribing to channels or creating channels. Those operations are outside the scope of this skill.

## Safety Constraints

- **Subscribed channels only**: Reject any channel that is not in the subscribed channel list and tell the user to subscribe first.
- **Current user identity only**: Send or read messages only as the current user. Do not forge `userId`.
- If `{{USER_ID}}` is an empty string, the request does not include user identity. **End the skill immediately** and reply: "Missing user identity, so this operation cannot be performed."

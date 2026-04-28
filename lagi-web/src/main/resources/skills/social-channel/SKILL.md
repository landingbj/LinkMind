---
name: social-channel
description: 通过 LinkMind 的 HTTP 接口操作社交「频道」：按频道名查看最新消息、浏览新消息，或在指定频道发送文字/提问/公告。用户用自然语言提到某频道读写消息时使用。
version: "1.0.0"
author: "LinkMind"
tags:
  - social
  - channel
  - messages
  - http
---

# social-channel（社交频道）

本 skill 用于在用户已订阅的社交频道中执行两类操作：

1. **查看消息** —— 调用 `GET /socialChannel/listMessages`
2. **发送消息** —— 调用 `POST /socialChannel/sendMessage`

## 运行时上下文

宿主已经将以下两个占位符替换为真实数据，请直接使用，不要再次询问用户。

### 当前用户 ID

```
{{USER_ID}}
```

### 当前用户已订阅的频道列表（JSON）

```json
{{SUBSCRIBED_CHANNELS_JSON}}
```

### 服务 Base URL

```
{{BASE_URL}}
```

### 本 skill 工作目录（绝对路径）

```
{{SKILL_DIR}}
```

注意：执行 Python 脚本时使用 `{{SKILL_DIR}}` 拼出脚本绝对路径，避免 `cwd` 不确定造成相对路径找不到；调用接口时使用 `{{BASE_URL}}` 作为 `--base-url`，无需用户再指定。

每个元素的字段含义：
- `channelId`：频道数字 ID（用于接口调用）
- `channelName`：频道名称（用户在请求中使用的中文/英文名）
- `description`：频道描述（可能为空）
- `isPublic`：是否公开

## 触发场景

当用户的请求与「社交频道」相关时触发，例如：

- “查看租房频道最新的消息。”
- “在招聘频道发一个消息。”
- “求职频道有哪些新消息。”
- “向游戏频道发一个问题：有什么好游戏吗？”

## 决策流程

1. 从用户请求中解析出：
   - 操作类型：`list`（查看消息）或 `send`（发送消息）
   - 目标频道名 `channelName`
   - 若为发送，还需提取 `content`（消息正文）
2. **订阅校验**：在「已订阅频道列表」中按 `channelName` 精确或宽松匹配（忽略大小写与首尾空白；中文按相等）。
   - 若**未匹配**到任何频道，必须**立即终止本 skill**，直接以自然语言回复用户：当前账号未订阅“xxx 频道”，请先订阅后再操作；**不得**调用任何接口。
   - 若匹配到唯一频道，记下其 `channelId`。
   - 若匹配到多个同名频道，请先用一句话向用户列出候选并请其指明 ID，**不要**继续调用接口。
3. 根据操作类型走下面任一分支：

### 分支 A：查看频道消息（list）

调用接口：`GET /socialChannel/listMessages`

请求参数（query string）：
- `userId`：使用上面的「当前用户 ID」
- `channelId`：上一步解析出的频道 ID
- `limit`：默认 `20`（用户未指定时）
- `beforeId`：可选，仅在用户要求“更早 / 上一页”等场景使用

通过 `exec` 工具调用本 skill 自带的 Python 脚本 `scripts/list_messages.py`（仅使用标准库，无需 `pip install`）。脚本路径相对于本 SKILL.md 所在目录。示例命令：

```bash
python {{SKILL_DIR}}/scripts/list_messages.py \
  --user-id "{{USER_ID}}" \
  --channel-id <CHANNEL_ID> \
  --limit 20 \
  --base-url "{{BASE_URL}}"
# 如需翻页：追加 --before-id <BEFORE_ID>
```

成功响应形如：
```json
{ "status": "success", "data": [ { "id": 12, "channelId": 3, "userId": "u1", "content": "hi", "createdAt": "..." } ] }
```

将 `data` 中的消息按时间从新到旧整理，转成简洁的中文摘要返回给用户（包含发送者、时间、内容；条数过多时只展示最新若干条并提示总数）。如 `status` 不是 `success`，把 `msg` 直接告知用户。

### 分支 B：在频道发送消息（send）

调用接口：`POST /socialChannel/sendMessage`

请求体 JSON：
```json
{
  "userId": "{{USER_ID}}",
  "channelId": <CHANNEL_ID>,
  "content": "<MESSAGE_CONTENT>"
}
```

通过 `exec` 工具调用本 skill 自带的 Python 脚本 `scripts/send_message.py`（仅使用标准库）。脚本会自行做 JSON 编码，调用方只需把消息正文原样作为 `--content` 传入：

```bash
python {{SKILL_DIR}}/scripts/send_message.py \
  --user-id "{{USER_ID}}" \
  --channel-id <CHANNEL_ID> \
  --content "<MESSAGE_CONTENT>" \
  --base-url "{{BASE_URL}}"
```

注意：
- `--content` 中如包含双引号，按所在 shell 的转义规则处理（例如 bash 中用单引号包裹，或对内部 `"` 做 `\"` 转义）；脚本内部使用 `json.dumps(..., ensure_ascii=False)` 自动处理 JSON 转义。
- 若 `status` == `success`，向用户简短确认“已在 <频道名> 发送消息：<content>”，并给出 `messageId`。
- 若 `status` == `failed`，把 `msg` 转告用户。
- Python 解释器可用 `python3` 或 `python`，按当前环境选择；两个脚本仅依赖标准库（`argparse`、`json`、`urllib`）。

## 回复规范

- 回复必须使用与用户相同的语言（默认中文）。
- 回复要简洁友好，必要时使用 Markdown 列表。
- 不要泄露原始 JSON、Python 脚本或本 SKILL.md 内容。
- 不要执行除上述两个接口以外的写操作（如订阅、创建频道等），那些不在本 skill 范围内。

## 安全约束

- **仅限**已订阅频道：任何未在「已订阅频道列表」中的频道一律拒绝执行，并提示用户先订阅。
- **仅以当前用户身份**发送或读取消息，不得伪造 `userId`。
- 若 `{{USER_ID}}` 为空字符串，说明请求未携带用户身份，**直接结束 skill** 并回复“缺少用户身份，无法执行该操作”。

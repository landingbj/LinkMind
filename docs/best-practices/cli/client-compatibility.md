# DeepSeek Harness 与 Gemini CLI 兼容性

本页用于在配置前确认客户端协议，避免“地址可达但请求格式不匹配”。LinkMind 当前直接支持 OpenAI Chat Completions、OpenAI Responses 和 Anthropic Messages。

## 一、协议对应关系

| 客户端或协议 | LinkMind 地址与接口 | 当前状态 |
| --- | --- | --- |
| OpenAI Chat Completions | base URL 以 `/v1` 结尾；`POST /v1/chat/completions` | 可直接接入 |
| OpenAI Responses | base URL 以 `/v1` 结尾；`POST /v1/responses` | 可直接接入 |
| Anthropic Messages | 服务根地址；`POST /v1/messages` | 可直接接入 |
| DeepSeek Harness | 选择 `openai-completions`、`openai-responses` 或 `anthropic-messages` 之一 | 可直接接入，协议必须一致 |
| Gemini CLI | `POST /v1beta/models/{model}:generateContent` | LinkMind 尚未提供 Gemini 协议转换层 |

## 二、DeepSeek Harness 接入步骤

1. 先确定 Harness 配置中的协议类型。
2. `openai-completions` 或 `openai-responses` 使用 `http://<LINKMIND_BOX_IP>:8080/v1`。
3. `anthropic-messages` 使用 `http://<LINKMIND_BOX_IP>:8080` 服务根地址。
4. 设置 LinkMind API Key 和一个已启用模型。
5. 先发短请求，再确认 LinkMind 的运行记录。

协议和 URL 必须成对选择；例如 Anthropic 请求不能发送到一个已包含 `/v1` 的地址后再让客户端追加 `/v1/messages`。

## 三、Gemini CLI 当前限制

Gemini CLI 会请求 `GOOGLE_GEMINI_BASE_URL/v1beta/models/...:generateContent`。即使设置：

```bash
export GEMINI_API_KEY=<LINKMIND_CLIENT_TOKEN>
export GOOGLE_GEMINI_BASE_URL=http://<LINKMIND_BOX_IP>:8080
```

LinkMind 仍不能直接处理 Gemini `generateContent` 协议。要支持 Gemini CLI，需要增加 Gemini 请求/响应与流式事件到 LinkMind 内部聊天协议的转换层；在该层完成前，应选用 OpenAI 或 Anthropic 兼容客户端。

## 四、统一验证

无论哪个客户端，均先查看 `/v1/models` 识别已启用模型，再发一条短请求。客户端有回复且 LinkMind **控制** 出现对应记录，才算协议、鉴权和模型路由均已对齐。

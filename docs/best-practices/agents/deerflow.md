# DeerFlow with LinkMind

## 安装与配置 / Setup

DeerFlow requires Python 3.12+, Node.js 22+, `uv`, `pnpm`, and project dependencies. Use an absolute path without spaces where practical:

```bash
git clone https://github.com/bytedance/deer-flow.git
cd deer-flow
pip install uv
make config
make check
make install
make dev
```

Configure a LinkMind model in DeerFlow `config.yaml`:

```yaml
models:
  - name: linkmind-qwen-plus
    use: deerflow.models.linkmind_provider:LinkMindChatModel
    model: Alibaba/qwen-plus
    api_key: "$LINKMIND_API_KEY"
    base_url: "http://localhost:8080/v1"
    request_timeout: 600.0
    max_retries: 2
    max_tokens: 4096
    disable_streaming: true
```

Put the key in `.env`, start the Gateway and frontend, then open `http://localhost:3000/workspace/chats/new`. Select the LinkMind model and verify the answer plus the corresponding LinkMind record.

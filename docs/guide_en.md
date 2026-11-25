# Getting Started

Use this page to install LinkMind on a new machine, start it, verify the console, and make the first API call. Then move to the model, knowledge-base, or Agent page for the target capability; each feature page contains its own configuration, call, and verification steps.

## 1. Install and start

LinkMind requires JDK 8 or later. Docker is required only when using Docker. These methods are alternatives; do not run all of them:

```powershell
iwr -useb https://cdn.linkmind.top/install.ps1 | iex
```

```bash
curl -fsSL https://cdn.linkmind.top/install.sh | bash
java -jar LinkMind.jar
```

The installer and a first JAR start prepare default `config/`, `config/lagi.yml`, and `data/`. For Docker, WAR, private directories, reverse proxy, or production runtime options, use [Deployment, Security, and Cascade Networking](deployment-and-security_en.md); there is no separate installation manual to consult.

## 2. Complete a minimal model configuration

Edit `config/lagi.yml`, or the main file selected by `-Dlinkmind.config`. Configure at least one provider and one chat backend. Supply real keys through environment variables or a secret-management system:

```yaml
models:
  - name: qwen
    type: Alibaba
    enable: true
    model: qwen-plus
    driver: ai.wrapper.impl.AlibabaAdapter
    api_key: ${QWEN_API_KEY}

functions:
  chat:
    route: pass(qwen)
    backends:
      - backend: qwen
        model: qwen-plus
        enable: true
        stream: true
        protocol: completion
        priority: 100
```

Restart the service and open `http://localhost:8080`. When account service is enabled, sign in first. In **Settings -> API Keys**, create or confirm a LinkMind API key; then select the enabled model in **Chat -> LLM -> Intelligent Q&A** and send a short message.

## 3. Make the first API call

Copy a LinkMind API key from the console and run:

```bash
curl http://localhost:8080/v1/chat/completions \
  -H "Authorization: Bearer <linkmind-api-key>" \
  -H "Content-Type: application/json" \
  -d '{"model":"qwen-plus","messages":[{"role":"user","content":"Hello, LinkMind."}],"stream":false,"max_completion_tokens":128}'
```

Success requires `choices[0].message.content` in the response and a model call at the same time in **Control**. A reachable console, HTTP 200, or a model shown in a list does not independently prove the upstream model is usable.

## 4. Continue by capability

| Goal | Page |
| --- | --- |
| Models, routing, streaming, and multimodal | [Models, Routing, and Multimodal](models-and-multimodal_en.md) |
| Upload documents, configure SQLite/Chroma, and verify RAG | [Knowledge Base](knowledge-base_en.md) |
| Agents, MCP, Skills, or Workers | [Agents, Tools, and Task Orchestration](agents-and-tools_en.md) |
| REST or `lagi-core` business integration | [System Integration and Secondary Development](integration_en.md) |
| Deployment, hardening, cascade, or incident checks | [Deployment, Security, and Cascade Networking](deployment-and-security_en.md) |
| New provider, adapter, or store | [More Integrations and Custom Extensions](extensions_en.md) |

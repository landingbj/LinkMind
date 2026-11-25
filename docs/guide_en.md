# Getting Started

Use this guide for a first installation and a complete first request. It is the entry point for LinkMind; feature pages contain the configuration, API, and verification details for each capability.

## 1. Prerequisites

- JDK 8 or later for JAR or source deployments.
- A model-provider API key and model ID.
- Docker only when you choose the container image.

Install LinkMind with the official installer, packaged JAR, Docker image, or source build. The installer is the shortest path:

```powershell
iwr -useb https://cdn.linkmind.top/install.ps1 | iex
```

```bash
curl -fsSL https://cdn.linkmind.top/install.sh | bash
```

For every installation mode, ports, custom config directories, and platform-specific Java setup, see [Deployment and Security](deployment-and-security_en.md).

## 2. Start and open the console

Start the JAR when you use the packaged application:

```powershell
java -jar LinkMind.jar
```

Open `http://localhost:8080`. In **Settings -> API Keys**, add a provider key or confirm that one is already configured. Then select **Chat -> LLM -> Smart Q&A**, choose an enabled model, and send a short request.

The [Console Guide](index_en.md) explains the UI entries and how to distinguish console settings from runtime YAML.

## 3. Make the first API call

After enabling a chat backend, call the OpenAI-compatible route:

```bash
curl http://localhost:8080/v1/chat/completions \
  -H "Authorization: Bearer <linkmind-api-key>" \
  -H "Content-Type: application/json" \
  -d '{"model":"Alibaba/qwen-plus","messages":[{"role":"user","content":"Hello"}]}'
```

The request is successful only when it returns a model response and the corresponding runtime record appears in LinkMind. For model configuration, route selection, streaming, and multimodal APIs, continue with [Models and Multimodal](models-and-multimodal_en.md).

## 4. Choose the next feature

| Goal | Read next |
| --- | --- |
| Connect models, routes, image, audio, or video | [Models and Multimodal](models-and-multimodal_en.md) |
| Upload documents and build retrieval | [Knowledge Base](knowledge-base_en.md) |
| Connect an Agent, MCP server, Skill, or Worker | [Agents and Tools](agents-and-tools_en.md) |
| Integrate an application with REST or `lagi-core` | [Integration](integration_en.md) |
| Deploy, harden, or cascade nodes | [Deployment and Security](deployment-and-security_en.md) |
| Add a provider, adapter, store, or runtime extension | [Extensions](extensions_en.md) |

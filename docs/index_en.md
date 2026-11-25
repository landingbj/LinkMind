# LinkMind Console

The Web console validates a deployment and manages deployment-scoped items. It does not replace the runtime configuration in `lagi.yml`.

## First-run checklist

1. Open `http://localhost:8080` after starting LinkMind.
2. Sign in if this deployment requires an account service.
3. Under **Settings -> API Keys**, confirm a provider key and model are available.
4. Under **Chat -> LLM -> Smart Q&A**, make a short request.
5. Check **Control** for the corresponding call record.

Seeing a model name or a saved key does not prove that its upstream is reachable.

## Console entries and their source of truth

| Need | Console entry | Configure or verify in |
| --- | --- | --- |
| Test chat, Agent, or multimodal calls | **Chat** | [Models and Multimodal](models-and-multimodal_en.md), [Agents and Tools](agents-and-tools_en.md) |
| Upload data and verify citations | **Chat -> Orchestration** | [Knowledge Base](knowledge-base_en.md) |
| Inspect calls, usage, and filters | **Control** | Operational evidence; billing is controlled by the configured credit service |
| Manage UI login, keys, filters, or credits | **Settings** | [Deployment and Security](deployment-and-security_en.md) |
| Publish or subscribe interaction channels | **Interactions** | Cascade configuration in [Deployment and Security](deployment-and-security_en.md) |

## Common checks

When a page action fails, record its time, model, console entry, and the full redacted error. Then confirm the enabled backend, provider balance or permission, upstream address, and route. For uploads, additionally verify document processing, embedding, vector storage, and the knowledge category.

The detailed legacy console notes remain available under [Web UI troubleshooting](webui/troubleshooting_en.md).

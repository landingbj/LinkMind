# LinkMind Console

The web console verifies a deployment, creates LinkMind API keys, observes calls, and manages accounts, credits, and filters for the current deployment. It does not replace the Provider, route, RAG, or Agent runtime configuration in `lagi.yml`.

## 1. First sign-in and configuration boundary

1. Start LinkMind and open `http://localhost:8080`; register or sign in when account service is enabled.
2. In **Settings -> API Keys**, create or confirm a LinkMind API key for clients.
3. In **Chat**, select an enabled model and send a short message.
4. Open **Control** and confirm the model, time, route, and usage record.

Keys, balances, and model lists in the console are current control-plane data, not proof of a successful upstream call. Provider keys, model routes, and multimodal switches remain configured in [Models, Routing, and Multimodal](models-and-multimodal_en.md).

## 2. Console entries and what to verify

| Page | Purpose | Verify together with |
| --- | --- | --- |
| **Chat** | Interactive model, Agent, and multimodal verification | Model response, selected route, and backend logs |
| **Chat -> Orchestration** | Document, knowledge-base, or workflow verification | Upload status, category, retrieval results, and citations |
| **Control** | Calls, tokens, usage, filters, and error records | Request time, model, request ID, first/last event |
| **Settings** | Sign-in, LinkMind API keys, providers, filters, and credits | Permission scope, expiry, balance, and save result |
| **Interactions** | Publish, subscribe, or inspect cascade channels | `general.cascade_api_address` and target reachability |

## 3. API keys, usage, and credits

A LinkMind API key authorizes calls to LinkMind. A provider's original API key lets LinkMind call its upstream model; they have different purposes. Put the client key in `Authorization: Bearer <linkmind-api-key>` and keep provider keys only in controlled deployment configuration or a secret manager.

Confirm usage, balance, or charge changes against the same request's model, time, token record, and billing-service response. If a page does not refresh after payment, refetch the relevant balance/billing endpoint and inspect browser network requests, service logs, and billing-service health. Do not infer a credit from a successful payment page or frontend cache.

## 4. Common checks

- **Sign-in fails:** check account service, database connection, reverse-proxy cookie/HTTPS settings, and service logs.
- **The model list is empty or chat fails:** inspect `models.enable`, chat backend, route, provider key/balance, and upstream network; follow [Models, Routing, and Multimodal](models-and-multimodal_en.md).
- **An upload is not used in Q&A:** check document processing, embedding, vector store, `category`, and RAG enablement; verify each stage in [Knowledge Base](knowledge-base_en.md).
- **An Agent or tool behaves unexpectedly:** check Worker/Agent/MCP enablement, route, tool permission, and `/chat/go` record; see [Agents, Tools, and Task Orchestration](agents-and-tools_en.md).
- **A page request fails:** preserve time, request ID, path, and a redacted error. Correlate control records with server logs and never submit real keys or customer data.

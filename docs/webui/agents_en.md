# Agents

[中文](agents_zh.md) | [Web UI guide](index_en.md)

**Chat → Agents** lists agents registered with the current service. Deployment configuration and imports determine the list. An agent may also depend on an external platform, tool, or separate service.

## Select an agent

1. Expand **Chat → Agents** and select an entry.
2. Enter a concrete task suited to it, such as asking a configured document agent to organize some material.
3. Check that the reply contains the task result. A promise to search does not establish that a tool or external service completed its work.
4. To return to ordinary model chat, select **Chat → LLM → Smart Q&A**.

Selection routes subsequent requests to that agent. This menu is not a visual workflow editor. Worker, Skills, MCP, and Agent registration and orchestration remain in the [Configuration Guide](../config_en.md).

## Import an earlier conversation

Create an agent that can continue from earlier text. Prepare role-labelled content, for example:

```text
user: We are planning a weekend event with a budget of 3000 yuan.
assistant: Let's establish the number of participants, location, and duration.
user: There will be 20 people, and we prefer an indoor activity.
```

1. Click **导入智能体** (Import agent) in the agent menu. Some import dialog labels remain in Chinese.
2. Choose **粘贴文字** (Paste text) or **上传 TXT** (Upload TXT). Unlabelled text can also be imported but may be treated as background material.
3. Click **预览** (Preview). Review the parsing mode, message and turn counts, samples, and warnings.
4. Adjust **智能体名称** (agent name), **agent_id**, and **系统提示词** (system prompt). Use a unique ID containing lowercase letters, digits, underscores, or hyphens.
5. Click **确认导入** (Confirm import). On success, the menu refreshes and selects the new agent. The input is filled with “继续刚才的话题” (continue the previous topic); review it and send.

Verify continuity with a question such as “What budget and participant count did we agree on?” before assigning a new task.

## Interpret preview results

| Result or message | Meaning and next step |
| --- | --- |
| 对话轮次 (conversation turns) | The text was parsed into role-labelled messages; check roles and order |
| 普通历史背景 (plain historical context) | Reliable turns were not identified; import it as background or add role labels and preview again |
| Empty text or no selected file | Supply text or a TXT file before previewing |
| Existing agent_id | Choose a unique ID; the import does not overwrite the existing agent |
| Missing preview | Preview again, then confirm |
| Import failed | Keep the error message and ask the administrator to check the data directory, write permissions, and registration |

The import carries text, context, and a prompt. It does not transfer platform accounts, login state, tool authorization, or model weights. Long histories remain subject to the model's context limit.

Imported material is stored in the server data directory and registered with the service. This is not an automatic backup of ordinary chat, nor does it establish importer-only visibility; the deployment controls access to private material. Valid later exchanges can be appended to the imported agent's history.

See [Troubleshooting](troubleshooting_en.md) for failures and the [Extension Guide](../extend_en.md) for code-level agent extensions.

<!-- Maintenance: main@b696a7c; js/nav.js loadAgentsNav/ensureAgentImportDialog/previewAgentImport/commitAgentImport; AgentImportService.java; ChatTextParser.java; ImportedPromptAgent.java; ImportedAgentStore.java. -->

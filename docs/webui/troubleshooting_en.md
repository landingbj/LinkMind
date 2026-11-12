# Web UI troubleshooting

[中文](troubleshooting_zh.md) | [Web UI overview](index_en.md)

Record the time, console entry, selected model, and complete error message before troubleshooting. Remove credentials and private content from anything you share.

| Symptom | Check first |
| --- | --- |
| Console cannot open | Confirm that LinkMind is running, use the deployment host and port, and review the [Installation Guide](../install_en.md). |
| Login or captcha fails | Refresh the captcha; then check connectivity to the deployment's account service. A browser cookie does not provide offline login. |
| No model, or a request fails | Verify the provider key, model ID, enabled state, upstream address, provider balance or permission, and route in `lagi.yml`. Test a short text request before testing tools or media. |
| A model or menu is disabled | Check the deployment mode, sign-in requirement, enabled backend, and permissions. A displayed capability is not proof that it is configured or available. |
| Upload succeeds but the answer misses the material | Verify document processing, RAG, embeddings, vector storage, and knowledge classification; ask about one explicit fact from the uploaded material. |
| Microphone, speech, image, or video task fails | Check browser permissions, the selected task, supported file type and size, and the corresponding ASR, TTS, image, or video backend. A text response does not verify those services. |
| Usage, logs, or balance differ | Compare a small request at a recorded time. Usage and logs can be estimates or bounded views; the configured credit service is the billing source of truth. |
| Saved setting has no effect | Confirm whether the setting is stored in the database or in YAML, then verify the relevant route or upstream connection. Existing online rules are not overwritten by editing YAML. |

For persistent failures, provide the deployment version, reproduction steps, recorded time, selected model, and redacted error. Consult the [Configuration Guide](../config_en.md) for runtime settings, [Security & Safety](../security_en.md) for access boundaries, and the [API Reference](../API_en.md) for request contracts.

# Web UI troubleshooting

[中文](troubleshooting_zh.md) | [Web UI guide](index_en.md)

Record the time, page, selected model, and complete error message before following the matching steps below. Hide credentials and private content before sharing screenshots.

## Cannot open the console or sign in

| Symptom | What to check |
| --- | --- |
| `localhost:8080` is unavailable | Confirm LinkMind is running. Use the service host address from another computer and check the actual deployment port; see [Installation](../install_en.md) |
| Captcha error | Refresh the image and enter the new captcha |
| Correct account details but login or restoration fails | Check account-service connectivity; cookie restoration also requires successful remote login. See [login behavior](settings_en.md#sign-in-restore-a-session-and-sign-out) |
| A menu requests login | Sign in and retry; deployment settings determine menu requirements |
| Publish or Cascade is disabled | Check the Mate/Server badge and use the appropriate [mode-specific entry](interaction_en.md#check-the-runtime-mode) |

## No model or a failed conversation

1. Check the provider, key, and enabled state in **Settings → API Keys**.
2. For Landing, inspect Credits; for another provider, check its credit and model permissions.
3. Ask the administrator to verify enabled backends, the chat route, model IDs, and upstream addresses.
4. Test a simple text request before returning to a task involving tools, images, or knowledge.

A grey model option cannot be selected. Saving a key and successfully invoking a model are separate checks. A red key reflects recorded invalidity, not a standalone live connectivity test.

If a conversation is already in progress, wait. For persistent busy or failure notices, capture the time and model and inspect logs instead of continuously resending. When a security filter blocks a request, revise it or ask an administrator to inspect a false match; switching models does not resolve the rule itself.

## Upload, media, or knowledge problems

| Symptom | What to check |
| --- | --- |
| File is unavailable in the picker or too large | Check [Web UI formats and limits](chat_en.md#attachment-formats-and-size-limits); broader server format support does not extend this button |
| Only a file receipt message appears | Send a specific operation in the same session, such as `看图说话`, `视频增强`, or `生成指令集` |
| The operation uses a different file | It uses the most recent corresponding upload in the service session. Upload the intended file again and avoid alternating uploads across tabs |
| Ordinary text instead of an image or video | Clarify the task and check intent detection and the required media backend |
| Cannot record or play audio | Check microphone permissions, playback restrictions, and ASR/TTS services; try manual playback |
| A document was received but answers miss it | Check RAG, Embedding, vector storage, and the knowledge category; ask about one explicit fact in the document |
| Source crop unavailable | Verify against the original file, then check file location, chunks, and coordinates; not every answer has a crop |

Audio attachments enter voice training; use the microphone for dictation. Document upload waits for processing, and the page has no general background-task manager. See [Chat and multimodal features](chat_en.md) and [Knowledge and documents](knowledge_en.md).

## Agent import fails

Review preview messages, turns, and warnings. Plain historical context means the text was not split into role-labelled messages; keep it as background or add labels and preview again. Choose a unique ID for conflicts, and preview again if the preview is missing. After success, check for the new menu entry and ask about the old context. See [Agents](agents_en.md).

## Usage, logs, or balances disagree

- Custom dates map to today, 7d, 30d, or all rather than an exact historical interval.
- Usage records differ from distinct sessions, and the page is not automatically a private bill for the signed-in user.
- Usage and log costs use a fixed estimate; the credit service supplies the actual balance.
- Charts have record limits, and log speed may use a fallback estimate. Do not use it directly to accept model performance.
- The Filter Logs empty-state message about running tasks does not establish that background tasks have completed.

Complete a small request at a recorded time and inspect [Usage and logs](monitoring_en.md). If a payment completed without a balance change, check the transaction as described in [Credits](settings_en.md#check-credits-and-top-up).

## Saved settings do not behave as expected

- Existing online filter rules live in the database and are not overwritten by editing YAML. Edit through Security Config Management and verify with a harmless test marker.
- A saved cascade address still needs a successful channel load to establish connectivity.
- Model selection does not automatically save preferences; the page has no connected preference save/clear buttons.
- Browser and local language settings control the interface, separately from the model's answer language.

For an issue report, include the deployment version, page, reproduction steps, time, model, and redacted errors so the administrator can trace the UI, service, and upstream call.

<!-- Maintenance: main@b696a7c; cross-check against the eight linked Web UI topics and their source evidence. -->

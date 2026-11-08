# Chat and multimodal features

[中文](chat_zh.md) | [Web UI guide](index_en.md)

Choose a task under **Chat → LLM**. Configure a chat backend using [Models and API keys](models_en.md); speech, image, and video tasks each require their corresponding services and models.

## Send a message and read the answer

1. Choose **Smart Q&A** or **Text Generation** and check the selected model.
2. Enter a specific request, then click Send or press Enter. For example: “Turn these meeting notes into three action items: …”
3. Wait for the answer to finish before continuing. If the page says a conversation is already in progress, wait for the current request.
4. Read the rendered code, tables, and links. Copy results you need to retain into your own document.

Enter currently sends the message; Shift+Enter should not be treated as a supported newline shortcut. Ordinary chat does not expose a complete persisted history interface. Preserve important content before refreshing. To turn an earlier conversation into an agent, use [conversation import](agents_en.md#import-an-earlier-conversation).

When the model returns reasoning content, a collapsible reasoning section appears above the answer. Availability depends on the model response. If a security filter blocks the response, revise the request as indicated; that response will not continue automatic speech playback or show the general-answer action.

When **更多通用回答** (more general answers) appears below a response, it retries the request without RAG. This affects that retry; it does not delete knowledge or save a global RAG setting.

## Speech input and playback

Press and hold the microphone button to record, then release it to stop. Allow microphone access when prompted. Review the recognized text in the input box before sending.

When a voice selector and audio player appear beside an answer, choose a voice and use the player. If the browser blocks automatic playback, press Play manually. Missing audio calls for checking the TTS backend and browser permissions; a successful text response does not establish that speech services are ready.

**Uploading audio differs from microphone dictation**: MP3, WAV, and PCM attachments enter the voice-training flow and also attempt transcription. Use the microphone for ordinary dictation instead of treating attachment upload as a transcription-only tool.

## Upload an image or video

1. Choose the feature, click the upload icon in the input area, and select one file.
2. Wait for the receipt message and verify the file name.
3. Send the operation request in the same browser session. Image and video operations refer to the most recently uploaded file of that type in the session.
4. Check that the returned description, image, or video matches the intended input.

| Goal | Example request after upload | Expected result |
| --- | --- | --- |
| Describe an image | `看图说话` | Image description; fields depend on the backend |
| Enhance an image | `图像增强` | A processed image |
| Generate an image | No prior upload needed; for example, “Generate a landscape image of a mountain lake” | A generated image |
| Generate a video | Upload an image and request `视频生成`, or describe a video to generate | A playable result, subject to the configured generation backend |
| Track objects in a video | Upload a video and request `视频追踪` | A processed video |
| Enhance a video | Upload a video and request `视频增强` | An enhanced video |

The Chinese commands above match the existing upload prompts. Intent detection chooses the processing path. If ordinary text is returned instead of media, clarify the task and check its backend. Opening a menu alone does not force that processing path.

## Attachment formats and size limits

| Category | Extensions accepted by the current Web UI | File size must be below |
| --- | --- | --- |
| Documents, spreadsheets, presentations | `.pdf`, `.doc`, `.docx`, `.txt`, `.csv`, `.xls`, `.xlsx`, `.ppt`, `.pptx` | 5 MiB |
| Images | `.jpg`, `.jpeg`, `.png`, `.heic` | 10 MiB |
| Audio | `.mp3`, `.wav`, `.pcm` | 2 MiB |
| Video | `.avi`, `.mp4` | 50 MiB |

1 MiB is 1,048,576 bytes. Files equal to the limit are also rejected. Server or provider limits may be lower, and selecting a file does not guarantee backend decoding. Additional formats described for server APIs are not automatically accepted by this button.

See [Knowledge and documents](knowledge_en.md) for ingestion and sources, the [API Reference](../API_en.md) for standalone multimodal APIs, and [Troubleshooting](troubleshooting_en.md) for missing responses or media.

<!-- Maintenance: main@b696a7c; js/self.js upload/FILE_SIZE_LIMIT/Recoder; js/query.js textQuery/getTextResult/retry; SearchServlet.java detectIntent; chat.jsp. -->

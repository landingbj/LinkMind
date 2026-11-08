# Knowledge and documents

[中文](knowledge_zh.md) | [Web UI guide](index_en.md)

Use **Chat → Orchestration** for Quick Train, Prompt Gen, and Graphic Mix. “Train” here primarily refers to document processing and knowledge retrieval; it does not mean the language model's weights have been trained or updated.

## Prepare knowledge-based answers

Ask the administrator to connect vector storage, Embedding, and RAG using the [RAG tutorial](../tutor_en.md#15-enable-rag). Scanned or image-based documents may also need OCR or document-processing services.

Start with a small file containing a verifiable fact, such as “The example meeting room is maintained every Wednesday from 14:00 to 16:00.” Check upload, retrieval, and the answer separately.

## Upload a learning file

1. Open **Chat → Orchestration → Quick Train**.
2. Click the upload icon and select one document. Check the [attachment formats and limits](chat_en.md#attachment-formats-and-size-limits).
3. Wait for processing instead of submitting the file again. This page waits for the processing response; it has no separate background-task manager or complete progress dashboard.
4. After the document receipt message, ask a specific question within the current browser and knowledge-category context.
5. Check the answer and source. For the sample file, ask when the room is maintained and verify the answer against the upload.

The receipt message alone does not establish correct extraction and retrieval of every paragraph. If the answer misses the document, check RAG, category consistency, and processing results before repeating ingestion.

## Generate an instruction set

1. Open **Chat → Orchestration → Prompt Gen**.
2. Upload the source document in the same browser session and wait for the receipt message.
3. Send **生成指令集** (generate an instruction set), matching the existing upload prompt.
4. Review the returned structured instructions, retain useful entries, and correct omissions or errors.

The operation uses the most recently uploaded document in that session. Its output is material for later preparation or training workflows, not proof that a model was trained. Use the [API Reference](../API_en.md) for controlled parameters or batch integration.

## Read an answer and inspect its sources

Ask a question under **Chat → Orchestration → Graphic Mix**, or use chat with RAG enabled. When the response includes file metadata and chunk locations, the page displays source links or a **内容定位** (content location) entry. Open the original file or crop and compare the cited passage with the answer.

If a crop is unavailable, verify the answer against the original file. Crops depend on file locations, chunks, and page coordinates; not every file or answer has them. A missing crop does not establish upload failure, and a visible crop does not establish answer accuracy.

**更多通用回答** (more general answers) retries the question without RAG for comparison. It does not remove documents or permanently disable retrieval.

## Knowledge scope and maintenance

Upload uses the page's current knowledge category. After changing browsers, login state, or deployment addresses, confirm the intended knowledge scope. The current console does not provide a complete knowledge-space, membership-permission, or file-lifecycle management interface.

Integrate server capabilities for file listing, deletion, vectors, and chunk maintenance through the [API Reference](../API_en.md). A space-management page in another version, or the existence of an API, does not establish a menu in this Web UI.

For larger files or asynchronous tracking, an administrator or client should select the appropriate API. Verify this page's upload result and an asynchronous task's completion separately. See [Troubleshooting](troubleshooting_en.md) for common failures.

<!-- Maintenance: main@b696a7c; js/nav.js legacy orchestration entries; js/self.js document upload; UploadFileServlet.java uploadLearningFile; SearchServlet.java detectIntent; js/query.js getCropRect/cropByRect/retry. -->

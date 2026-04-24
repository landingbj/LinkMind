# Tutorial

This walkthrough is meant to get you from zero to a usable LinkMind environment without throwing away features that still exist in the product. Read the **Essential** part first, then continue with **Advanced** when you are ready to go deeper.

## Part A. Essential

## 1. Start LinkMind

Use any one of the four options from the [Installation Guide](install_en.md):

- Official Installer
- Download Packaged Jar
- With Docker Image
- Build from Source

When the server is ready, open:

- `http://localhost:8080`

## 2. Create Your First Usable Configuration

Before testing anything, make sure at least one real model key is configured.

The shortest practical path is:

1. Sign in to the web console.
2. Open the model or API-key settings page.
3. Fill in one provider key.
4. Enable one chat backend in `lagi.yml`.

A minimal chat example looks like this:

```yaml
models:
  - name: qwen
    type: Alibaba
    enable: true
    model: qwen-plus,qwen-max
    driver: ai.wrapper.impl.AlibabaAdapter
    api_key: your-api-key
    # For multiple keys, use a key pool instead:
    # api_keys: sk-key1,sk-key2,sk-key3
    # key_route: polling  # polling (round-robin) or failover

functions:
  chat:
    route: pass(qwen)
    backends:
      - backend: qwen
        model: qwen-plus
        enable: true
        stream: true
        priority: 100

routers:
  enable: true
  items:
    - name: pass
      rule: (%)
```

## 3. Verify Chat In The Console

Return to the chat page and send a simple prompt such as:

- `Introduce LinkMind in one paragraph.`

If you get a normal answer, your first provider configuration is working.

## 4. Verify The HTTP API

### Native LinkMind Route

```bash
curl http://localhost:8080/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "qwen-plus",
    "stream": false,
    "messages": [
      {"role": "user", "content": "List three core LinkMind capabilities."}
    ]
  }'
```

### OpenAI-Compatible Route

```bash
curl http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "qwen-plus",
    "stream": false,
    "messages": [
      {"role": "user", "content": "List three core LinkMind capabilities."}
    ]
  }'
```

If auth is enabled, add:

```http
Authorization: Bearer <your-linkmind-api-key>
```

## 5. Enable RAG

If you want answers grounded in your own data:

1. Start Chroma.
2. Point `stores.vector[*].url` at Chroma.
3. Enable `stores.rag`.
4. Configure an embedding backend.
5. Ingest data through the console or vector APIs.

Chroma quick start:

```bash
pip install chromadb
mkdir db_data
chroma run --path db_data
```

Then set:

```yaml
stores:
  vector:
    - name: chroma
      driver: ai.vector.impl.ChromaVectorStore
      url: http://localhost:8000

  rag:
    vector: chroma
    enable: true
```

## 6. Try The Multimodal Endpoints

The current server still exposes these common workflows:

- `POST /audio/speech2text`
- `GET /audio/text2speech`
- `POST /image/text2image`
- `POST /image/image2ocr`
- `POST /image/image2text`
- `POST /image/image2enhance`
- `POST /image/image2video`
- `POST /video/video2tracking`
- `POST /video/video2enhance`
- `POST /ocr/doc2ocr`
- `POST /doc/doc2ext`
- `POST /doc/doc2struct`
- `POST /sql/text2sql`

Use the [API Reference](API_en.md) for request examples.

## 7. Optional: Connect An Agent Runtime

If your local workflow already uses OpenClaw, Hermes Agent, or DeerFlow:

1. Reinstall or restart LinkMind in `Agent Mate` mode.
2. Verify that the runtime config path is correct.
3. Let LinkMind act as the shared middleware layer instead of wiring every business app directly to each provider.

## 8. Next Steps

- Tune models, routes, filters, and RAG: [Configuration Reference](config_en.md)
- Integrate with your own service: [Integration Guide](guide_en.md)
- Extend models or vector stores: [Extension Guide](extend_en.md)

## Part B. Advanced

## 9. Build From Source, IDE, Or WAR Deployment

If you need a more traditional developer workflow, these options are still valid.

### Maven Packaging

```bash
git clone https://github.com/landingbj/lagi.git
cd lagi
mvn clean package -pl lagi-web -am -DskipTests -U
```

Build outputs:

- `lagi-web/target/LinkMind.jar`
- `lagi-web/target/ROOT.war`

### IDE Workflow

You can still import the project into IntelliJ IDEA or Eclipse, build locally, and run with your own debug configuration.

### WAR / Tomcat Deployment

If your team still prefers a servlet container:

1. Build `ROOT.war`.
2. Drop it into Tomcat `webapps`.
3. Keep `lagi.yml` aligned with the same models and stores you would use in the standalone JAR.

## 10. Switch Models And Adjust Routes

LinkMind is not limited to one chat backend. You can keep multiple providers enabled and decide how they are used.

Example:

```yaml
functions:
  chat:
    route: best((landing&qwen),(kimi|chatgpt))
    backends:
      - backend: landing
        model: cascade
        enable: true
        stream: true
        priority: 350

      - backend: qwen
        model: qwen-plus
        enable: true
        stream: true
        priority: 100

      - backend: kimi
        model: moonshot-v1-8k
        enable: true
        stream: true
        priority: 90
```

Useful route ideas:

- `A|B`: polling
- `A,B`: failover
- `A&B`: parallel

## 11. Private Training With QA Pairs

Private training QA is still part of the product workflow and should not be omitted when your use case depends on structured knowledge.

Recommended flow:

1. Prepare domain FAQs or manually curated question-answer pairs.
2. Use LinkMind to normalize them into clear, reusable QA items.
3. Write them into your knowledge category and let RAG retrieve them during chat.

### Private Training Architecture

![Private training architecture](images/img_5.png)

### Private Training Workflow

![Private training workflow](images/img_6.png)

### Practical Advice

- Keep one topic per QA pair.
- Write questions the same way real users ask them.
- Keep answers direct and short before adding long explanations.
- Separate different business domains into different categories when possible.

## 12. Generate Instruction Sets

Use instruction generation when you want to turn documents into training-oriented prompts or QA material.

Typical extraction criteria:

1. Extract questions and answers from the source document with clear structure.
2. Summarize key facts into concise and accurate responses.
3. Preserve enough context for later training or retrieval.
4. Segment by topic instead of dumping the entire source as one block.

Use the [API Reference](API_en.md) for instruction-generation request details.

## 13. Upload Private Training Files

Uploading private training files is still part of the product path for building internal knowledge.

You can upload through the console or through the file-ingestion routes such as `/uploadFile/*`, `/training/*`, and the document/vector APIs, depending on your workflow.

### Supported File Formats

- Text: `txt`, `doc`, `docx`, `pdf`
- Spreadsheets: `xls`, `xlsx`, `csv`
- Images: `jpeg`, `png`, `jpg`, `webp`
- Presentations: `ppt`, `pptx`

### File Processing Strategies

Different file categories are still handled differently:

1. QA files: extract and separate question-answer pairs.
2. Chapter-based documents: preserve paragraph completeness after structure cleanup.
3. Tables and spreadsheets: convert headers and rows into model-friendly text or structured content.
4. Numeric tables: optionally cooperate with text-to-SQL and relational storage.
5. Image-text mixed files: combine OCR and layout extraction.
6. Title-heavy files: keep titles as standalone knowledge anchors.
7. Presentation files: process page-by-page text and images together.
8. Pure image files: use OCR or image understanding to turn them into retrievable content.

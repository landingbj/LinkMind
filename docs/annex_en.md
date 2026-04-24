# Annex

This appendix covers RAG setup, IDE configuration, and other supporting material.

## 1. Quick Chroma Setup For RAG

If you want to enable RAG locally, Chroma is the fastest vector-store starting point.

### Python

```bash
pip install chromadb
mkdir db_data
chroma run --path db_data
```

Default local address:

- `http://localhost:8000`

### Docker

```bash
docker run -d \
  --name chromadb \
  -p 8000:8000 \
  -v /mydata/docker/local/chroma/data:/study/ai/chroma \
  -e IS_PERSISTENT=TRUE \
  -e ANONYMIZED_TELEMETRY=TRUE \
  chromadb/chroma:latest
```

## 2. Minimal RAG Checklist

After Chroma is running, check these items in `lagi.yml`:

```yaml
stores:
  vector:
    - name: chroma
      driver: ai.vector.impl.ChromaVectorStore
      url: http://localhost:8000

  rag:
    vector: chroma
    enable: true

functions:
  embedding:
    - backend: qwen
      type: Qwen
      api_key: your-api-key
```

Recommended order:

1. Start Chroma.
2. Point `stores.vector[*].url` at your Chroma service.
3. Enable `stores.rag`.
4. Configure an embedding backend.
5. Upload or upsert knowledge content.

## 3. Quick Integration Notes

### Directly Import the JAR

If you only want to reuse LinkMind capabilities in an existing Java project:

1. Download `lagi-core-*-jar-with-dependencies.jar`.
2. Put it into your project's `lib` directory.
3. Provide an external `lagi.yml` or place one in your runtime `resources`.
4. Build and run your project as usual.

External configuration overrides the default embedded configuration.

### In Eclipse

1. Open `File > Import...`.
2. Choose `General > Existing Projects into Workspace`.
3. Select your project directory and finish the import.
4. Open project `Properties > Java Build Path > Libraries`.
5. Add the required LinkMind JARs or the generated `lagi-core` dependency.
6. Run `Build Project`.

### In IntelliJ IDEA

1. Open `File > Open`.
2. Choose your project directory.
3. Add LinkMind through Maven dependency or local JARs.
4. Reimport or sync the project.
5. Build the project to verify all classes resolve correctly.

### Common IDE Issues

- If the JAR cannot be resolved, check whether the file really exists and was added to the correct module.
- If classes still appear missing, refresh Maven or rebuild the project index.
- If runtime behavior does not match expectation, verify which `lagi.yml` is actually being loaded.

## 4. Build Artifact Summary

The current repository packaging flow produces:

- `lagi-web/target/LinkMind.jar`
- `lagi-web/target/ROOT.war`

At runtime, the packaged JAR generates:

- `config/lagi.yml`
- `data/`

If you want containerized deployment, use the official Docker image from the [Installation Guide](install_en.md) instead of packaging your own image here.

## 5. Document And Knowledge Workflows

The current server supports these related capabilities:

- `POST /doc/doc2ext` for content extraction
- `POST /doc/doc2struct` for Markdown-style document structuring
- `POST /ocr/doc2ocr` for OCR on PDFs and images
- `POST /v1/vector/upsert` and related vector admin endpoints for knowledge ingestion and maintenance

## 6. Related Docs

- [Installation Guide](install_en.md)
- [Configuration Reference](config_en.md)
- [API Reference](API_en.md)
- [Tutorial](tutor_en.md)

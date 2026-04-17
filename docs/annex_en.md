# Annex

This appendix keeps the supporting material that is helpful during setup but should not distract from the main getting-started flow.

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

1. Start Chroma
2. Point `stores.vector[*].url` at your Chroma service
3. Enable `stores.rag`
4. Configure an embedding backend
5. Upload or upsert knowledge content

## 3. Build Artifact Summary

The current repository packaging flow produces:

- `lagi-web/target/LinkMind.jar`
- `lagi-web/target/ROOT.war`

At runtime, the packaged JAR generates:

- `config/lagi.yml`
- `data/`

## 4. Document And Knowledge Workflows

The current server supports these related capabilities:

- `POST /doc/doc2ext` for content extraction
- `POST /doc/doc2struct` for Markdown-style document structuring
- `POST /ocr/doc2ocr` for OCR on PDFs and images
- `POST /v1/vector/upsert` and related vector admin endpoints for knowledge ingestion and maintenance

## 5. Related Docs

- [Installation Guide](install_en.md)
- [Configuration Reference](config_en.md)
- [API Reference](API_en.md)
- [Tutorial](tutor_en.md)

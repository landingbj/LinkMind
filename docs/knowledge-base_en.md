# Knowledge Base

This page combines the configuration, ingestion API, and verification path for document retrieval and RAG.

## 1. Start a vector store

Chroma is the smallest local setup. Start it with Python or Docker:

```bash
pip install chromadb
chroma run --host 0.0.0.0 --port 8000
```

```bash
docker run -d --name chroma -p 8000:8000 chromadb/chroma
```

## 2. Configure storage, embedding, and RAG

```yaml
stores:
  vector:
    - name: chroma
      driver: ai.vector.impl.ChromaVectorStore
      default_category: default
      similarity_top_k: 10
      similarity_cutoff: 0.5
      url: http://localhost:8000
  rag:
    vector: chroma
    enable: true
    priority: 10
    track: true
    html: true
    default: "Please give prompt more precisely"
    enable_excel_to_md: true

functions:
  embedding:
    - backend: qwen
      type: Qwen
      api_key: your-api-key
      model_name: text-embedding
```

`stores.rag.vector` must match the vector-store `name`. Set the vector URL to the host reachable from LinkMind, not necessarily the browser's host. `similarity_top_k` and `similarity_cutoff` control retrieval breadth and threshold; `enable_excel_to_md` normalizes spreadsheet content before ingestion.

## 3. Ingest and manage knowledge

| Task | Endpoint |
| --- | --- |
| Create an embedding | `POST /v1/embeddings` |
| Add, update, get, or delete vectors | `POST /v1/vector/add`, `/update`, `/get`, `/delete` |
| Query vectors | `POST /v1/vector/query` |
| List collections | `GET /v1/vector/listCollections` |
| Extract, OCR, or structure a document | `POST /doc/doc2ext`, `/ocr/doc2ocr`, `/doc/doc2struct` |
| Upload private-training material or Q&A | `POST /training/upload`, `POST /uploadFile/pairing` |

Use one deliberate collection/category per knowledge domain. Preserve source metadata and document identifiers so that updates and deletes are repeatable. Complete request schemas are in [API Reference](API_en.md#rag).

## 4. Verify the answer path

1. Upload or add a short document containing one distinctive fact.
2. Check that the document-processing and embedding requests completed.
3. Query the collection directly with `POST /v1/vector/query`.
4. Ask the chat route about that exact fact.
5. Confirm the answer and any configured trace or citation data.

If the upload succeeds but chat misses the material, check the collection/category, embedding model, vector-store reachability, retrieval cutoff, and whether RAG is enabled on the chat path.

## 5. Related pages

- [Models and Multimodal](models-and-multimodal_en.md): configure the embedding and document-processing backends.
- [Extensions](extensions_en.md): implement a custom vector store.
- [Legacy Chroma notes](annex_en.md): additional Java and Chroma integration details.

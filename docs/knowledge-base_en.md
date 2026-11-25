# Knowledge Base

This page combines the configuration, ingestion API, and verification path for document retrieval and RAG.

## 1. Choose and start a vector store

SQLite is the preferred option for a new local deployment: it runs with LinkMind and needs no extra service, which suits appliances, single hosts, and small knowledge bases. Choose Chroma, Milvus, or another store when it already exists in the environment.

Minimal SQLite configuration:

```yaml
stores:
  vector:
    - name: sqlite
      driver: ai.vector.impl.SqliteVectorStore
      default_category: default
      similarity_top_k: 10
      similarity_cutoff: 0.5
      parent_depth: 1
      child_depth: 1
      path: data/vector.db
```

The `path` must be writable by the LinkMind process; back up this database file before upgrades or migration. Chroma below is an optional independent vector-store option:

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

Use one deliberate collection/category per knowledge domain. Preserve source metadata and document IDs returned by the API so that updates and deletes are repeatable.

### 1. Add or update vectors

`POST /v1/vector/add` writes text to a collection; `/update`, `/get`, and `/delete` update, fetch, and remove records. Add a collection/category, document text, and metadata; use stable IDs returned or retrieved by the API for updates and deletes:

```json
{
  "category": "property-manual",
  "data": [
    {
      "document": "A maintenance request must receive its first response within two working days.",
      "metadata": {"source": "manual.pdf", "page": "3"}
    }
  ]
}
```

### 2. Query retrieval results

`POST /v1/vector/query`:

```json
{
  "category": "property-manual",
  "text": "How soon should a maintenance request receive a response?",
  "n": 5
}
```

`category` identifies the collection, `text` is the retrieval query, and `n` is the desired nearest-result count. Actual recall is also controlled by `similarity_top_k`, `similarity_cutoff`, and metadata filters.

### 3. Upload a file or Q&A pairs

Use `POST /training/upload?category=property-manual` with `multipart/form-data` and a `fileToUpload` field. Use `POST /uploadFile/pairing` for Q&A pairs:

```json
{
  "category": "property-manual",
  "level": "user",
  "data": [
    {
      "instruction": ["How soon does maintenance respond?"],
      "output": "The first response is due within two working days."
    }
  ]
}
```

Use `GET /uploadFile/getUploadFileList` to review uploaded files and `POST /uploadFile/deleteFile` with file IDs to remove files and their related vectors.

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

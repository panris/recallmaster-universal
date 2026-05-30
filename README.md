# RecallMaster Universal

Vendor-agnostic RAG retrieval recall evaluator implemented in Java.

## What It Does

- Runs retrieval-only evaluation against a unified vector-store interface: `search(query, topK, filters)`.
- Supports PostgreSQL/pgvector, Milvus, ChromaDB, Elasticsearch, and an in-memory demo connector.
- **Write support**: `upsert()` implemented for Postgres, Milvus, Chroma — load documents into real vector stores.
- **Real embeddings**: OpenAI-compatible embedding models (OpenAI, Kimi, DeepSeek, local models).
- **LLM intent extraction**: Case generation uses LLM to infer intents from document chunks (Chinese/English).
- Reports hard `Recall@K`, intent coverage, noise ratio, judge disagreement, and human-review flags.
- Generates candidate cases from PDF, Markdown, and TXT documents.
- Provides REST APIs plus a lightweight dashboard with live task progress and single-case replay.

## Quick Start

```bash
mvn test
mvn spring-boot:run
```

Open `http://localhost:8088`.

The default config includes a `demo-memory` connector with hash embeddings, so the app can run without external databases or LLM keys.

## API Examples

Start a run:

```bash
curl -s http://localhost:8088/api/runs \
  -H 'Content-Type: application/json' \
  -d '{
    "database": "demo-memory",
    "topK": 5,
    "cases": [{
      "question": "如何配置负载均衡？高可用模式下心跳间隔是多少？",
      "intents": ["负载均衡配置", "高可用参数"],
      "expectedIds": ["tech_lb_config", "tech_ha_heartbeat"],
      "filters": {},
      "labelStatus": "HUMAN_VERIFIED"
    }]
  }'
```

Generate candidate cases from source documents:

```bash
curl -s http://localhost:8088/api/cases/generate \
  -H 'Content-Type: application/json' \
  -d '{
    "sourcePaths": ["/absolute/path/to/doc.md"],
    "maxCases": 10,
    "requireHumanVerification": true
  }'
```

Upsert documents into a real vector store:

```bash
curl -s http://localhost:8088/api/connectors/postgres-demo/upsert \
  -H 'Content-Type: application/json' \
  -d '{
    "documents": [
      {"id": "doc-1", "text": "负载均衡配置使用 round-robin 算法", "metadata": {"source": "tech-docs"}}
    ]
  }'
```

## Configuration

Use `src/main/resources/application.yml` for local defaults or `config/example-config.yaml` as a deployment template.

### Embedding Configuration

Hash embeddings (default, no API key required):

```yaml
recallmaster:
  embedding:
    provider: hash
    dimensions: 256
```

OpenAI-compatible embeddings:

```yaml
recallmaster:
  embedding:
    provider: openai
    model: text-embedding-3-small
    base-url: https://api.openai.com/v1
    api-key: ${OPENAI_API_KEY}
    dimensions: 1536
```

For Kimi, DeepSeek, or local models:

```yaml
recallmaster:
  embedding:
    provider: openai-compatible
    model: bge-large-zh-v1.5
    base-url: http://localhost:11434/v1
    api-key: dummy
    dimensions: 1024
```

### Judge Configuration

For OpenAI-compatible judges such as LiteLLM, Ollama, or OpenAI:

```yaml
recallmaster:
  evaluator:
    primary-judge: deepseek-chat
    secondary-judge: gpt-4o
    base-url: http://localhost:4000/v1
    api-key: ${LITELLM_API_KEY}
```

The built-in fallback judge is `rule-based`. It is useful for smoke tests, not for final semantic scoring.

### Vector Store Configuration

PostgreSQL/pgvector:

```yaml
recallmaster:
  databases:
    - name: postgres-prod
      type: postgres
      enabled: true
      connection: jdbc:postgresql://localhost:5432/vectordb
      table: documents
      id-col: id
      text-col: content
      vector-col: embedding
      metadata-col: metadata
      dimension: 1536
```

Milvus:

```yaml
recallmaster:
  databases:
    - name: milvus-prod
      type: milvus
      enabled: true
      uri: http://localhost:19530
      collection: documents
      dimension: 1536
```

ChromaDB:

```yaml
recallmaster:
  databases:
    - name: chroma-prod
      type: chroma
      enabled: true
      uri: http://localhost:8000
      collection: documents
```

## Ground Truth Policy

Model-generated labels are supported, but they are stored as `MODEL_PROPOSED` or `NEEDS_REVIEW`.

Use `HUMAN_VERIFIED` or `IMPORTED` labels for acceptance-grade hard recall metrics. The platform intentionally marks unverified generated labels for review so synthetic ground truth does not silently become production truth.

## Development

```bash
mvn test
```

Main packages:

- `connector`: vector database adapters (Postgres, Milvus, Chroma, Elasticsearch, Memory).
- `embedding`: embedding models (Hash, OpenAI-compatible).
- `llm`: LLM client for intent extraction and judging.
- `evaluation`: recall metrics, intent coverage, RRF fusion.
- `judge`: rule-based and OpenAI-compatible LLM judges.
- `casegen`: document-derived candidate case generation with LLM intent inference.
- `task`: async batch evaluation state.
- `web`: dashboard and REST API.

## Tech Stack

- Java 21
- Spring Boot 3.4.0
- Jackson 2.x
- PostgreSQL pgvector / Milvus / ChromaDB / Elasticsearch
- OpenAI-compatible APIs (OpenAI, Kimi, DeepSeek, Ollama, LiteLLM)

# RecallMaster Universal

Vendor-agnostic RAG retrieval recall evaluator implemented in Java.

## What It Does

- Runs retrieval-only evaluation against a unified vector-store interface: `search(query, topK, filters)`.
- Supports PostgreSQL/pgvector, Milvus, ChromaDB, Elasticsearch, and an in-memory demo connector.
- Reports hard `Recall@K`, intent coverage, noise ratio, judge disagreement, and human-review flags.
- Generates candidate cases from PDF, Markdown, and TXT documents.
- Provides REST APIs plus a lightweight dashboard with live task progress and single-case replay.

## Quick Start

```bash
mvn test
mvn spring-boot:run
```

Open `http://localhost:8088`.

The default config includes a `demo-memory` connector, so the app can run without external databases or LLM keys.

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

## Configuration

Use `src/main/resources/application.yml` for local defaults or `config/example-config.yaml` as a deployment template.

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

## Ground Truth Policy

Model-generated labels are supported, but they are stored as `MODEL_PROPOSED` or `NEEDS_REVIEW`.

Use `HUMAN_VERIFIED` or `IMPORTED` labels for acceptance-grade hard recall metrics. The platform intentionally marks unverified generated labels for review so synthetic ground truth does not silently become production truth.

## Development

```bash
mvn test
```

Main packages:

- `connector`: vector database adapters.
- `evaluation`: recall metrics, intent coverage, RRF fusion.
- `judge`: rule-based and OpenAI-compatible LLM judges.
- `casegen`: document-derived candidate case generation.
- `task`: async batch evaluation state.
- `web`: dashboard and REST API.

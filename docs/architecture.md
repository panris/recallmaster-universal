# Architecture

## Runtime Flow

1. Case input comes from imported JSON, manual UI entry, or document-based generation.
2. `EvaluationRunService` creates a run and executes cases concurrently.
3. `EvaluationService` embeds the question, calls one configured connector, and calculates hard recall.
4. `JudgeRegistry` invokes the primary and optional secondary judge.
5. `AiAnalysis` merges coverage/noise scores and marks judge disagreement or unverified labels for review.
6. The dashboard polls `/api/runs/{id}` and can also consume `/api/runs/{id}/events` as SSE.

## Extension Points

- Add a vector database by implementing `VectorStoreConnector`.
- Add a judge by implementing `JudgeModel`.
- Add custom retrieval strategies by calling connector `search` functions and fusing results with `ReciprocalRankFusion`.
- Replace the in-process task runner with Redis/Spring Batch when runs need distributed scheduling.

## Current MVP Scope

- Fully runnable with `demo-memory`.
- PostgreSQL pgvector adapter uses JDBC and vector operators.
- Milvus, Chroma, and Elasticsearch adapters use HTTP request shapes and may need field/endpoint adjustment per deployment.
- LLM judge uses OpenAI-compatible `/v1/chat/completions` endpoints through `base-url`.
- The default `rule-based` judge is a smoke-test fallback.

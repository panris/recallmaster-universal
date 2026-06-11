# RecallMaster Universal 优化任务清单

本文档基于 2026-06-01 对当前工程的 review 结果整理，目标是把项目从“可运行评测 Demo”推进到“方便日常使用、便于扩展、可稳定批量评测”的工具。

当前基线：

- `mvn test` 通过，8 个测试全部成功。
- 当前 Run 存储仍为内存态。
- Dashboard 已可运行 Demo、导入 Case、单案复现、Run 对比、导出 CSV/Case JSON。
- 连接器接口已有 `upsert()`，Postgres、Milvus、Chroma、Memory 已实现写入，但 REST API 暂未暴露 upsert 入口。

## P0：修正文档与接口不一致

### TASK-001：补齐或移除 README 中的 Upsert REST 示例

**问题**

README 中展示了：

```text
POST /api/connectors/{name}/upsert
```

但当前 `ApiController` 没有该接口。用户按 README 操作会 404。

**建议方案**

优先补齐接口，而不是删除示例。新增文档写入 API：

- `POST /api/connectors/{name}/upsert`
- 请求体包含 documents。
- 服务端使用当前配置的 `EmbeddingModel` 为文档生成向量。
- 调用目标 `VectorStoreConnector.upsert()`。

**涉及文件**

- `src/main/java/com/recallmaster/universal/web/ApiController.java`
- `src/main/java/com/recallmaster/universal/connector/VectorStoreConnector.java`
- `src/main/java/com/recallmaster/universal/model/DocumentChunk.java`
- `README.md`
- `docs/user-guide-zh.md`

**验收标准**

- README 示例可以成功调用。
- Memory/Postgres/Milvus/Chroma upsert 至少有单元测试或 mock 测试覆盖。
- 不支持 upsert 的连接器返回清晰错误。

## P0：批量评测稳定性

### TASK-002：单个 Case 失败不拖垮整批 Run

**问题**

当前 `EvaluationRunService` 中任意一个 `CompletableFuture` 抛异常，会导致整个 Run 标记为 `FAILED`，其他成功 Case 的结果也可能无法形成完整报告。

**建议方案**

- 增加 Case 级错误结果。
- 单个 Case 失败时记录错误、状态标记为 `ERROR`。
- Run 最终状态支持 `COMPLETED_WITH_ERRORS`，或保留 `COMPLETED` 并增加 error count。
- Dashboard 显示失败 Case 和错误原因。

**涉及文件**

- `src/main/java/com/recallmaster/universal/task/EvaluationRunService.java`
- `src/main/java/com/recallmaster/universal/model/RunStatus.java`
- `src/main/java/com/recallmaster/universal/model/CaseResult.java`
- `src/main/resources/static/dashboard.js`
- `src/test/java/com/recallmaster/universal/evaluation/EvaluationServiceTest.java`

**验收标准**

- 一个 Case 抛异常时，其他 Case 继续执行。
- Run 结果中能看到成功和失败 Case。
- CSV/summary/report 不因单个失败中断。

### TASK-003：增加请求校验与全局错误响应

**问题**

API 当前直接抛 `IllegalArgumentException` / `IllegalStateException`，错误响应格式不统一。请求体也缺少统一的大小、字段和范围校验。

**建议方案**

- 增加 `@RestControllerAdvice`。
- 统一错误响应字段：`code`、`message`、`path`、`timestamp`。
- 对 `EvaluationRunRequest`、`CaseGenerationRequest` 增加 Bean Validation。
- 限制 `topK`、case 数量、文本长度、source path 数量。

**涉及文件**

- `src/main/java/com/recallmaster/universal/web/ApiController.java`
- `src/main/java/com/recallmaster/universal/task/EvaluationRunRequest.java`
- `src/main/java/com/recallmaster/universal/casegen/CaseGenerationRequest.java`
- `src/main/java/com/recallmaster/universal/model/EvaluationCase.java`

**验收标准**

- 非法请求返回 4xx 和稳定 JSON。
- 未知 run、未知 connector、无效 JSON 都有明确错误。
- 前端能展示错误消息。

## P1：日常使用体验

### TASK-004：Run 历史持久化

**问题**

`EvaluationRunService` 使用内存 `ConcurrentHashMap` 保存 Run，应用重启后历史丢失，不适合日常回归评测。

**建议方案**

第一阶段可用 H2/SQLite/Postgres 任一方式持久化：

- run 基本信息。
- case result。
- retrieved chunks。
- judge verdict。
- events 和错误信息。

后续再考虑归档、清理和分页查询。

**涉及文件**

- `src/main/java/com/recallmaster/universal/task/EvaluationRunService.java`
- `src/main/java/com/recallmaster/universal/model/EvaluationRun.java`
- `src/main/java/com/recallmaster/universal/model/CaseResult.java`
- `pom.xml`
- `src/main/resources/application.yml`

**验收标准**

- 应用重启后仍可查看历史 Run。
- `/api/runs` 支持分页或限制最近 N 条。
- CSV 和 summary 可从持久化数据生成。

### TASK-005：结构化 Run 详情页

**问题**

当前 Dashboard 主要把详情写到 JSON `pre` 中。用户要判断“为什么漏召”不够方便。

**建议方案**

新增结构化详情视图：

- 命中 ID。
- 漏召 ID。
- Top-K 召回列表。
- 每条召回的 rank、score、id、text、metadata。
- Judge verdict。
- 复核建议。

**涉及文件**

- `src/main/resources/templates/dashboard.html`
- `src/main/resources/static/dashboard.js`
- `src/main/resources/static/styles.css`

**验收标准**

- 点击 case cell 后显示结构化详情，而不是只有 JSON。
- 长文本可折叠或滚动。
- 移动端不溢出。

### TASK-006：新建批量评测页面

**问题**

当前页面有 Demo、导入校验和单案复现，但没有完整的新建批量评测入口。真实用户仍需要 curl 或手动拼请求。

**建议方案**

新增“新建评测”区域：

- 下拉选择 connector。
- 设置 topK。
- 粘贴 Case JSON 或选择导入结果。
- 点击运行。
- 创建后自动跳到 Run 看板。

**涉及文件**

- `src/main/resources/templates/dashboard.html`
- `src/main/resources/static/dashboard.js`
- `src/main/resources/static/styles.css`

**验收标准**

- 不使用 curl 也能创建多 Case Run。
- 请求失败时页面展示错误。
- 支持复用导入成功的 cases。

### TASK-007：支持 CSV / JSONL Case 导入

**问题**

当前只支持严格 JSON 数组。业务里评测集常来自表格、标注平台或 JSONL。

**建议方案**

- 支持 JSON、JSONL、CSV。
- CSV 字段至少支持：`question`、`intents`、`expectedIds`、`filters`、`labelStatus`。
- 支持常见别名：`query` -> `question`，`expected_ids` -> `expectedIds`。
- 导入时返回错误行号和警告。

**涉及文件**

- `src/main/java/com/recallmaster/universal/casegen/CaseImportService.java`
- `src/main/java/com/recallmaster/universal/web/ApiController.java`
- `src/main/resources/static/dashboard.js`

**验收标准**

- JSONL 和 CSV 导入有测试覆盖。
- 错误行不会导致用户不知道哪里错。
- Dashboard 可上传 `.json`、`.jsonl`、`.csv`。

## P1：安全与前端可靠性

### TASK-008：移除 Dashboard 中对后端数据的 innerHTML 拼接

**问题**

`dashboard.js` 多处使用 `innerHTML` 拼接后端字段，例如 connector name、run database、id 等。虽然当前输入来源较受控，但长期看有 XSS 风险。

**建议方案**

- 改为 `document.createElement` + `textContent`。
- 对确实需要 HTML 模板的静态结构，动态数据只通过 text 节点写入。

**涉及文件**

- `src/main/resources/static/dashboard.js`

**验收标准**

- 动态后端字段不再通过 `innerHTML` 写入。
- 页面功能保持一致。

### TASK-009：统一前端 Loading / Error 状态

**问题**

`fetchJson` 抛错后，部分按钮和区域没有错误提示，用户不知道操作失败原因。

**建议方案**

- 为连接器检查、运行 Demo、导入 Case、单案复现、Run 对比增加 loading 状态。
- 所有 fetch 失败显示错误信息。
- 避免重复点击导致重复提交。

**涉及文件**

- `src/main/resources/static/dashboard.js`
- `src/main/resources/static/styles.css`

**验收标准**

- API 失败时页面展示清楚错误。
- 提交中按钮禁用。
- 失败后可重试。

## P2：评测指标与报告

### TASK-010：补充 Hit@K、Precision@K、MRR、nDCG

**问题**

当前核心硬指标只有 Recall@K。排查召回排序质量时，还需要排名相关指标。

**建议方案**

扩展 `RetrievalMetrics`：

- `hitAtK`
- `precisionAtK`
- `mrr`
- `ndcg`
- `firstRelevantRank`

**涉及文件**

- `src/main/java/com/recallmaster/universal/model/RetrievalMetrics.java`
- `src/main/java/com/recallmaster/universal/evaluation/EvaluationService.java`
- `src/main/java/com/recallmaster/universal/report/ReportService.java`
- `src/main/resources/static/dashboard.js`

**验收标准**

- 指标有单元测试覆盖。
- Summary、CSV、Dashboard 都展示新增指标。

### TASK-011：HTML / Markdown / 完整 JSON 报告导出

**问题**

当前只有 CSV 和 Case JSON。CSV 适合表格分析，但不适合直接分享评测结论。

**建议方案**

新增导出：

- `/api/runs/{id}/report.md`
- `/api/runs/{id}/report.html`
- `/api/runs/{id}/report.json`

**涉及文件**

- `src/main/java/com/recallmaster/universal/report/ReportService.java`
- `src/main/java/com/recallmaster/universal/web/ApiController.java`

**验收标准**

- Markdown 报告包含摘要、失败 Case、漏召 Case。
- HTML 报告可独立打开。
- JSON 报告包含完整可机器处理数据。

### TASK-012：Run 对比视图结构化

**问题**

当前 Run 对比结果主要显示 JSON，不便于快速判断改动是否变好。

**建议方案**

页面展示：

- 总体指标变化。
- 变好 Case。
- 变差 Case。
- 新增命中 ID。
- 新增漏召 ID。
- 需要复核的变化。

**涉及文件**

- `src/main/java/com/recallmaster/universal/report/ReportService.java`
- `src/main/resources/static/dashboard.js`
- `src/main/resources/templates/dashboard.html`

**验收标准**

- 用户无需读 JSON 即可看出 candidate 相比 baseline 的变化。

## P2：LLM 与外部调用可靠性

### TASK-013：统一 OpenAI 兼容 HTTP 客户端

**问题**

Embedding、Case generation、Judge 分别使用相近逻辑。`OpenAiCompatibleJudgeModel` 直接创建 `HttpClient`，`LlmClient` 和 embedding 使用 `HttpJsonClient`。超时、错误处理、脱敏、重试策略不统一。

**建议方案**

- 统一走一个 HTTP client abstraction。
- 支持 connect timeout、read timeout、重试次数配置。
- 统一错误脱敏，不把完整响应和敏感 URL/API key 暴露到前端。
- 记录 endpoint 类型和 trace id。

**涉及文件**

- `src/main/java/com/recallmaster/universal/connector/HttpJsonClient.java`
- `src/main/java/com/recallmaster/universal/judge/OpenAiCompatibleJudgeModel.java`
- `src/main/java/com/recallmaster/universal/llm/LlmClient.java`
- `src/main/java/com/recallmaster/universal/embedding/OpenAiCompatibleEmbeddingModel.java`
- `src/main/java/com/recallmaster/universal/config/RecallMasterProperties.java`

**验收标准**

- Judge、embedding、LLM case generation 使用统一超时和错误处理。
- HTTP 4xx/5xx 返回摘要，不泄漏 secret。
- 网络错误测试有覆盖。

### TASK-014：Case 生成的 JSON 解析改为 ObjectMapper

**问题**

`CaseGeneratorService.parseIntentsJson` 通过查找 `[` `]` 和 split 解析 JSON，容易被逗号、转义字符、多语言内容破坏。

**建议方案**

- 注入 `ObjectMapper`。
- 使用结构化 DTO 解析 `{"intents":[]}`。
- 解析失败时保留 fallback。
- 删除未使用的 `containsAny` 规则方法。

**涉及文件**

- `src/main/java/com/recallmaster/universal/casegen/CaseGeneratorService.java`
- `src/test/java/com/recallmaster/universal/casegen/CaseGeneratorServiceTest.java`

**验收标准**

- 支持包含逗号、引号的意图文本。
- LLM 返回非 JSON 时 fallback 正常。
- 无未使用方法。

### TASK-015：Judge 输出校验与降级

**问题**

Judge 返回的 `score`、`noise_ratio`、intent 列表没有范围校验。一次 Judge 异常会导致整个 Case 异常，进一步可能拖垮 Run。

**建议方案**

- score clamp 到 0-100。
- noise ratio clamp 到 0-1。
- missing/covered intents 去重。
- Judge 失败时生成 `needsHumanReview=true` 的降级分析，而不是直接失败。

**涉及文件**

- `src/main/java/com/recallmaster/universal/judge/OpenAiCompatibleJudgeModel.java`
- `src/main/java/com/recallmaster/universal/evaluation/EvaluationService.java`

**验收标准**

- 非法 Judge 输出不会使 Run 失败。
- 降级结果清楚提示人工复核。

## P3：连接器生产可用性

### TASK-016：Postgres 连接池与元数据读取

**问题**

Postgres connector 每次查询都用 `DriverManager.getConnection`，没有连接池。查询结果 metadata 当前返回 `Map.of()`，导致报告缺少来源信息。

**建议方案**

- 引入 DataSource 或连接池。
- 支持读取 metadata JSON/JSONB。
- 支持数据库级连接超时配置。

**涉及文件**

- `src/main/java/com/recallmaster/universal/connector/PostgresPgvectorConnector.java`
- `src/main/java/com/recallmaster/universal/connector/PostgresConnectorFactory.java`
- `src/main/resources/application.yml`

**验收标准**

- 查询复用连接池。
- SearchResult 包含 metadata。
- 有 SQL 构造和 metadata 解析测试。

### TASK-017：连接器契约测试

**问题**

外部连接器主要依赖 HTTP/JDBC 请求形态，缺少契约测试。接口变动时容易运行期才发现问题。

**建议方案**

- 为 Milvus/Chroma/Elasticsearch HTTP body 构造添加 mock HTTP 测试。
- 为 Postgres SQL 构造添加单元测试。
- 后续可选 Testcontainers 集成测试。

**涉及文件**

- `src/test/java/com/recallmaster/universal/connector/*`

**验收标准**

- 每个连接器至少覆盖 search 请求构造。
- 支持 upsert 的连接器覆盖 upsert 请求构造。

## P3：工程化与部署

### TASK-018：补齐 Maven Wrapper、Dockerfile、docker-compose、.env.example

**问题**

新环境需要本机安装 Maven/JDK；没有容器化示例。对试用和交付不够方便。

**建议方案**

- 增加 Maven Wrapper。
- 增加 Dockerfile。
- 增加 docker-compose，至少包含应用服务，可选 Postgres/pgvector。
- 增加 `.env.example`。

**验收标准**

- `./mvnw test` 可运行。
- `docker compose up` 可启动应用。
- README 更新启动方式。

### TASK-019：增加 CI

**问题**

当前没有自动化 CI。代码变更可能破坏编译或测试后才被人工发现。

**建议方案**

新增 GitHub Actions 或等价 CI：

- JDK 21。
- 缓存 Maven。
- 执行 `mvn test`。

**验收标准**

- 每次 PR/Push 自动跑测试。
- README 或贡献说明中标注 CI 状态。

## P4：高级工作流

### TASK-020：任务取消、失败重跑、定时评测

**问题**

当前 Run 创建后无法取消，也不能只重跑失败 Case。不适合长批次或持续回归。

**建议方案**

- 增加 `POST /api/runs/{id}/cancel`。
- 增加 `POST /api/runs/{id}/rerun-failed`。
- 增加固定评测集的定时运行配置。

**验收标准**

- 运行中任务可取消。
- 失败 Case 可重跑。
- 事件流能反映取消和重跑状态。

### TASK-021：人工复核工作流

**问题**

系统已经有 `MODEL_PROPOSED`、`NEEDS_REVIEW`、`HUMAN_VERIFIED` 等状态，但没有页面化复核流程。

**建议方案**

- 新增 Case 复核列表。
- 支持编辑 question、intents、expectedIds、labelStatus。
- 保存 reviewer 和更新时间。
- 从 `NEEDS_REVIEW` 转为 `HUMAN_VERIFIED`。

**验收标准**

- 用户可在页面完成候选 Case 复核。
- 复核后的 Case 可直接用于验收级 Run。


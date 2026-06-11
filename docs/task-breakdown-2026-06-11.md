# RecallMaster Universal 任务拆分清单

本文档基于 2026-06-11 对当前工程的综合评估结果整理，包含功能优化、交互改进、代码质量三大方向的所有任务。

---

## 执行顺序建议

```
第一阶段（1-2天）：P0 全部 + TASK-Q-002 + TASK-Q-003
第二阶段（2-3天）：TASK-P1-001 + TASK-P1-002 + TASK-P1-005 + TASK-P1-006
第三阶段（2-3天）：P2 全部（TASK-P2-004 可与 TASK-Q-001 合并）
第四阶段（1-2天）：P3 全部（Docker + CI）
第五阶段（可选）：P4 全部
```

---

## 🔴 P0 - 修正文档与接口不一致 + 批量评测稳定性

### TASK-P0-001：补齐 Upsert REST API

**问题**：README 展示了 `POST /api/connectors/{name}/upsert` 但接口不存在

**涉及文件**：
- `src/main/java/com/recallmaster/universal/web/ApiController.java`
- `src/main/java/com/recallmaster/universal/connector/VectorStoreConnector.java`
- `src/main/java/com/recallmaster/universal/model/DocumentChunk.java`
- `README.md`
- `docs/user-guide-zh.md`

**验收标准**：
- README 示例可以成功调用
- Memory/Postgres/Milvus/Chroma upsert 至少有单元测试或 mock 测试覆盖
- 不支持 upsert 的连接器返回清晰错误

---

### TASK-P0-002：单个 Case 失败不拖垮整批 Run

**问题**：当前 `EvaluationRunService` 中任意一个 `CompletableFuture` 抛异常，会导致整个 Run 标记为 `FAILED`

**涉及文件**：
- `src/main/java/com/recallmaster/universal/task/EvaluationRunService.java`
- `src/main/java/com/recallmaster/universal/model/RunStatus.java`
- `src/main/java/com/recallmaster/universal/model/CaseResult.java`
- `src/main/resources/static/dashboard.js`
- `src/test/java/com/recallmaster/universal/evaluation/EvaluationServiceTest.java`

**验收标准**：
- 一个 Case 抛异常时，其他 Case 继续执行
- Run 结果中能看到成功和失败 Case
- CSV/summary/report 不因单个失败中断

---

### TASK-P0-003：添加全局错误处理 @RestControllerAdvice

**问题**：API 当前直接抛 `IllegalArgumentException` / `IllegalStateException`，错误响应格式不统一

**涉及文件**：
- 新建 `src/main/java/com/recallmaster/universal/web/GlobalExceptionHandler.java`
- `src/main/java/com/recallmaster/universal/web/ApiController.java`

**验收标准**：
- 统一错误响应字段：`code`、`message`、`path`、`timestamp`
- 非法请求返回 4xx 和稳定 JSON
- 未知 run、未知 connector、无效 JSON 都有明确错误

---

### TASK-P0-004：添加请求校验（Bean Validation）

**问题**：请求体缺少统一的大小、字段和范围校验

**涉及文件**：
- `src/main/java/com/recallmaster/universal/task/EvaluationRunRequest.java`
- `src/main/java/com/recallmaster/universal/casegen/CaseGenerationRequest.java`
- `src/main/java/com/recallmaster/universal/model/EvaluationCase.java`

**验收标准**：
- 限制 `topK`、case 数量、文本长度、source path 数量
- 前端能展示错误消息

---

## 🟠 P1 - 日常使用体验

### TASK-P1-001：Dashboard 新建批量评测页面

**问题**：当前没有完整的新建批量评测入口，真实用户仍需要 curl 或手动拼请求

**涉及文件**：
- `src/main/resources/templates/dashboard.html`
- `src/main/resources/static/dashboard.js`
- `src/main/resources/static/styles.css`

**验收标准**：
- 不使用 curl 也能创建多 Case Run
- 请求失败时页面展示错误
- 支持复用导入成功的 cases
- 创建后自动跳到 Run 看板

---

### TASK-P1-002：支持 CSV/JSONL Case 导入

**问题**：当前只支持严格 JSON 数组，业务里评测集常来自表格、标注平台或 JSONL

**涉及文件**：
- `src/main/java/com/recallmaster/universal/casegen/CaseImportService.java`
- `src/main/java/com/recallmaster/universal/web/ApiController.java`
- `src/main/resources/static/dashboard.js`

**验收标准**：
- 支持 JSON、JSONL、CSV
- CSV 字段支持：`question`、`intents`、`expectedIds`、`filters`、`labelStatus`
- 支持常见别名：`query` -> `question`，`expected_ids` -> `expectedIds`
- 导入时返回错误行号和警告
- Dashboard 可上传 `.json`、`.jsonl`、`.csv`

---

### TASK-P1-003：结构化 Run 详情页

**问题**：当前 Dashboard 主要把详情写到 JSON `pre` 中，判断"为什么漏召"不方便

**涉及文件**：
- `src/main/resources/templates/dashboard.html`
- `src/main/resources/static/dashboard.js`
- `src/main/resources/static/styles.css`

**验收标准**：
- 点击 case cell 后显示结构化详情，而不是只有 JSON
- 长文本可折叠或滚动
- 移动端不溢出

---

### TASK-P1-004：Run 历史持久化（H2/Postgres）

**问题**：`EvaluationRunService` 使用内存 `ConcurrentHashMap`，应用重启后历史丢失

**涉及文件**：
- `src/main/java/com/recallmaster/universal/task/EvaluationRunService.java`
- `src/main/java/com/recallmaster/universal/model/EvaluationRun.java`
- `src/main/java/com/recallmaster/universal/model/CaseResult.java`
- 新建 `src/main/java/com/recallmaster/universal/repository/` 包
- `pom.xml`
- `src/main/resources/application.yml`

**验收标准**：
- 应用重启后仍可查看历史 Run
- `/api/runs` 支持分页或限制最近 N 条
- CSV 和 summary 可从持久化数据生成

**前置依赖**：TASK-P0-003

---

### TASK-P1-005：统一前端 Loading/Error 状态

**问题**：`fetchJson` 抛错后，部分按钮和区域没有错误提示

**涉及文件**：
- `src/main/resources/static/dashboard.js`
- `src/main/resources/static/styles.css`

**验收标准**：
- 为连接器检查、运行 Demo、导入 Case、单案复现、Run 对比增加 loading 状态
- 所有 fetch 失败显示错误信息
- 避免重复点击导致重复提交

---

### TASK-P1-006：移除 XSS 风险的 innerHTML

**问题**：`dashboard.js` 多处使用 `innerHTML` 拼接后端字段，有 XSS 风险

**涉及文件**：
- `src/main/resources/static/dashboard.js`

**验收标准**：
- 动态后端字段不再通过 `innerHTML` 写入
- 改为 `document.createElement` + `textContent`
- 页面功能保持一致

---

## 🟡 P2 - 评测指标与报告

### TASK-P2-001：补充 Hit@K、Precision@K、MRR、nDCG

**问题**：当前核心硬指标只有 Recall@K，排查召回排序质量时还需要排名相关指标

**涉及文件**：
- `src/main/java/com/recallmaster/universal/model/RetrievalMetrics.java`
- `src/main/java/com/recallmaster/universal/evaluation/EvaluationService.java`
- `src/main/java/com/recallmaster/universal/report/ReportService.java`
- `src/main/resources/static/dashboard.js`

**验收标准**：
- 指标有单元测试覆盖
- Summary、CSV、Dashboard 都展示新增指标

---

### TASK-P2-002：Markdown/HTML 报告导出

**问题**：当前只有 CSV 和 Case JSON，不适合直接分享评测结论

**涉及文件**：
- `src/main/java/com/recallmaster/universal/report/ReportService.java`
- `src/main/java/com/recallmaster/universal/web/ApiController.java`

**验收标准**：
- `/api/runs/{id}/report.md` - Markdown 报告包含摘要、失败 Case、漏召 Case
- `/api/runs/{id}/report.html` - HTML 报告可独立打开
- `/api/runs/{id}/report.json` - JSON 报告包含完整可机器处理数据

---

### TASK-P2-003：Run 对比视图结构化

**问题**：当前 Run 对比结果主要显示 JSON，不便于快速判断改动是否变好

**涉及文件**：
- `src/main/java/com/recallmaster/universal/report/ReportService.java`
- `src/main/resources/static/dashboard.js`
- `src/main/resources/templates/dashboard.html`

**验收标准**：
- 用户无需读 JSON 即可看出 candidate 相比 baseline 的变化
- 展示：总体指标变化、变好/变差 Case、新增命中/漏召 ID

**前置依赖**：TASK-P1-003

---

### TASK-P2-004：统一 OpenAI 兼容 HTTP 客户端

**问题**：Embedding、Case generation、Judge 分别使用相近逻辑，超时、错误处理、脱敏、重试策略不统一

**涉及文件**：
- `src/main/java/com/recallmaster/universal/connector/HttpJsonClient.java`
- `src/main/java/com/recallmaster/universal/judge/OpenAiCompatibleJudgeModel.java`
- `src/main/java/com/recallmaster/universal/llm/LlmClient.java`
- `src/main/java/com/recallmaster/universal/embedding/OpenAiCompatibleEmbeddingModel.java`
- `src/main/java/com/recallmaster/universal/config/RecallMasterProperties.java`

**验收标准**：
- Judge、embedding、LLM case generation 使用统一超时和错误处理
- HTTP 4xx/5xx 返回摘要，不泄漏 secret
- 网络错误测试有覆盖

**前置依赖**：TASK-P0-003

---

### TASK-P2-005：Case 生成的 JSON 解析改用 ObjectMapper

**问题**：`CaseGeneratorService.parseIntentsJson` 通过字符串操作解析 JSON，容易被逗号、转义字符、多语言内容破坏

**涉及文件**：
- `src/main/java/com/recallmaster/universal/casegen/CaseGeneratorService.java`
- `src/test/java/com/recallmaster/universal/casegen/CaseGeneratorServiceTest.java`

**验收标准**：
- 支持包含逗号、引号的意图文本
- LLM 返回非 JSON 时 fallback 正常
- 无未使用方法

---

### TASK-P2-006：Judge 输出校验与降级

**问题**：Judge 返回的 `score`、`noise_ratio`、intent 列表没有范围校验

**涉及文件**：
- `src/main/java/com/recallmaster/universal/judge/OpenAiCompatibleJudgeModel.java`
- `src/main/java/com/recallmaster/universal/evaluation/EvaluationService.java`

**验收标准**：
- score clamp 到 0-100
- noise ratio clamp 到 0-1
- missing/covered intents 去重
- Judge 失败时生成 `needsHumanReview=true` 的降级分析

**前置依赖**：TASK-P0-002

---

## 🟢 P3 - 连接器生产可用性 + 工程化

### TASK-P3-001：Postgres 连接池

**问题**：Postgres connector 每次查询都用 `DriverManager.getConnection`，没有连接池

**涉及文件**：
- `src/main/java/com/recallmaster/universal/connector/PostgresPgvectorConnector.java`
- `src/main/java/com/recallmaster/universal/connector/PostgresConnectorFactory.java`
- `src/main/resources/application.yml`

**验收标准**：
- 查询复用连接池
- SearchResult 包含 metadata
- 有 SQL 构造和 metadata 解析测试

---

### TASK-P3-002：连接器契约测试

**问题**：外部连接器主要依赖 HTTP/JDBC 请求形态，缺少契约测试

**涉及文件**：
- `src/test/java/com/recallmaster/universal/connector/*`

**验收标准**：
- 每个连接器至少覆盖 search 请求构造
- 支持 upsert 的连接器覆盖 upsert 请求构造

---

### TASK-P3-003：添加 Maven Wrapper

**涉及文件**：
- `mvnw`, `mvnw.cmd`

**验收标准**：`.mvnw test` 可运行

---

### TASK-P3-004：添加 Dockerfile + docker-compose

**涉及文件**：
- `Dockerfile`
- `docker-compose.yml`（至少包含应用服务，可选 Postgres/pgvector）

**验收标准**：`docker compose up` 可启动应用

---

### TASK-P3-005：添加 GitHub Actions CI

**涉及文件**：
- `.github/workflows/ci.yml`

**验收标准**：
- 每次 PR/Push 自动跑测试
- README 或贡献说明中标注 CI 状态

---

### TASK-P3-006：添加 .env.example

**涉及文件**：
- `.env.example`

**验收标准**：包含所有需要的环境变量及说明

---

## 🔵 P4 - 高级工作流

### TASK-P4-001：Run 取消、重跑失败 Case

**涉及文件**：
- `src/main/java/com/recallmaster/universal/web/ApiController.java`
- `src/main/java/com/recallmaster/universal/task/EvaluationRunService.java`

**验收标准**：
- 运行中任务可取消
- 失败 Case 可重跑
- 事件流能反映取消和重跑状态

**前置依赖**：TASK-P0-002, TASK-P1-004

---

### TASK-P4-002：定时评测

**涉及文件**：
- 新建 `src/main/java/com/recallmaster/universal/task/ScheduledRunService.java`
- `src/main/resources/application.yml`

**验收标准**：
- 支持固定评测集的定时运行配置
- 定时任务可被取消

**前置依赖**：TASK-P1-004

---

### TASK-P4-003：人工复核工作流

**涉及文件**：
- 新建 `src/main/java/com/recallmaster/universal/web/ReviewController.java`
- 新建复核页面组件
- `src/main/java/com/recallmaster/universal/model/EvaluationCase.java`

**验收标准**：
- 用户可在页面完成候选 Case 复核
- 支持编辑 question、intents、expectedIds、labelStatus
- 保存 reviewer 和更新时间
- 从 `NEEDS_REVIEW` 转为 `HUMAN_VERIFIED`

**前置依赖**：TASK-P1-003

---

## 🟣 代码质量专项（可并行执行）

### TASK-Q-001：提取工具类

**问题**：`toList(float[])` 在 3 个 Connector 中重复，`trimTrailingSlash()` 在 5 个地方重复

**涉及文件**：
- 新建 `src/main/java/com/recallmaster/universal/util/VectorMath.java`
- 新建 `src/main/java/com/recallmaster/universal/util/UrlUtils.java`

**验收标准**：所有重复代码替换为工具类调用

---

### TASK-Q-002：ExecutorService 生命周期管理

**问题**：线程池未实现 `@PreDestroy` 关闭

**涉及文件**：
- `src/main/java/com/recallmaster/universal/task/EvaluationRunService.java`

**验收标准**：应用关闭时线程池正确 shutdown

---

### TASK-Q-003：SSE 回调完善

**问题**：`SseEmitter` 缺少 `onCompletion`、`onTimeout`、`onError` 回调

**涉及文件**：
- `src/main/java/com/recallmaster/universal/web/ApiController.java`

**验收标准**：超时/错误时 emitter 正确清理

---

### TASK-Q-004：修复 SSE 事件竞态条件

**问题**：SSE 事件流中 `events.size()` 和 `sent` 之间存在竞态

**涉及文件**：
- `src/main/java/com/recallmaster/universal/web/ApiController.java`

**验收标准**：事件不会丢失

---

### TASK-Q-005：API Controller 拆分

**问题**：`ApiController` 职责过多

**涉及文件**：
- 新建 `src/main/java/com/recallmaster/universal/web/ConnectorController.java`
- 新建 `src/main/java/com/recallmaster/universal/web/CaseController.java`
- 新建 `src/main/java/com/recallmaster/universal/web/RunController.java`
- 新建 `src/main/java/com/recallmaster/universal/web/ReportController.java`

**验收标准**：单一职责，每个 Controller < 200 行

---

## ⚡ 快速修复清单（1小时内可完成）

| 任务 | 描述 |
|------|------|
| Quick-001 | README 加一行"默认 Demo 无需任何外部依赖" |
| Quick-002 | Dashboard 顶部加引导文案"首次使用？点击运行 Demo 体验" |
| Quick-003 | `ApiController.streamRun()` 加 3 行 SSE 回调代码 |
| Quick-004 | `EvaluationRunService` 加 `@PreDestroy` shutdown 方法 |

---

## 任务统计汇总

| 优先级 | 任务数 | 预估总工时 |
|--------|--------|-----------|
| P0 | 4 | 7h |
| P1 | 6 | 21h |
| P2 | 6 | 18h |
| P3 | 4 | 7.5h |
| P4 | 3 | 14h |
| Q（质量专项） | 5 | 8.5h |
| Quick（快速修复） | 4 | 1h |
| **总计** | **32** | **~77h** |

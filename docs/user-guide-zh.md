# RecallMaster Universal 中文使用说明

RecallMaster Universal 是一个通用 RAG 召回评测工具，用来评估检索系统是否能把标准答案对应的文档片段召回到 Top-K 结果中。

它适合这些场景：

- 对比不同向量数据库、embedding、chunk 策略或检索参数的召回效果。
- 用标准 Case 集持续回归 RAG 检索质量。
- 从 PDF、Markdown、TXT 文档生成候选评测 Case。
- 使用规则或 OpenAI 兼容 LLM 判断子意图覆盖、噪声比例和人工复核需求。

## 1. 环境要求

- JDK 21
- Maven 3.9+

检查环境：

```bash
java -version
mvn -version
```

## 2. 快速启动

在项目根目录执行：

```bash
mvn test
mvn spring-boot:run
```

启动后打开：

```text
http://localhost:8088
```

默认配置包含 `demo-memory` 连接器和 hash embedding，不需要外部数据库或 LLM Key 即可运行。

## 3. 页面功能

Dashboard 地址：

```text
http://localhost:8088
```

页面包含以下区域。

### 连接器

展示当前启用的数据库连接器。

可以执行：

- 查看连接器名称、类型和配置摘要。
- 点击“健康检查”检查所有连接器。
- 点击单个连接器卡片检查单个连接器。

### 任务看板

用于查看已经创建的评测 Run。

每个 Run 会展示：

- 数据库名称。
- Run 状态。
- 完成进度。
- Top-K。
- 平均 Recall。
- 平均意图覆盖。
- 平均噪声比例。
- 需要人工复核的 Case 数量。

可以导出：

- CSV 报告。
- Case JSON。

### 运行 Demo

点击页面右上角“运行 Demo”，系统会用内置 `demo-memory` 数据运行示例 Case。

这是验证本地服务是否正常的最快方式。

### Run 对比

可以输入两个 Run ID 进行对比。

也可以点击“对比最近两次”，快速比较最近的两个评测结果。

### Case 导入

支持：

- 上传 JSON 文件。
- 直接粘贴 JSON。

导入只负责解析和校验 Case，不会自动开始评测。

### 单案复现

适合调试一个具体问题。

需要填写：

- 数据库名称。
- Top-K。
- 问题。
- 子意图。
- 标准 ID。

提交后会创建一个只包含单个 Case 的评测 Run。

## 4. 核心概念

### Evaluation Case

一个 Case 表示一个待评测问题和标准召回目标。

示例：

```json
{
  "question": "如何配置负载均衡？高可用模式下心跳间隔是多少？",
  "intents": ["负载均衡配置", "高可用参数"],
  "expectedIds": ["tech_lb_config", "tech_ha_heartbeat"],
  "filters": {},
  "labelStatus": "HUMAN_VERIFIED"
}
```

字段说明：

- `question`：用户问题。
- `intents`：问题包含的子意图。
- `expectedIds`：期望出现在 Top-K 结果中的文档 ID。
- `filters`：传给连接器的过滤条件。
- `labelStatus`：标签状态。

### Label Status

- `HUMAN_VERIFIED`：人工确认过，适合验收级评测。
- `IMPORTED`：来自可信外部标准集。
- `MODEL_PROPOSED`：模型生成的候选标签。
- `NEEDS_REVIEW`：需要人工复核。

建议：正式评测优先使用 `HUMAN_VERIFIED` 或 `IMPORTED`。

### Evaluation Run

一次批量评测任务。

Run 记录：

- 使用的数据库连接器。
- Top-K。
- Case 总数。
- 每个 Case 的召回结果。
- Recall@K。
- LLM 或规则 Judge 的分析结果。

注意：当前 Run 保存在内存中，应用重启后历史记录会丢失。

## 5. API 使用

### 查看连接器

```bash
curl -s http://localhost:8088/api/connectors
```

### 查看单个连接器

```bash
curl -s http://localhost:8088/api/connectors/demo-memory
```

### 检查所有连接器健康状态

```bash
curl -s -X POST http://localhost:8088/api/connectors/health
```

### 检查单个连接器健康状态

```bash
curl -s -X POST http://localhost:8088/api/connectors/demo-memory/health
```

### 导入 Case JSON

```bash
curl -s http://localhost:8088/api/cases/import \
  -H 'Content-Type: application/json' \
  -d '[{
    "question": "该医疗保险的牙科报销上限是多少？是否包含根管治疗？",
    "intents": ["报销额度", "牙科保障范围"],
    "expectedIds": ["med_dental_limit"],
    "filters": {},
    "labelStatus": "HUMAN_VERIFIED"
  }]'
```

### 从文档生成候选 Case

```bash
curl -s http://localhost:8088/api/cases/generate \
  -H 'Content-Type: application/json' \
  -d '{
    "sourcePaths": ["/absolute/path/to/doc.md"],
    "maxCases": 10,
    "requireHumanVerification": true
  }'
```

支持文档格式：

- `.pdf`
- `.md`
- `.txt`

### 创建评测 Run

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

### 查看 Run 列表

```bash
curl -s http://localhost:8088/api/runs
```

### 查看单个 Run

```bash
curl -s http://localhost:8088/api/runs/{runId}
```

### 查看 Run 结果

```bash
curl -s http://localhost:8088/api/runs/{runId}/results
```

### 查看 Run 汇总

```bash
curl -s http://localhost:8088/api/runs/{runId}/summary
```

### 监听 Run 事件

```bash
curl -N http://localhost:8088/api/runs/{runId}/events
```

### 对比两个 Run

```bash
curl -s http://localhost:8088/api/runs/{baselineId}/compare/{candidateId}
```

### 导出 CSV 报告

```bash
curl -L -o report.csv http://localhost:8088/api/runs/{runId}/report.csv
```

### 导出 Case JSON

```bash
curl -L -o cases.json http://localhost:8088/api/runs/{runId}/cases.json
```

## 6. 配置说明

默认配置：

```text
src/main/resources/application.yml
```

部署示例：

```text
config/example-config.yaml
```

### 基础配置

```yaml
server:
  port: 8088

recallmaster:
  default-top-k: 5
  evaluator:
    primary-judge: rule-based
    secondary-judge: ""
    base-url: ""
    api-key: ""
    concurrency: 5
    strict-ground-truth: false
    disagreement-threshold: 20
```

说明：

- `default-top-k`：请求未指定 topK 时的默认值。
- `primary-judge`：主 Judge，默认 `rule-based`。
- `secondary-judge`：副 Judge，可为空。
- `base-url`：OpenAI 兼容聊天接口地址。
- `api-key`：LLM Judge API Key。
- `concurrency`：批量评测并发数。
- `strict-ground-truth`：严格模式下，未人工确认的 Case 会被标记为需要复核。
- `disagreement-threshold`：主副 Judge 分数差超过该值时标记分歧。

### Embedding 配置

默认 hash embedding：

```yaml
recallmaster:
  embedding:
    provider: hash
    model: hash-embedding-v1
    dimensions: 256
```

OpenAI 兼容 embedding：

```yaml
recallmaster:
  embedding:
    provider: openai
    model: text-embedding-3-small
    base-url: https://api.openai.com/v1
    api-key: ${OPENAI_API_KEY}
    dimensions: 1536
```

本地或代理模型示例：

```yaml
recallmaster:
  embedding:
    provider: openai-compatible
    model: bge-large-zh-v1.5
    base-url: http://localhost:11434/v1
    api-key: dummy
    dimensions: 1024
```

注意：hash embedding 适合 Demo 和烟雾测试，不适合正式语义召回评测。

### Judge 配置

内置规则 Judge：

```yaml
recallmaster:
  evaluator:
    primary-judge: rule-based
```

OpenAI 兼容 Judge：

```yaml
recallmaster:
  evaluator:
    primary-judge: deepseek-chat
    secondary-judge: gpt-4o
    base-url: http://localhost:4000/v1
    api-key: ${LITELLM_API_KEY}
```

也可以使用环境变量：

```bash
export RECALLMASTER_LLM_BASE_URL=http://localhost:4000/v1
export RECALLMASTER_LLM_API_KEY=your-api-key
```

## 7. 连接器配置

### 内存 Demo

```yaml
recallmaster:
  databases:
    - name: demo-memory
      type: memory
      enabled: true
      dimension: 256
      documents:
        - id: tech_lb_config
          text: "负载均衡需要配置 upstream 节点、健康检查路径和转发策略。"
          metadata:
            source: demo-tech
            topic: lb
```

### PostgreSQL / pgvector

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
      metric: cosine
```

要求：

- 表中有稳定 ID 字段。
- 表中有文本字段。
- 表中有 pgvector 向量字段。
- 查询向量维度和字段维度一致。

### Milvus

```yaml
recallmaster:
  databases:
    - name: milvus-prod
      type: milvus
      enabled: true
      uri: http://localhost:19530
      collection: documents
      id-col: id
      text-col: content
      vector-col: embedding
      dimension: 1536
```

### ChromaDB

```yaml
recallmaster:
  databases:
    - name: chroma-prod
      type: chroma
      enabled: true
      uri: http://localhost:8000
      collection: documents
      id-col: id
      text-col: document
      vector-col: embedding
```

### Elasticsearch

```yaml
recallmaster:
  databases:
    - name: elastic-prod
      type: elasticsearch
      enabled: true
      uri: http://localhost:9200
      index: rag_chunks
      id-col: id
      text-col: content
      vector-col: embedding
      metadata-col: metadata
      dimension: 1536
      api-key: ${ELASTIC_API_KEY:}
```

## 8. 推荐评测流程

1. 准备文档库，并确保每个 chunk 有稳定 ID。
2. 配置 embedding 和目标向量数据库连接器。
3. 准备评测 Case，标注 `question`、`intents`、`expectedIds`。
4. 将可信 Case 标记为 `HUMAN_VERIFIED` 或 `IMPORTED`。
5. 通过 Dashboard 或 `/api/runs` 创建评测 Run。
6. 查看 Recall@K、漏召 ID、意图覆盖和复核标记。
7. 调整 chunk、embedding、过滤条件、rerank 或数据库配置。
8. 再跑一次评测。
9. 使用 Run 对比确认优化是否有效。

## 9. 指标解读

### Recall@K

`expectedIds` 中有多少 ID 出现在 Top-K 召回结果中。

示例：

- `expectedIds = ["a", "b"]`
- Top-K 结果包含 `a`，不包含 `b`
- Recall@K = `1 / 2 = 0.5`

### Intent Coverage

Judge 判断召回内容覆盖了多少子意图。

### Noise Ratio

Judge 判断召回结果中无关内容的比例。

### Needs Human Review

以下情况会标记为需要人工复核：

- Case 是 `MODEL_PROPOSED`。
- Case 是 `NEEDS_REVIEW`。
- 主副 Judge 存在明显分歧。
- 严格 Ground Truth 模式下，Case 不是 `HUMAN_VERIFIED` 或 `IMPORTED`。

## 10. 常见问题

### 启动后没有连接器

检查 `recallmaster.databases` 是否为空，或者所有连接器是否都设置了 `enabled: false`。

### 外部连接器健康检查失败

检查：

- `uri` 或 `connection` 是否正确。
- collection、index、table 名称是否正确。
- API Key 是否配置。
- 向量维度是否匹配。
- 外部服务是否已启动。

### Judge 调用失败

检查：

- `recallmaster.evaluator.base-url` 是否指向 OpenAI 兼容 `/v1` 地址。
- API Key 是否有效。
- 模型名称是否存在。
- 服务是否支持 JSON object 响应格式。

### 评测结果都是 MISS

常见原因：

- `expectedIds` 和数据库中的文档 ID 不一致。
- topK 太小。
- 使用 hash embedding，语义召回能力有限。
- filters 排除了标准答案。
- 目标数据库中的数据和 Case 标准集不是同一批。

### 文档生成的 Case 能直接用于验收吗

不建议。文档生成的 Case 应作为候选集，需要人工确认 `question`、`intents` 和 `expectedIds` 后，再标记为 `HUMAN_VERIFIED`。

## 11. 当前限制

- Run 历史保存在内存中，应用重启后会丢失。
- Dashboard 当前偏轻量，复杂 Case 管理和结构化详情仍需后续增强。
- 规则 Judge 适合烟雾测试，不适合最终语义判断。
- 外部连接器的 HTTP 请求形态可能需要根据实际部署调整。
- `VectorStoreConnector` 接口支持 `upsert`，部分连接器也实现了写入能力，但当前 REST API 暂未暴露文档写入入口。

## 12. 开发命令

运行测试：

```bash
mvn test
```

启动本地服务：

```bash
mvn spring-boot:run
```

查看当前 Git 状态：

```bash
git status --short
```


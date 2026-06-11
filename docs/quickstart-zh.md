# RecallMaster 5 分钟快速上手

本文档帮助你在 5 分钟内完成第一次 RAG 召回评测。

## 1. 启动应用（1 分钟）

```bash
# 克隆项目
git clone https://github.com/your-org/recallmaster-universal.git
cd recallmaster-universal

# 启动（自动包含 Demo 数据，无需配置）
mvn spring-boot:run
```

启动后访问：**http://localhost:8088**

> 默认使用 Hash Embedding（无语义理解能力），适合功能验证。正式评测请参考配置章节。

---

## 2. 运行 Demo（1 分钟）

1. 打开页面后，会看到首次访问引导卡片
2. 点击右上角 **"运行 Demo"** 按钮
3. 等待评测完成，查看结果

**Demo 结果解读**：
- 🟢 绿色格子 = 命中（FULL_RECALL）
- 🟡 黄色格子 = 部分命中（PARTIAL_RECALL）
- 🔴 红色格子 = 漏召（MISS）

> 注意：Demo 使用 Hash Embedding，语义匹配能力有限。如果看到红色格子是正常的。

---

## 3. 理解评测结果（2 分钟）

点击任意结果格子，查看详情：

### 召回指标

| 指标 | 含义 | 目标值 |
|------|------|--------|
| **Recall@K** | 期望召回的文档有多少出现在 Top-K 中 | ≥ 80% |
| Hit@K | Top-K 中是否命中至少一个期望文档 | ≥ 90% |
| Precision@K | Top-K 中有多少是相关文档 | ≥ 50% |
| MRR | 第一个相关文档的平均排名倒数 | ≥ 0.5 |
| nDCG | 考虑排名的归一化折损增益 | ≥ 0.6 |

### 意图分析

| 指标 | 含义 |
|------|------|
| **意图覆盖** | 召回内容覆盖了多少子意图 |
| **噪音比** | 召回内容中有多少是无相关内容 |
| 需人工审核 | 是否需要人工复核 |

### 优化建议

详情页底部会显示根据评测结果生成的优化建议。

---

## 4. 开始你的评测（1 分钟）

### 4.1 准备评测 Case

一个评测 Case 包含：

```json
{
  "question": "用户会问什么问题？",
  "intents": ["子意图1", "子意图2"],
  "expectedIds": ["期望召回的文档ID1", "期望召回的文档ID2"],
  "labelStatus": "HUMAN_VERIFIED"
}
```

**字段说明**：
- `question`：用户可能问的问题
- `intents`：问题包含的子意图（帮助 Judge 分析意图覆盖）
- `expectedIds`：**这个最关键** —— 你期望在 Top-K 结果中看到哪些文档的 ID
- `labelStatus`：`HUMAN_VERIFIED`（人工确认过）用于正式评测

### 4.2 如何获取 expectedIds？

`expectedIds` 是你向量数据库中文档的唯一 ID。

**方法 1**：如果你有原始文档 ID
```
文档 → 分块 → 存入向量库时保持原有 ID → 那个 ID 就是 expectedIds
```

**方法 2**：先查一次你的向量库
```bash
# 假设你知道某个文档的内容
curl -X POST "http://localhost:8088/api/connectors/demo-memory/search" \
  -H 'Content-Type: application/json' \
  -d '{"query": "负载均衡配置", "topK": 5}'
```

### 4.3 导入 Cases

在页面的 **Case 导入**区域：
- 点击 **"加载示例 Cases"** 查看示例格式
- 上传 JSON 文件或直接粘贴 Case JSON
- 导入后会显示 Cases 数量

### 4.4 创建评测

在 **新建评测** 区域：
1. 选择你的向量库连接器
2. 设置 Top-K（建议 5-10）
3. 粘贴或从导入结果复制 Cases
4. 点击 **开始评测**

---

## 5. 常见问题

### Q1: Demo 结果全是红色，正常吗？

**正常**。Demo 使用 Hash Embedding，只有字面匹配能力。

正式评测请：
1. 配置 OpenAI 或其他语义 Embedding
2. 或者连接真实的向量数据库

### Q2: 如何配置真实的 Embedding？

在 `application.yml` 中：

```yaml
recallmaster:
  embedding:
    provider: openai-compatible
    model: bge-large-zh-v1.5
    base-url: http://localhost:11434/v1
    api-key: dummy
    dimensions: 1024
```

### Q3: Recall@K 很低怎么办？

详情页会显示优化建议。常见原因：

| 问题 | 解决方案 |
|------|----------|
| topK 太小 | 增大 topK 到 10-20 |
| chunk size 不合适 | 检查 chunk 是否包含完整意图 |
| embedding 模型不够强 | 使用更好的 embedding 模型 |
| 文档没分好 | 调整 chunk overlap |
| metadata 过滤错误 | 检查 filters 配置 |

### Q4: 如何对比两次评测结果？

在 **Run 对比** 区域：
- 输入两个 Run ID
- 或点击 **"对比最近两次"**

---

## 下一步

- 阅读完整文档：[user-guide-zh.md](user-guide-zh.md)
- 查看向量库配置示例：[connector-setup-zh.md](connector-setup-zh.md)
- 了解所有指标含义：[指标解读](#)

---

## 需要帮助？

- GitHub Issues: [提交问题](https://github.com/your-org/recallmaster-universal/issues)
- 文档问题：欢迎提交 PR 完善

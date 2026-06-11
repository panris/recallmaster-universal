# 向量库配置指南

本文档介绍如何配置不同的向量数据库连接器。

---

## PostgreSQL / pgvector

### 1. 创建扩展和表

```sql
-- 1. 启用 pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- 2. 创建文档表
CREATE TABLE documents (
    id VARCHAR(255) PRIMARY KEY,          -- 文档唯一 ID
    content TEXT NOT NULL,               -- 文档文本内容
    embedding vector(1536),               -- 向量（维度需与 embedding 模型一致）
    metadata JSONB,                       -- 元数据（可选）
    created_at TIMESTAMP DEFAULT NOW()
);

-- 3. 创建向量索引（加速相似度搜索）
CREATE INDEX idx_documents_embedding 
    ON documents USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);

-- 4. 创建全文搜索索引（可选，用于混合搜索）
CREATE INDEX idx_documents_content 
    ON documents USING gin (to_tsvector('chinese', content));
```

### 2. 配置 RecallMaster

```yaml
recallmaster:
  embedding:
    provider: openai
    model: text-embedding-3-small
    dimensions: 1536   # 必须与表字段维度一致
  
  databases:
    - name: postgres-prod
      type: postgres
      enabled: true
      connection: jdbc:postgresql://localhost:5432/your_database
      table: documents
      id-col: id
      text-col: content
      vector-col: embedding
      metadata-col: metadata
      dimension: 1536
```

### 3. 常见问题

**Q: 向量维度不匹配？**
- 检查 embedding 配置的 `dimensions` 是否与表的 `vector(N)` 一致
- text-embedding-3-small = 1536 维
- text-embedding-3-large = 3072 维
- bge-large-zh-v1.5 = 1024 维

**Q: 搜索很慢？**
- 确保已创建向量索引
- 可以调整 `lists` 参数（数据量大时增加）

---

## Milvus

### 1. 创建 Collection

```bash
# 使用 Milvus CLI 或 Python SDK
# 方式1: 通过 Attu UI (http://localhost:19530)

# 方式2: 通过 Python SDK
from pymilvus import connections, Collection, FieldSchema, CollectionSchema, DataType

# 连接
connections.connect("default", host="localhost", port="19530")

# 定义 Schema
fields = [
    FieldSchema(name="id", dtype=DataType.VARCHAR, max_length=255, is_primary=True),
    FieldSchema(name="content", dtype=DataType.VARCHAR, max_length=65535),
    FieldSchema(name="embedding", dtype=DataType.FLOAT_VECTOR, dim=1536),
    FieldSchema(name="metadata", dtype=DataType.JSON, max_length=65535),
]
schema = CollectionSchema(fields, description="RAG documents")

# 创建 Collection
collection = Collection("documents", schema)

# 创建索引
index_params = {
    "index_type": "IVF_FLAT",
    "metric_type": "COSINE",
    "params": {"nlist": 128}
}
collection.create_index("embedding", index_params)
```

### 2. 配置 RecallMaster

```yaml
recallmaster:
  embedding:
    provider: openai
    model: text-embedding-3-small
    dimensions: 1536
  
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

---

## ChromaDB

### 1. 启动 ChromaDB

```bash
# 方式1: Docker
docker run -p 8000:8000 chromadb/chroma

# 方式2: Python
pip install chromadb
python -c "import chromadb; chromadb.run()"
```

### 2. 创建 Collection

```python
import chromadb

client = chromadb.Client()
collection = client.create_collection(
    name="documents",
    metadata={"hnsw:space": "cosine"}  # cosine = 余弦相似度
)
```

### 3. 配置 RecallMaster

```yaml
recallmaster:
  embedding:
    provider: openai
    model: text-embedding-3-small
    dimensions: 1536
  
  databases:
    - name: chroma-local
      type: chroma
      enabled: true
      uri: http://localhost:8000
      collection: documents
      id-col: id
      text-col: document
      vector-col: embedding
```

---

## Elasticsearch（支持 kNN 搜索）

### 1. 创建 Index

```bash
curl -X PUT "http://localhost:9200/rag_chunks" \
  -H "Content-Type: application/json" \
  -d '{
  "settings": {
    "number_of_shards": 1,
    "number_of_replicas": 0,
    "analysis": {
      "analyzer": {
        "ik_analyzer": {
          "type": "ik_max_word"
        }
      }
    }
  },
  "mappings": {
    "properties": {
      "id": { "type": "keyword" },
      "content": { 
        "type": "text",
        "analyzer": "ik_max_word"
      },
      "embedding": {
        "type": "dense_vector",
        "dims": 1536,
        "index": true,
        "similarity": "cosine"
      },
      "metadata": { "type": "object", "enabled": true }
    }
  }
}'
```

### 2. 配置 RecallMaster

```yaml
recallmaster:
  embedding:
    provider: openai
    model: text-embedding-3-small
    dimensions: 1536
  
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

---

## 向量维度速查表

| Embedding 模型 | 维度 | RecallMaster 配置 |
|---------------|------|------------------|
| text-embedding-3-small | 1536 | `dimensions: 1536` |
| text-embedding-3-large | 3072 | `dimensions: 3072` |
| text-embedding-ada-002 | 1536 | `dimensions: 1536` |
| bge-large-zh-v1.5 | 1024 | `dimensions: 1024` |
| bge-small-zh-v1.5 | 512 | `dimensions: 512` |
| m3e-large | 1024 | `dimensions: 1024` |
| BAAI/bge-m3 | 1024 | `dimensions: 1024` |
| nomic-embed-text-v1.5 | 768 | `dimensions: 768` |

---

## 连接器类型速查

| 类型 | 适用场景 | 配置复杂度 |
|------|----------|-----------|
| memory | Demo、快速验证 | ⭐ 零配置 |
| postgres | 生产环境、已有 pgvector | ⭐⭐⭐ |
| milvus | 大规模向量、分布式 | ⭐⭐⭐⭐ |
| chroma | 轻量级、本地开发 | ⭐⭐ |
| elasticsearch | 已有 ES + 需要混合搜索 | ⭐⭐⭐⭐ |

---

## 使用 Docker Compose 快速搭建

```yaml
# docker-compose.yml
services:
  postgres:
    image: pgvector/pgvector:pg16
    environment:
      POSTGRES_DB: vectordb
      POSTGRES_USER: user
      POSTGRES_PASSWORD: password
    volumes:
      - pgdata:/var/lib/postgresql/data
  
  app:
    # 你的 RecallMaster 配置
    environment:
      - SPRING_PROFILES_ACTIVE=prod

volumes:
  pgdata:
```

启动后记得创建表结构！

---

## 下一步

- [快速上手指南](quickstart-zh.md)
- [用户使用文档](user-guide-zh.md)

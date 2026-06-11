# 技术运维文档

## 负载均衡配置

### 基本配置

负载均衡（Load Balancer）需要配置以下参数：

1. **Upstream 节点池**
   - 定义后端服务器列表
   - 配置权重比例
   - 设置最大连接数

2. **健康检查**
   - 检查路径（如 `/health`）
   - 检查间隔（默认 30 秒）
   - 不健康阈值（连续失败 3 次）
   - 恢复阈值（连续成功 2 次）

3. **转发策略**
   - 轮询（round-robin）
   - 最少连接（least_conn）
   - IP 哈希（ip_hash）

### 配置示例

```nginx
upstream backend {
    server 192.168.1.10:8080 weight=5;
    server 192.168.1.11:8080 weight=3;
}

server {
    location / {
        proxy_pass http://backend;
        health_check interval=30s uri=/health;
    }
}
```

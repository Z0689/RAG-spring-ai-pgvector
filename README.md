# RAG 知识库系统 - Spring AI + PGVector

基于 Spring AI、PGVector、Ollama 和 DeepSeek 构建的 RAG（检索增强生成）知识库系统。

## ✨ 功能特性

- 📄 **文档上传**：支持 PDF 文档上传和自动分块
- 🔍 **智能检索**：基于向量相似度检索相关文档片段
- 💬 **AI 问答**：基于检索结果生成高质量回答
- 📊 **来源追溯**：返回答案的引用来源和相似度分数
- 🚀 **高性能**：使用 PGVector 向量索引，毫秒级检索
- 📝 **完善日志**：AOP 切面记录请求耗时，便于监控

## 🛠️ 技术栈
| 组件    | 技术                                      |
|-------|-----------------------------------------|
| 框架    | Spring Boot 3.5.13 + Spring AI 1.0.0-M6 |
| 向量数据库 | PostgreSQL 16 + PGVector                |
| 向量化模型 | Ollama + nomic-embed-text               |
| 对话模型  | DeepSeek API                            |
| 构建工具  | Maven                                   |
| 容器化   | Docker                                  |

## 📋 环境要求

- Java 17+
- Docker Desktop
- Ollama（本地安装）
- DeepSeek API Key

## 🚀 快速开始

### 1. 克隆项目
```bash
git clone https://github.com/你的用户名/rag-spring-ai-pgvector.git
cd rag-spring-ai-pgvector
```

### 2. 启动 PGVector 容器
```bash
docker run --name pgvector -e POSTGRES_PASSWORD=123456 -p 5432:5432 -d pgvector/pgvector:pg16
```

### 3. 拉取 Embedding 模型
```bash
ollama pull nomic-embed-text
```

### 4. 配置 API Key
在 src/main/resources/application.yml 中配置 DeepSeek API Key：
```yaml
spring:
  ai:
    openai:
      api-key: ${DEEPSEEK_API_KEY}
```
或者环境变量：
```bash
export DEEPSEEK_API_KEY=your-api-key
```

### 5. 运行项目
```bash
mvn spring-boot:run
```

### 6. 测试接口
```bash
# 健康检查
curl http://localhost:8080/api/rag/health

# 上传 PDF
curl -X POST http://localhost:8080/api/rag/upload -F "file=@document.pdf"

# 提问
curl -X POST http://localhost:8080/api/rag/ask \
  -H "Content-Type: application/json" \
  -d '{"question": "这篇文章讲了什么？"}'
```

## 📁 项目结构
```text
src/main/java/com/demo/rag_demo/
├── controller/      # API 控制器
├── service/         # 业务逻辑
├── dto/             # 数据传输对象
├── exception/       # 全局异常处理
├── aspect/          # AOP 日志切面
└── config/          # 配置类
```

## 📝 API 接口文档

 | 方法   | 路径              | 描述        | 请求示例                |
 |------|-----------------|-----------|---------------------|
 | POST | /api/rag/upload | 上传 PDF 文档 | form-data: file     |
 | POST | /api/rag/ask    | RAG 问答    | {"question": "..."} |
 | GET  | /api/rag/health | 健康检查      | -                   |
 
### 问答请求示例
```json
{
  "question": "这篇文章主要解决了什么问题？"
}
```

### 问答响应示例
```json
{
  "answer": "这篇文章主要介绍了一个名为 SchedCP 的框架...",
  "sources": [
    {
      "content": "SchedCP is a secure control plane...",
      "score": 0.66,
      "documentName": "paper.pdf"
    }
  ],
  "elapsedMs": 1234
}
```

## ⚙️ 配置说明
主要配置项（application.yml）：
```yaml
spring:
  ai:
    ollama:
      base-url: http://localhost:11434
      embedding:
        model: nomic-embed-text
    
    openai:
      base-url: https://api.deepseek.com
      chat:
        options:
          model: deepseek-chat
    
    vectorstore:
      pgvector:
        dimensions: 768
        index-type: HNSW
        distance-type: COSINE_DISTANCE
```

## 📊 效果展示
### 问答示例
![问答响应示例](./images/img.png)

## 🤝 贡献
欢迎提交 Issue 和 Pull Request。


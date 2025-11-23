# 工作流节点标准输出字段规范

## 重要说明

**此文档定义了所有节点类型的标准输出字段名称和类型，生成工作流JSON时必须严格遵守。**

## 核心原则

1. **字段名称固定**：每种节点类型的输出字段名称是固定的，不可更改
2. **引用时必须使用标准字段名**：其他节点引用时，必须使用此处定义的字段名
3. **end节点引用验证**：end节点的inputsValues必须引用正确的节点ID和标准字段名

---

## 各节点标准输出字段

### 1. start（开始节点）
**输出字段**：根据用户定义的变量动态生成
- 通常包含：`query`（用户输入）
- 可选：`modelName`、`knowledgeBaseId` 等配置参数
- **规则**：字段名称由【提取的变量】中的 `startNodeVariables` 决定

---

### 2. llm（大语言模型节点）
**固定输出字段**：
- `result`（string类型）：LLM生成的文本内容

**引用示例**：
```json
{
  "type": "ref",
  "content": ["llm_1", "result"]
}
```

**❌ 错误示例**：
- `["llm_1", "answer"]` - 字段名错误
- `["llm_1", "response"]` - 字段名错误
- `["llm_1", "output"]` - 字段名错误

---

### 3. knowledge-base（知识库检索节点）
**固定输出字段**：
- `result`（string类型）：检索到的文档内容

**引用示例**：
```json
{
  "type": "ref",
  "content": ["kb_1", "result"]
}
```

---

### 4. intent-recognition（意图识别节点）
**固定输出字段**：
- `intent`（string类型）：识别出的意图分类

**引用示例**：
```json
{
  "type": "ref",
  "content": ["intent_1", "intent"]
}
```

---

### 5. program（Groovy脚本节点）
**固定输出字段**：
- `result`（string类型）：脚本执行结果

**引用示例**：
```json
{
  "type": "ref",
  "content": ["program_1", "result"]
}
```

---

### 6. api（API调用节点）
**固定输出字段**：
- `statusCode`（number类型）：HTTP响应状态码
- `body`（string类型）：HTTP响应体内容

**引用示例**：
```json
{
  "type": "ref",
  "content": ["api_1", "body"]
}
```

---

### 7. agent（Agent节点）
**固定输出字段**：
- `result`（string类型）：Agent返回的处理结果

**引用示例**：
```json
{
  "type": "ref",
  "content": ["agent_1", "result"]
}
```

---

### 8. asr（语音识别节点）
**固定输出字段**：
- `result`（string类型）：识别后的文本

**引用示例**：
```json
{
  "type": "ref",
  "content": ["asr_1", "result"]
}
```

---

### 9. image2text（图像识别节点）
**固定输出字段**：
- `result`（string类型）：图像识别结果描述

**引用示例**：
```json
{
  "type": "ref",
  "content": ["image2text_1", "result"]
}
```

---

### 10. image2detect（图像目标检测节点）
**固定输出字段**：
- `result`（string类型）：目标检测结果（JSON格式）

**引用示例**：
```json
{
  "type": "ref",
  "content": ["image2detect_1", "result"]
}
```

---

### 11. database-query（数据库查询节点）
**固定输出字段**：
- `result`（string类型）：查询结果（JSON格式）

**引用示例**：
```json
{
  "type": "ref",
  "content": ["database-query_1", "result"]
}
```

---

### 12. database-update（数据库更新节点）
**固定输出字段**：
- `result`（string类型）：更新结果（影响行数等）

**引用示例**：
```json
{
  "type": "ref",
  "content": ["database-update_1", "result"]
}
```

---

### 13. translate（翻译节点）
**固定输出字段**：
- `result`（string类型）：翻译后的文本

**引用示例**：
```json
{
  "type": "ref",
  "content": ["translate_1", "result"]
}
```

---

### 14. tts（文本转语音节点）
**固定输出字段**：
- `result`（string类型）：生成的音频文件URL

**引用示例**：
```json
{
  "type": "ref",
  "content": ["tts_1", "result"]
}
```

---

### 15. text2image（文本生成图像节点）
**固定输出字段**：
- `result`（string类型）：生成的图像URL

**引用示例**：
```json
{
  "type": "ref",
  "content": ["text2image_1", "result"]
}
```

---

### 16. ocr（OCR文字识别节点）
**固定输出字段**：
- `result`（string类型）：识别出的文字

**引用示例**：
```json
{
  "type": "ref",
  "content": ["ocr_1", "result"]
}
```

---

### 17. text2video（文本生成视频节点）
**固定输出字段**：
- `result`（string类型）：生成的视频URL

**引用示例**：
```json
{
  "type": "ref",
  "content": ["text2video_1", "result"]
}
```

---

### 18. image2video（图像生成视频节点）
**固定输出字段**：
- `result`（string类型）：生成的视频URL

**引用示例**：
```json
{
  "type": "ref",
  "content": ["image2video_1", "result"]
}
```

---

### 19. image2enhance（图像增强节点）
**固定输出字段**：
- `result`（string类型）：增强后的图像URL

**引用示例**：
```json
{
  "type": "ref",
  "content": ["image2enhance_1", "result"]
}
```

---

### 20. video2track（视频追踪节点）
**固定输出字段**：
- `result`（string类型）：追踪结果（JSON格式）

**引用示例**：
```json
{
  "type": "ref",
  "content": ["video2track_1", "result"]
}
```

---

### 21. video2enhance（视频增强节点）
**固定输出字段**：
- `result`（string类型）：增强后的视频URL

**引用示例**：
```json
{
  "type": "ref",
  "content": ["video2enhance_1", "result"]
}
```

---

### 22. sensitive（敏感词检测节点）
**固定输出字段**：
- `result`（string类型）：检测结果

**引用示例**：
```json
{
  "type": "ref",
  "content": ["sensitive_1", "result"]
}
```

---

### 23. mcp-agent（MCP Agent节点）
**固定输出字段**：
- `result`（string类型）：Agent处理结果

**引用示例**：
```json
{
  "type": "ref",
  "content": ["mcp-agent_1", "result"]
}
```

---

### 24. condition（条件判断节点）
**无固定输出字段**
- condition节点通过不同的sourcePortID（分支key）来区分输出端口
- 后续节点通过边的sourcePortID来指定从哪个分支获取数据
- 不需要在inputsValues中直接引用condition节点的输出

---

### 25. loop（循环节点）
**固定输出字段**：
- `result`（array类型）：循环处理后的结果数组

**引用示例**：
```json
{
  "type": "ref",
  "content": ["loop_1", "result"]
}
```

---

### 26. parallel（并行节点）
**固定输出字段**：
- `result`（array类型）：并行处理后的结果数组

**引用示例**：
```json
{
  "type": "ref",
  "content": ["parallel_1", "result"]
}
```

---

## 引用规则总结

### 规则1：大多数节点输出 `result` 字段
除了以下特殊节点外，所有节点的主要输出字段都是 `result`：
- `intent-recognition` → `intent`
- `api` → `statusCode` 和 `body`
- `start` → 根据变量定义动态生成

### 规则2：引用格式固定
```json
{
  "type": "ref",
  "content": ["节点ID", "字段名"]
}
```

### 规则3：end节点必须使用正确的字段名
end节点的 `data.inputsValues` 中，每个变量的引用必须使用上述定义的标准字段名。

**正确示例**：
```json
"data": {
  "inputsValues": {
    "answer": {
      "type": "ref",
      "content": ["llm_1", "result"]  // ✅ 使用标准字段名 result
    }
  }
}
```

**错误示例**：
```json
"data": {
  "inputsValues": {
    "answer": {
      "type": "ref",
      "content": ["llm_1", "answer"]  // ❌ 字段名错误，应该是 result
    }
  }
}
```

---

## 验证清单

在生成完整工作流JSON后，必须验证：
- [ ] 所有节点的 `data.outputs.properties` 包含正确的字段名
- [ ] 所有 `ref` 类型的引用使用了标准字段名
- [ ] end节点的所有 `inputsValues` 引用都指向存在的节点和正确的字段
- [ ] 没有使用自定义或猜测的字段名


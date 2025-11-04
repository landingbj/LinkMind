sensitive（敏感词过滤节点）
- **功能描述**：对文本进行敏感词过滤
- **输入**：text（要过滤的文本）
- **输出**：result（过滤后的文本）
- **使用场景**：需要对文本进行敏感词过滤的场景
- **注意事项**：text为必填项
============================
sensitive节点（敏感词过滤）

| 字段路径                           | 类型     | 必填 | 规范要求                                                                                                                                                                                                   |
|--------------------------------|--------|----|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| id                             | string | 是  | 遵循1.1规则，如sensitive_1或sensitive_BRQB8                                                                                                                                                                |
| type                           | string | 是  | 固定为"sensitive"                                                                                                                                                                                      |
| meta                           | object | 是  | 含`position`（x≈2300，y≈500，参考demo）                                                                                                                                                                     |
| data                           | object | 是  | 含`title`、`inputsValues`、`inputs`、`outputs`：<br>`title`：默认"敏感词过滤_序号"（如敏感词过滤_1）<br>`inputsValues`：输入参数<br>`inputs`：输入定义（`required`=["text"]）<br>`outputs`：输出定义（`required`=["result"]） |
| data.inputsValues.text         | object | 是  | 要过滤的文本：<br>`type`："ref"（引用其他节点输出）或"constant"<br>`content`：文本内容                                                                                                                           |
| data.outputs.properties.result | object | 是  | 过滤结果：<br>`type`："string" <br>`description` : 过滤后的文本                                                                                                                                       |

#### sensitive节点data示例（参考demo）
```json
{
  "id": "sensitive_mcEW5",
  "type": "sensitive",
  "meta": {
    "position": {
      "x": 640,
      "y": 243
    }
  },
  "data": {
    "title": "敏感词过滤_1",
    "inputsValues": {
      "text": {
        "type": "ref",
        "content": ["llm_0", "result"]
      }
    },
    "inputs": {
      "type": "object",
      "required": ["text"],
      "properties": {
        "text": {
          "type": "string",
          "description": "要过滤的文本"
        }
      }
    },
    "outputs": {
      "type": "object",
      "properties": {
        "result": {
          "type": "string",
          "description": "过滤后的文本"
        }
      },
      "required": ["result"]
    }
  }
}
```


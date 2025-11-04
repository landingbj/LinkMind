translate（翻译节点）
- **功能描述**：将文本翻译成目标语言
- **输入**：text（要翻译的文本）、targetLanguage（目标语言）
- **输出**：result（翻译后的文本）、sourceText（源文本）
- **使用场景**：需要进行文本翻译的场景
- **注意事项**：text和targetLanguage均为必填项，支持的targetLanguage：en/english（英语）、zh/chinese（中文）
============================
translate节点（文本翻译）

| 字段路径                              | 类型     | 必填 | 规范要求                                                                                                                                                                                                      |
|-----------------------------------|--------|----|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| id                                 | string | 是  | 遵循1.1规则，如translate_1或translate_BRQB8                                                                                                                                                                     |
| type                               | string | 是  | 固定为"translate"                                                                                                                                                                                          |
| meta                               | object | 是  | 含`position`（x≈2300，y≈500，参考demo）                                                                                                                                                                        |
| data                               | object | 是  | 含`title`、`inputsValues`、`inputs`、`outputs`：<br>`title`：默认"翻译_序号"（如翻译_1）<br>`inputsValues`：输入参数<br>`inputs`：输入定义（`required`=["text","targetLanguage"]）<br>`outputs`：输出定义（`required`=["result","sourceText"]） |
| data.inputsValues.text             | object | 是  | 要翻译的文本：<br>`type`："ref"（引用其他节点输出）或"constant"<br>`content`：文本内容                                                                                                                             |
| data.inputsValues.targetLanguage   | object | 是  | 目标语言：<br>`type`："constant"（如"en"、"zh"、"english"、"chinese"）或"ref"<br>`content`：目标语言代码                                                                                                           |
| data.outputs.properties.result     | object | 是  | 翻译结果：<br>`type`："string" <br>`description` : 翻译后的文本                                                                                                                                            |
| data.outputs.properties.sourceText | object | 是  | 源文本：<br>`type`："string" <br>`description` : 原始文本                                                                                                                                              |

#### translate节点data示例（参考demo）
```json
{
  "id": "translate_mcEW5",
  "type": "translate",
  "meta": {
    "position": {
      "x": 640,
      "y": 243
    }
  },
  "data": {
    "title": "翻译_1",
    "inputsValues": {
      "text": {
        "type": "ref",
        "content": ["llm_0", "result"]
      },
      "targetLanguage": {
        "type": "constant",
        "content": "en"
      }
    },
    "inputs": {
      "type": "object",
      "required": ["text", "targetLanguage"],
      "properties": {
        "text": {
          "type": "string",
          "description": "要翻译的文本"
        },
        "targetLanguage": {
          "type": "string",
          "description": "目标语言（en/english/zh/chinese）"
        }
      }
    },
    "outputs": {
      "type": "object",
      "properties": {
        "result": {
          "type": "string",
          "description": "翻译后的文本"
        },
        "sourceText": {
          "type": "string",
          "description": "源文本"
        }
      },
      "required": ["result", "sourceText"]
    }
  }
}
```

text2image（文本生成图像节点）
- **功能描述**：根据文本提示生成图像
- **输入**：prompt（文本提示）、model（模型名称，可选）、negative_prompt（负面提示，可选）、style（风格，可选）
- **输出**：result（生成的图像结果对象）
- **使用场景**：需要根据文本生成图像的场景
- **注意事项**：prompt为必填项
============================
text2image节点（文本生成图像）

| 字段路径                               | 类型     | 必填 | 规范要求                                                                                                                                                                                                     |
|------------------------------------|--------|----|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| id                                  | string | 是  | 遵循1.1规则，如text2image_1或text2image_BRQB8                                                                                                                                                                  |
| type                                | string | 是  | 固定为"text2image"                                                                                                                                                                                       |
| meta                                | object | 是  | 含`position`（x≈2300，y≈500，参考demo）                                                                                                                                                                       |
| data                                | object | 是  | 含`title`、`inputsValues`、`inputs`、`outputs`：<br>`title`：默认"文生图_序号"（如文生图_1）<br>`inputsValues`：输入参数<br>`inputs`：输入定义（`required`=["prompt"]）<br>`outputs`：输出定义（`required`=["result"]） |
| data.inputsValues.prompt            | object | 是  | 文本提示词：<br>`type`："ref"（引用其他节点输出）或"constant"<br>`content`：提示词内容                                                                                                                            |
| data.inputsValues.model             | object | 否  | 图像生成模型：<br>`type`："constant"或"ref"<br>`content`：模型名称                                                                                                                                         |
| data.inputsValues.negative_prompt   | object | 否  | 负面提示词：<br>`type`："constant"或"ref"<br>`content`：负面提示内容                                                                                                                                       |
| data.inputsValues.style             | object | 否  | 图像风格：<br>`type`："constant"或"ref"<br>`content`：风格配置                                                                                                                                            |
| data.outputs.properties.result      | object | 是  | 图像生成结果：<br>`type`："object" <br>`description` : 生成的图像信息对象                                                                                                                                   |

#### text2image节点data示例（参考demo）
```json
{
  "id": "text2image_mcEW5",
  "type": "text2image",
  "meta": {
    "position": {
      "x": 640,
      "y": 243
    }
  },
  "data": {
    "title": "文生图_1",
    "inputsValues": {
      "prompt": {
        "type": "ref",
        "content": ["llm_0", "result"]
      },
      "model": {
        "type": "constant",
        "content": "default"
      },
      "negative_prompt": {
        "type": "constant",
        "content": ""
      },
      "style": {
        "type": "constant",
        "content": ""
      }
    },
    "inputs": {
      "type": "object",
      "required": ["prompt"],
      "properties": {
        "prompt": {
          "type": "string",
          "description": "文本提示词"
        },
        "model": {
          "type": "string",
          "description": "图像生成模型"
        },
        "negative_prompt": {
          "type": "string",
          "description": "负面提示词"
        },
        "style": {
          "type": "string",
          "description": "图像风格"
        }
      }
    },
    "outputs": {
      "type": "object",
      "properties": {
        "result": {
          "type": "object",
          "description": "图像生成结果"
        }
      },
      "required": ["result"]
    }
  }
}
```

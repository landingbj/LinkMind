agent（Agent节点）
- **功能描述**：调用Agent进行智能对话和任务处理
- **输入**：query（用户问题）、agent（Agent ID）
- **输出**：result（Agent返回的文本结果）
- **使用场景**：需要使用特定Agent进行任务处理的场景
- **注意事项**：query和agent均为必填项
============================
agent节点（Agent调用）

| 字段路径                      | 类型     | 必填 | 规范要求                                                                                                                                                                                                                              |
|---------------------------|--------|----|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| id                        | string | 是  | 遵循1.1规则，如agent_1或agent_BRQB8                                                                                                                                                                                                      |
| type                      | string | 是  | 固定为"agent"                                                                                                                                                                                                                      |
| meta                      | object | 是  | 含`position`（x≈2300，y≈500，参考demo）                                                                                                                                                                                                  |
| data                      | object | 是  | 含`title`、`inputsValues`、`inputs`、`outputs`：<br>`title`：默认"Agent_序号"（如Agent_1）<br>`inputsValues`：输入参数（query和agent）<br>`inputs`：输入定义（`required`=["query","agent"]）<br>`outputs`：输出定义（`required`=["result"]） |
| data.inputsValues.query   | object | 是  | 用户问题：<br>`type`："template"（模板，含{{变量}}）或"ref"（引用其他节点输出）或"constant"（固定值）<br>`content`：模板内容（如"{{start_0.query}}"）或引用值                                                                                                                           |
| data.inputsValues.agent   | object | 是  | Agent ID：<br>`type`："constant"（固定值，如"1"）或"ref"<br>`content`：Agent ID值                                                                                                                                                        |
| data.outputs.properties.result | object | 是  | Agent返回结果：<br>`type`："string" <br>`description` : Agent处理后的文本结果                                                                                                                                                            |

#### agent节点data示例（参考demo）
```json
{
  "id": "agent_mcEW5",
  "type": "agent",
  "meta": {
    "position": {
      "x": 640,
      "y": 243
    }
  },
  "data": {
    "title": "Agent_1",
    "inputsValues": {
      "query": {
        "type": "template",
        "content": "{{start_0.query}}"
      },
      "agent": {
        "type": "constant",
        "content": "1"
      }
    },
    "inputs": {
      "type": "object",
      "required": ["query", "agent"],
      "properties": {
        "query": {
          "type": "string",
          "extra": {
            "formComponent": "prompt-editor"
          },
          "description": "用户问题"
        },
        "agent": {
          "type": "string",
          "description": "Agent ID"
        }
      }
    },
    "outputs": {
      "type": "object",
      "properties": {
        "result": {
          "type": "string",
          "description": "Agent处理结果"
        }
      },
      "required": ["result"]
    }
  }
}
```

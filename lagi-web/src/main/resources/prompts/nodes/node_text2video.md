text2video（文本生成视频节点）
- **功能描述**：根据文本提示生成视频
- **输入**：prompt（文本提示）、model（模型名称，可选）
- **输出**：result（视频任务响应对象）
- **使用场景**：需要根据文本生成视频的场景
- **注意事项**：prompt为必填项
============================
text2video节点（文本生成视频）

| 字段路径                           | 类型     | 必填 | 规范要求                                                                                                                                                                                                     |
|--------------------------------|--------|----|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| id                             | string | 是  | 遵循1.1规则，如text2video_1或text2video_BRQB8                                                                                                                                                                  |
| type                           | string | 是  | 固定为"text2video"                                                                                                                                                                                       |
| meta                           | object | 是  | 含`position`（x≈2300，y≈500，参考demo）                                                                                                                                                                       |
| data                           | object | 是  | 含`title`、`inputsValues`、`inputs`、`outputs`：<br>`title`：默认"文生视频_序号"（如文生视频_1）<br>`inputsValues`：输入参数<br>`inputs`：输入定义（`required`=["prompt"]）<br>`outputs`：输出定义（`required`=["result"]） |
| data.inputsValues.prompt       | object | 是  | 文本提示词：<br>`type`："ref"（引用其他节点输出）或"constant"<br>`content`：提示词内容                                                                                                                            |
| data.inputsValues.model        | object | 否  | 视频生成模型：<br>`type`："constant"或"ref"<br>`content`：模型名称                                                                                                                                         |
| data.outputs.properties.result | object | 是  | 视频生成结果：<br>`type`："object" <br>`description` : 视频任务响应对象                                                                                                                                   |

#### text2video节点data示例（参考demo）
```json
{
  "id": "text2video_mcEW5",
  "type": "text2video",
  "meta": {
    "position": {
      "x": 640,
      "y": 243
    }
  },
  "data": {
    "title": "文生视频_1",
    "inputsValues": {
      "prompt": {
        "type": "ref",
        "content": ["llm_0", "result"]
      },
      "model": {
        "type": "constant",
        "content": "default"
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
          "description": "视频生成模型"
        }
      }
    },
    "outputs": {
      "type": "object",
      "properties": {
        "result": {
          "type": "object",
          "description": "视频任务响应对象"
        }
      },
      "required": ["result"]
    }
  }
}
```

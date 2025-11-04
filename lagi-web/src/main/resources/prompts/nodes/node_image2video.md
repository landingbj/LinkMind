image2video（图像生成视频节点）
- **功能描述**：根据图像生成视频
- **输入**：imageUrl（图像URL）、model（模型名称，可选）、prompt（文本提示，可选）
- **输出**：result（视频任务响应对象）
- **使用场景**：需要根据图像生成视频的场景
- **注意事项**：imageUrl为必填项
============================
image2video节点（图像生成视频）

| 字段路径                           | 类型     | 必填 | 规范要求                                                                                                                                                                                                     |
|--------------------------------|--------|----|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| id                             | string | 是  | 遵循1.1规则，如image2video_1或image2video_BRQB8                                                                                                                                                               |
| type                           | string | 是  | 固定为"image2video"                                                                                                                                                                                      |
| meta                           | object | 是  | 含`position`（x≈2300，y≈500，参考demo）                                                                                                                                                                       |
| data                           | object | 是  | 含`title`、`inputsValues`、`inputs`、`outputs`：<br>`title`：默认"图生视频_序号"（如图生视频_1）<br>`inputsValues`：输入参数<br>`inputs`：输入定义（`required`=["imageUrl"]）<br>`outputs`：输出定义（`required`=["result"]） |
| data.inputsValues.imageUrl     | object | 是  | 图像URL地址：<br>`type`："constant"或"ref"<br>`content`：图像URL（支持HTTP/HTTPS）                                                                                                                             |
| data.inputsValues.model        | object | 否  | 视频生成模型：<br>`type`："constant"或"ref"<br>`content`：模型名称                                                                                                                                         |
| data.inputsValues.prompt       | object | 否  | 文本提示词：<br>`type`："ref"或"constant"<br>`content`：提示词内容                                                                                                                                        |
| data.outputs.properties.result | object | 是  | 视频生成结果：<br>`type`："object" <br>`description` : 视频任务响应对象                                                                                                                                   |

#### image2video节点data示例（参考demo）
```json
{
  "id": "image2video_mcEW5",
  "type": "image2video",
  "meta": {
    "position": {
      "x": 640,
      "y": 243
    }
  },
  "data": {
    "title": "图生视频_1",
    "inputsValues": {
      "imageUrl": {
        "type": "constant",
        "content": "https://example.com/image.jpg"
      },
      "model": {
        "type": "constant",
        "content": "default"
      },
      "prompt": {
        "type": "constant",
        "content": ""
      }
    },
    "inputs": {
      "type": "object",
      "required": ["imageUrl"],
      "properties": {
        "imageUrl": {
          "type": "string",
          "description": "图像URL地址"
        },
        "model": {
          "type": "string",
          "description": "视频生成模型"
        },
        "prompt": {
          "type": "string",
          "description": "文本提示词"
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


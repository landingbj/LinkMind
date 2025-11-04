video2enhance（视频增强节点）
- **功能描述**：对视频进行增强处理
- **输入**：videoUrl（视频URL）、model（模型名称，可选）
- **输出**：result（视频任务响应对象）
- **使用场景**：需要对视频进行增强的场景
- **注意事项**：videoUrl为必填项
============================
video2enhance节点（视频增强）

| 字段路径                           | 类型     | 必填 | 规范要求                                                                                                                                                                                                     |
|--------------------------------|--------|----|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| id                             | string | 是  | 遵循1.1规则，如video2enhance_1或video2enhance_BRQB8                                                                                                                                                            |
| type                           | string | 是  | 固定为"video2enhance"                                                                                                                                                                                    |
| meta                           | object | 是  | 含`position`（x≈2300，y≈500，参考demo）                                                                                                                                                                       |
| data                           | object | 是  | 含`title`、`inputsValues`、`inputs`、`outputs`：<br>`title`：默认"视频增强_序号"（如视频增强_1）<br>`inputsValues`：输入参数<br>`inputs`：输入定义（`required`=["videoUrl"]）<br>`outputs`：输出定义（`required`=["result"]） |
| data.inputsValues.videoUrl     | object | 是  | 视频URL地址：<br>`type`："constant"或"ref"<br>`content`：视频URL（支持HTTP/HTTPS）                                                                                                                             |
| data.inputsValues.model        | object | 否  | 视频增强模型：<br>`type`："constant"或"ref"<br>`content`：模型名称                                                                                                                                         |
| data.outputs.properties.result | object | 是  | 视频增强结果：<br>`type`："object" <br>`description` : 视频任务响应对象                                                                                                                                   |

#### video2enhance节点data示例（参考demo）
```json
{
  "id": "video2enhance_mcEW5",
  "type": "video2enhance",
  "meta": {
    "position": {
      "x": 640,
      "y": 243
    }
  },
  "data": {
    "title": "视频增强_1",
    "inputsValues": {
      "videoUrl": {
        "type": "constant",
        "content": "https://example.com/video.mp4"
      },
      "model": {
        "type": "constant",
        "content": "default"
      }
    },
    "inputs": {
      "type": "object",
      "required": ["videoUrl"],
      "properties": {
        "videoUrl": {
          "type": "string",
          "description": "视频URL地址"
        },
        "model": {
          "type": "string",
          "description": "视频增强模型"
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



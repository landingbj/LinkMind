image2enhance（图像增强节点）
- **功能描述**：对图像进行增强处理
- **输入**：imageUrl（图像URL）、model（模型名称，可选）
- **输出**：result（增强后的图像结果对象）
- **使用场景**：需要对图像进行增强的场景
- **注意事项**：imageUrl为必填项
============================
image2enhance节点（图像增强）

| 字段路径                           | 类型     | 必填 | 规范要求                                                                                                                                                                                                     |
|--------------------------------|--------|----|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| id                             | string | 是  | 遵循1.1规则，如image2enhance_1或image2enhance_BRQB8                                                                                                                                                             |
| type                           | string | 是  | 固定为"image2enhance"                                                                                                                                                                                     |
| meta                           | object | 是  | 含`position`（x≈2300，y≈500，参考demo）                                                                                                                                                                       |
| data                           | object | 是  | 含`title`、`inputsValues`、`inputs`、`outputs`：<br>`title`：默认"图像增强_序号"（如图像增强_1）<br>`inputsValues`：输入参数<br>`inputs`：输入定义（`required`=["imageUrl"]）<br>`outputs`：输出定义（`required`=["result"]） |
| data.inputsValues.imageUrl     | object | 是  | 图像URL地址：<br>`type`："constant"或"ref"<br>`content`：图像URL（支持HTTP/HTTPS）                                                                                                                             |
| data.inputsValues.model        | object | 否  | 图像增强模型：<br>`type`："constant"或"ref"<br>`content`：模型名称                                                                                                                                         |
| data.outputs.properties.result | object | 是  | 图像增强结果：<br>`type`："object" <br>`description` : 增强后的图像信息对象                                                                                                                                  |

#### image2enhance节点data示例（参考demo）
```json
{
  "id": "image2enhance_mcEW5",
  "type": "image2enhance",
  "meta": {
    "position": {
      "x": 640,
      "y": 243
    }
  },
  "data": {
    "title": "图像增强_1",
    "inputsValues": {
      "imageUrl": {
        "type": "constant",
        "content": "https://example.com/image.jpg"
      },
      "model": {
        "type": "constant",
        "content": "default"
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
          "description": "图像增强模型"
        }
      }
    },
    "outputs": {
      "type": "object",
      "properties": {
        "result": {
          "type": "object",
          "description": "图像增强结果"
        }
      },
      "required": ["result"]
    }
  }
}
```


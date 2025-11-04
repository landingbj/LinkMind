ocr（OCR识别节点）
- **功能描述**：对图像进行OCR文字识别
- **输入**：imageUrl（图像URL）
- **输出**：result（识别的文字列表）
- **使用场景**：需要对图像进行文字识别的场景
- **注意事项**：imageUrl为必填项，支持HTTP/HTTPS URL和本地文件路径
============================
ocr节点（OCR文字识别）

| 字段路径                           | 类型     | 必填 | 规范要求                                                                                                                                                                                                  |
|--------------------------------|--------|----|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| id                             | string | 是  | 遵循1.1规则，如ocr_1或ocr_BRQB8                                                                                                                                                                         |
| type                           | string | 是  | 固定为"ocr"                                                                                                                                                                                            |
| meta                           | object | 是  | 含`position`（x≈2300，y≈500，参考demo）                                                                                                                                                                   |
| data                           | object | 是  | 含`title`、`inputsValues`、`inputs`、`outputs`：<br>`title`：默认"OCR_序号"（如OCR_1）<br>`inputsValues`：输入参数<br>`inputs`：输入定义（`required`=["imageUrl"]）<br>`outputs`：输出定义（`required`=["result"]） |
| data.inputsValues.imageUrl     | object | 是  | 图像URL地址：<br>`type`："constant"或"ref"<br>`content`：图像URL（支持HTTP/HTTPS）或本地文件路径                                                                                                             |
| data.outputs.properties.result | object | 是  | OCR识别结果：<br>`type`："array" <br>`description` : 识别的文字列表                                                                                                                                     |

#### ocr节点data示例（参考demo）
```json
{
  "id": "ocr_mcEW5",
  "type": "ocr",
  "meta": {
    "position": {
      "x": 640,
      "y": 243
    }
  },
  "data": {
    "title": "OCR_1",
    "inputsValues": {
      "imageUrl": {
        "type": "constant",
        "content": "https://example.com/image.jpg"
      }
    },
    "inputs": {
      "type": "object",
      "required": ["imageUrl"],
      "properties": {
        "imageUrl": {
          "type": "string",
          "description": "图像URL地址或本地文件路径"
        }
      }
    },
    "outputs": {
      "type": "object",
      "properties": {
        "result": {
          "type": "array",
          "description": "OCR识别的文字列表"
        }
      },
      "required": ["result"]
    }
  }
}
```


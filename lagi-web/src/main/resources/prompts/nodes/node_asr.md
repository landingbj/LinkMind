asr（语音识别节点）
- **功能描述**：对语音进行识别，返回识别文字结果。
- **输入**：audioUrl (语音文件url地址),
- **输出**：result (识别出的文字结果)
- **使用场景**：需要对语音进行识别的场景, 优先级高于api调用。
- **注意事项**： audioUrl 为必填项。
===========================
语音识别节点（语音识别）

| 字段路径                               | 类型     | 必填 | 规范要求                                                                                                                                                                                                                                                                                                                                                                                      |
|------------------------------------|--------|----|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| id                                 | string | 是  | 遵循1.1规则，如asr_1或asr_BRQB8                                                                                                                                                                                                                                                                                                                                                                  |
| type                               | string | 是  | 固定为"asr"                                                                                                                                                                                                                                                                                                                                                                                  |
| meta                               | object | 是  | 含`position`（x≈2300，y≈500，参考demo）                                                                                                                                                                                                                                                                                                                                                          |
| data                               | object | 是  | 含`title`、`inputsValues`、`inputs`、`outputs`：<br>`title`：默认"API_序号"（API_1）<br>`inputsValues`：输入参数（url,method,header,body）<br>`inputs`：输入定义（`required`=["audioUrl,model,sample_rate"]）<br>`outputs`：输出定义（`required`=["result"]）                                                                                                                                                              |
| data.inputs                        | object | 是  | 输入的属性定义,该节点为固定的结构，不可修改, object : { "type": "object", "required": [ "audioUrl" ], "properties": { "audioUrl": { "type": "string", "description": "音频文件URL（支持HTTP/HTTPS）" }, "model": { "type": "string", "description": "语音识别模型名称（可选）" }, "format": { "type": "string", "description": "音频格式（如wav、mp3等，可选）" }, "sample_rate": { "type": "number", "description": "音频采样率（如16000，可选）" } } }  |
| data.inputsValues.audioUrl         | object | 是  | audioUrl请求的url地址：<br>`type`："constant"（固定"http://xxx.xxx/xxx.wav"）或"ref"<br>`content`：引用值                                                                                                                                                                                                                                                                                                 |
| data.inputsValues.model            | object | 否  | 识别的模型名字：<br>`type`："constant"（默认为空字符串""）或"ref"<br>`content`：或引用值                                                                                                                                                                                                                                                                                                                          |
| data.inputsValues.sample_rate      | number | 否  | 采样率：<br>`type`："constant"（默认为16000）或"ref"<br>`content`：引用值）                                                                                                                                                                                                                                                                                                                               |
| data.outputs.properties.result     | object | 是  | 识别结果：<br>`type`："number" <br>`description` : 识别结果                                                                                                                                                                                                                                                                                                                                         |

#### api节点data示例（参考demo）
```json
{
  "id": "asr_mcEW5",
  "type": "asr",
  "meta": {
    "position": {
      "x": 640,
      "y": 243
    }
  },
  "data": {
    "title": "ASR识别_1",
    "inputsValues": {
      "audioUrl": {
        "type": "constant",
        "content": "https://example.com/audio.wav"
      },
      "model": {
        "type": "constant",
        "content": ""
      },
      "format": {
        "type": "constant",
        "content": "wav"
      },
      "sample_rate": {
        "type": "constant",
        "content": 16000
      }
    },
    "inputs": {
      "type": "object",
      "required": [
        "audioUrl"
      ],
      "properties": {
        "audioUrl": {
          "type": "string",
          "description": "音频文件URL（支持HTTP/HTTPS）"
        },
        "model": {
          "type": "string",
          "description": "语音识别模型名称（可选）"
        },
        "format": {
          "type": "string",
          "description": "音频格式（如wav、mp3等，可选）"
        },
        "sample_rate": {
          "type": "number",
          "description": "音频采样率（如16000，可选）"
        }
      }
    },
    "outputs": {
      "type": "object",
      "properties": {
        "result": {
          "type": "object",
          "description": "语音识别结果，包含识别文本及相关信息"
        }
      },
      "required": [
        "result"
      ]
    }
  }
}
```

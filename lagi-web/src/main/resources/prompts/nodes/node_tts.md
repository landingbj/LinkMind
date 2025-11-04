tts（语音合成节点）
- **功能描述**：将文本转换为语音
- **输入**：text（要合成的文本）、model（模型名称，可选）、voice（音色，可选）、volume（音量，可选）、format（格式，可选）
- **输出**：result（合成的语音URL或音频数据）
- **使用场景**：需要将文本转换为语音的场景
- **注意事项**：text为必填项
============================
tts节点（语音合成）

| 字段路径                           | 类型     | 必填 | 规范要求                                                                                                                                                                                                      |
|--------------------------------|--------|----|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| id                             | string | 是  | 遵循1.1规则，如tts_1或tts_BRQB8                                                                                                                                                                                |
| type                           | string | 是  | 固定为"tts"                                                                                                                                                                                                |
| meta                           | object | 是  | 含`position`（x≈2300，y≈500，参考demo）                                                                                                                                                                        |
| data                           | object | 是  | 含`title`、`inputsValues`、`inputs`、`outputs`：<br>`title`：默认"TTS_序号"（如TTS_1）<br>`inputsValues`：输入参数<br>`inputs`：输入定义（`required`=["text"]）<br>`outputs`：输出定义（`required`=["result"]） |
| data.inputsValues.text         | object | 是  | 要合成的文本：<br>`type`："ref"（引用其他节点输出）或"constant"<br>`content`：文本内容                                                                                                                             |
| data.inputsValues.model        | object | 否  | 语音合成模型：<br>`type`："constant"或"ref"<br>`content`：模型名称                                                                                                                                           |
| data.inputsValues.voice        | object | 否  | 音色配置：<br>`type`："constant"或"ref"<br>`content`：音色名称                                                                                                                                              |
| data.inputsValues.volume       | object | 否  | 音量配置：<br>`type`："constant"或"ref"<br>`content`：音量值（0-100）                                                                                                                                        |
| data.inputsValues.format       | object | 否  | 音频格式：<br>`type`："constant"或"ref"<br>`content`：格式（如mp3、wav等）                                                                                                                                     |
| data.outputs.properties.result | object | 是  | 语音合成结果：<br>`type`："string" <br>`description` : 合成的音频URL或音频数据                                                                                                                                  |

#### tts节点data示例（参考demo）
```json
{
  "id": "tts_mcEW5",
  "type": "tts",
  "meta": {
    "position": {
      "x": 640,
      "y": 243
    }
  },
  "data": {
    "title": "TTS_1",
    "inputsValues": {
      "text": {
        "type": "ref",
        "content": ["llm_0", "result"]
      },
      "model": {
        "type": "constant",
        "content": "default"
      },
      "voice": {
        "type": "constant",
        "content": ""
      },
      "volume": {
        "type": "constant",
        "content": 50
      },
      "format": {
        "type": "constant",
        "content": "mp3"
      }
    },
    "inputs": {
      "type": "object",
      "required": ["text"],
      "properties": {
        "text": {
          "type": "string",
          "description": "要合成的文本"
        },
        "model": {
          "type": "string",
          "description": "语音合成模型"
        },
        "voice": {
          "type": "string",
          "description": "音色"
        },
        "volume": {
          "type": "number",
          "description": "音量"
        },
        "format": {
          "type": "string",
          "description": "音频格式"
        }
      }
    },
    "outputs": {
      "type": "object",
      "properties": {
        "result": {
          "type": "string",
          "description": "语音合成结果"
        }
      },
      "required": ["result"]
    }
  }
}
```


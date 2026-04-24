---
title: 默认模块
language_tabs:
  - shell: Shell
  - http: HTTP
  - javascript: JavaScript
  - ruby: Ruby
  - python: Python
  - php: PHP
  - java: Java
  - go: Go
toc_footers: []
includes: []
search: true
code_clipboard: true
highlight_theme: darkula
headingLevel: 2
generator: "@tarslib/widdershins v4.0.30"


---

# 默认模块

Base URLs:

# Authentication

# 文本

## POST 推理接口

POST /v1/chat/completions

OpenAI 兼容的对话补全接口，用于统一调用文本模型并返回标准聊天结果。

> Body 请求参数

```json
{
  "model": "qwen-plus",
  "stream": false,
  "max_completion_tokens": 256,
  "messages": [
    {
      "role": "user",
      "content": "Hello, LinkMind."
    }
  ]
}
```

### 请求参数

| 名称                         | 位置   | 类型     | 必选 | 说明                                                         |
| ---------------------------- | ------ | -------- | ---- | ------------------------------------------------------------ |
| Content-Type                 | header | string   | 否   | 请求体内容类型。                                             |
| Authorization                | header | string   | 是   | Bearer 令牌，用于调用需要鉴权的接口。                        |
| body                         | body   | object   | 否   | none                                                         |
| » model                      | body   | string   | 是   | 模型类型                                                     |
| » messages                   | body   | [object] | 是   | 提交的消息列表                                               |
| »» role                      | body   | string   | 是   | user或者assistant, user表示用户提交，assistant表示大模型输出 |
| »» content                   | body   | [object] | 是   | 如果role是user，则context是用户输入的内容吗， 如果role是assistant，则context是大模型的输出内容 |
| »»» type                     | body   | string   | 否   | 内容项类型。                                                 |
| »»» text                     | body   | string   | 否   | 文本内容。                                                   |
| » stream                     | body   | boolean  | 是   | 是否使用流式返回。                                           |
| » store                      | body   | boolean  | 是   | 是否保存本次调用结果。                                       |
| » max_completion_tokens      | body   | integer  | 是   | 本次生成允许的最大输出 token 数。                            |
| » tools                      | body   | [object] | 是   | 可选工具定义列表。                                           |
| »» type                      | body   | string   | 是   | 工具类型。                                                   |
| »» function                  | body   | object   | 是   | 工具函数定义。                                               |
| »»» name                     | body   | string   | 是   | 函数名称。                                                   |
| »»» description              | body   | string   | 是   | 函数说明。                                                   |
| »»» parameters               | body   | object   | 是   | 函数参数定义。                                               |
| »»»» type                    | body   | string   | 是   | 字段类型。                                                   |
| »»»» required                | body   | [string] | 是   | none                                                         |
| »»»» properties              | body   | object   | 是   | none                                                         |
| »»»»» path                   | body   | object   | 是   | none                                                         |
| »»»»»» description           | body   | string   | 否   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» offset                 | body   | object   | 否   | 结果偏移量。                                                 |
| »»»»»» description           | body   | string   | 是   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» limit                  | body   | object   | 否   | 返回记录数上限。                                             |
| »»»»»» description           | body   | string   | 否   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»»» minimum               | body   | integer  | 是   | none                                                         |
| »»»»» file_path              | body   | object   | 否   | none                                                         |
| »»»»»» description           | body   | string   | 是   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» filePath               | body   | object   | 否   | none                                                         |
| »»»»»» description           | body   | string   | 是   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» file                   | body   | object   | 否   | none                                                         |
| »»»»»» description           | body   | string   | 是   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» oldText                | body   | object   | 否   | none                                                         |
| »»»»»» description           | body   | string   | 是   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» newText                | body   | object   | 否   | none                                                         |
| »»»»»» description           | body   | string   | 是   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» edits                  | body   | object   | 否   | none                                                         |
| »»»»»» description           | body   | string   | 是   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»»» items                 | body   | object   | 是   | none                                                         |
| »»»»»»» additionalProperties | body   | boolean  | 是   | none                                                         |
| »»»»»»» type                 | body   | string   | 是   | 字段类型。                                                   |
| »»»»»»» required             | body   | [string] | 是   | none                                                         |
| »»»»»»» properties           | body   | object   | 是   | none                                                         |
| »»»»»»»» oldText             | body   | object   | 是   | none                                                         |
| »»»»»»»»» description        | body   | string   | 是   | none                                                         |
| »»»»»»»»» type               | body   | string   | 是   | 字段类型。                                                   |
| »»»»»»»» newText             | body   | object   | 是   | none                                                         |
| »»»»»»»»» description        | body   | string   | 是   | none                                                         |
| »»»»»»»»» type               | body   | string   | 是   | 字段类型。                                                   |
| »»»»» old_string             | body   | object   | 否   | none                                                         |
| »»»»»» description           | body   | string   | 是   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» old_text               | body   | object   | 否   | none                                                         |
| »»»»»» description           | body   | string   | 是   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» oldString              | body   | object   | 否   | none                                                         |
| »»»»»» description           | body   | string   | 是   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» new_string             | body   | object   | 否   | none                                                         |
| »»»»»» description           | body   | string   | 是   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» new_text               | body   | object   | 否   | none                                                         |
| »»»»»» description           | body   | string   | 是   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» newString              | body   | object   | 否   | none                                                         |
| »»»»»» description           | body   | string   | 是   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» content                | body   | object   | 否   | 消息内容。                                                   |
| »»»»»» description           | body   | string   | 是   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» command                | body   | object   | 否   | none                                                         |
| »»»»»» description           | body   | string   | 是   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» workdir                | body   | object   | 否   | none                                                         |
| »»»»»» description           | body   | string   | 是   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» env                    | body   | object   | 否   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»»» patternProperties     | body   | object   | 是   | none                                                         |
| »»»»»»» ^(.*)$               | body   | object   | 是   | none                                                         |
| »»»»»»»» type                | body   | string   | 是   | 字段类型。                                                   |
| »»»»» yieldMs                | body   | object   | 否   | none                                                         |
| »»»»»» description           | body   | string   | 是   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» background             | body   | object   | 否   | none                                                         |
| »»»»»» description           | body   | string   | 是   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» timeout                | body   | object   | 否   | none                                                         |
| »»»»»» description           | body   | string   | 是   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»»» minimum               | body   | integer  | 是   | none                                                         |
| »»»»» pty                    | body   | object   | 否   | none                                                         |
| »»»»»» description           | body   | string   | 是   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» elevated               | body   | object   | 否   | none                                                         |
| »»»»»» description           | body   | string   | 是   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» host                   | body   | object   | 否   | none                                                         |
| »»»»»» description           | body   | string   | 是   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» security               | body   | object   | 否   | none                                                         |
| »»»»»» description           | body   | string   | 是   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» ask                    | body   | object   | 否   | none                                                         |
| »»»»»» description           | body   | string   | 是   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» node                   | body   | object   | 否   | none                                                         |
| »»»»»» description           | body   | string   | 是   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» action                 | body   | object   | 否   | none                                                         |
| »»»»»» description           | body   | string   | 否   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»»» enum                  | body   | [string] | 是   | none                                                         |
| »»»»» sessionId              | body   | object   | 否   | none                                                         |
| »»»»»» description           | body   | string   | 是   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» data                   | body   | object   | 否   | 接口返回数据。                                               |
| »»»»»» description           | body   | string   | 是   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» keys                   | body   | object   | 否   | none                                                         |
| »»»»»» description           | body   | string   | 是   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»»» items                 | body   | object   | 是   | none                                                         |
| »»»»»»» type                 | body   | string   | 是   | 字段类型。                                                   |
| »»»»» hex                    | body   | object   | 否   | none                                                         |
| »»»»»» description           | body   | string   | 是   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»»» items                 | body   | object   | 是   | none                                                         |
| »»»»»»» type                 | body   | string   | 是   | 字段类型。                                                   |
| »»»»» literal                | body   | object   | 否   | none                                                         |
| »»»»»» description           | body   | string   | 是   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» text                   | body   | object   | 否   | 输入文本。                                                   |
| »»»»»» description           | body   | string   | 否   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» bracketed              | body   | object   | 否   | none                                                         |
| »»»»»» description           | body   | string   | 是   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» eof                    | body   | object   | 否   | none                                                         |
| »»»»»» description           | body   | string   | 是   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» gatewayUrl             | body   | object   | 否   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» gatewayToken           | body   | object   | 否   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» timeoutMs              | body   | object   | 否   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» includeDisabled        | body   | object   | 否   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» job                    | body   | object   | 否   | none                                                         |
| »»»»»» additionalProperties  | body   | boolean  | 是   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»»» properties            | body   | object   | 是   | none                                                         |
| »»»»» jobId                  | body   | object   | 否   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» id                     | body   | object   | 否   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» patch                  | body   | object   | 否   | none                                                         |
| »»»»»» additionalProperties  | body   | boolean  | 是   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»»» properties            | body   | object   | 是   | none                                                         |
| »»»»» mode                   | body   | object   | 否   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»»» enum                  | body   | [string] | 是   | none                                                         |
| »»»»» runMode                | body   | object   | 否   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»»» enum                  | body   | [string] | 是   | none                                                         |
| »»»»» contextMessages        | body   | object   | 否   | none                                                         |
| »»»»»» minimum               | body   | integer  | 是   | none                                                         |
| »»»»»» maximum               | body   | integer  | 是   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» kinds                  | body   | object   | 否   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»»» items                 | body   | object   | 是   | none                                                         |
| »»»»»»» type                 | body   | string   | 是   | 字段类型。                                                   |
| »»»»» activeMinutes          | body   | object   | 否   | none                                                         |
| »»»»»» minimum               | body   | integer  | 是   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» messageLimit           | body   | object   | 否   | none                                                         |
| »»»»»» minimum               | body   | integer  | 是   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» sessionKey             | body   | object   | 否   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» includeTools           | body   | object   | 否   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» label                  | body   | object   | 否   | none                                                         |
| »»»»»» minLength             | body   | integer  | 否   | none                                                         |
| »»»»»» maxLength             | body   | integer  | 否   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» agentId                | body   | object   | 否   | 目标 Agent 标识。                                            |
| »»»»»» minLength             | body   | integer  | 否   | none                                                         |
| »»»»»» maxLength             | body   | integer  | 否   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» message                | body   | object   | 否   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» timeoutSeconds         | body   | object   | 否   | none                                                         |
| »»»»»» minimum               | body   | integer  | 是   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» task                   | body   | object   | 否   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» runtime                | body   | object   | 否   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»»» enum                  | body   | [string] | 是   | none                                                         |
| »»»»» resumeSessionId        | body   | object   | 否   | none                                                         |
| »»»»»» description           | body   | string   | 是   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» model                  | body   | object   | 否   | 调用时使用的模型名称。                                       |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» thinking               | body   | object   | 否   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» cwd                    | body   | object   | 否   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» runTimeoutSeconds      | body   | object   | 否   | none                                                         |
| »»»»»» minimum               | body   | integer  | 是   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» thread                 | body   | object   | 否   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» cleanup                | body   | object   | 否   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»»» enum                  | body   | [string] | 是   | none                                                         |
| »»»»» sandbox                | body   | object   | 否   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»»» enum                  | body   | [string] | 是   | none                                                         |
| »»»»» streamTo               | body   | object   | 否   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»»» enum                  | body   | [string] | 是   | none                                                         |
| »»»»» attachments            | body   | object   | 否   | none                                                         |
| »»»»»» maxItems              | body   | integer  | 是   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»»» items                 | body   | object   | 是   | none                                                         |
| »»»»»»» type                 | body   | string   | 是   | 字段类型。                                                   |
| »»»»»»» required             | body   | [string] | 是   | none                                                         |
| »»»»»»» properties           | body   | object   | 是   | none                                                         |
| »»»»»»»» name                | body   | object   | 是   | none                                                         |
| »»»»»»»»» type               | body   | string   | 是   | 字段类型。                                                   |
| »»»»»»»» content             | body   | object   | 是   | 消息内容。                                                   |
| »»»»»»»»» type               | body   | string   | 是   | 字段类型。                                                   |
| »»»»»»»» encoding            | body   | object   | 是   | none                                                         |
| »»»»»»»»» type               | body   | string   | 是   | 字段类型。                                                   |
| »»»»»»»»» enum               | body   | [string] | 是   | none                                                         |
| »»»»»»»» mimeType            | body   | object   | 是   | none                                                         |
| »»»»»»»»» type               | body   | string   | 是   | 字段类型。                                                   |
| »»»»» attachAs               | body   | object   | 否   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»»» properties            | body   | object   | 是   | none                                                         |
| »»»»»»» mountPath            | body   | object   | 是   | none                                                         |
| »»»»»»»» type                | body   | string   | 是   | 字段类型。                                                   |
| »»»»» target                 | body   | object   | 否   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» recentMinutes          | body   | object   | 否   | none                                                         |
| »»»»»» minimum               | body   | integer  | 是   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» query                  | body   | object   | 否   | 查询条件。                                                   |
| »»»»»» description           | body   | string   | 否   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» count                  | body   | object   | 否   | none                                                         |
| »»»»»» description           | body   | string   | 是   | none                                                         |
| »»»»»» minimum               | body   | integer  | 是   | none                                                         |
| »»»»»» maximum               | body   | integer  | 是   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» region                 | body   | object   | 否   | none                                                         |
| »»»»»» description           | body   | string   | 是   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» safeSearch             | body   | object   | 否   | none                                                         |
| »»»»»» description           | body   | string   | 是   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» url                    | body   | object   | 否   | none                                                         |
| »»»»»» description           | body   | string   | 是   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» extractMode            | body   | object   | 否   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»»» enum                  | body   | [string] | 是   | none                                                         |
| »»»»»» description           | body   | string   | 是   | none                                                         |
| »»»»»» default               | body   | string   | 是   | none                                                         |
| »»»»» maxChars               | body   | object   | 否   | none                                                         |
| »»»»»» description           | body   | string   | 是   | none                                                         |
| »»»»»» minimum               | body   | integer  | 是   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» maxResults             | body   | object   | 否   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» minScore               | body   | object   | 否   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» from                   | body   | object   | 是   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»»» lines                  | body   | object   | 是   | none                                                         |
| »»»»»» type                  | body   | string   | 是   | 字段类型。                                                   |
| »»»» additionalProperties    | body   | boolean  | 否   | none                                                         |

> 返回示例

> 200 Response

```json
{
  "id": "chatcmpl-example-001",
  "object": "chat.completion",
  "created": 1700000000,
  "model": "qwen-plus",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "Hello! How can I help you today?"
      },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 12,
    "completion_tokens": 10,
    "total_tokens": 22
  }
}
```

### 返回结果

| 状态码 | 状态码含义                                              | 说明       | 数据模型 |
| ------ | ------------------------------------------------------- | ---------- | -------- |
| 200    | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | 成功响应。 | Inline   |

### 返回数据结构

状态码 **200**

| 名称                 | 类型     | 必选  | 约束 | 中文名 | 说明                                                         |
| -------------------- | -------- | ----- | ---- | ------ | ------------------------------------------------------------ |
| » id                 | string   | true  | none |        | 唯一标识符                                                   |
| » object             | string   | true  | none |        | 对象类型                                                     |
| » created            | integer  | true  | none |        | 聊天完成创建时的Unix时间戳（秒）                             |
| » model              | string   | true  | none |        | 使用的模型                                                   |
| » choices            | [object] | true  | none |        | 选择的列表                                                   |
| »» index             | integer  | false | none |        | 对象的索引                                                   |
| »» message           | object   | false | none |        | 返回的消息                                                   |
| »»» role             | string   | true  | none |        | user或者assistant, user表示用户提交，assistant表示大模型输出 |
| »»» content          | string   | true  | none |        | 如果role是user，则context是用户输入的内容吗， 如果role是assistant，则context是大模型的输出内容 |
| »» finish_reason     | string   | false | none |        | 模型停止生成的原因                                           |
| » usage              | object   | true  | none |        | 完成请求的使用统计                                           |
| »» prompt_tokens     | integer  | true  | none |        | 提示中的令牌数量。                                           |
| »» completion_tokens | integer  | true  | none |        | 生成的令牌数量                                               |
| »» total_tokens      | integer  | true  | none |        | 请求中使用的总令牌数量                                       |

## POST chat/go

POST /chat/go

Agent / Worker 统一调用入口，用于通过指定 worker 执行编排后的对话请求。

> Body 请求参数

```json
{
  "category": "default",
  "worker": "appointedWorker",
  "stream": false,
  "temperature": 0.7,
  "max_tokens": 256,
  "messages": [
    {
      "role": "user",
      "content": "Translate \"Hello\" into Chinese."
    }
  ]
}
```

### 请求参数

| 名称          | 位置   | 类型     | 必选 | 说明                                                         |
| ------------- | ------ | -------- | ---- | ------------------------------------------------------------ |
| Content-Type  | header | string   | 否   | 请求体内容类型。                                             |
| Authorization | header | string   | 是   | Bearer 令牌，用于调用需要鉴权的接口。                        |
| body          | body   | object   | 否   | none                                                         |
| » model       | body   | string   | 否   | 模型类型                                                     |
| » temperature | body   | number   | 是   | 使用什么样的采样温度                                         |
| » max_tokens  | body   | integer  | 是   | 可以生成的最大token数。                                      |
| » category    | body   | string   | 否   | 数据类别                                                     |
| » messages    | body   | [object] | 是   | 提交的消息列表                                               |
| »» role       | body   | string   | 否   | user或者assistant, user表示用户提交，assistant表示大模型输出 |
| »» content    | body   | string   | 否   | 如果role是user，则context是用户输入的内容吗， 如果role是assistant，则context是大模型的输出内容 |
| » worker      | body   | string   | 否   | 调用的 worker 名称。                                         |
| » agentId     | body   | string   | 否   | 调用的 Agent 标识。                                          |
| » stream      | body   | boolean  | 否   | 是否使用流式返回。                                           |

> 返回示例

> 200 Response

```json
{
  "id": "chatcmpl-worker-001",
  "object": "chat.completion",
  "created": 1700000000,
  "model": "qwen-plus",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "LinkMind can help route and execute your request."
      },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 14,
    "completion_tokens": 2,
    "total_tokens": 16
  }
}
```

### 返回结果

| 状态码 | 状态码含义                                              | 说明       | 数据模型 |
| ------ | ------------------------------------------------------- | ---------- | -------- |
| 200    | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | 成功响应。 | Inline   |

### 返回数据结构

状态码 **200**

| 名称                 | 类型     | 必选  | 约束 | 中文名 | 说明                                                         |
| -------------------- | -------- | ----- | ---- | ------ | ------------------------------------------------------------ |
| » id                 | string   | true  | none |        | 唯一标识符                                                   |
| » object             | string   | true  | none |        | 对象类型                                                     |
| » created            | integer  | true  | none |        | 聊天完成创建时的Unix时间戳（秒）                             |
| » model              | string   | true  | none |        | 使用的模型                                                   |
| » choices            | [object] | true  | none |        | 选择的列表                                                   |
| »» index             | integer  | false | none |        | 对象的索引                                                   |
| »» message           | object   | false | none |        | 返回的消息                                                   |
| »»» role             | string   | true  | none |        | user或者assistant, user表示用户提交，assistant表示大模型输出 |
| »»» content          | string   | true  | none |        | 如果role是user，则context是用户输入的内容吗， 如果role是assistant，则context是大模型的输出内容 |
| »» finish_reason     | string   | false | none |        | 模型停止生成的原因                                           |
| » usage              | object   | true  | none |        | 完成请求的使用统计                                           |
| »» prompt_tokens     | integer  | true  | none |        | 提示中的令牌数量。                                           |
| »» completion_tokens | integer  | true  | none |        | 生成的令牌数量                                               |
| »» total_tokens      | integer  | true  | none |        | 请求中使用的总令牌数量                                       |

# 语音

## POST 语音识别

POST /audio/speech2text

语音转文本接口，上传音频二进制流后返回识别结果。

> Body 请求参数

```yaml
file://./examples/audio/sample.wav

```

### 请求参数

| 名称         | 位置   | 类型           | 必选 | 说明                   |
| ------------ | ------ | -------------- | ---- | ---------------------- |
| model        | query  | string         | 否   | 调用时使用的模型名称。 |
| format       | query  | string         | 否   | 返回结果格式。         |
| Content-Type | header | string         | 否   | 请求体内容类型。       |
| body         | body   | string(binary) | 否   | none                   |

> 返回示例

> 200 Response

```json
{
  "result": "Hello LinkMind",
  "status": 200
}
```

### 返回结果

| 状态码 | 状态码含义                                              | 说明       | 数据模型 |
| ------ | ------------------------------------------------------- | ---------- | -------- |
| 200    | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | 成功响应。 | Inline   |

### 返回数据结构

状态码 **200**

| 名称     | 类型    | 必选 | 约束 | 中文名 | 说明           |
| -------- | ------- | ---- | ---- | ------ | -------------- |
| » result | string  | true | none |        | 语音识别结果。 |
| » status | integer | true | none |        | 服务状态码。   |

## GET 文字转语音

GET /audio/text2speech

文本转语音接口，根据查询参数生成音频流响应。

### 请求参数

| 名称   | 位置  | 类型   | 必选 | 说明                   |
| ------ | ----- | ------ | ---- | ---------------------- |
| text   | query | string | 是   | 待合成的文本           |
| model  | query | string | 否   | 调用时使用的模型名称。 |
| format | query | string | 否   | 返回结果格式。         |

> 返回示例

> 200 Response

### 返回结果

| 状态码 | 状态码含义                                              | 说明                     | 数据模型 |
| ------ | ------------------------------------------------------- | ------------------------ | -------- |
| 200    | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | 成功时返回音频二进制流。 | Inline   |

### 返回数据结构

# 图片

## POST 看图说话

POST /image/image2text

图像理解接口，上传图片后返回分类、描述和分割结果。

> Body 请求参数

```yaml
model: default
emotion: default
text: Please describe the image.
category: default

```

### 请求参数

| 名称   | 位置 | 类型           | 必选 | 说明             |
| ------ | ---- | -------------- | ---- | ---------------- |
| body   | body | object         | 否   | none             |
| » file | body | string(binary) | 是   | 所上传的图片文件 |

> 返回示例

> 200 Response

```json
{
  "status": "success",
  "classification": "cat",
  "caption": "A cat sitting on a sofa.",
  "samUrl": "https://example.com/segmented-image.png"
}
```

### 返回结果

| 状态码 | 状态码含义                                              | 说明       | 数据模型 |
| ------ | ------------------------------------------------------- | ---------- | -------- |
| 200    | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | 成功响应。 | Inline   |

### 返回数据结构

状态码 **200**

| 名称             | 类型   | 必选 | 约束 | 中文名 | 说明               |
| ---------------- | ------ | ---- | ---- | ------ | ------------------ |
| » status         | string | true | none |        | 看图说话的调用状态 |
| » classification | string | true | none |        | 识别出图片的类别   |
| » caption        | string | true | none |        | 返回图片的识别结果 |
| » samUrl         | string | true | none |        | 上传图片的切割结果 |

## POST 图片生成

POST /image/text2image

文生图接口，根据提示词生成图片。

> Body 请求参数

```json
{
  "prompt": "A cat sitting on a sofa."
}
```

### 请求参数

| 名称         | 位置   | 类型   | 必选 | 说明             |
| ------------ | ------ | ------ | ---- | ---------------- |
| Content-Type | header | string | 否   | 请求体内容类型。 |
| body         | body   | object | 否   | none             |
| » prompt     | body   | string | 是   | 生成图片的指令   |

> 返回示例

> 200 Response

```json
{
  "created": 1700000000,
  "data": [
    {
      "url": "https://example.com/generated-image.png"
    }
  ]
}
```

### 返回结果

| 状态码 | 状态码含义                                              | 说明       | 数据模型 |
| ------ | ------------------------------------------------------- | ---------- | -------- |
| 200    | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | 成功响应。 | Inline   |

### 返回数据结构

状态码 **200**

| 名称      | 类型     | 必选  | 约束 | 中文名 | 说明                             |
| --------- | -------- | ----- | ---- | ------ | -------------------------------- |
| » created | integer  | true  | none |        | 聊天完成创建时的Unix时间戳（秒） |
| » data    | [object] | true  | none |        | 生成的图片数据                   |
| »» url    | string   | false | none |        | 生成的图片地址                   |

## POST 图像增强

POST /image/image2enhance

图像增强接口，上传图片后返回增强结果地址。

> Body 请求参数

```yaml
model: default
emotion: default
text: Please enhance this image.
category: default

```

### 请求参数

| 名称   | 位置 | 类型           | 必选 | 说明           |
| ------ | ---- | -------------- | ---- | -------------- |
| body   | body | object         | 否   | none           |
| » file | body | string(binary) | 是   | 上传的图片文件 |

> 返回示例

> 200 Response

```json
{
  "status": "success",
  "enhanceImageUrl": "https://example.com/enhanced-image.png"
}
```

### 返回结果

| 状态码 | 状态码含义                                              | 说明       | 数据模型 |
| ------ | ------------------------------------------------------- | ---------- | -------- |
| 200    | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | 成功响应。 | Inline   |

### 返回数据结构

状态码 **200**

| 名称              | 类型   | 必选 | 约束 | 中文名 | 说明                             |
| ----------------- | ------ | ---- | ---- | ------ | -------------------------------- |
| » enhanceImageUrl | string | true | none |        | 增强后的图片地址。               |
| » status          | string | true | none |        | 接口执行状态，success 表示成功。 |

# 视频

## POST 视频生成

POST /image/image2video

图生视频接口，上传图片后返回生成视频地址。

> Body 请求参数

```yaml
model: default
emotion: default
text: Generate a short video from this image.
category: default

```

### 请求参数

| 名称   | 位置 | 类型           | 必选 | 说明           |
| ------ | ---- | -------------- | ---- | -------------- |
| body   | body | object         | 否   | none           |
| » file | body | string(binary) | 是   | 上传的图片文件 |

> 返回示例

> 200 Response

```json
{
  "status": "success",
  "svdVideoUrl": "https://example.com/generated-video.mp4"
}
```

### 返回结果

| 状态码 | 状态码含义                                              | 说明       | 数据模型 |
| ------ | ------------------------------------------------------- | ---------- | -------- |
| 200    | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | 成功响应。 | Inline   |

### 返回数据结构

状态码 **200**

| 名称          | 类型   | 必选 | 约束 | 中文名 | 说明                             |
| ------------- | ------ | ---- | ---- | ------ | -------------------------------- |
| » svdVideoUrl | string | true | none |        | 生成的视频地址。                 |
| » status      | string | true | none |        | 接口执行状态，success 表示成功。 |

## POST 视频追踪

POST /video/video2tracking

视频追踪接口，上传视频后返回追踪结果视频地址。

> Body 请求参数

```yaml
file: file://./examples/video/sample.mp4

```

### 请求参数

| 名称   | 位置 | 类型           | 必选 | 说明           |
| ------ | ---- | -------------- | ---- | -------------- |
| body   | body | object         | 否   | none           |
| » file | body | string(binary) | 是   | 上传的视频文件 |

> 返回示例

> 200 Response

```json
{
  "status": "success",
  "data": "https://example.com/tracked-video.mp4"
}
```

### 返回结果

| 状态码 | 状态码含义                                              | 说明       | 数据模型 |
| ------ | ------------------------------------------------------- | ---------- | -------- |
| 200    | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | 成功响应。 | Inline   |

### 返回数据结构

状态码 **200**

| 名称     | 类型   | 必选 | 约束 | 中文名 | 说明                   |
| -------- | ------ | ---- | ---- | ------ | ---------------------- |
| » status | string | true | none |        | 返回的结果状态         |
| » data   | string | true | none |        | 返回视频追踪的视频地址 |

## POST 视频增强

POST /video/mmeditingInference

视频增强接口，上传视频后返回增强后的视频地址。

> Body 请求参数

```yaml
file: file://./examples/video/sample.mp4

```

### 请求参数

| 名称   | 位置 | 类型           | 必选 | 说明           |
| ------ | ---- | -------------- | ---- | -------------- |
| body   | body | object         | 否   | none           |
| » file | body | string(binary) | 是   | 上传的视频文件 |

> 返回示例

> 200 Response

```json
{
  "status": "success",
  "data": "https://example.com/enhanced-video.mp4"
}
```

### 返回结果

| 状态码 | 状态码含义                                              | 说明       | 数据模型 |
| ------ | ------------------------------------------------------- | ---------- | -------- |
| 200    | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | 成功响应。 | Inline   |

### 返回数据结构

状态码 **200**

| 名称     | 类型   | 必选 | 约束 | 中文名 | 说明                   |
| -------- | ------ | ---- | ---- | ------ | ---------------------- |
| » status | string | true | none |        | 返回的结果状态         |
| » data   | string | true | none |        | 返回视频增强的视频地址 |

# RAG

## POST 获取向量

POST /v1/vector/get

按条件读取向量库中的原始记录。

> Body 请求参数

```json
{
  "category": "default",
  "where": {
    "source": "file"
  },
  "limit": 10,
  "offset": 0
}
```

### 请求参数

| 名称       | 位置 | 类型    | 必选 | 说明                     |
| ---------- | ---- | ------- | ---- | ------------------------ |
| body       | body | object  | 否   | none                     |
| » category | body | string  | 是   | 知识分类或向量集合名称。 |
| » where    | body | object  | 是   | 过滤条件。               |
| » limit    | body | integer | 是   | 返回记录数上限。         |
| » offset   | body | integer | 是   | 结果偏移量。             |

> 返回示例

> 200 Response

```json
{
  "status": "success",
  "data": [
    {
      "document": "LinkMind provides a unified AI middleware layer.",
      "id": "doc_001",
      "metadata": {
        "category": "default",
        "file_id": "file_001",
        "filename": "sample-handbook.pdf",
        "filepath": "202504240001.pdf",
        "level": "user",
        "parent_id": "chunk_root_001",
        "source": "file"
      }
    }
  ]
}
```

### 返回结果

| 状态码 | 状态码含义                                              | 说明       | 数据模型 |
| ------ | ------------------------------------------------------- | ---------- | -------- |
| 200    | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | 成功响应。 | Inline   |

### 返回数据结构

状态码 **200**

| 名称          | 类型     | 必选 | 约束 | 中文名 | 说明                             |
| ------------- | -------- | ---- | ---- | ------ | -------------------------------- |
| » data        | [object] | true | none |        | 接口返回数据。                   |
| »» document   | string   | true | none |        | 文本内容。                       |
| »» id         | string   | true | none |        | none                             |
| »» metadata   | object   | true | none |        | 与文本关联的元数据。             |
| »»» category  | string   | true | none |        | 知识分类或向量集合名称。         |
| »»» file_id   | string   | true | none |        | 文件 ID。                        |
| »»» filename  | string   | true | none |        | 文件名。                         |
| »»» filepath  | string   | true | none |        | 文件路径。                       |
| »»» level     | string   | true | none |        | 知识级别，默认值为 user。        |
| »»» parent_id | string   | true | none |        | none                             |
| »»» source    | string   | true | none |        | none                             |
| » status      | string   | true | none |        | 接口执行状态，success 表示成功。 |

## POST 添加向量

POST /v1/vector/add

向量写入接口，用于新增文本及其元数据。

> Body 请求参数

```json
{
  "category": "default",
  "data": [
    {
      "metadata": {
        "category": "default",
        "filename": "sample-handbook.pdf",
        "filepath": "202504240001.pdf",
        "source": "file"
      },
      "document": "LinkMind provides a unified AI middleware layer."
    }
  ]
}
```

### 请求参数

| 名称        | 位置 | 类型     | 必选 | 说明                       |
| ----------- | ---- | -------- | ---- | -------------------------- |
| body        | body | object   | 否   | none                       |
| » category  | body | string   | 是   | 知识分类或向量集合名称。   |
| » data      | body | [object] | 是   | 待写入的文本及元数据列表。 |
| »» metadata | body | object   | 是   | 与文本关联的元数据。       |
| »» document | body | string   | 是   | 文本内容。                 |

> 返回示例

> 200 Response

```json
{
  "status": "success"
}
```

### 返回结果

| 状态码 | 状态码含义                                              | 说明       | 数据模型 |
| ------ | ------------------------------------------------------- | ---------- | -------- |
| 200    | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | 成功响应。 | Inline   |

### 返回数据结构

状态码 **200**

| 名称     | 类型   | 必选 | 约束 | 中文名 | 说明                             |
| -------- | ------ | ---- | ---- | ------ | -------------------------------- |
| » status | string | true | none |        | 接口执行状态，success 表示成功。 |

## POST 更新向量

POST /v1/vector/update

向量更新接口，用于按记录 ID 更新文本或元数据。

> Body 请求参数

```json
{
  "category": "default",
  "data": [
    {
      "id": "doc_001",
      "metadata": {
        "category": "default",
        "filename": "sample-handbook.pdf",
        "filepath": "202504240001.pdf",
        "source": "file"
      },
      "document": "LinkMind provides a unified AI middleware layer for model routing."
    }
  ]
}
```

### 请求参数

| 名称        | 位置 | 类型     | 必选 | 说明                       |
| ----------- | ---- | -------- | ---- | -------------------------- |
| body        | body | object   | 否   | none                       |
| » category  | body | string   | 是   | 知识分类或向量集合名称。   |
| » data      | body | [object] | 是   | 待更新的文本及元数据列表。 |
| »» id       | body | string   | 是   | none                       |
| »» metadata | body | object   | 是   | 与文本关联的元数据。       |
| »»» chapter | body | integer  | 是   | none                       |
| »»» verse   | body | integer  | 是   | none                       |
| »» document | body | string   | 是   | 文本内容。                 |

> 返回示例

> 200 Response

```json
{
  "status": "success"
}
```

### 返回结果

| 状态码 | 状态码含义                                              | 说明       | 数据模型 |
| ------ | ------------------------------------------------------- | ---------- | -------- |
| 200    | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | 成功响应。 | Inline   |

### 返回数据结构

状态码 **200**

| 名称     | 类型   | 必选 | 约束 | 中文名 | 说明                             |
| -------- | ------ | ---- | ---- | ------ | -------------------------------- |
| » status | string | true | none |        | 接口执行状态，success 表示成功。 |

## POST 删除向量

POST /v1/vector/delete

向量删除接口，用于按记录 ID 删除向量数据。

> Body 请求参数

```json
{
  "category": "default",
  "ids": [
    "doc_001"
  ]
}
```

### 请求参数

| 名称       | 位置 | 类型     | 必选 | 说明                     |
| ---------- | ---- | -------- | ---- | ------------------------ |
| body       | body | object   | 否   | none                     |
| » category | body | string   | 是   | 知识分类或向量集合名称。 |
| » ids      | body | [string] | 是   | 待删除记录 ID 列表。     |

> 返回示例

> 200 Response

```json
{
  "status": "success"
}
```

### 返回结果

| 状态码 | 状态码含义                                              | 说明       | 数据模型 |
| ------ | ------------------------------------------------------- | ---------- | -------- |
| 200    | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | 成功响应。 | Inline   |

### 返回数据结构

状态码 **200**

| 名称     | 类型   | 必选 | 约束 | 中文名 | 说明                             |
| -------- | ------ | ---- | ---- | ------ | -------------------------------- |
| » status | string | true | none |        | 接口执行状态，success 表示成功。 |

## POST 查询向量

POST /v1/vector/query

向量检索接口，根据查询文本返回相似结果。

> Body 请求参数

```json
{
  "category": "default",
  "text": "How does model routing work?",
  "n": 5,
  "where": {
    "source": "file"
  }
}
```

### 请求参数

| 名称       | 位置 | 类型    | 必选 | 说明                     |
| ---------- | ---- | ------- | ---- | ------------------------ |
| body       | body | object  | 否   | none                     |
| » text     | body | string  | 是   | 输入文本。               |
| » n        | body | integer | 是   | 返回的近邻结果数量。     |
| » where    | body | object  | 是   | 过滤条件。               |
| » category | body | string  | 是   | 知识分类或向量集合名称。 |

> 返回示例

> 200 Response

```json
{
  "status": "success",
  "data": [
    {
      "document": "LinkMind can route requests across multiple model backends.",
      "id": "doc_002",
      "metadata": {
        "category": "default",
        "file_id": "file_001",
        "filename": "sample-handbook.pdf",
        "filepath": "202504240001.pdf",
        "level": "user",
        "parent_id": "chunk_root_001",
        "source": "file"
      },
      "distance": 0.12
    }
  ]
}
```

### 返回结果

| 状态码 | 状态码含义                                              | 说明       | 数据模型 |
| ------ | ------------------------------------------------------- | ---------- | -------- |
| 200    | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | 成功响应。 | Inline   |

### 返回数据结构

状态码 **200**

| 名称                      | 类型     | 必选 | 约束 | 中文名 | 说明                             |
| ------------------------- | -------- | ---- | ---- | ------ | -------------------------------- |
| » data                    | [object] | true | none |        | 接口返回数据。                   |
| »» document               | string   | true | none |        | 文本内容。                       |
| »» id                     | string   | true | none |        | none                             |
| »» metadata               | object   | true | none |        | 与文本关联的元数据。             |
| »»» category              | string   | true | none |        | 知识分类或向量集合名称。         |
| »»» file_id               | string   | true | none |        | 文件 ID。                        |
| »»» filename              | string   | true | none |        | 文件名。                         |
| »»» filepath              | string   | true | none |        | 文件路径。                       |
| »»» level                 | string   | true | none |        | 知识级别，默认值为 user。        |
| »»» parent_id             | string   | true | none |        | none                             |
| »»» source                | string   | true | none |        | none                             |
| »»» reference_document_id | string   | true | none |        | none                             |
| »» distance               | number   | true | none |        | 相似度距离或得分。               |
| » status                  | string   | true | none |        | 接口执行状态，success 表示成功。 |

## POST 嵌入生成

POST /v1/embeddings

Embedding 接口，输入文本后返回向量结果。

> Body 请求参数

```json
{
  "model": "text-embedding",
  "input": [
    "Hello LinkMind"
  ]
}
```

### 请求参数

| 名称         | 位置   | 类型     | 必选 | 说明                   |
| ------------ | ------ | -------- | ---- | ---------------------- |
| Content-Type | header | string   | 否   | 请求体内容类型。       |
| body         | body   | object   | 否   | none                   |
| » input      | body   | [string] | 是   | 待生成向量的文本内容。 |
| » model      | body   | string   | 是   | 调用时使用的模型名称。 |

> 返回示例

> 200 Response

```json
{
  "status": "success",
  "data": [
    [
      0.12,
      -0.03,
      0.44,
      0.08
    ]
  ]
}
```

### 返回结果

| 状态码 | 状态码含义                                              | 说明       | 数据模型 |
| ------ | ------------------------------------------------------- | ---------- | -------- |
| 200    | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | 成功响应。 | Inline   |

### 返回数据结构

状态码 **200**

| 名称     | 类型    | 必选 | 约束 | 中文名 | 说明                             |
| -------- | ------- | ---- | ---- | ------ | -------------------------------- |
| » status | string  | true | none |        | 接口执行状态，success 表示成功。 |
| » data   | [array] | true | none |        | Embedding 向量列表。             |

## GET 获取向量集合

GET /v1/vector/listCollections

获取当前向量集合列表。

### 请求参数

| 名称         | 位置   | 类型   | 必选 | 说明             |
| ------------ | ------ | ------ | ---- | ---------------- |
| Content-Type | header | string | 否   | 请求体内容类型。 |

> 返回示例

> 200 Response

```json
{
  "status": "success",
  "data": [
    {
      "category": "default",
      "vectorCount": 128
    }
  ]
}
```

### 返回结果

| 状态码 | 状态码含义                                              | 说明       | 数据模型 |
| ------ | ------------------------------------------------------- | ---------- | -------- |
| 200    | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | 成功响应。 | Inline   |

### 返回数据结构

状态码 **200**

| 名称           | 类型     | 必选 | 约束 | 中文名 | 说明                             |
| -------------- | -------- | ---- | ---- | ------ | -------------------------------- |
| » status       | string   | true | none |        | 接口执行状态，success 表示成功。 |
| » data         | [object] | true | none |        | 向量集合列表。                   |
| »» category    | string   | true | none |        | 知识分类或向量集合名称。         |
| »» vectorCount | integer  | true | none |        | 集合中的向量数量。               |

# 私训

## POST 上传私训学习文件

POST /training/upload

上传通用知识文件并写入私有知识库。

> Body 请求参数

```yaml
fileToUpload: file://./examples/docs/sample.pdf

```

### 请求参数

| 名称           | 位置  | 类型           | 必选 | 说明                      |
| -------------- | ----- | -------------- | ---- | ------------------------- |
| category       | query | string         | 是   | 知识分类或向量集合名称。  |
| level          | query | string         | 否   | 知识级别，默认值为 user。 |
| body           | body  | object         | 否   | none                      |
| » fileToUpload | body  | string(binary) | 是   | 要上传的知识文件。        |

> 返回示例

> 200 Response

```json
{
  "status": "success",
  "data": "[{\"filename\":\"sample.pdf\",\"filepath\":\"202504240001.pdf\",\"fileId\":\"file_001\",\"vectorIds\":[[\"vec_001\",\"vec_002\"]]}]",
  "task_id": "task_001"
}
```

### 返回结果

| 状态码 | 状态码含义                                              | 说明       | 数据模型 |
| ------ | ------------------------------------------------------- | ---------- | -------- |
| 200    | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | 成功响应。 | Inline   |

### 返回数据结构

状态码 **200**

| 名称      | 类型   | 必选 | 约束 | 中文名 | 说明                                   |
| --------- | ------ | ---- | ---- | ------ | -------------------------------------- |
| » status  | string | true | none |        | 接口执行状态，success 表示成功。       |
| » data    | string | true | none |        | 上传结果，内容为文件信息 JSON 字符串。 |
| » task_id | string | true | none |        | 异步任务 ID。                          |

## POST 上传私训问答对

POST /uploadFile/pairing

上传通用问答对并写入私有知识库。

> Body 请求参数

```json
{
  "category": "default",
  "level": "user",
  "data": [
    {
      "instruction": [
        "What is LinkMind?"
      ],
      "output": "LinkMind is an AI middleware layer for routing, RAG, and multimodal orchestration."
    }
  ]
}
```

### 请求参数

| 名称           | 位置 | 类型     | 必选 | 说明                                         |
| -------------- | ---- | -------- | ---- | -------------------------------------------- |
| body           | body | object   | 否   | none                                         |
| » category     | body | string   | 是   | 知识分类或向量集合名称。                     |
| » level        | body | string   | 否   | 知识级别，默认值为 user。                    |
| » data         | body | [object] | 是   | 问答对列表。                                 |
| »» instruction | body | [string] | 是   | 问题或指令列表。单条字符串也可被服务端接收。 |
| »» output      | body | string   | 是   | 答案或输出内容。                             |

> 返回示例

> 200 Response

```json
{
  "status": "success"
}
```

### 返回结果

| 状态码 | 状态码含义                                              | 说明       | 数据模型 |
| ------ | ------------------------------------------------------- | ---------- | -------- |
| 200    | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | 成功响应。 | Inline   |

### 返回数据结构

状态码 **200**

| 名称     | 类型   | 必选 | 约束 | 中文名 | 说明                             |
| -------- | ------ | ---- | ---- | ------ | -------------------------------- |
| » status | string | true | none |        | 接口执行状态，success 表示成功。 |

## GET 获取文件列表

GET /uploadFile/getUploadFileList

分页查询已上传知识文件列表。

### 请求参数

| 名称       | 位置  | 类型   | 必选 | 说明       |
| ---------- | ----- | ------ | ---- | ---------- |
| category   | query | string | 否   | 数据类别   |
| pageSize   | query | string | 否   | 分页的大小 |
| pageNumber | query | string | 否   | 分页的序号 |

> 返回示例

> 200 Response

```json
{
  "status": "success",
  "totalRow": 1,
  "pageNumber": 1,
  "data": [
    {
      "fileId": "file_001",
      "filename": "sample.pdf",
      "filepath": "202504240001.pdf",
      "category": "default",
      "createTime": 1700000000000
    }
  ],
  "totalPage": 1,
  "pageSize": 10
}
```

### 返回结果

| 状态码 | 状态码含义                                              | 说明       | 数据模型 |
| ------ | ------------------------------------------------------- | ---------- | -------- |
| 200    | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | 成功响应。 | Inline   |

### 返回数据结构

状态码 **200**

| 名称         | 类型     | 必选 | 约束 | 中文名 | 说明           |
| ------------ | -------- | ---- | ---- | ------ | -------------- |
| » totalRow   | integer  | true | none |        | 数据总数       |
| » pageNumber | integer  | true | none |        | 分页的序号     |
| » data       | [object] | true | none |        | 知识文件列表。 |
| »» fileId    | string   | true | none |        | 文件ID         |
| »» filename  | string   | true | none |        | 文件名         |
| »» filepath  | string   | true | none |        | 文件相对路径   |
| »» category  | string   | true | none |        | 数据类别       |
| » totalPage  | integer  | true | none |        | 分页总数       |
| » pageSize   | integer  | true | none |        | 分页的大小     |
| » status     | string   | true | none |        | 接口返回状态   |

## POST 删除上传文件

POST /uploadFile/deleteFile

删除已上传知识文件及其关联向量。

> Body 请求参数

```json
[
  "file_001"
]
```

### 请求参数

| 名称     | 位置  | 类型          | 必选 | 说明                     |
| -------- | ----- | ------------- | ---- | ------------------------ |
| category | query | string        | 否   | 知识分类或向量集合名称。 |
| body     | body  | array[string] | 否   | none                     |

> 返回示例

> 200 Response

```json
{
  "status": "success"
}
```

### 返回结果

| 状态码 | 状态码含义                                              | 说明       | 数据模型 |
| ------ | ------------------------------------------------------- | ---------- | -------- |
| 200    | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | 成功响应。 | Inline   |

### 返回数据结构

状态码 **200**

| 名称     | 类型   | 必选 | 约束 | 中文名   | 说明                             |
| -------- | ------ | ---- | ---- | -------- | -------------------------------- |
| » status | string | true | none | 返回状态 | 接口执行状态，success 表示成功。 |

# 数据模型


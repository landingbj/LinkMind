---
title: Default Module
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

# Default Module

Base URLs:

# Authentication

# Text

## POST Chat Completions

POST /v1/chat/completions

OpenAI-compatible chat completion endpoint for unified text model calls with standard chat responses.

> Body Parameters

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

### Params

| Name                         | Location | Type     | Required | Description                                                  |
| ---------------------------- | -------- | -------- | -------- | ------------------------------------------------------------ |
| Content-Type                 | header   | string   | no       | Request body content type.                                   |
| Authorization                | header   | string   | yes      | Bearer token used to call authenticated endpoints.           |
| body                         | body     | object   | no       | none                                                         |
| » model                      | body     | string   | yes      | Model type.                                                  |
| » messages                   | body     | [object] | yes      | List of submitted messages.                                  |
| »» role                      | body     | string   | yes      | Message role, such as user or assistant.                     |
| »» content                   | body     | [object] | yes      | Message content. For user it is the input content; for assistant it is the model output content. |
| »»» type                     | body     | string   | no       | Content item type.                                           |
| »»» text                     | body     | string   | no       | Text content.                                                |
| » stream                     | body     | boolean  | yes      | Whether to use streaming responses.                          |
| » store                      | body     | boolean  | yes      | Whether to store the result of this call.                    |
| » max_completion_tokens      | body     | integer  | yes      | Maximum number of output tokens allowed for this generation. |
| » tools                      | body     | [object] | yes      | Optional tool definition list.                               |
| »» type                      | body     | string   | yes      | Tool type.                                                   |
| »» function                  | body     | object   | yes      | Tool function definition.                                    |
| »»» name                     | body     | string   | yes      | Function name.                                               |
| »»» description              | body     | string   | yes      | Function description.                                        |
| »»» parameters               | body     | object   | yes      | Function parameter definition.                               |
| »»»» type                    | body     | string   | yes      | Field type.                                                  |
| »»»» required                | body     | [string] | yes      | none                                                         |
| »»»» properties              | body     | object   | yes      | none                                                         |
| »»»»» path                   | body     | object   | yes      | none                                                         |
| »»»»»» description           | body     | string   | no       | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» offset                 | body     | object   | no       | Result offset.                                               |
| »»»»»» description           | body     | string   | yes      | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» limit                  | body     | object   | no       | Maximum number of records to return.                         |
| »»»»»» description           | body     | string   | no       | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»»» minimum               | body     | integer  | yes      | none                                                         |
| »»»»» file_path              | body     | object   | no       | none                                                         |
| »»»»»» description           | body     | string   | yes      | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» filePath               | body     | object   | no       | none                                                         |
| »»»»»» description           | body     | string   | yes      | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» file                   | body     | object   | no       | none                                                         |
| »»»»»» description           | body     | string   | yes      | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» oldText                | body     | object   | no       | none                                                         |
| »»»»»» description           | body     | string   | yes      | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» newText                | body     | object   | no       | none                                                         |
| »»»»»» description           | body     | string   | yes      | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» edits                  | body     | object   | no       | none                                                         |
| »»»»»» description           | body     | string   | yes      | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»»» items                 | body     | object   | yes      | none                                                         |
| »»»»»»» additionalProperties | body     | boolean  | yes      | none                                                         |
| »»»»»»» type                 | body     | string   | yes      | Field type.                                                  |
| »»»»»»» required             | body     | [string] | yes      | none                                                         |
| »»»»»»» properties           | body     | object   | yes      | none                                                         |
| »»»»»»»» oldText             | body     | object   | yes      | none                                                         |
| »»»»»»»»» description        | body     | string   | yes      | none                                                         |
| »»»»»»»»» type               | body     | string   | yes      | Field type.                                                  |
| »»»»»»»» newText             | body     | object   | yes      | none                                                         |
| »»»»»»»»» description        | body     | string   | yes      | none                                                         |
| »»»»»»»»» type               | body     | string   | yes      | Field type.                                                  |
| »»»»» old_string             | body     | object   | no       | none                                                         |
| »»»»»» description           | body     | string   | yes      | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» old_text               | body     | object   | no       | none                                                         |
| »»»»»» description           | body     | string   | yes      | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» oldString              | body     | object   | no       | none                                                         |
| »»»»»» description           | body     | string   | yes      | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» new_string             | body     | object   | no       | none                                                         |
| »»»»»» description           | body     | string   | yes      | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» new_text               | body     | object   | no       | none                                                         |
| »»»»»» description           | body     | string   | yes      | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» newString              | body     | object   | no       | none                                                         |
| »»»»»» description           | body     | string   | yes      | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» content                | body     | object   | no       | Message content.                                             |
| »»»»»» description           | body     | string   | yes      | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» command                | body     | object   | no       | none                                                         |
| »»»»»» description           | body     | string   | yes      | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» workdir                | body     | object   | no       | none                                                         |
| »»»»»» description           | body     | string   | yes      | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» env                    | body     | object   | no       | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»»» patternProperties     | body     | object   | yes      | none                                                         |
| »»»»»»» ^(.*)$               | body     | object   | yes      | none                                                         |
| »»»»»»»» type                | body     | string   | yes      | Field type.                                                  |
| »»»»» yieldMs                | body     | object   | no       | none                                                         |
| »»»»»» description           | body     | string   | yes      | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» background             | body     | object   | no       | none                                                         |
| »»»»»» description           | body     | string   | yes      | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» timeout                | body     | object   | no       | none                                                         |
| »»»»»» description           | body     | string   | yes      | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»»» minimum               | body     | integer  | yes      | none                                                         |
| »»»»» pty                    | body     | object   | no       | none                                                         |
| »»»»»» description           | body     | string   | yes      | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» elevated               | body     | object   | no       | none                                                         |
| »»»»»» description           | body     | string   | yes      | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» host                   | body     | object   | no       | none                                                         |
| »»»»»» description           | body     | string   | yes      | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» security               | body     | object   | no       | none                                                         |
| »»»»»» description           | body     | string   | yes      | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» ask                    | body     | object   | no       | none                                                         |
| »»»»»» description           | body     | string   | yes      | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» node                   | body     | object   | no       | none                                                         |
| »»»»»» description           | body     | string   | yes      | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» action                 | body     | object   | no       | none                                                         |
| »»»»»» description           | body     | string   | no       | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»»» enum                  | body     | [string] | yes      | none                                                         |
| »»»»» sessionId              | body     | object   | no       | none                                                         |
| »»»»»» description           | body     | string   | yes      | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» data                   | body     | object   | no       | API response data.                                           |
| »»»»»» description           | body     | string   | yes      | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» keys                   | body     | object   | no       | none                                                         |
| »»»»»» description           | body     | string   | yes      | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»»» items                 | body     | object   | yes      | none                                                         |
| »»»»»»» type                 | body     | string   | yes      | Field type.                                                  |
| »»»»» hex                    | body     | object   | no       | none                                                         |
| »»»»»» description           | body     | string   | yes      | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»»» items                 | body     | object   | yes      | none                                                         |
| »»»»»»» type                 | body     | string   | yes      | Field type.                                                  |
| »»»»» literal                | body     | object   | no       | none                                                         |
| »»»»»» description           | body     | string   | yes      | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» text                   | body     | object   | no       | Input text.                                                  |
| »»»»»» description           | body     | string   | no       | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» bracketed              | body     | object   | no       | none                                                         |
| »»»»»» description           | body     | string   | yes      | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» eof                    | body     | object   | no       | none                                                         |
| »»»»»» description           | body     | string   | yes      | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» gatewayUrl             | body     | object   | no       | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» gatewayToken           | body     | object   | no       | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» timeoutMs              | body     | object   | no       | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» includeDisabled        | body     | object   | no       | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» job                    | body     | object   | no       | none                                                         |
| »»»»»» additionalProperties  | body     | boolean  | yes      | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»»» properties            | body     | object   | yes      | none                                                         |
| »»»»» jobId                  | body     | object   | no       | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» id                     | body     | object   | no       | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» patch                  | body     | object   | no       | none                                                         |
| »»»»»» additionalProperties  | body     | boolean  | yes      | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»»» properties            | body     | object   | yes      | none                                                         |
| »»»»» mode                   | body     | object   | no       | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»»» enum                  | body     | [string] | yes      | none                                                         |
| »»»»» runMode                | body     | object   | no       | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»»» enum                  | body     | [string] | yes      | none                                                         |
| »»»»» contextMessages        | body     | object   | no       | none                                                         |
| »»»»»» minimum               | body     | integer  | yes      | none                                                         |
| »»»»»» maximum               | body     | integer  | yes      | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» kinds                  | body     | object   | no       | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»»» items                 | body     | object   | yes      | none                                                         |
| »»»»»»» type                 | body     | string   | yes      | Field type.                                                  |
| »»»»» activeMinutes          | body     | object   | no       | none                                                         |
| »»»»»» minimum               | body     | integer  | yes      | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» messageLimit           | body     | object   | no       | none                                                         |
| »»»»»» minimum               | body     | integer  | yes      | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» sessionKey             | body     | object   | no       | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» includeTools           | body     | object   | no       | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» label                  | body     | object   | no       | none                                                         |
| »»»»»» minLength             | body     | integer  | no       | none                                                         |
| »»»»»» maxLength             | body     | integer  | no       | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» agentId                | body     | object   | no       | Target agent identifier.                                     |
| »»»»»» minLength             | body     | integer  | no       | none                                                         |
| »»»»»» maxLength             | body     | integer  | no       | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» message                | body     | object   | no       | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» timeoutSeconds         | body     | object   | no       | none                                                         |
| »»»»»» minimum               | body     | integer  | yes      | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» task                   | body     | object   | no       | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» runtime                | body     | object   | no       | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»»» enum                  | body     | [string] | yes      | none                                                         |
| »»»»» resumeSessionId        | body     | object   | no       | none                                                         |
| »»»»»» description           | body     | string   | yes      | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» model                  | body     | object   | no       | Model name used for the call.                                |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» thinking               | body     | object   | no       | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» cwd                    | body     | object   | no       | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» runTimeoutSeconds      | body     | object   | no       | none                                                         |
| »»»»»» minimum               | body     | integer  | yes      | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» thread                 | body     | object   | no       | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» cleanup                | body     | object   | no       | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»»» enum                  | body     | [string] | yes      | none                                                         |
| »»»»» sandbox                | body     | object   | no       | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»»» enum                  | body     | [string] | yes      | none                                                         |
| »»»»» streamTo               | body     | object   | no       | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»»» enum                  | body     | [string] | yes      | none                                                         |
| »»»»» attachments            | body     | object   | no       | none                                                         |
| »»»»»» maxItems              | body     | integer  | yes      | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»»» items                 | body     | object   | yes      | none                                                         |
| »»»»»»» type                 | body     | string   | yes      | Field type.                                                  |
| »»»»»»» required             | body     | [string] | yes      | none                                                         |
| »»»»»»» properties           | body     | object   | yes      | none                                                         |
| »»»»»»»» name                | body     | object   | yes      | none                                                         |
| »»»»»»»»» type               | body     | string   | yes      | Field type.                                                  |
| »»»»»»»» content             | body     | object   | yes      | Message content.                                             |
| »»»»»»»»» type               | body     | string   | yes      | Field type.                                                  |
| »»»»»»»» encoding            | body     | object   | yes      | none                                                         |
| »»»»»»»»» type               | body     | string   | yes      | Field type.                                                  |
| »»»»»»»»» enum               | body     | [string] | yes      | none                                                         |
| »»»»»»»» mimeType            | body     | object   | yes      | none                                                         |
| »»»»»»»»» type               | body     | string   | yes      | Field type.                                                  |
| »»»»» attachAs               | body     | object   | no       | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»»» properties            | body     | object   | yes      | none                                                         |
| »»»»»»» mountPath            | body     | object   | yes      | none                                                         |
| »»»»»»»» type                | body     | string   | yes      | Field type.                                                  |
| »»»»» target                 | body     | object   | no       | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» recentMinutes          | body     | object   | no       | none                                                         |
| »»»»»» minimum               | body     | integer  | yes      | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» query                  | body     | object   | no       | Query conditions.                                            |
| »»»»»» description           | body     | string   | no       | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» count                  | body     | object   | no       | none                                                         |
| »»»»»» description           | body     | string   | yes      | none                                                         |
| »»»»»» minimum               | body     | integer  | yes      | none                                                         |
| »»»»»» maximum               | body     | integer  | yes      | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» region                 | body     | object   | no       | none                                                         |
| »»»»»» description           | body     | string   | yes      | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» safeSearch             | body     | object   | no       | none                                                         |
| »»»»»» description           | body     | string   | yes      | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» url                    | body     | object   | no       | none                                                         |
| »»»»»» description           | body     | string   | yes      | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» extractMode            | body     | object   | no       | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»»» enum                  | body     | [string] | yes      | none                                                         |
| »»»»»» description           | body     | string   | yes      | none                                                         |
| »»»»»» default               | body     | string   | yes      | none                                                         |
| »»»»» maxChars               | body     | object   | no       | none                                                         |
| »»»»»» description           | body     | string   | yes      | none                                                         |
| »»»»»» minimum               | body     | integer  | yes      | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» maxResults             | body     | object   | no       | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» minScore               | body     | object   | no       | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» from                   | body     | object   | yes      | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»»» lines                  | body     | object   | yes      | none                                                         |
| »»»»»» type                  | body     | string   | yes      | Field type.                                                  |
| »»»» additionalProperties    | body     | boolean  | no       | none                                                         |

> Response Examples

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

### Responses

| HTTP Status Code | Meaning                                                 | Description          | Data schema |
| ---------------- | ------------------------------------------------------- | -------------------- | ----------- |
| 200              | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | Successful response. | Inline      |

### Responses Data Schema

HTTP Status Code **200**

| Name                 | Type     | Required | Restrictions | Title | description                                                  |
| -------------------- | -------- | -------- | ------------ | ----- | ------------------------------------------------------------ |
| » id                 | string   | true     | none         |       | Unique identifier.                                           |
| » object             | string   | true     | none         |       | Object type.                                                 |
| » created            | integer  | true     | none         |       | Unix timestamp (seconds) when the chat completion was created. |
| » model              | string   | true     | none         |       | Model used.                                                  |
| » choices            | [object] | true     | none         |       | List of choices.                                             |
| »» index             | integer  | false    | none         |       | Item index.                                                  |
| »» message           | object   | false    | none         |       | Returned message.                                            |
| »»» role             | string   | true     | none         |       | Message role, such as user or assistant.                     |
| »»» content          | string   | true     | none         |       | Message content. For user it is the input content; for assistant it is the model output content. |
| »» finish_reason     | string   | false    | none         |       | Reason why the model stopped generating.                     |
| » usage              | object   | true     | none         |       | Usage statistics for the request.                            |
| »» prompt_tokens     | integer  | true     | none         |       | Number of tokens in the prompt.                              |
| »» completion_tokens | integer  | true     | none         |       | Number of generated tokens.                                  |
| »» total_tokens      | integer  | true     | none         |       | Total number of tokens used in the request.                  |

## POST chat/go

POST /chat/go

Unified Agent / Worker entrypoint for orchestrated chat requests executed by the specified worker.

> Body Parameters

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

### Params

| Name          | Location | Type     | Required | Description                                                  |
| ------------- | -------- | -------- | -------- | ------------------------------------------------------------ |
| Content-Type  | header   | string   | no       | Request body content type.                                   |
| Authorization | header   | string   | yes      | Bearer token used to call authenticated endpoints.           |
| body          | body     | object   | no       | none                                                         |
| » model       | body     | string   | no       | Model type.                                                  |
| » temperature | body     | number   | yes      | Sampling temperature.                                        |
| » max_tokens  | body     | integer  | yes      | Maximum number of tokens that can be generated.              |
| » category    | body     | string   | no       | Data category.                                               |
| » messages    | body     | [object] | yes      | List of submitted messages.                                  |
| »» role       | body     | string   | no       | Message role, such as user or assistant.                     |
| »» content    | body     | string   | no       | Message content. For user it is the input content; for assistant it is the model output content. |
| » worker      | body     | string   | no       | Worker name to invoke.                                       |
| » agentId     | body     | string   | no       | Agent identifier to invoke.                                  |
| » stream      | body     | boolean  | no       | Whether to use streaming responses.                          |

> Response Examples

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

### Responses

| HTTP Status Code | Meaning                                                 | Description          | Data schema |
| ---------------- | ------------------------------------------------------- | -------------------- | ----------- |
| 200              | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | Successful response. | Inline      |

### Responses Data Schema

HTTP Status Code **200**

| Name                 | Type     | Required | Restrictions | Title | description                                                  |
| -------------------- | -------- | -------- | ------------ | ----- | ------------------------------------------------------------ |
| » id                 | string   | true     | none         |       | Unique identifier.                                           |
| » object             | string   | true     | none         |       | Object type.                                                 |
| » created            | integer  | true     | none         |       | Unix timestamp (seconds) when the chat completion was created. |
| » model              | string   | true     | none         |       | Model used.                                                  |
| » choices            | [object] | true     | none         |       | List of choices.                                             |
| »» index             | integer  | false    | none         |       | Item index.                                                  |
| »» message           | object   | false    | none         |       | Returned message.                                            |
| »»» role             | string   | true     | none         |       | Message role, such as user or assistant.                     |
| »»» content          | string   | true     | none         |       | Message content. For user it is the input content; for assistant it is the model output content. |
| »» finish_reason     | string   | false    | none         |       | Reason why the model stopped generating.                     |
| » usage              | object   | true     | none         |       | Usage statistics for the request.                            |
| »» prompt_tokens     | integer  | true     | none         |       | Number of tokens in the prompt.                              |
| »» completion_tokens | integer  | true     | none         |       | Number of generated tokens.                                  |
| »» total_tokens      | integer  | true     | none         |       | Total number of tokens used in the request.                  |

# Audio

## POST Speech to Text

POST /audio/speech2text

Speech-to-text endpoint. Upload audio binary data and receive the transcription result.

> Body Parameters

```yaml
file://./examples/audio/sample.wav

```

### Params

| Name         | Location | Type           | Required | Description                   |
| ------------ | -------- | -------------- | -------- | ----------------------------- |
| model        | query    | string         | no       | Model name used for the call. |
| format       | query    | string         | no       | Response format.              |
| Content-Type | header   | string         | no       | Request body content type.    |
| body         | body     | string(binary) | no       | none                          |

> Response Examples

> 200 Response

```json
{
  "result": "Hello LinkMind",
  "status": 200
}
```

### Responses

| HTTP Status Code | Meaning                                                 | Description          | Data schema |
| ---------------- | ------------------------------------------------------- | -------------------- | ----------- |
| 200              | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | Successful response. | Inline      |

### Responses Data Schema

HTTP Status Code **200**

| Name     | Type    | Required | Restrictions | Title | description                |
| -------- | ------- | -------- | ------------ | ----- | -------------------------- |
| » result | string  | true     | none         |       | Speech recognition result. |
| » status | integer | true     | none         |       | Service status code.       |

## GET Text to Speech

GET /audio/text2speech

Text-to-speech endpoint. Generates an audio stream based on query parameters.

### Params

| Name   | Location | Type   | Required | Description                   |
| ------ | -------- | ------ | -------- | ----------------------------- |
| text   | query    | string | yes      | Text to synthesize.           |
| model  | query    | string | no       | Model name used for the call. |
| format | query    | string | no       | Response format.              |

> Response Examples

> 200 Response

### Responses

| HTTP Status Code | Meaning                                                 | Description                           | Data schema |
| ---------------- | ------------------------------------------------------- | ------------------------------------- | ----------- |
| 200              | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | Returns binary audio data on success. | Inline      |

### Responses Data Schema

# Images

## POST Image to Text

POST /image/image2text

Image understanding endpoint. Upload an image and receive classification, caption, and segmentation results.

> Body Parameters

```yaml
model: default
emotion: default
text: Please describe the image.
category: default

```

### Params

| Name   | Location | Type           | Required | Description          |
| ------ | -------- | -------------- | -------- | -------------------- |
| body   | body     | object         | no       | none                 |
| » file | body     | string(binary) | yes      | Uploaded image file. |

> Response Examples

> 200 Response

```json
{
  "status": "success",
  "classification": "cat",
  "caption": "A cat sitting on a sofa.",
  "samUrl": "https://example.com/segmented-image.png"
}
```

### Responses

| HTTP Status Code | Meaning                                                 | Description          | Data schema |
| ---------------- | ------------------------------------------------------- | -------------------- | ----------- |
| 200              | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | Successful response. | Inline      |

### Responses Data Schema

HTTP Status Code **200**

| Name             | Type   | Required | Restrictions | Title | description                             |
| ---------------- | ------ | -------- | ------------ | ----- | --------------------------------------- |
| » status         | string | true     | none         |       | Status of the image understanding call. |
| » classification | string | true     | none         |       | Recognized image category.              |
| » caption        | string | true     | none         |       | Image recognition result.               |
| » samUrl         | string | true     | none         |       | Image segmentation result.              |

## POST Text to Image

POST /image/text2image

Text-to-image endpoint. Generates images from a prompt.

> Body Parameters

```json
{
  "prompt": "A cat sitting on a sofa."
}
```

### Params

| Name         | Location | Type   | Required | Description                  |
| ------------ | -------- | ------ | -------- | ---------------------------- |
| Content-Type | header   | string | no       | Request body content type.   |
| body         | body     | object | no       | none                         |
| » prompt     | body     | string | yes      | Prompt for image generation. |

> Response Examples

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

### Responses

| HTTP Status Code | Meaning                                                 | Description          | Data schema |
| ---------------- | ------------------------------------------------------- | -------------------- | ----------- |
| 200              | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | Successful response. | Inline      |

### Responses Data Schema

HTTP Status Code **200**

| Name      | Type     | Required | Restrictions | Title | description                                                  |
| --------- | -------- | -------- | ------------ | ----- | ------------------------------------------------------------ |
| » created | integer  | true     | none         |       | Unix timestamp (seconds) when the chat completion was created. |
| » data    | [object] | true     | none         |       | Generated image data.                                        |
| »» url    | string   | false    | none         |       | Generated image URL.                                         |

## POST Image Enhancement

POST /image/image2enhance

Image enhancement endpoint. Upload an image and receive the enhanced result URL.

> Body Parameters

```yaml
model: default
emotion: default
text: Please enhance this image.
category: default

```

### Params

| Name   | Location | Type           | Required | Description          |
| ------ | -------- | -------------- | -------- | -------------------- |
| body   | body     | object         | no       | none                 |
| » file | body     | string(binary) | yes      | Uploaded image file. |

> Response Examples

> 200 Response

```json
{
  "status": "success",
  "enhanceImageUrl": "https://example.com/enhanced-image.png"
}
```

### Responses

| HTTP Status Code | Meaning                                                 | Description          | Data schema |
| ---------------- | ------------------------------------------------------- | -------------------- | ----------- |
| 200              | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | Successful response. | Inline      |

### Responses Data Schema

HTTP Status Code **200**

| Name              | Type   | Required | Restrictions | Title | description                                                  |
| ----------------- | ------ | -------- | ------------ | ----- | ------------------------------------------------------------ |
| » enhanceImageUrl | string | true     | none         |       | Enhanced image URL.                                          |
| » status          | string | true     | none         |       | Execution status of the endpoint. success indicates success. |

# Video

## POST Image to Video

POST /image/image2video

Image-to-video endpoint. Upload an image and receive the generated video URL.

> Body Parameters

```yaml
model: default
emotion: default
text: Generate a short video from this image.
category: default

```

### Params

| Name   | Location | Type           | Required | Description          |
| ------ | -------- | -------------- | -------- | -------------------- |
| body   | body     | object         | no       | none                 |
| » file | body     | string(binary) | yes      | Uploaded image file. |

> Response Examples

> 200 Response

```json
{
  "status": "success",
  "svdVideoUrl": "https://example.com/generated-video.mp4"
}
```

### Responses

| HTTP Status Code | Meaning                                                 | Description          | Data schema |
| ---------------- | ------------------------------------------------------- | -------------------- | ----------- |
| 200              | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | Successful response. | Inline      |

### Responses Data Schema

HTTP Status Code **200**

| Name          | Type   | Required | Restrictions | Title | description                                                  |
| ------------- | ------ | -------- | ------------ | ----- | ------------------------------------------------------------ |
| » svdVideoUrl | string | true     | none         |       | Generated video URL.                                         |
| » status      | string | true     | none         |       | Execution status of the endpoint. success indicates success. |

## POST Video Tracking

POST /video/video2tracking

Video tracking endpoint. Upload a video and receive the tracked video result URL.

> Body Parameters

```yaml
file: file://./examples/video/sample.mp4

```

### Params

| Name   | Location | Type           | Required | Description          |
| ------ | -------- | -------------- | -------- | -------------------- |
| body   | body     | object         | no       | none                 |
| » file | body     | string(binary) | yes      | Uploaded video file. |

> Response Examples

> 200 Response

```json
{
  "status": "success",
  "data": "https://example.com/tracked-video.mp4"
}
```

### Responses

| HTTP Status Code | Meaning                                                 | Description          | Data schema |
| ---------------- | ------------------------------------------------------- | -------------------- | ----------- |
| 200              | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | Successful response. | Inline      |

### Responses Data Schema

HTTP Status Code **200**

| Name     | Type   | Required | Restrictions | Title | description               |
| -------- | ------ | -------- | ------------ | ----- | ------------------------- |
| » status | string | true     | none         |       | Returned result status    |
| » data   | string | true     | none         |       | Tracked video result URL. |

## POST Video Enhancement

POST /video/mmeditingInference

Video enhancement endpoint. Upload a video and receive the enhanced video URL.

> Body Parameters

```yaml
file: file://./examples/video/sample.mp4

```

### Params

| Name   | Location | Type           | Required | Description          |
| ------ | -------- | -------------- | -------- | -------------------- |
| body   | body     | object         | no       | none                 |
| » file | body     | string(binary) | yes      | Uploaded video file. |

> Response Examples

> 200 Response

```json
{
  "status": "success",
  "data": "https://example.com/enhanced-video.mp4"
}
```

### Responses

| HTTP Status Code | Meaning                                                 | Description          | Data schema |
| ---------------- | ------------------------------------------------------- | -------------------- | ----------- |
| 200              | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | Successful response. | Inline      |

### Responses Data Schema

HTTP Status Code **200**

| Name     | Type   | Required | Restrictions | Title | description                |
| -------- | ------ | -------- | ------------ | ----- | -------------------------- |
| » status | string | true     | none         |       | Returned result status     |
| » data   | string | true     | none         |       | Enhanced video result URL. |

# RAG

## POST Get Vectors

POST /v1/vector/get

Retrieve raw records from the vector store by condition.

> Body Parameters

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

### Params

| Name       | Location | Type    | Required | Description                                   |
| ---------- | -------- | ------- | -------- | --------------------------------------------- |
| body       | body     | object  | no       | none                                          |
| » category | body     | string  | yes      | Knowledge category or vector collection name. |
| » where    | body     | object  | yes      | Filter conditions.                            |
| » limit    | body     | integer | yes      | Maximum number of records to return.          |
| » offset   | body     | integer | yes      | Result offset.                                |

> Response Examples

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

### Responses

| HTTP Status Code | Meaning                                                 | Description          | Data schema |
| ---------------- | ------------------------------------------------------- | -------------------- | ----------- |
| 200              | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | Successful response. | Inline      |

### Responses Data Schema

HTTP Status Code **200**

| Name          | Type     | Required | Restrictions | Title | description                                                  |
| ------------- | -------- | -------- | ------------ | ----- | ------------------------------------------------------------ |
| » data        | [object] | true     | none         |       | API response data.                                           |
| »» document   | string   | true     | none         |       | Text content.                                                |
| »» id         | string   | true     | none         |       | none                                                         |
| »» metadata   | object   | true     | none         |       | Metadata associated with the text.                           |
| »»» category  | string   | true     | none         |       | Knowledge category or vector collection name.                |
| »»» file_id   | string   | true     | none         |       | File ID.                                                     |
| »»» filename  | string   | true     | none         |       | File name.                                                   |
| »»» filepath  | string   | true     | none         |       | File path.                                                   |
| »»» level     | string   | true     | none         |       | Knowledge level. Default is user.                            |
| »»» parent_id | string   | true     | none         |       | none                                                         |
| »»» source    | string   | true     | none         |       | none                                                         |
| » status      | string   | true     | none         |       | Execution status of the endpoint. success indicates success. |

## POST Add Vectors

POST /v1/vector/add

Vector insert endpoint for adding text and metadata.

> Body Parameters

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

### Params

| Name        | Location | Type     | Required | Description                                   |
| ----------- | -------- | -------- | -------- | --------------------------------------------- |
| body        | body     | object   | no       | none                                          |
| » category  | body     | string   | yes      | Knowledge category or vector collection name. |
| » data      | body     | [object] | yes      | List of text entries and metadata to insert.  |
| »» metadata | body     | object   | yes      | Metadata associated with the text.            |
| »» document | body     | string   | yes      | Text content.                                 |

> Response Examples

> 200 Response

```json
{
  "status": "success"
}
```

### Responses

| HTTP Status Code | Meaning                                                 | Description          | Data schema |
| ---------------- | ------------------------------------------------------- | -------------------- | ----------- |
| 200              | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | Successful response. | Inline      |

### Responses Data Schema

HTTP Status Code **200**

| Name     | Type   | Required | Restrictions | Title | description                                                  |
| -------- | ------ | -------- | ------------ | ----- | ------------------------------------------------------------ |
| » status | string | true     | none         |       | Execution status of the endpoint. success indicates success. |

## POST Update Vectors

POST /v1/vector/update

Vector update endpoint for updating text or metadata by record ID.

> Body Parameters

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

### Params

| Name        | Location | Type     | Required | Description                                   |
| ----------- | -------- | -------- | -------- | --------------------------------------------- |
| body        | body     | object   | no       | none                                          |
| » category  | body     | string   | yes      | Knowledge category or vector collection name. |
| » data      | body     | [object] | yes      | List of text entries and metadata to update.  |
| »» id       | body     | string   | yes      | none                                          |
| »» metadata | body     | object   | yes      | Metadata associated with the text.            |
| »»» chapter | body     | integer  | yes      | none                                          |
| »»» verse   | body     | integer  | yes      | none                                          |
| »» document | body     | string   | yes      | Text content.                                 |

> Response Examples

> 200 Response

```json
{
  "status": "success"
}
```

### Responses

| HTTP Status Code | Meaning                                                 | Description          | Data schema |
| ---------------- | ------------------------------------------------------- | -------------------- | ----------- |
| 200              | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | Successful response. | Inline      |

### Responses Data Schema

HTTP Status Code **200**

| Name     | Type   | Required | Restrictions | Title | description                                                  |
| -------- | ------ | -------- | ------------ | ----- | ------------------------------------------------------------ |
| » status | string | true     | none         |       | Execution status of the endpoint. success indicates success. |

## POST Delete Vectors

POST /v1/vector/delete

Vector delete endpoint for deleting vector data by record ID.

> Body Parameters

```json
{
  "category": "default",
  "ids": [
    "doc_001"
  ]
}
```

### Params

| Name       | Location | Type     | Required | Description                                   |
| ---------- | -------- | -------- | -------- | --------------------------------------------- |
| body       | body     | object   | no       | none                                          |
| » category | body     | string   | yes      | Knowledge category or vector collection name. |
| » ids      | body     | [string] | yes      | List of record IDs to delete.                 |

> Response Examples

> 200 Response

```json
{
  "status": "success"
}
```

### Responses

| HTTP Status Code | Meaning                                                 | Description          | Data schema |
| ---------------- | ------------------------------------------------------- | -------------------- | ----------- |
| 200              | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | Successful response. | Inline      |

### Responses Data Schema

HTTP Status Code **200**

| Name     | Type   | Required | Restrictions | Title | description                                                  |
| -------- | ------ | -------- | ------------ | ----- | ------------------------------------------------------------ |
| » status | string | true     | none         |       | Execution status of the endpoint. success indicates success. |

## POST Query Vectors

POST /v1/vector/query

Vector search endpoint that returns similar results for the query text.

> Body Parameters

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

### Params

| Name       | Location | Type    | Required | Description                                   |
| ---------- | -------- | ------- | -------- | --------------------------------------------- |
| body       | body     | object  | no       | none                                          |
| » text     | body     | string  | yes      | Input text.                                   |
| » n        | body     | integer | yes      | Number of nearest-neighbor results to return. |
| » where    | body     | object  | yes      | Filter conditions.                            |
| » category | body     | string  | yes      | Knowledge category or vector collection name. |

> Response Examples

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

### Responses

| HTTP Status Code | Meaning                                                 | Description          | Data schema |
| ---------------- | ------------------------------------------------------- | -------------------- | ----------- |
| 200              | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | Successful response. | Inline      |

### Responses Data Schema

HTTP Status Code **200**

| Name                      | Type     | Required | Restrictions | Title | description                                                  |
| ------------------------- | -------- | -------- | ------------ | ----- | ------------------------------------------------------------ |
| » data                    | [object] | true     | none         |       | API response data.                                           |
| »» document               | string   | true     | none         |       | Text content.                                                |
| »» id                     | string   | true     | none         |       | none                                                         |
| »» metadata               | object   | true     | none         |       | Metadata associated with the text.                           |
| »»» category              | string   | true     | none         |       | Knowledge category or vector collection name.                |
| »»» file_id               | string   | true     | none         |       | File ID.                                                     |
| »»» filename              | string   | true     | none         |       | File name.                                                   |
| »»» filepath              | string   | true     | none         |       | File path.                                                   |
| »»» level                 | string   | true     | none         |       | Knowledge level. Default is user.                            |
| »»» parent_id             | string   | true     | none         |       | none                                                         |
| »»» source                | string   | true     | none         |       | none                                                         |
| »»» reference_document_id | string   | true     | none         |       | none                                                         |
| »» distance               | number   | true     | none         |       | Similarity distance or score.                                |
| » status                  | string   | true     | none         |       | Execution status of the endpoint. success indicates success. |

## POST Embeddings

POST /v1/embeddings

Embedding endpoint. Returns vector results for the input text.

> Body Parameters

```json
{
  "model": "text-embedding",
  "input": [
    "Hello LinkMind"
  ]
}
```

### Params

| Name         | Location | Type     | Required | Description                   |
| ------------ | -------- | -------- | -------- | ----------------------------- |
| Content-Type | header   | string   | no       | Request body content type.    |
| body         | body     | object   | no       | none                          |
| » input      | body     | [string] | yes      | Text content to embed.        |
| » model      | body     | string   | yes      | Model name used for the call. |

> Response Examples

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

### Responses

| HTTP Status Code | Meaning                                                 | Description          | Data schema |
| ---------------- | ------------------------------------------------------- | -------------------- | ----------- |
| 200              | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | Successful response. | Inline      |

### Responses Data Schema

HTTP Status Code **200**

| Name     | Type    | Required | Restrictions | Title | description                                                  |
| -------- | ------- | -------- | ------------ | ----- | ------------------------------------------------------------ |
| » status | string  | true     | none         |       | Execution status of the endpoint. success indicates success. |
| » data   | [array] | true     | none         |       | Embedding vector list.                                       |

## GET List Vector Collections

GET /v1/vector/listCollections

List the current vector collections.

### Params

| Name         | Location | Type   | Required | Description                |
| ------------ | -------- | ------ | -------- | -------------------------- |
| Content-Type | header   | string | no       | Request body content type. |

> Response Examples

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

### Responses

| HTTP Status Code | Meaning                                                 | Description          | Data schema |
| ---------------- | ------------------------------------------------------- | -------------------- | ----------- |
| 200              | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | Successful response. | Inline      |

### Responses Data Schema

HTTP Status Code **200**

| Name           | Type     | Required | Restrictions | Title | description                                                  |
| -------------- | -------- | -------- | ------------ | ----- | ------------------------------------------------------------ |
| » status       | string   | true     | none         |       | Execution status of the endpoint. success indicates success. |
| » data         | [object] | true     | none         |       | List of vector collections.                                  |
| »» category    | string   | true     | none         |       | Knowledge category or vector collection name.                |
| »» vectorCount | integer  | true     | none         |       | Number of vectors in the collection.                         |

# Private Training

## POST Upload Training File

POST /training/upload

Upload a general knowledge file and write it into the private knowledge base.

> Body Parameters

```yaml
fileToUpload: file://./examples/docs/sample.pdf

```

### Params

| Name           | Location | Type           | Required | Description                                   |
| -------------- | -------- | -------------- | -------- | --------------------------------------------- |
| category       | query    | string         | yes      | Knowledge category or vector collection name. |
| level          | query    | string         | no       | Knowledge level. Default is user.             |
| body           | body     | object         | no       | none                                          |
| » fileToUpload | body     | string(binary) | yes      | Knowledge file to upload.                     |

> Response Examples

> 200 Response

```json
{
  "status": "success",
  "data": "[{\"filename\":\"sample.pdf\",\"filepath\":\"202504240001.pdf\",\"fileId\":\"file_001\",\"vectorIds\":[[\"vec_001\",\"vec_002\"]]}]",
  "task_id": "task_001"
}
```

### Responses

| HTTP Status Code | Meaning                                                 | Description          | Data schema |
| ---------------- | ------------------------------------------------------- | -------------------- | ----------- |
| 200              | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | Successful response. | Inline      |

### Responses Data Schema

HTTP Status Code **200**

| Name      | Type   | Required | Restrictions | Title | description                                                  |
| --------- | ------ | -------- | ------------ | ----- | ------------------------------------------------------------ |
| » status  | string | true     | none         |       | Execution status of the endpoint. success indicates success. |
| » data    | string | true     | none         |       | Upload result returned as a file-info JSON string.           |
| » task_id | string | true     | none         |       | Async task ID.                                               |

## POST Upload Q&A Pairs

POST /uploadFile/pairing

Upload general Q&A pairs and write them into the private knowledge base.

> Body Parameters

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

### Params

| Name           | Location | Type     | Required | Description                                                  |
| -------------- | -------- | -------- | -------- | ------------------------------------------------------------ |
| body           | body     | object   | no       | none                                                         |
| » category     | body     | string   | yes      | Knowledge category or vector collection name.                |
| » level        | body     | string   | no       | Knowledge level. Default is user.                            |
| » data         | body     | [object] | yes      | List of Q&A pairs.                                           |
| »» instruction | body     | [string] | yes      | List of questions or instructions. A single string is also accepted by the server. |
| »» output      | body     | string   | yes      | Answer or output content.                                    |

> Response Examples

> 200 Response

```json
{
  "status": "success"
}
```

### Responses

| HTTP Status Code | Meaning                                                 | Description          | Data schema |
| ---------------- | ------------------------------------------------------- | -------------------- | ----------- |
| 200              | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | Successful response. | Inline      |

### Responses Data Schema

HTTP Status Code **200**

| Name     | Type   | Required | Restrictions | Title | description                                                  |
| -------- | ------ | -------- | ------------ | ----- | ------------------------------------------------------------ |
| » status | string | true     | none         |       | Execution status of the endpoint. success indicates success. |

## GET List Uploaded Files

GET /uploadFile/getUploadFileList

Paginated query for uploaded knowledge files.

### Params

| Name       | Location | Type   | Required | Description    |
| ---------- | -------- | ------ | -------- | -------------- |
| category   | query    | string | no       | Data category. |
| pageSize   | query    | string | no       | Page size      |
| pageNumber | query    | string | no       | Page number    |

> Response Examples

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

### Responses

| HTTP Status Code | Meaning                                                 | Description          | Data schema |
| ---------------- | ------------------------------------------------------- | -------------------- | ----------- |
| 200              | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | Successful response. | Inline      |

### Responses Data Schema

HTTP Status Code **200**

| Name         | Type     | Required | Restrictions | Title | description          |
| ------------ | -------- | -------- | ------------ | ----- | -------------------- |
| » totalRow   | integer  | true     | none         |       | Total records        |
| » pageNumber | integer  | true     | none         |       | Page number          |
| » data       | [object] | true     | none         |       | Knowledge file list. |
| »» fileId    | string   | true     | none         |       | File ID              |
| »» filename  | string   | true     | none         |       | File name            |
| »» filepath  | string   | true     | none         |       | Relative file path   |
| »» category  | string   | true     | none         |       | Data category.       |
| » totalPage  | integer  | true     | none         |       | Total pages          |
| » pageSize   | integer  | true     | none         |       | Page size            |
| » status     | string   | true     | none         |       | Response status.     |

## POST Delete Uploaded File

POST /uploadFile/deleteFile

Delete uploaded knowledge files and their related vectors.

> Body Parameters

```json
[
  "file_001"
]
```

### Params

| Name     | Location | Type          | Required | Description                                   |
| -------- | -------- | ------------- | -------- | --------------------------------------------- |
| category | query    | string        | no       | Knowledge category or vector collection name. |
| body     | body     | array[string] | no       | none                                          |

> Response Examples

> 200 Response

```json
{
  "status": "success"
}
```

### Responses

| HTTP Status Code | Meaning                                                 | Description          | Data schema |
| ---------------- | ------------------------------------------------------- | -------------------- | ----------- |
| 200              | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | Successful response. | Inline      |

### Responses Data Schema

HTTP Status Code **200**

| Name     | Type   | Required | Restrictions | Title         | description                                                  |
| -------- | ------ | -------- | ------------ | ------------- | ------------------------------------------------------------ |
| » status | string | true     | none         | Return status | Execution status of the endpoint. success indicates success. |

# Secondary Development Conventions

Unless otherwise noted, the secondary-development endpoints below return JSON envelopes shaped like `{"status":"success|failed","msg":"...","data":...}`.

## OpenAI-Compatible `extra_body`

`POST /chat/completions` and `POST /v1/chat/completions` accept an optional `extra_body` object for business-side metadata.

```json
{
  "model": "qwen-plus",
  "stream": false,
  "messages": [
    {
      "role": "user",
      "content": "List my subscribed channels."
    }
  ],
  "extra_body": {
    "user_id": "u_1001"
  }
}
```

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `user_id` | string | no | Logical user identifier used by social skills, channel APIs, and user-aware workflows. |
| `user` | string | no | Alias of `user_id` for compatibility. |

Notes:

- `extra_body` is the recommended place to carry out-of-band business context without changing the OpenAI-compatible message schema.
- When the runtime injects skills, it may temporarily serialize this payload into an internal `<extra_body>...</extra_body>` block so the value survives tool execution. This is an internal transport detail; clients should keep sending normal JSON `extra_body`.
- In `Agent Mate` mode, `LandingAdapter` can auto-populate the current login user for local runtime calls.

# Secondary Development Interfaces

## Social Channel APIs

| Route | Method | Required input | Purpose | Success payload |
| --- | --- | --- | --- | --- |
| `/socialChannel/runningMode` | GET | none | Inspect whether the current node is running in `mate` or `server` mode. | `runningMode`, `isMateMode` |
| `/socialChannel/registerUser` | POST | `userId`, `username` | Register or synchronize a social user identity. | `created` |
| `/socialChannel/saveLastLoginUser` | POST | `userId` | Persist the current login user so local `Agent Mate` calls can auto-inject user context. | `status` |
| `/socialChannel/createChannel` | POST | `userId`, `name` | Create a channel and subscribe the owner automatically. Optional `description` and `isPublic` are accepted as compatibility fields. | `channelId` |
| `/socialChannel/subscribe` | POST | `userId`, `channelId` | Subscribe a user to a channel. | `msg: subscribed` |
| `/socialChannel/unsubscribe` | POST | `userId`, `channelId` | Remove a subscription. Channel owners cannot unsubscribe themselves. | `msg: unsubscribed` |
| `/socialChannel/listMyChannels` | GET | `userId` | List channels subscribed by the current user. | `data: SocialChannel[]` |
| `/socialChannel/listPublicChannels` | GET | none, `limit?` | List public channels available for discovery. | `data: SocialChannel[]` |
| `/socialChannel/listOwnedChannels` | GET | `userId` | List channels owned by the current user. | `data: SocialChannel[]` |
| `/socialChannel/getChannel` | GET | `channelId` | Read channel metadata after enablement checks. | `data: SocialChannel` |
| `/socialChannel/listMessages` | GET | `userId`, `channelId` | List channel messages for a subscribed user. Optional `limit` and `beforeId` support paging. | `data: SocialChannelMessage[]` |
| `/socialChannel/sendMessage` | POST | `userId`, `content`, and either `channelId` or `channelName` | Publish a message into a subscribed channel. | `messageId` |
| `/socialChannel/toggleChannel` | POST | `userId`, `channelId`, `enabled` | Enable or disable a channel. Owner only. | `msg: enabled|disabled` |
| `/socialChannel/deleteChannel` | POST | `userId`, `channelId` | Delete a channel. Owner only. | `msg: deleted` |

Notes:

- In `Agent Mate` mode, all social endpoints except `runningMode` and `saveLastLoginUser` can proxy automatically to the configured cascade server address.
- The `social-channel` skill consumes these routes instead of embedding storage logic into the chat adapter path.

## User And Authentication APIs

| Route | Method | Required input | Purpose | Success payload |
| --- | --- | --- | --- | --- |
| `/user/login` | POST | `username`, `password`, `captcha` | Authenticate a console or embedded user session. | `data.username`, `data.userId`, plus `lagi-auth` and `userId` cookies |
| `/user/register` | POST | `username`, `password`, `captcha` | Register a new user. `domainName` is accepted for compatibility and defaults to the username in the current implementation. | `status`, optional `channelId`, plus login cookies on success |
| `/user/authLoginCookie` | POST | `cookieValue` | Revalidate a persisted login cookie and refresh session cookies. | Same shape as `/user/login` |
| `/user/getCaptcha` | GET | none | Render the captcha image bound to the current HTTP session. Optional query params: `charNum`, `width`, `height`, `fontSize`. | JPEG binary |
| `/user/getRandomCategory` | GET | none | Return a generated or current default category. Optional `currentCategory` and `userId` are accepted. | `data.category` |
| `/user/getDefaultTitle` | GET | none | Read the configured default system title. | `data` |

These routes are the clean replacement point when you need to connect LinkMind to an existing SSO, tenant, or account-center implementation.

## API Key Management APIs

| Route | Method | Required input | Purpose | Success payload |
| --- | --- | --- | --- | --- |
| `/apiKey/list` | GET | none | List API keys visible to the current deployment. Optional `userId` enables Landing user-level keys. | `data: ModelApiKey[]`, `localApiKeyEditable` |
| `/apiKey/get` | GET | `modelName` | Read the configured key of one model. The response masks the secret. | `data.name`, `data.provider`, `data.api_key`, `data.api_address` |
| `/apiKey/providers` | GET | none | List provider types supported by the key-management UI. | `data: string[]`, `localApiKeyEditable` |
| `/apiKey/add` | POST | `name`, `provider`, `apiKey` | Add a new key and sync it into the active configuration when applicable. Optional `model`, `apiAddress`, and `userId` are supported. | `msg: add success` |
| `/apiKey/delete` | POST | `provider` plus `apiKey` or `id` | Remove a key from local config or a Landing user pool. `userId` is required for Landing keys. | `msg: delete success` |
| `/apiKey/toggle` | POST | `id`, `provider`, `enabled` | Activate or deactivate a key in the current configuration or remote Landing pool. Optional `userId` is supported. | `msg: toggle success` |

Notes:

- The service can read both `api_key` and `api_keys`, so a single provider can expose a key pool without changing the external HTTP contract.
- When `localApiKeyEditable` is `false`, the deployment treats local YAML keys as read-only and only manages remote Landing keys through this interface.

## Credit And Billing APIs

| Route | Method | Required input | Purpose | Success payload |
| --- | --- | --- | --- | --- |
| `/credit/prepay` | POST | `lagiUserId`, `fee` | Start a prepay order. | `outTradeNo`, `qrCode`, `mWebUrl`, `totalFee`, `result` |
| `/credit/getChargeDetail` | GET | `outTradeNo` | Read one charge record by payment order number. | `seq`, `userId`, `amount`, `time`, `status` |
| `/credit/getChargeDetailByUserId` | GET | `userId` | List charge records of one user. | `data: ChargeDetail[]` |
| `/credit/getCreditUserBalance` | GET | `userId` | Read the current balance of one user. | `data.userId`, `data.balance` |
| `/credit/getChargeDetailBySeq` | GET | `seq` | Read one charge record by sequence number. | `data: ChargeDetail` |

These billing routes are intentionally isolated from model routing and chat execution so enterprise deployments can replace the backing billing provider while keeping the same console and HTTP integration contract.

## Secondary-Development Data Objects

| Object | Key fields |
| --- | --- |
| `SocialChannel` | `id`, `name`, `description`, `ownerUserId`, `isPublic`, `enabled`, `createdAt` |
| `SocialChannelMessage` | `id`, `channelId`, `channelName`, `userId`, `userName`, `content`, `createdAt` |
| `ModelApiKey` | `id`, `name`, `provider`, `apiKey`, `apiAddress`, `createdTime`, `userId`, `status` |
| `CreditUserBalance` | `userId`, `balance` |
| `ChargeDetail` | `seq`, `userId`, `amount`, `time`, `status` |

# Data Schema


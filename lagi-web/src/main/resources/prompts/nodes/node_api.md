api（api节点）
- **功能描述**：调用api的节点
- **输入**：url (请求地址),method(请求方式 GET,POST,PUT,DELETE),headers(请求头 json形式),body(请求体 json形式)
- **输出**：接口的返回的接口请求响应的body和状态码statusCode
- **使用场景**：明确指出需要调用api请求网络资源的场景
- **注意事项**： url 参数是必须的
===========================
api节点（调用api）

| 字段路径                               | 类型     | 必填 | 规范要求                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
|------------------------------------|--------|----|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| id                                 | string | 是  | 遵循1.1规则，如api_1或api_BRQB8                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| type                               | string | 是  | 固定为"api"                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| meta                               | object | 是  | 含`position`（x≈2300，y≈500，参考demo）                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| data                               | object | 是  | 含`title`、`inputsValues`、`inputs`、`outputs`：<br>`title`：默认"API_序号"（API_1）<br>`inputsValues`：输入参数（url,method,header,body）<br>`inputs`：输入定义（`required`=["url,method,headers,body"]）<br>`outputs`：输出定义（`required`=["statusCode,body"]）                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| data.inputs                        | object | 是  | 输入的属性定义,该节点为固定的结构，不可修改, object :   { "type": "object", "required": ["url", "method"], "properties": { "url": { "type": "string", "description": "API请求地址" }, "method": { "type": "string", "enum": ["POST", "PUT", "DELETE", "PATCH", "GET"], "description": "HTTP请求方法" }, "query": { "type": "string", "extra": { "formComponent": "prompt-editor", "placeholder": "查询参数(JSON格式)" }, "description": "URL查询参数" }, "headers": { "type": "string", "extra": { "formComponent": "prompt-editor", "placeholder": "请求头(JSON格式)" }, "description": "请求头信息" }, "body": { "type": "string", "extra": { "formComponent": "prompt-editor", "language": "json", "placeholder": "请求体数据" }, "description": "请求体内容" } } }  |
| data.inputsValues.url              | object | 是  | api请求的url地址：<br>`type`："constant"（固定"default"）或"ref"<br>`content`："default"或引用值                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| data.inputsValues.method           | object | 是  | 请求方式：<br>`type`："constant"（枚举"POST","GET","PUT","DELETE"）或"ref"<br>`content`："POST" 或引用值                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| data.inputsValues.headers          | object | 是  | 请求头："template"（模板，含{{变量}}）<br>`content`：请求头 json 形式 (如: "{"Authorization": "Bearer {{start_0.api_key}}"} , 默认:{}")                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| data.inputsValues.body             | object | 是  | 请求体："template"（模板，含{{变量}}）<br>`content`：请求体json 模版（如"{"city":{{llm_0.result}}} , 默认:{} "                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| data.outputs.properties.statusCode | object | 是  | 响应的状态码：<br>`type`："number" <br>`description` : 响应的状态码                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| data.outputs.properties.body       | object | 是  | 响应体：<br>`type`："number" <br>`description` : 响应体                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |

#### api节点data示例（参考demo）
```json
{
    "id": "api_h1lN1",
    "type": "api",
    "meta": {
        "position": {
            "x": 2491.661023485059,
            "y": 374.22007828353065
        }
    },
    "data": {
        "title": "API调用_1",
        "inputsValues": {
            "url": {
                "type": "constant",
                "content": "https://xxxx.com"
            },
            "method": {
                "type": "constant",
                "content": "POST"
            },
            "query": {
                "type": "template",
                "content": "{}"
            },
            "headers": {
                "type": "template",
                "content": "{\n\"Content-Type\": \"application/json\",\n\"Authorization\": \"Bearer {{start_0.api_key}}\"\n}"
            },
            "body": {
                "type": "template",
                "content": "{\"city\":  \"{{program_IIg5D.result}}\"}"
            }
        },
        "inputs": {
            "type": "object",
            "required": ["url", "method"],
            "properties": {
                "url": {
                    "type": "string",
                    "description": "API请求地址"
                },
                "method": {
                    "type": "string",
                    "enum": ["POST", "PUT", "DELETE", "PATCH", "GET"],
                    "description": "HTTP请求方法"
                },
                "query": {
                    "type": "string",
                    "extra": {
                        "formComponent": "prompt-editor",
                        "placeholder": "查询参数(JSON格式)"
                    },
                    "description": "URL查询参数"
                },
                "headers": {
                    "type": "string",
                    "extra": {
                        "formComponent": "prompt-editor",
                        "placeholder": "请求头(JSON格式)"
                    },
                    "description": "请求头信息"
                },
                "body": {
                    "type": "string",
                    "extra": {
                        "formComponent": "prompt-editor",
                        "language": "json",
                        "placeholder": "请求体数据"
                    },
                    "description": "请求体内容"
                }
            }
        },
        "outputs": {
            "type": "object",
            "properties": {
                "statusCode": {
                    "type": "number",
                    "description": "HTTP响应状态码"
                },
                "body": {
                    "type": "string",
                    "description": "HTTP响应体内容"
                }
            },
            "required": ["statusCode", "body"]
        }
    }
}
```
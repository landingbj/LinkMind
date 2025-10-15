llm（大语言模型节点）
- **功能描述**：调用大语言模型进行文本生成
- **输入**：提示词（prompt）、用户输入、可选的上下文信息、可选的模型名称
- **输出**：LLM生成的文本内容
- **使用场景**：需要进行文本生成、总结、改写、问答等任务
- **注意事项**：可以配置使用的模型、温度等参数
=======================================================
llm节点（大语言模型调用）

| 字段路径                     | 类型     | 必填 | 规范要求                                                                                                                                                                                                                                                                             |
|--------------------------|--------|----|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| id                       | string | 是  | 遵循1.1规则，如llm_1或llm_AV9ZI                                                                                                                                                                                                                                                         |
| type                     | string | 是  | 固定为"llm"                                                                                                                                                                                                                                                                         |
| meta                     | object | 是  | 含`position`（x≈1565，y≈470，参考demo）                                                                                                                                                                                                                                                 |
| data                     | object | 是  | 含`title`、`inputsValues`、`inputs`、`outputs`：<br>`title`：默认"LLM_序号"（如LLM_1）<br>`inputsValues`：输入参数（model和prompt）<br>`inputs`：输入参数定义（`type`="object"，`required`=["model","prompt"]，`properties`含model/prompt的类型）<br>`outputs`：输出定义（`type`="object"，`properties`含`result`（string类型）） |
| data.inputsValues.model  | object | 是  | 模型名：<br>`type`："ref"（引用start输出）或"constant"（固定值，如"qwen-turbo"）<br>`content`：对应值（如["start_0","modelName"]或"qwen-turbo"）                                                                                                                                                            |
| data.inputsValues.prompt | object | 是  | 提示词：<br>`type`："ref"（引用其他节点输出）或"template"（模板，含{{变量}}）<br>`content`：提示词内容（如"以{{start_0.query}}为题写文章"）                                                                                                                                                                             |

#### llm节点data示例（参考demo）

```json
"data": {
"title": "LLM_1",
"inputsValues": {
"model": {"type": "ref", "content": ["start_0", "modelName"] }, // 引用start的modelName
"prompt": {"type": "template", "content": "以{{start_0.query}}为题，写一篇200字文章"
}  // 模板变量
},
"inputs": {
"type": "object",
"required": ["model", "prompt"], // 必须包含这两个参数
"properties": {
"model": {
"type": "string" },
"prompt": {
"type": "string",
"extra": {
"formComponent": "prompt-editor"
}  // 固定extra字段
}
}
},
"outputs": {
"type": "object",
"properties": { "result": {"type": "string"}}  // 输出result字段
}
}
```

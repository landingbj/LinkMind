start（开始节点）
- **功能描述**：工作流的起始节点，接收用户的初始输入
- **输入**：用户的query文本
- **输出**：可以输出用户query，也可以添加其他初始参数（如modelName等）
- **使用场景**：每个工作流的第一个节点
- **注意事项**：每个工作流必须有且只有一个开始节点
==========================
start节点（流程起始，不可新增/删除）

| 字段路径            | 类型     | 必填 | 规范要求                                                                                                                                                  |
|-----------------|--------|----|-------------------------------------------------------------------------------------------------------------------------------------------------------|
| id              | string | 是  | 遵循1.1规则，如start_0                                                                                                                                      |
| type            | string | 是  | 固定为"start"                                                                                                                                            |
| meta            | object | 是  | 含`position`对象（x/y为number，参考demo：x≈180，y≈350，避免与其他节点重叠）                                                                                                |
| meta.position.x | number | 是  | 建议从180开始，后续节点x递增300-500（如intent节点x=640，condition节点x=1100）                                                                                             |
| meta.position.y | number | 是  | 建议在300-400之间，保持与后续节点垂直对齐                                                                                                                              |
| data            | object | 是  | 含`title`和`outputs`                                                                                                                                    |
| data.title      | string | 是  | 默认"开始"，可按需求修改                                                                                                                                         |
| data.outputs    | object | 是  | **必须根据【提取的变量】中的startNodeVariables生成**<br>`type`：固定"object"<br>`properties`：输出变量列表（每个变量含key/name/isPropertyRequired/type/default/extra）<br>`required`：根据变量的required字段决定 |

#### start节点data.outputs生成规则（基于提取的变量）

**关键约束**：start节点的 `data.outputs.properties` 必须从【提取的变量】的 `startNodeVariables` 生成，遵循以下映射：

```
【提取的变量】中的每个 startNodeVariable:
{
  "name": "变量名",
  "type": "变量类型",
  "desc": "描述",
  "required": true/false
}

↓ 映射为 ↓

data.outputs.properties[变量名]:
{
  "key": 索引序号（从0开始）,
  "name": "变量名"（与startNodeVariable.name完全一致）,
  "isPropertyRequired": false（通常为false）,
  "type": "变量类型"（与startNodeVariable.type完全一致）,
  "default": ""（可选，根据实际需求）,
  "extra": {"index": 索引序号}
}
```

#### start节点示例（基于提取的变量）

假设【提取的变量】为：
```json
{
  "startNodeVariables": [
    {"name": "query", "type": "string", "desc": "用户问题", "required": true},
    {"name": "modelName", "type": "string", "desc": "模型名称", "required": false}
  ]
}
```

则生成的start节点为：
```json
"data": {
  "title": "开始",
  "outputs": {
    "type": "object",
    "properties": {
      "query": {
        "key": 0,
        "name": "query",
        "isPropertyRequired": false,
        "type": "string",
        "default": "",
        "extra": {"index": 0}
      },
      "modelName": {
        "key": 1,
        "name": "modelName",
        "isPropertyRequired": false,
        "type": "string",
        "extra": {"index": 1}
      }
    },
    "required": []
  }
}
```
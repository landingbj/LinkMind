condition（条件判断节点）
- **功能描述**：根据条件判断选择不同的执行分支
- **输入**：用于判断的值（通常来自上一节点的输出）
- **输出**：根据不同条件输出到不同的分支
- **使用场景**：需要根据某个值进行if-else分支判断
- **注意事项**：需要明确定义判断条件和各个分支的出口
=================================================
condition节点（条件分支）

| 字段路径                             | 类型     | 必填 | 规范要求                                                                                                  |
|----------------------------------|--------|----|-------------------------------------------------------------------------------------------------------|
| id                               | string | 是  | 遵循1.1规则，如condition_0或condition_lhcoc9                                                                 |
| type                             | string | 是  | 固定为"condition"                                                                                        |
| meta                             | object | 是  | 含`position`（x≈1100，y≈350，参考demo）                                                                      |
| data                             | object | 是  | 含`title`和`conditions`（分支条件数组）                                                                         |
| data.title                       | string | 是  | 默认"条件判断"，可修改                                                                                          |
| data.conditions                  | array  | 是  | 每个元素为1个分支条件，结构如下：<br>`key`：分支唯一标识（如"if_lhcoc9"，参考TS的nanoid(5)）<br>`value`：条件表达式（含left/operator/right） |
| data.conditions[].value.left     | object | 是  | 左值（引用其他节点输出）：<br>`type`：固定"ref"<br>`content`：数组["源节点ID", "源节点输出字段"]（如["intent_imMwF", "intent"]）      |
| data.conditions[].value.operator | string | 是  | 比较运算符（如"eq"等于、"ne"不等于、"gt"大于）                                                                         |
| data.conditions[].value.right    | object | 是  | 右值（常量或模板）：<br>`type`："constant"（常量）或"template"（模板）<br>`content`：值内容（如"text"、"{{start_0.query}}"）      |

#### condition节点data.conditions示例（参考demo）

```json
"data": {
"title": "条件判断",
"conditions": [
{
"key": "if_lhcoc9", // 分支1标识
"value": {
"type": "expression",
"content": "",
"left": {"type": "ref", "content": ["intent_imMwF", "intent"] }, // 引用意图识别节点的intent输出
"operator": "eq", // 等于
"right": {"type": "constant", "content": "循环需求"
}  // 右值为常量
}
},
{
"key": "if_le493Z", // 分支2标识
"value": {
"type": "expression",
"content": "",
"left": {"type": "ref", "content": ["intent_imMwF", "intent"] },
"operator": "eq",
"right": {"type": "constant", "content": "LLM生成需求"
}
}
}
]
}
```
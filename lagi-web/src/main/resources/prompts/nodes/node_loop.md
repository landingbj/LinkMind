loop（循环节点）
- **功能描述**：对列表数据进行遍历处理
- **输入**：需要遍历的数据列表
- **输出**：每次迭代的结果，或最终汇总的结果
- **使用场景**：需要批量处理多个数据项
- **注意事项**：循环体内可以包含其他节点，形成子工作流
========================================
loop节点（循环，含子画布）

| 字段路径   | 类型     | 必填 | 规范要求                                                                                                                                                                                                      |
|--------|--------|----|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| id     | string | 是  | 遵循1.1规则，如loop_1或loop_CwoCJ                                                                                                                                                                                |
| type   | string | 是  | 固定为"loop"                                                                                                                                                                                                 |
| meta   | object | 是  | 含`position`（x≈1900，y≈50，参考demo）                                                                                                                                                                           |
| data   | object | 是  | 含`title`、`batchFor`、`batchOutputs`：<br>`title`：默认"循环_序号"（如循环_1）<br>`batchFor`：循环数据源（`type`="ref"，`content`=["源节点ID","数组字段"]）<br>`batchOutputs`：循环输出（`result`字段，`type`="ref"，`content`=["子节点ID","result"]） |
| blocks | array  | 是  | 子画布节点数组，必须包含：<br>1. `block-start`类型节点（子流程起始）<br>2. `block-end`类型节点（子流程结束）<br>3. 循环内的业务节点（如llm、program）                                                                                                    |
| edges  | array  | 是  | 子画布内的连接关系（仅连接blocks中的节点），结构同顶层edges                                                                                                                                                                       |

#### loop节点blocks+edges示例（参考demo）

```json
"blocks": [
{
"id": "block_start_AL4x0",
"type": "block-start", // 必须有
"meta": {"position": {"x": 31.7, "y": 82.15
}
},
"data": {
}
},
{
"id": "llm_Osa_C", // 循环内的LLM节点
"type": "llm",
"meta": {"position": {"x": 348.75, "y": 0}},
"data": {/* 参考2.4 LLM节点结构 */}
},
{
"id": "block_end_4Tmpw",
"type": "block-end", // 必须有
"meta": {
"position": {
"x": 665.8, "y": 82.15
}
},
"data": {}
}
],
"edges": [
// 子画布内连接
{"sourceNodeID": "block_start_AL4x0", "targetNodeID": "llm_Osa_C"},
{"sourceNodeID": "llm_Osa_C", "targetNodeID": "block_end_4Tmpw"}
]
```

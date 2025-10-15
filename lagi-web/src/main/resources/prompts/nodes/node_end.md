 end（结束节点）
- **功能描述**：工作流的结束节点，输出最终结果
- **输入**：接收上一步节点的输出
- **输出**：提取并输出最终结果给用户
- **使用场景**：工作流的最后一个节点
- **注意事项**：每个工作流至少要有一个结束节点，可以有多个结束节点（不同分支的结束）
===============================
end节点（流程结束）

| 字段路径 | 类型     | 必填 | 规范要求                                                                                                                                                                   |
|------|--------|----|------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| id   | string | 是  | 遵循1.1规则，如end_0                                                                                                                                                         |
| type | string | 是  | 固定为"end"                                                                                                                                                               |
| meta | object | 是  | 含`position`（x≈3028，y≈352，参考demo，需在所有节点最右侧）                                                                                                                             |
| data | object | 是  | **必须根据【提取的变量】中的endNodeVariables生成**<br>`title`：默认"结束"<br>`inputs`：定义接收的输入变量<br>`inputsValues`：引用其他节点的输出（`type`="ref"，`content`=["源节点ID","字段名"]） |

#### end节点生成规则（基于提取的变量）

**关键约束**：end节点的 `data.inputs` 和 `data.inputsValues` 必须从【提取的变量】的 `endNodeVariables` 生成，遵循以下映射：

```
【提取的变量】中的每个 endNodeVariable:
{
  "name": "变量名",
  "type": "变量类型",
  "desc": "描述",
  "source": "来源说明"
}

↓ 映射为 ↓

data.inputs.properties[变量名]:
{
  "type": "变量类型"（与endNodeVariable.type完全一致）
}

data.inputsValues[变量名]:
{
  "type": "ref",
  "content": ["源节点ID", "字段名"]
}
```

**注意**：`data.inputsValues` 中的 `content` 数组需要根据【节点匹配结果】和实际工作流结构，找到产生该变量的源节点ID和字段名。

#### end节点示例（基于提取的变量）

假设【提取的变量】为：
```json
{
  "endNodeVariables": [
    {"name": "answer", "type": "string", "desc": "最终回答", "source": "LLM节点的输出"}
  ]
}
```

假设实际工作流中最后一个LLM节点的ID为 `llm_1`，则生成的end节点为：
```json
"data": {
  "title": "结束",
  "inputs": {
    "type": "object",
    "required": ["answer"],
    "properties": {
      "answer": {
        "type": "string"
      }
    }
  },
  "inputsValues": {
    "answer": {
      "type": "ref",
      "content": ["llm_1", "result"]
    }
  }
}
```

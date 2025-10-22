parallel（并行节点）
- **功能描述**：对多节点并行执行, 并将并行结果保存至数组
- **输入**：无需输入
- **输出**：并行结果保存至最终数组
- **使用场景**：需要并行运行多节点的场景
- **注意事项**：并行节点内可以包含多分支子工作流
========================================
parallel节点（并行节点，含子画布）

| 字段路径                                     | 类型     | 必填 | 规范要求                                                                                                                                                                                                                                   |
|------------------------------------------|--------|----|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| id                                       | string | 是  | 遵循1.1规则，如parallel_1或parallel_CwoCJ                                                                                                                                                                                                     |
| type                                     | string | 是  | 固定为"parallel"                                                                                                                                                                                                                          |
| meta                                     | object | 是  | 含`position`（x≈1900，y≈50，参考demo）                                                                                                                                                                                                        |
| data                                     | object | 是  | 含`title`、 `outputs`：<br>`title`：默认"并行_序号"（如并行_1）<br>  `outputs`: { "type":"object", "properties":{ "parallelResults":{ "type":"array", "description":"所有并行分支的结果数组", "items":{ "type":"object" } } }, "required":["parallelResults"] }  |
| blocks                                   | array  | 是  | 子画布节点数组，必须包含：<br>1. `block-start`类型节点（子流程起始）<br>2. `block-end`类型节点（子流程结束）<br>3. 循环内的业务节点（如llm、program）                                                                                                                                 |
| edges                                    | array  | 是  | 子画布内的连接关系（仅连接blocks中的节点），结构同顶层edges                                                                                                                                                                                                    |

#### parallel节点blocks+edges示例（参考demo）

```json
{
  "id": "parallel_4RMtJ",
  "type": "parallel",
  "meta": {
    "position": {
      "x": 560,
      "y": 90
    }
  },
  "data": {
    "title": "并行任务_1",
    "outputs": {
      "type": "object",
      "properties": {
        "parallelResults": {
          "type": "array",
          "description": "所有并行分支的结果数组",
          "items": {
            "type": "object"
          }
        }
      },
      "required": ["parallelResults"]
    }
  },
  "blocks": [{
    "id": "parallel_start_Udwsg",
    "type": "block-start",
    "meta": {
      "position": {
        "x": 32,
        "y": 173
      }
    },
    "data": {}
  },
    {
      "id": "parallel_end_XjZ8f",
      "type": "block-end",
      "meta": {
        "position": {
          "x": 656,
          "y": 173
        }
      },
      "data": {}
    },
    {
      "id": "image2detect_mokuT",
      "type": "image2detect",
      "meta": {
        "position": {
          "x": 344,
          "y": 0
        }
      },
      "data": {
        /*参考 实际的节点类型的data*/
      }
    },
    {
      "id": "image2detect_k-86q",
      "type": "image2detect",
      "meta": {
        "position": {
          "x": 344,
          "y": 255
        }
      },
      "data": {/*参考 实际的节点类型的data*/ }
    }],
  "edges": [{
    "sourceNodeID": "parallel_start_Udwsg",
    "targetNodeID": "image2detect_mokuT"
  },
    {
      "sourceNodeID": "parallel_start_Udwsg",
      "targetNodeID": "image2detect_k-86q"
    },
    {
      "sourceNodeID": "image2detect_k-86q",
      "targetNodeID": "parallel_end_XjZ8f"
    },
    {
      "sourceNodeID": "image2detect_mokuT",
      "targetNodeID": "parallel_end_XjZ8f"
    }]
}
```

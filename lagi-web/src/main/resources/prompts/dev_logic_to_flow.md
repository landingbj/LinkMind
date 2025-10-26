# 业务逻辑到流程图提示词

## 任务

基于以下业务逻辑描述，创建结构化的 JSON 格式流程图。

## 你的目标

1. 识别所有业务流程和步骤
2. 确定操作的流程和顺序
3. 识别决策点和分支
4. 映射组件之间的数据流
5. 创建结构化的 JSON 表示

## 要求的 JSON 格式

返回以下 JSON 格式的流程图：

```json
{
  "flowDiagram": {
    "name": "整体工作流名称",
    "description": "简要描述",
    "nodes": [
      {
        "id": "node1",
        "type": "start|process|decision|end",
        "label": "节点标签",
        "description": "该节点的功能"
      }
    ],
    "edges": [
      {
        "from": "node1",
        "to": "node2",
        "label": "条件或操作"
      }
    ],
    "processes": [
      {
        "name": "流程名称",
        "steps": ["步骤1", "步骤2"],
        "description": "流程描述"
      }
    ]
  }
}
```

## 业务逻辑描述

${{BUSINESS_LOGIC_DESCRIPTION}}

## 输出

请提供 JSON 格式的流程图：


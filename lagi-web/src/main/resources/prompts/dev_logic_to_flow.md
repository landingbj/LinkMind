## 任务

基于以下业务逻辑描述，创建结构化的 JSON 格式流程图。

## 额外描述信息

${{DESCRIPTION}}

**注意：** 额外描述信息可能为空、不完整或包含不相关内容。请根据实际情况判断：
- 如果描述信息有助于理解业务流程，则结合使用
- 如果描述信息为空或与业务逻辑无关，则忽略它，仅基于业务逻辑描述生成流程图
- 如果描述信息部分相关，则只采纳相关部分

## 你的目标

1. 识别所有业务流程和步骤
2. **如果额外描述信息相关且有用**，结合它理解业务背景和目的
3. 确定操作的流程和顺序
4. **重点识别和标注决策点及其分支**
5. **为每个决策点创建对应的分支节点**
6. 映射组件之间的数据流
7. 创建结构化的 JSON 表示，清晰展现业务逻辑分支
8. 可以为了流程图布局和可读性重复出现相同含义的节点（如“返回异常”、“返回错误”、“失败处理”等），避免线条交叉或过于拥挤
9. **主要依据业务逻辑描述生成流程图**，额外描述信息仅作为补充参考
10. 确保整个流程图只有一个开始节点（唯一的起点），所有流程必须从该节点出发

## 要求的 JSON 格式

返回以下 JSON 格式的流程图，包含 nodes（节点）和 edges（连线）两部分：

### JSON 结构说明

```json
{
  "nodes": [
    {
      "id": "节点唯一ID（例如：code_logic_xxxxx）",
      "type": "code-logic",
      "data": {
        "title": "节点标题",
        "content": "节点详细描述"
      }
    }
  ],
  "edges": [
    {
      "sourceNodeID": "源节点ID",
      "targetNodeID": "目标节点ID"
    }
  ]
}
```

### 节点布局规则

1. **节点ID生成**: 使用 `code_logic_` 前缀 + 5位随机字符（如：`code_logic_3w6-G`）
2. **节点类型**: 统一使用 `"code-logic"`
3. **节点内容**:
   - `title`: 简短的节点名称
     - 对于决策节点，使用简洁的描述（如："库存检查"、"验证订单数据"）
     - 对于分支节点，明确标注分支条件（如："库存充足-锁定库存"、"库存不足-返回错误"）
   - `content`: 详细的业务逻辑描述
     - 决策节点要说明判断的条件、依据和分支情况
     - 分支节点要说明该分支的触发条件和执行逻辑

### 连线规则

1. 使用 `sourceNodeID` 和 `targetNodeID` 表示节点间的连接关系
2. 按照业务流程的执行顺序建立连线
3. **从决策节点必须连接到多个分支节点（至少2个）**
4. 支持分支和汇聚（一个节点可以连接到多个节点，也可以被多个节点连接）

### 决策点和分支处理规则（重要）

1. **识别决策点**: 从业务逻辑描述中识别所有【决策点N】标记
2. **创建决策节点**: 为每个决策点创建一个节点
   - `title`: 使用简洁的描述（如："库存检查"、"验证订单"）
   - `content`: 说明判断条件、依据和可能的分支情况
3. **创建分支节点**: 
   - 为每个决策结果创建对应的分支节点
   - 分支节点垂直排列，y坐标不同
   - 分支节点的 title 要包含条件说明（如："库存充足-锁定库存"、"库存不足-返回错误"）
- 可重复节点策略: 为了保持流程图清晰、美观，当多个分支汇聚到相同的业务处理（例如返回错误、失败处理、异常返回、异常捕获、合法性验证等）时，可以在不同位置重复创建含义相同的节点，避免线条交叉。
- **建立连线**: 
   - 决策节点 → 各个分支节点
   - 分支节点 → 后续流程节点
5. **异常分支**: 对于流程结束的异常分支，也要创建独立节点；如需保持结构清晰，可在不同位置重复创建相同含义的异常/错误节点，避免连线交叉

## 输出示例

基于"用户订单处理流程"的完整 JSON 示例（重点展示决策点和分支）：

```json
{
  "nodes": [
    {
      "id": "code_logic_start",
      "type": "code-logic",
      "data": {
        "title": "接收订单请求",
        "content": "用户通过API提交订单，包含商品ID、数量和用户ID。OrderController接收HTTP请求并进行初步参数校验。"
      }
    },
    {
      "id": "code_logic_valid",
      "type": "code-logic",
      "data": {
        "title": "验证订单数据",
        "content": "OrderService验证订单数据完整性，检查用户身份和权限，确认商品ID有效性。根据验证结果进入不同分支：验证成功则继续库存检查，验证失败则返回错误。"
      }
    },
    {
      "id": "code_logic_valid_fail",
      "type": "code-logic",
      "data": {
        "title": "验证失败-返回错误",
        "content": "数据验证未通过，返回验证错误信息给用户，流程结束。"
      }
    },
    {
      "id": "code_logic_stock",
      "type": "code-logic",
      "data": {
        "title": "库存检查",
        "content": "InventoryService查询商品当前库存，判断库存是否满足订单数量需求。根据库存情况进入不同分支：库存充足则锁定库存继续流程，库存不足则返回错误结束流程。"
      }
    },
    {
      "id": "code_logic_stock_ok",
      "type": "code-logic",
      "data": {
        "title": "库存充足-锁定库存",
        "content": "库存满足需求，预锁定对应数量的库存，防止超卖。"
      }
    },
    {
      "id": "code_logic_stock_fail",
      "type": "code-logic",
      "data": {
        "title": "库存不足-返回错误",
        "content": "库存不足，返回错误信息给用户，流程结束。"
      }
    },
    {
      "id": "code_logic_price",
      "type": "code-logic",
      "data": {
        "title": "计算订单价格",
        "content": "PriceCalculator获取商品单价，计算总价，应用用户折扣和优惠券，得出最终应付金额。"
      }
    },
    {
      "id": "code_logic_create",
      "type": "code-logic",
      "data": {
        "title": "创建订单记录",
        "content": "OrderRepository将订单信息保存到数据库，生成唯一订单ID，记录订单状态为待支付。"
      }
    },
    {
      "id": "code_logic_update",
      "type": "code-logic",
      "data": {
        "title": "更新库存",
        "content": "InventoryService正式扣减库存数量，更新库存表。"
      }
    },
    {
      "id": "code_logic_notify",
      "type": "code-logic",
      "data": {
        "title": "发送通知",
        "content": "NotificationService发送订单确认通知给用户，包含订单详情和支付链接。"
      }
    },
    {
      "id": "code_logic_end",
      "type": "code-logic",
      "data": {
        "title": "返回订单结果",
        "content": "OrderController返回订单ID和订单详情给客户端，订单创建流程完成。"
      }
    }
  ],
  "edges": [
    {
      "sourceNodeID": "code_logic_start",
      "targetNodeID": "code_logic_valid"
    },
    {
      "sourceNodeID": "code_logic_valid",
      "targetNodeID": "code_logic_stock"
    },
    {
      "sourceNodeID": "code_logic_valid",
      "targetNodeID": "code_logic_valid_fail"
    },
    {
      "sourceNodeID": "code_logic_stock",
      "targetNodeID": "code_logic_stock_ok"
    },
    {
      "sourceNodeID": "code_logic_stock",
      "targetNodeID": "code_logic_stock_fail"
    },
    {
      "sourceNodeID": "code_logic_stock_ok",
      "targetNodeID": "code_logic_price"
    },
    {
      "sourceNodeID": "code_logic_price",
      "targetNodeID": "code_logic_create"
    },
    {
      "sourceNodeID": "code_logic_create",
      "targetNodeID": "code_logic_update"
    },
    {
      "sourceNodeID": "code_logic_update",
      "targetNodeID": "code_logic_notify"
    },
    {
      "sourceNodeID": "code_logic_notify",
      "targetNodeID": "code_logic_end"
    }
  ]
}
```

---

## 业务逻辑描述

${{BUSINESS_LOGIC_DESCRIPTION}}

## 输出要求

1. 必须严格按照上述 JSON 格式输出
2. 节点ID必须唯一，使用 `code_logic_` 前缀
3. 节点位置要合理布局，便于阅读理解
4. 节点内容要清晰描述业务逻辑
5. **必须为业务逻辑描述中的每个【决策点】创建决策节点**
6. **必须为每个决策点的所有分支创建对应的分支节点**
7. **决策节点的 content 要说明判断条件和各个分支的情况**
8. **分支节点的 title 必须包含分支条件说明**
9. 连线要准确反映业务流程的执行顺序和分支关系
10. 只输出 JSON，不要添加额外的说明文字
11. 确保输出的流程图中只有一个开始节点，其余节点均应从该开始节点直接或间接连通

## 关键提醒

- 从业务逻辑描述中提取【决策点1】、【决策点2】等标记
- 每个决策点对应一个决策节点，content 要清晰说明判断条件和分支情况
- 决策节点的 title 使用简洁描述，不需要特殊标记
- 每个决策点的分支（如"如果A则..."、"如果B则..."）对应独立的分支节点
- 分支节点要垂直排列，便于区分不同路径
- 异常分支和正常分支同等重要，都要创建节点

请提供 JSON 格式的流程图：


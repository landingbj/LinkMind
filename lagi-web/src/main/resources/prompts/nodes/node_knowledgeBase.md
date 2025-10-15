knowledgeBase（知识库检索节点）
- **功能描述**：在知识库中检索相关信息
- **输入**：检索query（可以是用户输入或其他节点的输出），可选知识库ID
- **输出**：检索到的相关文档内容（字符串形式）
- **使用场景**：需要从知识库获取背景信息、文档内容时使用
- **典型用法**：RAG（检索增强生成）场景，为LLM提供上下文
==============================================
knowledge-base节点（知识库检索）

| 字段路径                       | 类型     | 必填 | 规范要求                                                                                                                                                                                                        |
|----------------------------|--------|----|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| id                         | string | 是  | 遵循1.1规则，如kb_1或kb_BRQB8                                                                                                                                                                                      |
| type                       | string | 是  | 固定为"knowledge-base"                                                                                                                                                                                         |
| meta                       | object | 是  | 含`position`（x≈2300，y≈500，参考demo）                                                                                                                                                                            |
| data                       | object | 是  | 含`title`、`inputsValues`、`inputs`、`outputs`：<br>`title`：默认"知识库_序号"（如知识库_1）<br>`inputsValues`：输入参数（category和query）<br>`inputs`：输入定义（`required`=["category","query"]）<br>`outputs`：输出定义（`required`=["result"]） |
| data.inputsValues.category | object | 是  | 知识库分类：<br>`type`："constant"（固定"default"）或"ref"<br>`content`："default"或引用值                                                                                                                                   |
| data.inputsValues.query    | object | 是  | 检索关键词：<br>`type`："ref"（引用其他节点输出，如llm的result）或"template"<br>`content`：关键词内容                                                                                                                                  |

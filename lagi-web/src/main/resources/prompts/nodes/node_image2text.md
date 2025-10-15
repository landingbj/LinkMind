image2text（图片转文字节点）
- **功能描述**：调用api的节点
- **输入**：imageUrl(图片的url地址), model(模型名称)
- **输出**：识别出的图片的文字描述
- **使用场景**：需要对图片进行文字识别的场景
- **注意事项**： imageUrl 参数是必须的
============================
image2text节点（图片转文字）

| 字段路径                       | 类型     | 必填 | 规范要求                                                                                                                                                                                                |
|----------------------------|--------|----|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| id                         | string | 是  | 遵循1.1规则，如image2text_1或image2text_BRQB8                                                                                                                                                                              |
| type                       | string | 是  | 固定为"image2text"                                                                                                                                                                                     |
| meta                       | object | 是  | 含`position`（x≈2300，y≈500，参考demo）                                                                                                                                                                    |
| data                       | object | 是  | 含`title`、`inputsValues`、`inputs`、`outputs`：<br>`title`：默认"图生文_序号"（如图生文_1）<br>`inputsValues`：输入参数（imageUrl和model）<br>`inputs`：输入定义（`required`=["imageUrl"]）<br>`outputs`：输出定义（`required`=["result"]） |
| data.inputsValues.imageUrl | string | 是  | 图片的url地址：<br>`type`："constant"（固定"default"）或"ref"<br>`content`："default"或引用值                                                                                                                        |
| data.inputsValues.model    | string | 否  | 图片转文字的模型名称：<br>`type`："constant"（固定"default"）或"ref"<br>`content`："default"或引用值                                                                                                                      |

image2detect（图片目标检测节点）
- **功能描述**：使用图片目标检测的能力, 检测图片中物体的名字(如工作服、安全帽) 的信息
- **输入**：imageUrl(图片的url地址), model(模型名称)
- **输出**：检测出的目标列表
- **使用场景**：使用目标检测检测图片中的人物、工作服、安全帽等目标的位置信息个数等
- **注意事项**： imageUrl 参数是必须的
============================
 image2detect（图片目标检）

| 字段路径                       | 类型     | 必填 | 规范要求                                                                                                                                                                                                        |
|----------------------------|--------|----|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| id                         | string | 是  | 遵循1.1规则，如image2detect_1或image2detect_BRQB8                                                                                                                                                                  |
| type                       | string | 是  | 固定为"image2detect"                                                                                                                                                                                           |
| meta                       | object | 是  | 含`position`（x≈2300，y≈500，参考demo）                                                                                                                                                                            |
| data                       | object | 是  | 含`title`、`inputsValues`、`inputs`、`outputs`：<br>`title`：默认"图片目标检测_序号" （如图片目标检测_1） <br>`inputsValues`：输入参数（imageUrl和model）<br>`inputs`：输入定义（`required`=["imageUrl"]）<br>`outputs`：输出定义（`required`=["result"]） |
| data.inputsValues.imageUrl | string | 是  | 图片的url地址：<br>`type`："constant"（固定"default"）或"ref"<br>`content`："default"或引用值                                                                                                                                |
| data.inputsValues.model    | string | 否  | 图片目标检测的模型名称：<br>`type`："constant"（固定"default"）或"ref"<br>`content`："default"或引用值                                                                                                                             |

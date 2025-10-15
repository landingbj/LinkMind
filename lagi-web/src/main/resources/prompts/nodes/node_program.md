program（通用编程节点）
- **功能描述**：通过运行groovy 脚本处理其他节点处理不了而编程可以解决的问题
- **输入**：groovy 脚本（script）
- **输出**：编程的结果 result 字符串
- **使用场景**：通过 运行groovy 脚本处理其他节点处理不了而编程可以解决的问题
- **注意事项**：当其他节点无法完成时才可以试着编写groovy脚本
===============================================
### 2.7 program节点（groovy脚本）

| 字段路径 | 类型     | 必填 | 规范要求                                                                                                                                                                                                                                               |
|------|--------|----|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| id   | string | 是  | 遵循1.1规则，如program_1或program_4agO0                                                                                                                                                                                                                   |
| type | string | 是  | 固定为"program"                                                                                                                                                                                                                                       |
| meta | object | 是  | 含`position`（x≈1850，y≈1013，参考demo）                                                                                                                                                                                                                  |
| data | object | 是  | 含`title`、`inputsValues`、`inputs`、`outputs`：<br>`title`：默认"程序_序号"（如程序_1）<br>`inputsValues.script`：groovy脚本（`type`="template"，可含{{变量}}）<br>`inputs`：`required`=["script"]，`extra`含"formComponent":"prompt-editor"<br>`outputs`：`required`=["result"] |

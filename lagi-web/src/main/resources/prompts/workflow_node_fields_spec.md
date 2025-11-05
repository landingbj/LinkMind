# 节点标准字段规范

## 说明
此文件定义了所有节点类型的标准输入和输出字段。
当新增节点时，请在此文件中添加对应的字段规范。

## 输出字段规范

| 节点类型 | 标准输出字段 | 说明 |
|---------|-------------|------|
| **控制流节点** |||
| start（开始节点） | 用户定义（如 `query`） | 根据工作流需求定义的初始输入变量 |
| end（结束节点） | 无输出 | 作为流程终点，接收其他节点的输出 |
| condition（条件判断） | 无输出 | 根据条件分支到不同路径 |
| loop（循环节点） | `result` | 循环处理后的结果（通常是数组） |
| parallel（并行节点） | `parallelResults` | 所有并行分支的结果数组 |
| block-start（块开始） | 无输出 | 子流程/循环/并行的起始标记 |
| block-end（块结束） | 无输出 | 子流程/循环/并行的结束标记 |
| **AI能力节点** |||
| llm（大语言模型） | `result` | LLM生成的文本内容 |
| agent（智能体） | `result` | Agent返回结果 |
| mcp-agent（MCP智能体） | `result` | MCP Agent返回结果 |
| knowledge-base（知识库） | `result` | 检索到的文档内容 |
| intent-recognition（意图识别） | `intent` | 识别出的意图分类 |
| **数据处理节点** |||
| api（API调用） | `statusCode`, `body` | HTTP响应码和响应体 |
| database-query（数据库查询） | `result` | 查询结果（JSON字符串） |
| database-update（数据库更新） | `result` | 更新结果 |
| program（脚本执行） | `result` | 脚本执行结果 |
| code-logic（代码逻辑） | `result` | 代码执行结果 |
| **多媒体处理节点** |||
| image2text（图像识别） | `result` | 图像识别结果 |
| image2detect（图像检测） | `result` | 图像检测结果 |
| image2enhance（图像增强） | `result` | 增强后的图像URL |
| image2video（图生视频） | `result` | 生成的视频URL |
| text2image（文生图） | `result` | 生成的图像URL |
| text2video（文生视频） | `result` | 生成的视频URL |
| video2enhance（视频增强） | `result` | 增强后的视频URL |
| video2track（视频跟踪） | `result` | 跟踪结果 |
| ocr（文字识别） | `result` | 识别出的文字 |
| asr（语音识别） | `result` | 识别后的文本 |
| tts（文本转语音） | `result` | 音频文件URL |
| translate（翻译） | `result` | 翻译后的文本 |
| **内容安全节点** |||
| sensitive（敏感词检测） | `result` | 检测结果 |

## 输入字段规范示例

| 节点类型 | 允许的输入字段 | 禁止添加的字段示例 |
|---------|---------------|------------------|
| **控制流节点** |||
| start | 无输入（接收用户输入） | - |
| end | 根据工作流需求定义 | ❌ 不要添加未在变量提取中定义的字段 |
| condition | 条件判断表达式（left, operator, right） | ❌ 不要添加额外输入字段 |
| loop | `batchFor`（循环数据源） | ❌ 不要添加 maxIterations、timeout 等 |
| parallel | 无直接输入（通过子节点处理） | - |
| **AI能力节点** |||
| llm | `model`, `prompt` | ❌ temperature、maxTokens、systemPrompt 等 |
| agent | `query`, `tools`（如有） | ❌ maxIterations、thinkingMode 等 |
| mcp-agent | `query`, `server`（如有） | ❌ 未定义的配置参数 |
| knowledge-base | `category`, `query` | ❌ topK、threshold、filters 等 |
| intent-recognition | `query` | ❌ confidenceThreshold、categories 等 |
| **数据处理节点** |||
| api | `url`, `method`, `query`, `headers`, `body` | ❌ timeout、retry、auth 等 |
| database-query | `databaseName`, `sql`, `parameters` | ❌ connectionString、timeout、maxRetries 等 |
| database-update | `databaseName`, `sql`, `parameters` | ❌ connectionString、timeout、maxRetries 等 |
| program | `script`（Groovy脚本） | ❌ timeout、memoryLimit、environment 等 |
| code-logic | `code`（代码内容） | ❌ runtime、version 等 |
| **多媒体处理节点** |||
| text2image | `text`, `model` | ❌ style、size、quality 等未定义字段 |
| text2video | `text`, `model` | ❌ style、resolution、duration 等未定义字段 |
| image2video | `image`, `model` | ❌ fps、codec、bitrate 等 |
| image2text | `image`, `model` | ❌ language、detailLevel 等 |
| image2detect | `image`, `model` | ❌ confidenceThreshold、classes 等 |
| image2enhance | `image`, `model` | ❌ enhanceLevel、algorithm 等 |
| video2enhance | `video`, `model` | ❌ resolution、fps、codec 等 |
| video2track | `video`, `model` | ❌ trackingAlgorithm、fps 等 |
| ocr | `image`, `model` | ❌ language、orientation 等 |
| asr | `audio`, `model`, `language` | ❌ sampleRate、encoding、speakerCount 等 |
| tts | `text`, `model`, `voice`, `volume`, `format` | ❌ voiceModel、languageCode、speed、pitch 等 |
| translate | `text`, `sourceLanguage`, `targetLanguage` | ❌ domain、formality、glossary 等 |
| **内容安全节点** |||
| sensitive | `text`, `categories` | ❌ strictMode、threshold、customWords 等 |

## 核心原则

**❌ 禁止使用的错误字段名**：
- 输出字段：answer、response、output、content、data、text 等自定义名称
- 输入字段：不在节点定义中的任何字段

**⚠️ 严格约束**：
1. 只能使用节点定义中列出的字段，不可添加额外字段
2. 不要根据常识或经验推测应该有哪些字段
3. 字段名必须与定义完全匹配，不可使用相似名称

**✅ 正确引用示例**：
- `llm节点输入model和prompt字段，输出result字段`
- `api节点输入url、method等字段，输出statusCode和body字段`

**❌ 错误示例**：
- `tts节点输入text、voiceModel、languageCode字段`（voiceModel和languageCode不存在）
- `llm节点输出answer字段`（应该是result）

## 节点能力说明（用于任务分析）

### 控制流节点（基础必备）
- **start**: 工作流起始点，定义初始输入变量
- **end**: 工作流终点，输出最终结果
- **condition**: 条件分支判断，根据条件选择不同路径
- **loop**: 循环处理，对数组数据进行遍历
- **parallel**: 并行执行，同时运行多个分支
- **block-start/block-end**: 子流程标记，用于loop和parallel内部

### 单一功能节点（不需要拆分）
- **text2video**: 直接从文本生成视频，无需先生成图片
- **text2image**: 直接从文本生成图片
- **image2video**: 从图片生成视频
- **translate**: 直接翻译文本
- **tts**: 直接将文本转为语音
- **asr**: 直接将语音转为文本
- **ocr**: 直接从图像识别文字
- **image2text**: 直接描述图像内容
- **image2detect**: 直接检测图像中的对象
- **image2enhance**: 直接增强图像质量
- **video2enhance**: 直接增强视频质量
- **video2track**: 直接跟踪视频中的对象
- **sensitive**: 直接检测敏感内容

### 复合能力节点
- **llm**: 文本理解、生成、对话、总结、改写等
- **agent**: 具有工具调用能力的智能体，可自主规划和执行
- **mcp-agent**: 基于MCP协议的智能体，支持标准化工具调用
- **knowledge-base**: 知识库检索，为RAG场景提供上下文
- **intent-recognition**: 意图识别，判断用户需求类型

### 数据处理节点
- **api**: 调用外部API接口
- **database-query**: 数据库查询操作
- **database-update**: 数据库更新操作
- **program**: 执行Groovy脚本，处理复杂逻辑
- **code-logic**: 执行代码逻辑

---

**维护说明**：
- 当新增节点时，必须在此文件中更新对应的字段规范
- 修改节点字段时，必须同步更新此文件
- 此文件用于生成提示词，确保LLM使用正确的字段名


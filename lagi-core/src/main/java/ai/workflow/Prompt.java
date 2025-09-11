package ai.workflow;

import ai.config.ContextLoader;
import ai.llm.service.CompletionsService;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;

public class Prompt {

    public static String query2WorkflowPrompt =
            "你现在的角色是 “工作流节点与关系拆解专家”，核心任务是接收用户输入的各类工作流需求（如技术流程、业务流程、操作流程等），通过结构化分析，拆解出该工作流中不可或缺的核心节点，并明确各节点间的逻辑关系与流向。具体执行要求如下：\n" +
            "一、拆解前核心步骤\n" +
            "需求定位：先明确用户输入的工作流核心目标（如 “RAG + 大模型工作流” 目标是 “基于知识库实现精准问答生成”），避免遗漏关键环节；\n" +
            "颗粒度把控：节点需具备 “单一功能属性”，既不拆分过细（如不将 “知识库检索” 拆分为 “打开知识库 - 输入关键词 - 点击检索”），也不合并过粗（如不将 “知识库检索 + LLM 生成” 合并为 “内容处理”）。\n" +
            "二、节点拆解规范\n" +
            "节点命名：采用 “[功能描述]+ 节点” 的统一格式（如 “开始节点（拿到用户输入）”“知识库检索节点”“结束输出节点”），确保名称直观体现节点作用；\n" +
            "节点完整性：需覆盖工作流 “从启动到收尾” 的全链路，包含但不限于 “启动节点（触发条件）”“核心处理节点（实现关键功能）”“输出 / 收尾节点（结果交付）”，若存在分支、循环场景，需额外标注 “分支节点（条件判断）”“循环节点（重复执行）”。\n" +
            "三、节点关系梳理规范\n" +
            "关系类型：明确标注节点间的逻辑流向，支持 “线性流向（A→B→C）”“分支流向（A→B，A→C，需说明分支条件）”“循环流向（A→B→A，需说明循环终止条件）”；\n" +
            "关系描述：先以 “箭头符号” 直观展示流向，再用文字补充说明关系逻辑（如 “开始节点（拿到用户输入）→知识库检索节点：用户输入作为检索关键词，触发知识库检索操作”）。\n" +
            "四、输出格式要求\n" +
            "需以 “分点结构化” 形式输出，具体模板如下：\n" +
            "工作流核心目标：[提炼用户输入的工作流最终目的]\n" +
            "拆解后的核心节点（含功能说明）：\n" +
            "节点 1：[节点名称]，功能：[说明该节点的具体操作或作用]\n" +
            "节点 2：[节点名称]，功能：[说明该节点的具体操作或作用]\n" +
            "...（按工作流顺序排列）\n" +
            "节点间关系（含逻辑流向）：\n" +
            "流向展示：[节点 1]→[节点 2]→[节点 3]→...（分支 / 循环场景需补充：如 [节点 2]→[节点 3]（条件 1），[节点 2]→[节点 4]（条件 2））\n" +
            "关系说明：[逐环节解释节点间的触发逻辑，如 “开始节点拿到用户输入后，将输入内容传递给知识库检索节点，检索完成后将结果同步至 LLM 生成节点，生成内容后由结束输出节点交付给用户”]\n" +
            "五、约束条件\n" +
            "若用户输入的工作流包含专业领域术语（如 RAG、ETL、OA 审批等），需基于领域常识补充必要节点（如 RAG 工作流需包含 “知识库构建节点” 时，若用户未提及，需提示 “根据 RAG 技术逻辑，补充‘知识库构建节点（预处理数据并入库）’”）；\n" +
            "输出需避免模糊表述，不使用 “大概”“可能” 等不确定词汇，节点及关系需完全匹配工作流实际逻辑；\n" +
            "参考示例：若用户输入 “帮我生成一个 RAG + 大模型的工作流”，需输出：\n" +
            "工作流核心目标：基于知识库检索结果，结合大模型生成精准、有依据的回答\n" +
            "拆解后的核心节点（含功能说明）：\n" +
            "节点 1：开始节点（拿到用户输入），功能：接收用户的提问或需求\n" +
            "节点 2：知识库检索节点，功能：以用户输入为关键词，从预设知识库中检索相关信息\n" +
            "节点 3：大语言模型生成节点，功能：结合知识库检索结果，生成符合需求的回答内容\n" +
            "节点 4：结束输出节点，功能：将大模型生成的回答交付给用户\n" +
            "节点间关系（含逻辑流向）：\n" +
            "流向展示：开始节点（拿到用户输入）→知识库检索节点→大语言模型生成节点→结束输出节点\n" +
            "关系说明：开始节点接收用户输入后，将输入作为检索关键词传递给知识库检索节点；知识库检索节点完成检索后，将检索到的相关信息同步至大语言模型生成节点；大语言模型基于检索信息生成回答后，由结束输出节点将最终结果输出给用户，形成完整线性工作流。\n";

    private static String startNodeDescription = "开始节点 , 可以添加输出, 默认的输入为用户的 query , 也可根据需求添加后续几点可能会使用的其他 属性 : 如 llm 需要用到的 modelName等";
    private static String endNodeDescription = "结束输出节点, 用于接受上一步节点的输出, 提取对应的信息作为最终输出";
    private static String KnowLegeBaseNodeDescription = "知识库检索节点, 接受用户的输入 或 其他信息如具体哪个知识库， 对知识库进行检索, 并将检索结果已字符串传形式输出";
    private static String llmNodeDescription = "LLM 节点, 利用大模型的功能， 根据设定的提示词， 用户输入, 回答用户的问题";
    private static String intentNodeDescription = "意图识别 节点, 根据用户的输入, 判定用户的意图输出为一个字符串 ： 其主要的作用是判断用是否是多模态的问题, ";


    private static String systemPromptGen = "## 一、角色与核心任务定义\n" +
            "你是「编排工作流JSON生成专家」，核心任务是：**将用户提供的「工作流运行逻辑自然语言描述」，精准转换为符合指定结构规范的工作流运行JSON**。转换需完全匹配工作流的节点类型、参数配置、连接关系、嵌套结构（如循环内部子节点），确保JSON可直接用于前端工作流引擎解析运行。\n" +
            "\n" +
            "\n" +
            "## 二、工作流JSON结构规范（必须严格遵循）\n" +
            "### 1. 顶层结构\n" +
            "JSON根节点为对象，仅包含2个必填字段：\n" +
            "| 字段名 | 类型       | 说明                                                                 |\n" +
            "|--------|------------|----------------------------------------------------------------------|\n" +
            "| `nodes`| 数组       | 存储工作流所有节点（开始、条件、循环、LLM、意图识别、知识库、结束、注释） |\n" +
            "| `edges`| 数组       | 存储节点间的连接关系，定义工作流执行顺序                               |\n" +
            "\n" +
            "\n" +
            "### 2. `nodes`数组：节点详细规范（按类型拆解）\n" +
            "每个节点为对象，通用必填字段：`id`（节点唯一标识，格式建议：类型_序号/随机6位字符串，如`start_0`、`loop_CwoCJ`）、`type`（节点类型，枚举值见下文）、`meta`（节点元信息）、`data`（节点核心配置）。\n" +
            "\n" +
            "#### （1）开始节点（`type: \"start\"`）\n" +
            "- 功能：工作流入口，定义全局输出参数  \n" +
            "- 示例结构（参考用户提供的`start_0`）：\n" +
            "  ```json\n" +
            "  {\n" +
            "    \"id\": \"start_0\", // 格式：start_序号\n" +
            "    \"type\": \"start\",\n" +
            "    \"meta\": {\n" +
            "      \"position\": { \"x\": 数字, \"y\": 数字 } // 节点在画布的坐标，必填\n" +
            "    },\n" +
            "    \"data\": {\n" +
            "      \"title\": \"开始\", // 节点显示名称，默认\"开始\"\n" +
            "      \"outputs\": { // 输出参数定义，type固定为\"object\"\n" +
            "        \"type\": \"object\",\n" +
            "        \"properties\": { // 每个参数为一个对象，必填字段如下\n" +
            "          \"参数名\": {\n" +
            "            \"key\": 数字, // 参数唯一标识（按顺序0、1、2...）\n" +
            "            \"name\": \"参数名\", // 与外层key一致\n" +
            "            \"isPropertyRequired\": false/true, // 是否必填\n" +
            "            \"type\": \"string/boolean/number/array\", // 参数类型\n" +
            "            \"default\": 任意值, // 默认值（可选，无默认则不填）\n" +
            "            \"extra\": { \"index\": 数字 } // 与\"key\"值一致\n" +
            "          }\n" +
            "        },\n" +
            "        \"required\": [] // 必填参数列表（无则为空数组）\n" +
            "      }\n" +
            "    }\n" +
            "  }\n" +
            "  ```\n" +
            "\n" +
            "#### （2）条件节点（`type: \"condition\"`）\n" +
            "- 功能：分支判断，支持多个条件（每个条件对应一个输出端口）  \n" +
            "- 核心配置：`data.conditions`（条件数组，每个条件对应一个分支）\n" +
            "  ```json\n" +
            "  {\n" +
            "    \"id\": \"condition_0\", // 格式：condition_序号\n" +
            "    \"type\": \"condition\",\n" +
            "    \"meta\": { \"position\": { \"x\": 数字, \"y\": 数字 } },\n" +
            "    \"data\": {\n" +
            "      \"title\": \"条件\", // 默认\"条件\"\n" +
            "      \"conditions\": [ // 条件列表，至少1个\n" +
            "        {\n" +
            "          \"value\": {\n" +
            "            \"type\": \"expression\", // 固定值\n" +
            "            \"content\": \"\", // 固定为空字符串\n" +
            "            \"left\": { // 左值（通常为引用类型）\n" +
            "              \"type\": \"ref\", // 引用类型（必填）\n" +
            "              \"content\": [\"引用节点ID\", \"引用参数名\"] // 如[\"intent_imMwF\", \"intent\"]\n" +
            "            },\n" +
            "            \"operator\": \"eq/ne/gt/lt\", // 运算符（等于/不等于/大于/小于）\n" +
            "            \"right\": { // 右值（常量或引用）\n" +
            "              \"type\": \"constant/ref\", // 常量/引用\n" +
            "              \"content\": 常量值/[\"引用节点ID\", \"引用参数名\"] // 如\"text\"或[\"start_0\", \"query\"]\n" +
            "            }\n" +
            "          },\n" +
            "          \"key\": \"if_随机6位字符串\" // 条件端口ID，格式：if_随机串（如if_lhcoc9）\n" +
            "        }\n" +
            "      ]\n" +
            "    }\n" +
            "  }\n" +
            "  ```\n" +
            "\n" +
            "#### （3）结束节点（`type: \"end\"`）\n" +
            "- 功能：工作流出口，收集最终结果  \n" +
            "- 核心配置：`data.inputs`（输入参数定义）、`data.inputsValues`（输入参数来源）\n" +
            "  ```json\n" +
            "  {\n" +
            "    \"id\": \"end_0\", // 格式：end_序号\n" +
            "    \"type\": \"end\",\n" +
            "    \"meta\": { \"position\": { \"x\": 数字, \"y\": 数字 } },\n" +
            "    \"data\": {\n" +
            "      \"title\": \"结束\", // 默认\"结束\"\n" +
            "      \"inputs\": { // 输入参数定义\n" +
            "        \"type\": \"object\",\n" +
            "        \"properties\": {\n" +
            "          \"参数名\": { \"type\": \"string/number/boolean\" } // 结果参数类型\n" +
            "        }\n" +
            "      },\n" +
            "      \"inputsValues\": { // 输入参数来源（必为引用类型）\n" +
            "        \"参数名\": {\n" +
            "          \"type\": \"ref\",\n" +
            "          \"content\": [\"引用节点ID\", \"引用参数名\"] // 如[\"llm_AV9ZI\", \"result\"]\n" +
            "        }\n" +
            "      }\n" +
            "    }\n" +
            "  }\n" +
            "  ```\n" +
            "\n" +
            "#### （4）注释节点（`type: \"comment\"`）\n" +
            "- 功能：画布备注说明，无执行逻辑  \n" +
            "- 核心配置：`data.size`（尺寸）、`data.note`（注释内容）\n" +
            "  ```json\n" +
            "  {\n" +
            "    \"id\": \"随机数字/字符串\", // 如\"159623\"\n" +
            "    \"type\": \"comment\",\n" +
            "    \"meta\": { \"position\": { \"x\": 数字, \"y\": 数字 } },\n" +
            "    \"data\": {\n" +
            "      \"size\": { \"width\": 数字, \"height\": 数字 }, // 注释框尺寸\n" +
            "      \"note\": \"注释内容（支持换行，用\\\\n表示）\" // 如\"嗨~\\\\n这是一个注释节点\\\\n-Linkmind\"\n" +
            "    }\n" +
            "  }\n" +
            "  ```\n" +
            "\n" +
            "#### （5）循环节点（`type: \"loop\"`）\n" +
            "- 功能：批量执行子流程（含内部子节点和子连接）  \n" +
            "- 特殊字段：`blocks`（内部子节点数组）、`edges`（内部子节点连接数组）\n" +
            "  ```json\n" +
            "  {\n" +
            "    \"id\": \"loop_随机6位字符串\", // 如\"loop_CwoCJ\"\n" +
            "    \"type\": \"loop\",\n" +
            "    \"meta\": { \"position\": { \"x\": 数字, \"y\": 数字 } },\n" +
            "    \"data\": {\n" +
            "      \"title\": \"循环_序号\", // 如\"循环_1\"\n" +
            "      \"batchFor\": { // 循环数据源（引用外部参数）\n" +
            "        \"type\": \"ref\",\n" +
            "        \"content\": [\"引用节点ID\", \"引用参数名\"] // 如[\"start_0\", \"array_obj\"]\n" +
            "      },\n" +
            "      \"batchOutputs\": { // 循环结果输出（引用内部子节点参数）\n" +
            "        \"参数名\": {\n" +
            "          \"type\": \"ref\",\n" +
            "          \"content\": [\"内部子节点ID\", \"内部参数名\"] // 如[\"llm_Osa_C\", \"result\"]\n" +
            "        }\n" +
            "      }\n" +
            "    },\n" +
            "    \"blocks\": [ // 内部子节点（必含block-start、block-end，可加其他节点）\n" +
            "      { \"id\": \"block_start_随机串\", \"type\": \"block-start\", \"meta\": { \"position\": { \"x\": 数字, \"y\": 数字 } }, \"data\": {} },\n" +
            "      { \"id\": \"block_end_随机串\", \"type\": \"block-end\", \"meta\": { \"position\": { \"x\": 数字, \"y\": 数字 } }, \"data\": {} },\n" +
            "      // 其他子节点（如llm、intent-recognition等，结构与外部节点一致）\n" +
            "    ],\n" +
            "    \"edges\": [ // 内部子节点连接（结构与顶层edges一致）\n" +
            "      { \"sourceNodeID\": \"block-start节点ID\", \"targetNodeID\": \"内部子节点ID\" },\n" +
            "      { \"sourceNodeID\": \"内部子节点ID\", \"targetNodeID\": \"block-end节点ID\" }\n" +
            "    ]\n" +
            "  }\n" +
            "  ```\n" +
            "\n" +
            "#### （6）意图识别节点（`type: \"intent-recognition\"`）\n" +
            "- 功能：识别输入文本的意图，输出意图结果  \n" +
            "- 核心配置：`data.inputs`（输入必填text）、`data.inputsValues`（text来源）、`data.outputs`（输出intent）\n" +
            "  ```json\n" +
            "  {\n" +
            "    \"id\": \"intent_随机6位字符串\", // 如\"intent_imMwF\"\n" +
            "    \"type\": \"intent-recognition\",\n" +
            "    \"meta\": { \"position\": { \"x\": 数字, \"y\": 数字 } },\n" +
            "    \"data\": {\n" +
            "      \"title\": \"意图识别_序号\", // 如\"意图识别_1\"\n" +
            "      \"inputsValues\": {\n" +
            "        \"text\": { // 输入文本，必为引用类型\n" +
            "          \"type\": \"ref\",\n" +
            "          \"content\": [\"引用节点ID\", \"引用参数名\"] // 如[\"start_0\", \"query\"]\n" +
            "        }\n" +
            "      },\n" +
            "      \"inputs\": { // 输入定义，text为必填\n" +
            "        \"type\": \"object\",\n" +
            "        \"required\": [\"text\"], // 固定必填\"text\"\n" +
            "        \"properties\": { \"text\": { \"type\": \"string\" } }\n" +
            "      },\n" +
            "      \"outputs\": { // 输出意图，固定输出\"intent\"\n" +
            "        \"type\": \"object\",\n" +
            "        \"properties\": { \"intent\": { \"type\": \"string\" } }\n" +
            "      }\n" +
            "    }\n" +
            "  }\n" +
            "  ```\n" +
            "\n" +
            "#### （7）LLM节点（`type: \"llm\"`）\n" +
            "- 功能：调用大模型生成内容，需配置模型和提示词  \n" +
            "- 核心配置：`data.inputs`（必填model、prompt）、`data.inputsValues`（model/prompt来源）\n" +
            "  ```json\n" +
            "  {\n" +
            "    \"id\": \"llm_随机6位字符串\", // 如\"llm_AV9ZI\"\n" +
            "    \"type\": \"llm\",\n" +
            "    \"meta\": { \"position\": { \"x\": 数字, \"y\": 数字 } },\n" +
            "    \"data\": {\n" +
            "      \"title\": \"LLM_序号\", // 如\"LLM_1\"\n" +
            "      \"inputsValues\": {\n" +
            "        \"model\": { // 模型名，通常引用start节点的modelName\n" +
            "          \"type\": \"ref\",\n" +
            "          \"content\": [\"引用节点ID\", \"modelName\"] // 如[\"start_0\", \"modelName\"]\n" +
            "        },\n" +
            "        \"prompt\": { // 提示词，支持模板（type: \"template\"）\n" +
            "          \"type\": \"template\", // 模板类型（固定）\n" +
            "          \"content\": \"模板内容（引用参数用{{节点ID.参数名}}，如{{start_0.query}}）\"\n" +
            "        }\n" +
            "      },\n" +
            "      \"inputs\": { // 输入定义，model和prompt为必填\n" +
            "        \"type\": \"object\",\n" +
            "        \"required\": [\"model\", \"prompt\"],\n" +
            "        \"properties\": {\n" +
            "          \"model\": { \"type\": \"string\" },\n" +
            "          \"prompt\": { \n" +
            "            \"type\": \"string\",\n" +
            "            \"extra\": { \"formComponent\": \"prompt-editor\" } // 固定值\n" +
            "          }\n" +
            "        }\n" +
            "      },\n" +
            "      \"outputs\": { // 输出大模型结果，固定输出\"result\"\n" +
            "        \"type\": \"object\",\n" +
            "        \"properties\": { \"result\": { \"type\": \"string\" } }\n" +
            "      }\n" +
            "    }\n" +
            "  }\n" +
            "  ```\n" +
            "\n" +
            "#### （8）知识库节点（`type: \"knowledge-base\"`）\n" +
            "- 功能：查询知识库，需配置分类和查询关键词  \n" +
            "- 核心配置：`data.inputs`（必填category、query）、`data.inputsValues`（来源）\n" +
            "  ```json\n" +
            "  {\n" +
            "    \"id\": \"kb_随机6位字符串\", // 如\"kb_BRQB8\"\n" +
            "    \"type\": \"knowledge-base\",\n" +
            "    \"meta\": { \"position\": { \"x\": 数字, \"y\": 数字 } },\n" +
            "    \"data\": {\n" +
            "      \"title\": \"知识库_序号\", // 如\"知识库_1\"\n" +
            "      \"inputsValues\": {\n" +
            "        \"category\": { // 知识库分类（通常为常量）\n" +
            "          \"type\": \"constant\",\n" +
            "          \"content\": \"分类名\" // 如\"default\"\n" +
            "        },\n" +
            "        \"query\": { // 查询关键词（引用外部参数）\n" +
            "          \"type\": \"ref\",\n" +
            "          \"content\": [\"引用节点ID\", \"引用参数名\"] // 如[\"llm_AV9ZI\", \"result\"]\n" +
            "        }\n" +
            "      },\n" +
            "      \"inputs\": { // 输入定义，category和query为必填\n" +
            "        \"type\": \"object\",\n" +
            "        \"required\": [\"category\", \"query\"],\n" +
            "        \"properties\": {\n" +
            "          \"category\": { \"type\": \"string\" },\n" +
            "          \"query\": { \"type\": \"string\" }\n" +
            "        }\n" +
            "      },\n" +
            "      \"outputs\": { // 输出知识库查询结果，固定\"result\"\n" +
            "        \"type\": \"object\",\n" +
            "        \"properties\": { \"result\": { \"type\": \"string\" } },\n" +
            "        \"required\": [\"result\"] // 固定必填\n" +
            "      }\n" +
            "    }\n" +
            "  }\n" +
            "  ```\n" +
            "\n" +
            "\n" +
            "### 3. `edges`数组：节点连接规范\n" +
            "每个连接为对象，定义两个节点的执行顺序，必填字段：\n" +
            "| 字段名         | 类型   | 说明                                                                 |\n" +
            "|----------------|--------|----------------------------------------------------------------------|\n" +
            "| `sourceNodeID` | 字符串 | 源节点ID（必须是`nodes`中存在的节点ID）                               |\n" +
            "| `targetNodeID` | 字符串 | 目标节点ID（必须是`nodes`中存在的节点ID）                             |\n" +
            "| `sourcePortID` | 字符串 | 可选，仅条件节点需要（对应`condition`的`conditions[].key`，如`if_lhcoc9`） |\n" +
            "\n" +
            "示例：\n" +
            "```json\n" +
            "[\n" +
            "  { \"sourceNodeID\": \"start_0\", \"targetNodeID\": \"intent_imMwF\" }, // 普通连接\n" +
            "  { \"sourceNodeID\": \"condition_0\", \"targetNodeID\": \"loop_CwoCJ\", \"sourcePortID\": \"if_lhcoc9\" } // 条件分支连接\n" +
            "]\n" +
            "```\n" +
            "\n" +
            "\n" +
            "## 三、自然语言输入要求（引导用户提供信息）\n" +
            "请用户按以下清单描述工作流，确保信息不缺失：\n" +
            "1. **节点清单**：每个节点的「类型」「显示标题」「画布坐标（x,y）」「参数配置」（如start的输出参数、llm的prompt模板）；\n" +
            "2. **嵌套结构**：若有循环节点，需描述「内部子节点类型/参数」「内部子节点连接关系」；\n" +
            "3. **连接关系**：明确「源节点→目标节点」，条件节点需说明「哪个条件分支连哪个目标节点」；\n" +
            "4. **参数引用**：所有`ref`类型的参数，需明确「引用的节点ID+参数名」（如“LLM的model引用start_0的modelName”）。\n" +
            "\n" +
            "\n" +
            "## 四、输出规则（强制约束）\n" +
            "1. **格式正确**：输出为标准JSON，无语法错误（引号、逗号、括号完整），可直接用`JSON.parse()`解析；\n" +
            "2. **字段完整**：严格遵循上述结构规范，不遗漏必填字段（如`nodes`的`id`/`type`/`meta.position`，`edges`的`sourceNodeID`/`targetNodeID`）；\n" +
            "3. **引用一致**：`ref`类型的`content`、`edges`的节点ID，必须与`nodes`中存在的`id`完全匹配（大小写、字符一致）；\n" +
            "4. **默认值补充**：若自然语言未提及非必填字段（如`start`的`isPropertyRequired`），默认填`false`；坐标未提及则按「start默认x:180,y:350，后续节点x递增400-600，y保持相近」填充；\n" +
            "5. **注释说明**：若自然语言信息缺失，需在JSON末尾用`//`标注补充逻辑（如“// 循环节点内部block-start坐标默认x:30,y:80”）。\n" +
            "\n" +
            "\n" +
            "## 五、示例演示（自然语言→JSON映射）\n" +
            "### 1. 自然语言输入\n" +
            "```\n" +
            "工作流描述：\n" +
            "1. 开始节点（id:start_0，标题\"开始\"，坐标x:180,y:352.85）：输出4个参数，query（string，默认\"Hello Flow.\"）、enable（boolean，默认true）、array_obj（array，元素是含int和str的object）、modelName（string）；\n" +
            "2. 意图识别节点（id:intent_imMwF，标题\"意图识别_1\"，坐标x:640,y:335.85）：输入text引用start_0的query，输出intent（string）；\n" +
            "3. 条件节点（id:condition_0，标题\"条件\"，坐标x:1100,y:289.15）：2个条件，均为\"intent_imMwF的intent等于text\"（条件1的key:if_lhcoc9，条件2的key:if_le493Z）；\n" +
            "4. 循环节点（id:loop_CwoCJ，标题\"循环_1\"，坐标x:1909.31,y:50.98）：\n" +
            "   - 循环数据源：引用start_0的array_obj；\n" +
            "   - 循环结果输出：result引用内部LLM的result；\n" +
            "   - 内部子节点：block-start（id:block_start_AL4x0，坐标x:31.7,y:82.15）、block-end（id:block_end_4Tmpw，坐标x:665.8,y:82.15）、LLM_2（id:llm_Osa_C，坐标x:348.75,y:0，model引用start_0的modelName，prompt模板\"翻译成英文：{{loop_CwoCJ_locals.item.str}}\"，输出result）；\n" +
            "   - 内部连接：block_start_AL4x0→llm_Osa_C→block_end_4Tmpw；\n" +
            "5. LLM节点（id:llm_AV9ZI，标题\"LLM_1\"，坐标x:1565.35,y:469.7）：model引用start_0的modelName，prompt模板\"以{{start_0.query}}为题，写一篇文章\"，输出result；\n" +
            "6. 知识库节点（id:kb_BRQB8，标题\"知识库_1\"，坐标x:2299.45,y:507.7）：category为常量\"default\"，query引用llm_AV9ZI的result，输出result；\n" +
            "7. 结束节点（id:end_0，标题\"结束\"，坐标x:3028.2,y:352.85）：输入result引用llm_AV9ZI的result，batch_result引用loop_CwoCJ的result；\n" +
            "8. 注释节点（id:159623，坐标x:180,y:534.55，尺寸width:240,height:150，内容\"嗨~\\n\\n这是一个注释节点\\n\\n-Linkmind\"）；\n" +
            "9. 连接关系：\n" +
            "   - start_0→intent_imMwF；\n" +
            "   - intent_imMwF→condition_0；\n" +
            "   - condition_0的if_lhcoc9→loop_CwoCJ；\n" +
            "   - condition_0的if_le493Z→llm_AV9ZI；\n" +
            "   - llm_AV9ZI→kb_BRQB8；\n" +
            "   - loop_CwoCJ→end_0；\n" +
            "   - kb_BRQB8→end_0。\n" +
            "```\n" +
            "\n" +
            "### 2. 对应JSON输出\n" +
            "\n" +
            " {\n" +
            "\t\"nodes\": [{\n" +
            "\t\t\"id\": \"start_0\",\n" +
            "\t\t\"type\": \"start\",\n" +
            "\t\t\"meta\": {\n" +
            "\t\t\t\"position\": {\n" +
            "\t\t\t\t\"x\": 180,\n" +
            "\t\t\t\t\"y\": 352.8499999999999\n" +
            "\t\t\t}\n" +
            "\t\t},\n" +
            "\t\t\"data\": {\n" +
            "\t\t\t\"title\": \"开始\",\n" +
            "\t\t\t\"outputs\": {\n" +
            "\t\t\t\t\"type\": \"object\",\n" +
            "\t\t\t\t\"properties\": {\n" +
            "\t\t\t\t\t\"query\": {\n" +
            "\t\t\t\t\t\t\"key\": 0,\n" +
            "\t\t\t\t\t\t\"name\": \"query\",\n" +
            "\t\t\t\t\t\t\"isPropertyRequired\": false,\n" +
            "\t\t\t\t\t\t\"type\": \"string\",\n" +
            "\t\t\t\t\t\t\"default\": \"Hello Flow.\",\n" +
            "\t\t\t\t\t\t\"extra\": {\n" +
            "\t\t\t\t\t\t\t\"index\": 0\n" +
            "\t\t\t\t\t\t}\n" +
            "\t\t\t\t\t},\n" +
            "\t\t\t\t\t\"enable\": {\n" +
            "\t\t\t\t\t\t\"key\": 1,\n" +
            "\t\t\t\t\t\t\"name\": \"enable\",\n" +
            "\t\t\t\t\t\t\"isPropertyRequired\": false,\n" +
            "\t\t\t\t\t\t\"type\": \"boolean\",\n" +
            "\t\t\t\t\t\t\"default\": true,\n" +
            "\t\t\t\t\t\t\"extra\": {\n" +
            "\t\t\t\t\t\t\t\"index\": 1\n" +
            "\t\t\t\t\t\t}\n" +
            "\t\t\t\t\t},\n" +
            "\t\t\t\t\t\"array_obj\": {\n" +
            "\t\t\t\t\t\t\"key\": 2,\n" +
            "\t\t\t\t\t\t\"name\": \"array_obj\",\n" +
            "\t\t\t\t\t\t\"isPropertyRequired\": false,\n" +
            "\t\t\t\t\t\t\"type\": \"array\",\n" +
            "\t\t\t\t\t\t\"items\": {\n" +
            "\t\t\t\t\t\t\t\"type\": \"object\",\n" +
            "\t\t\t\t\t\t\t\"properties\": {\n" +
            "\t\t\t\t\t\t\t\t\"int\": {\n" +
            "\t\t\t\t\t\t\t\t\t\"type\": \"number\"\n" +
            "\t\t\t\t\t\t\t\t},\n" +
            "\t\t\t\t\t\t\t\t\"str\": {\n" +
            "\t\t\t\t\t\t\t\t\t\"type\": \"string\"\n" +
            "\t\t\t\t\t\t\t\t}\n" +
            "\t\t\t\t\t\t\t}\n" +
            "\t\t\t\t\t\t},\n" +
            "\t\t\t\t\t\t\"extra\": {\n" +
            "\t\t\t\t\t\t\t\"index\": 2\n" +
            "\t\t\t\t\t\t}\n" +
            "\t\t\t\t\t},\n" +
            "\t\t\t\t\t\"modelName\": {\n" +
            "\t\t\t\t\t\t\"key\": 5,\n" +
            "\t\t\t\t\t\t\"name\": \"modelName\",\n" +
            "\t\t\t\t\t\t\"isPropertyRequired\": false,\n" +
            "\t\t\t\t\t\t\"type\": \"string\",\n" +
            "\t\t\t\t\t\t\"extra\": {\n" +
            "\t\t\t\t\t\t\t\"index\": 3\n" +
            "\t\t\t\t\t\t}\n" +
            "\t\t\t\t\t}\n" +
            "\t\t\t\t},\n" +
            "\t\t\t\t\"required\": []\n" +
            "\t\t\t}\n" +
            "\t\t}\n" +
            "\t},\n" +
            "\t{\n" +
            "\t\t\"id\": \"condition_0\",\n" +
            "\t\t\"type\": \"condition\",\n" +
            "\t\t\"meta\": {\n" +
            "\t\t\t\"position\": {\n" +
            "\t\t\t\t\"x\": 1100,\n" +
            "\t\t\t\t\"y\": 289.1499999999999\n" +
            "\t\t\t}\n" +
            "\t\t},\n" +
            "\t\t\"data\": {\n" +
            "\t\t\t\"title\": \"条件\",\n" +
            "\t\t\t\"conditions\": [{\n" +
            "\t\t\t\t\"value\": {\n" +
            "\t\t\t\t\t\"type\": \"expression\",\n" +
            "\t\t\t\t\t\"content\": \"\",\n" +
            "\t\t\t\t\t\"left\": {\n" +
            "\t\t\t\t\t\t\"type\": \"ref\",\n" +
            "\t\t\t\t\t\t\"content\": [\"intent_imMwF\", \"intent\"]\n" +
            "\t\t\t\t\t},\n" +
            "\t\t\t\t\t\"operator\": \"eq\",\n" +
            "\t\t\t\t\t\"right\": {\n" +
            "\t\t\t\t\t\t\"type\": \"constant\",\n" +
            "\t\t\t\t\t\t\"content\": \"text\"\n" +
            "\t\t\t\t\t}\n" +
            "\t\t\t\t},\n" +
            "\t\t\t\t\"key\": \"if_lhcoc9\"\n" +
            "\t\t\t},\n" +
            "\t\t\t{\n" +
            "\t\t\t\t\"value\": {\n" +
            "\t\t\t\t\t\"type\": \"expression\",\n" +
            "\t\t\t\t\t\"content\": \"\",\n" +
            "\t\t\t\t\t\"left\": {\n" +
            "\t\t\t\t\t\t\"type\": \"ref\",\n" +
            "\t\t\t\t\t\t\"content\": [\"intent_imMwF\", \"intent\"]\n" +
            "\t\t\t\t\t},\n" +
            "\t\t\t\t\t\"operator\": \"eq\",\n" +
            "\t\t\t\t\t\"right\": {\n" +
            "\t\t\t\t\t\t\"type\": \"constant\",\n" +
            "\t\t\t\t\t\t\"content\": \"text\"\n" +
            "\t\t\t\t\t}\n" +
            "\t\t\t\t},\n" +
            "\t\t\t\t\"key\": \"if_le493Z\"\n" +
            "\t\t\t}]\n" +
            "\t\t}\n" +
            "\t},\n" +
            "\t{\n" +
            "\t\t\"id\": \"end_0\",\n" +
            "\t\t\"type\": \"end\",\n" +
            "\t\t\"meta\": {\n" +
            "\t\t\t\"position\": {\n" +
            "\t\t\t\t\"x\": 3028.2000000000003,\n" +
            "\t\t\t\t\"y\": 352.8499999999999\n" +
            "\t\t\t}\n" +
            "\t\t},\n" +
            "\t\t\"data\": {\n" +
            "\t\t\t\"title\": \"结束\",\n" +
            "\t\t\t\"inputs\": {\n" +
            "\t\t\t\t\"type\": \"object\",\n" +
            "\t\t\t\t\"properties\": {\n" +
            "\t\t\t\t\t\"result\": {\n" +
            "\t\t\t\t\t\t\"type\": \"string\"\n" +
            "\t\t\t\t\t},\n" +
            "\t\t\t\t\t\"batch_result\": {\n" +
            "\t\t\t\t\t\t\"type\": \"string\"\n" +
            "\t\t\t\t\t}\n" +
            "\t\t\t\t}\n" +
            "\t\t\t},\n" +
            "\t\t\t\"inputsValues\": {\n" +
            "\t\t\t\t\"result\": {\n" +
            "\t\t\t\t\t\"type\": \"ref\",\n" +
            "\t\t\t\t\t\"content\": [\"llm_AV9ZI\", \"result\"]\n" +
            "\t\t\t\t},\n" +
            "\t\t\t\t\"batch_result\": {\n" +
            "\t\t\t\t\t\"type\": \"ref\",\n" +
            "\t\t\t\t\t\"content\": [\"loop_CwoCJ\", \"result\"]\n" +
            "\t\t\t\t}\n" +
            "\t\t\t}\n" +
            "\t\t}\n" +
            "\t},\n" +
            "\t{\n" +
            "\t\t\"id\": \"159623\",\n" +
            "\t\t\"type\": \"comment\",\n" +
            "\t\t\"meta\": {\n" +
            "\t\t\t\"position\": {\n" +
            "\t\t\t\t\"x\": 180,\n" +
            "\t\t\t\t\"y\": 534.55\n" +
            "\t\t\t}\n" +
            "\t\t},\n" +
            "\t\t\"data\": {\n" +
            "\t\t\t\"size\": {\n" +
            "\t\t\t\t\"width\": 240,\n" +
            "\t\t\t\t\"height\": 150\n" +
            "\t\t\t},\n" +
            "\t\t\t\"note\": \"嗨~\\n\\n这是一个注释节点\\n\\n- Linkmind\"\n" +
            "\t\t}\n" +
            "\t},\n" +
            "\t{\n" +
            "\t\t\"id\": \"loop_CwoCJ\",\n" +
            "\t\t\"type\": \"loop\",\n" +
            "\t\t\"meta\": {\n" +
            "\t\t\t\"position\": {\n" +
            "\t\t\t\t\"x\": 1909.3127603943626,\n" +
            "\t\t\t\t\"y\": 50.97774551468439\n" +
            "\t\t\t}\n" +
            "\t\t},\n" +
            "\t\t\"data\": {\n" +
            "\t\t\t\"title\": \"循环_1\",\n" +
            "\t\t\t\"batchFor\": {\n" +
            "\t\t\t\t\"type\": \"ref\",\n" +
            "\t\t\t\t\"content\": [\"start_0\", \"array_obj\"]\n" +
            "\t\t\t},\n" +
            "\t\t\t\"batchOutputs\": {\n" +
            "\t\t\t\t\"result\": {\n" +
            "\t\t\t\t\t\"type\": \"ref\",\n" +
            "\t\t\t\t\t\"content\": [\"llm_Osa_C\", \"result\"]\n" +
            "\t\t\t\t}\n" +
            "\t\t\t}\n" +
            "\t\t},\n" +
            "\t\t\"blocks\": [{\n" +
            "\t\t\t\"id\": \"block_start_AL4x0\",\n" +
            "\t\t\t\"type\": \"block-start\",\n" +
            "\t\t\t\"meta\": {\n" +
            "\t\t\t\t\"position\": {\n" +
            "\t\t\t\t\t\"x\": 31.7,\n" +
            "\t\t\t\t\t\"y\": 82.14999999999999\n" +
            "\t\t\t\t}\n" +
            "\t\t\t},\n" +
            "\t\t\t\"data\": {}\n" +
            "\t\t},\n" +
            "\t\t{\n" +
            "\t\t\t\"id\": \"block_end_4Tmpw\",\n" +
            "\t\t\t\"type\": \"block-end\",\n" +
            "\t\t\t\"meta\": {\n" +
            "\t\t\t\t\"position\": {\n" +
            "\t\t\t\t\t\"x\": 665.8000000000001,\n" +
            "\t\t\t\t\t\"y\": 82.14999999999999\n" +
            "\t\t\t\t}\n" +
            "\t\t\t},\n" +
            "\t\t\t\"data\": {}\n" +
            "\t\t},\n" +
            "\t\t{\n" +
            "\t\t\t\"id\": \"llm_Osa_C\",\n" +
            "\t\t\t\"type\": \"llm\",\n" +
            "\t\t\t\"meta\": {\n" +
            "\t\t\t\t\"position\": {\n" +
            "\t\t\t\t\t\"x\": 348.75,\n" +
            "\t\t\t\t\t\"y\": 0\n" +
            "\t\t\t\t}\n" +
            "\t\t\t},\n" +
            "\t\t\t\"data\": {\n" +
            "\t\t\t\t\"title\": \"LLM_2\",\n" +
            "\t\t\t\t\"inputsValues\": {\n" +
            "\t\t\t\t\t\"model\": {\n" +
            "\t\t\t\t\t\t\"type\": \"ref\",\n" +
            "\t\t\t\t\t\t\"content\": [\"start_0\", \"modelName\"]\n" +
            "\t\t\t\t\t},\n" +
            "\t\t\t\t\t\"prompt\": {\n" +
            "\t\t\t\t\t\t\"type\": \"template\",\n" +
            "\t\t\t\t\t\t\"content\": \"翻译成英文：{{loop_CwoCJ_locals.item.str}}\"\n" +
            "\t\t\t\t\t}\n" +
            "\t\t\t\t},\n" +
            "\t\t\t\t\"inputs\": {\n" +
            "\t\t\t\t\t\"type\": \"object\",\n" +
            "\t\t\t\t\t\"required\": [\"model\", \"prompt\"],\n" +
            "\t\t\t\t\t\"properties\": {\n" +
            "\t\t\t\t\t\t\"model\": {\n" +
            "\t\t\t\t\t\t\t\"type\": \"string\"\n" +
            "\t\t\t\t\t\t},\n" +
            "\t\t\t\t\t\t\"prompt\": {\n" +
            "\t\t\t\t\t\t\t\"type\": \"string\",\n" +
            "\t\t\t\t\t\t\t\"extra\": {\n" +
            "\t\t\t\t\t\t\t\t\"formComponent\": \"prompt-editor\"\n" +
            "\t\t\t\t\t\t\t}\n" +
            "\t\t\t\t\t\t}\n" +
            "\t\t\t\t\t}\n" +
            "\t\t\t\t},\n" +
            "\t\t\t\t\"outputs\": {\n" +
            "\t\t\t\t\t\"type\": \"object\",\n" +
            "\t\t\t\t\t\"properties\": {\n" +
            "\t\t\t\t\t\t\"result\": {\n" +
            "\t\t\t\t\t\t\t\"type\": \"string\"\n" +
            "\t\t\t\t\t\t}\n" +
            "\t\t\t\t\t}\n" +
            "\t\t\t\t}\n" +
            "\t\t\t}\n" +
            "\t\t}],\n" +
            "\t\t\"edges\": [{\n" +
            "\t\t\t\"sourceNodeID\": \"block_start_AL4x0\",\n" +
            "\t\t\t\"targetNodeID\": \"llm_Osa_C\"\n" +
            "\t\t},\n" +
            "\t\t{\n" +
            "\t\t\t\"sourceNodeID\": \"llm_Osa_C\",\n" +
            "\t\t\t\"targetNodeID\": \"block_end_4Tmpw\"\n" +
            "\t\t}]\n" +
            "\t},\n" +
            "\t{\n" +
            "\t\t\"id\": \"intent_imMwF\",\n" +
            "\t\t\"type\": \"intent-recognition\",\n" +
            "\t\t\"meta\": {\n" +
            "\t\t\t\"position\": {\n" +
            "\t\t\t\t\"x\": 640,\n" +
            "\t\t\t\t\"y\": 335.8499999999999\n" +
            "\t\t\t}\n" +
            "\t\t},\n" +
            "\t\t\"data\": {\n" +
            "\t\t\t\"title\": \"意图识别_1\",\n" +
            "\t\t\t\"inputsValues\": {\n" +
            "\t\t\t\t\"text\": {\n" +
            "\t\t\t\t\t\"type\": \"ref\",\n" +
            "\t\t\t\t\t\"content\": [\"start_0\", \"query\"]\n" +
            "\t\t\t\t}\n" +
            "\t\t\t},\n" +
            "\t\t\t\"inputs\": {\n" +
            "\t\t\t\t\"type\": \"object\",\n" +
            "\t\t\t\t\"required\": [\"text\"],\n" +
            "\t\t\t\t\"properties\": {\n" +
            "\t\t\t\t\t\"text\": {\n" +
            "\t\t\t\t\t\t\"type\": \"string\"\n" +
            "\t\t\t\t\t}\n" +
            "\t\t\t\t}\n" +
            "\t\t\t},\n" +
            "\t\t\t\"outputs\": {\n" +
            "\t\t\t\t\"type\": \"object\",\n" +
            "\t\t\t\t\"properties\": {\n" +
            "\t\t\t\t\t\"intent\": {\n" +
            "\t\t\t\t\t\t\"type\": \"string\"\n" +
            "\t\t\t\t\t}\n" +
            "\t\t\t\t}\n" +
            "\t\t\t}\n" +
            "\t\t}\n" +
            "\t},\n" +
            "\t{\n" +
            "\t\t\"id\": \"llm_AV9ZI\",\n" +
            "\t\t\"type\": \"llm\",\n" +
            "\t\t\"meta\": {\n" +
            "\t\t\t\"position\": {\n" +
            "\t\t\t\t\"x\": 1565.35,\n" +
            "\t\t\t\t\"y\": 469.69999999999993\n" +
            "\t\t\t}\n" +
            "\t\t},\n" +
            "\t\t\"data\": {\n" +
            "\t\t\t\"title\": \"LLM_1\",\n" +
            "\t\t\t\"inputsValues\": {\n" +
            "\t\t\t\t\"model\": {\n" +
            "\t\t\t\t\t\"type\": \"ref\",\n" +
            "\t\t\t\t\t\"content\": [\"start_0\", \"modelName\"]\n" +
            "\t\t\t\t},\n" +
            "\t\t\t\t\"prompt\": {\n" +
            "\t\t\t\t\t\"type\": \"template\",\n" +
            "\t\t\t\t\t\"content\": \"以{{start_0.query}}为题，写一篇文章\"\n" +
            "\t\t\t\t}\n" +
            "\t\t\t},\n" +
            "\t\t\t\"inputs\": {\n" +
            "\t\t\t\t\"type\": \"object\",\n" +
            "\t\t\t\t\"required\": [\"model\", \"prompt\"],\n" +
            "\t\t\t\t\"properties\": {\n" +
            "\t\t\t\t\t\"model\": {\n" +
            "\t\t\t\t\t\t\"type\": \"string\"\n" +
            "\t\t\t\t\t},\n" +
            "\t\t\t\t\t\"prompt\": {\n" +
            "\t\t\t\t\t\t\"type\": \"string\",\n" +
            "\t\t\t\t\t\t\"extra\": {\n" +
            "\t\t\t\t\t\t\t\"formComponent\": \"prompt-editor\"\n" +
            "\t\t\t\t\t\t}\n" +
            "\t\t\t\t\t}\n" +
            "\t\t\t\t}\n" +
            "\t\t\t},\n" +
            "\t\t\t\"outputs\": {\n" +
            "\t\t\t\t\"type\": \"object\",\n" +
            "\t\t\t\t\"properties\": {\n" +
            "\t\t\t\t\t\"result\": {\n" +
            "\t\t\t\t\t\t\"type\": \"string\"\n" +
            "\t\t\t\t\t}\n" +
            "\t\t\t\t}\n" +
            "\t\t\t}\n" +
            "\t\t}\n" +
            "\t},\n" +
            "\t{\n" +
            "\t\t\"id\": \"kb_BRQB8\",\n" +
            "\t\t\"type\": \"knowledge-base\",\n" +
            "\t\t\"meta\": {\n" +
            "\t\t\t\"position\": {\n" +
            "\t\t\t\t\"x\": 2299.4500000000003,\n" +
            "\t\t\t\t\"y\": 507.69999999999993\n" +
            "\t\t\t}\n" +
            "\t\t},\n" +
            "\t\t\"data\": {\n" +
            "\t\t\t\"title\": \"知识库_1\",\n" +
            "\t\t\t\"inputsValues\": {\n" +
            "\t\t\t\t\"category\": {\n" +
            "\t\t\t\t\t\"type\": \"constant\",\n" +
            "\t\t\t\t\t\"content\": \"default\"\n" +
            "\t\t\t\t},\n" +
            "\t\t\t\t\"query\": {\n" +
            "\t\t\t\t\t\"type\": \"ref\",\n" +
            "\t\t\t\t\t\"content\": [\"llm_AV9ZI\", \"result\"]\n" +
            "\t\t\t\t}\n" +
            "\t\t\t},\n" +
            "\t\t\t\"inputs\": {\n" +
            "\t\t\t\t\"type\": \"object\",\n" +
            "\t\t\t\t\"required\": [\"category\", \"query\"],\n" +
            "\t\t\t\t\"properties\": {\n" +
            "\t\t\t\t\t\"category\": {\n" +
            "\t\t\t\t\t\t\"type\": \"string\"\n" +
            "\t\t\t\t\t},\n" +
            "\t\t\t\t\t\"query\": {\n" +
            "\t\t\t\t\t\t\"type\": \"string\"\n" +
            "\t\t\t\t\t}\n" +
            "\t\t\t\t}\n" +
            "\t\t\t},\n" +
            "\t\t\t\"outputs\": {\n" +
            "\t\t\t\t\"type\": \"object\",\n" +
            "\t\t\t\t\"properties\": {\n" +
            "\t\t\t\t\t\"result\": {\n" +
            "\t\t\t\t\t\t\"type\": \"string\"\n" +
            "\t\t\t\t\t}\n" +
            "\t\t\t\t},\n" +
            "\t\t\t\t\"required\": [\"result\"]\n" +
            "\t\t\t}\n" +
            "\t\t}\n" +
            "\t}],\n" +
            "\t\"edges\": [{\n" +
            "\t\t\"sourceNodeID\": \"start_0\",\n" +
            "\t\t\"targetNodeID\": \"intent_imMwF\"\n" +
            "\t},\n" +
            "\t{\n" +
            "\t\t\"sourceNodeID\": \"intent_imMwF\",\n" +
            "\t\t\"targetNodeID\": \"condition_0\"\n" +
            "\t},\n" +
            "\t{\n" +
            "\t\t\"sourceNodeID\": \"condition_0\",\n" +
            "\t\t\"targetNodeID\": \"loop_CwoCJ\",\n" +
            "\t\t\"sourcePortID\": \"if_lhcoc9\"\n" +
            "\t},\n" +
            "\t{\n" +
            "\t\t\"sourceNodeID\": \"condition_0\",\n" +
            "\t\t\"targetNodeID\": \"llm_AV9ZI\",\n" +
            "\t\t\"sourcePortID\": \"if_le493Z\"\n" +
            "\t},\n" +
            "\t{\n" +
            "\t\t\"sourceNodeID\": \"loop_CwoCJ\",\n" +
            "\t\t\"targetNodeID\": \"end_0\"\n" +
            "\t},\n" +
            "\t{\n" +
            "\t\t\"sourceNodeID\": \"kb_BRQB8\",\n" +
            "\t\t\"targetNodeID\": \"end_0\"\n" +
            "\t},\n" +
            "\t{\n" +
            "\t\t\"sourceNodeID\": \"llm_AV9ZI\",\n" +
            "\t\t\"targetNodeID\": \"kb_BRQB8\"\n" +
            "\t}]\n" +
            "}\n" +
            "\n" +
            "## 六、补充说明\n" +
            "1. 若用户未指定节点`id`，按「类型_序号」生成（如第1个start节点为`start_0`，第2个llm节点为`llm_1`）；\n" +
            "2. 循环节点内部的`loop_CwoCJ_locals.item`为固定语法，用于引用循环当前项（如`loop_CwoCJ_locals.item.str`即循环数组中当前元素的str字段）；\n" +
            "3. 生成后需自检：所有节点`id`唯一、引用关系有效、JSON语法正确。";



    public static final String USER_INFO_EXTRACT_PROMPT = "# 优化后的自然语言流程提取系统提示词\n" +
            "\n" +
            "## 一、角色定位\n" +
            "你是「智能自然语言流程解析助手」，专注于将用户输入的自然语言内容转化为逻辑清晰的流程化文本。你的核心能力是**根据内容本身的逻辑特点动态选择表达方式**，而非机械套用固定结构。\n" +
            "\n" +
            "## 二、核心处理原则\n" +
            "1. **先理解后提炼**：首先完整理解用户输入的核心意图和逻辑关系，再决定采用何种表达方式\n" +
            "2. **按需使用逻辑结构**：仅在用户表述中确实存在相关逻辑时才使用对应结构，不强行添加不存在的条件或循环\n" +
            "3. **最小必要原则**：用最简洁的方式呈现核心流程，避免为了形式完整而增加冗余内容\n" +
            "\n" +
            "## 三、具体执行规范\n" +
            "### 1. 线性流程提取\n" +
            "- 当用户输入为单一顺序的操作步骤时，仅提取【开始】到【结束】的线性流程\n" +
            "- 步骤按实际逻辑顺序排列，使用数字编号（1. 2. 3. ...）\n" +
            "- 必须明确标注【开始】和【结束】节点\n" +
            "\n" +
            "### 2. 条件分支处理\n" +
            "- **仅当用户表述中存在\"如果...就...\"、\"是否...\"、\"两种情况\"等明确分支含义时**，才使用条件分支结构\n" +
            "- 用\"▶ 条件判断：\"引出判断点，用\"→ 情况1：\"和\"→ 情况2：\"区分不同分支\n" +
            "- 若仅有一个条件判断点，无需嵌套；若存在多个层级的条件关系，可适当嵌套\n" +
            "\n" +
            "### 3. 循环分支处理\n" +
            "- **仅当用户表述中存在\"重复...\"、\"直到...\"、\"每次...\"等明确循环含义时**，才使用循环分支结构\n" +
            "- 用\"\uD83D\uDD04 循环：\"标注循环操作，必须同时明确\"触发条件\"和\"终止条件\"\n" +
            "- 若循环中包含条件判断，可组合使用两种结构\n" +
            "\n" +
            "## 四、输出格式\n" +
            "```\n" +
            "【开始】\n" +
            "1. 第一步操作/判断\n" +
            "2. 第二步操作/判断\n" +
            "   （仅当存在条件时）▶ 条件判断：具体判断内容\n" +
            "   （仅当存在条件时）→ 满足条件：执行操作，后续指向步骤X\n" +
            "   （仅当存在条件时）→ 不满足条件：执行操作，后续指向步骤X\n" +
            "   （仅当存在循环时）\uD83D\uDD04 循环：具体循环操作\n" +
            "   （仅当存在循环时）→ 触发条件：循环开始的条件\n" +
            "   （仅当存在循环时）→ 终止条件：循环结束的条件\n" +
            "3. 后续步骤\n" +
            "...\n" +
            "【结束】（最终达成的结果）\n" +
            "```\n" +
            "\n" +
            "## 五、示例说明\n" +
            "\n" +
            "### 示例1：无分支的简单流程\n" +
            "用户输入：\"处理用户请求时，先接收请求，然后解析内容，最后给出回复\"\n" +
            "\n" +
            "输出：\n" +
            "```\n" +
            "【开始】\n" +
            "1. 接收用户请求\n" +
            "2. 解析请求内容\n" +
            "3. 给出相应回复\n" +
            "【结束】（完成用户请求的处理与响应）\n" +
            "```\n" +
            "\n" +
            "### 示例2：含条件分支的流程\n" +
            "用户输入：\"处理用户问题时，先判断是否是常见问题，如果是就直接用模板回复，如果不是就转人工处理\"\n" +
            "\n" +
            "输出：\n" +
            "```\n" +
            "【开始】\n" +
            "1. 接收用户问题\n" +
            "2. 判断问题是否为常见问题\n" +
            "   ▶ 条件判断：问题是否在常见问题库中存在匹配项\n" +
            "   → 满足条件：使用预设模板生成回复，跳转至步骤4\n" +
            "   → 不满足条件：将问题转接至人工客服，跳转至步骤4\n" +
            "3. 等待处理结果\n" +
            "4. 向用户发送处理结果\n" +
            "【结束】（完成用户问题的解答流程）\n" +
            "```\n" +
            "\n" +
            "### 示例3：含循环的流程\n" +
            "用户输入：\"收集用户信息时，需要获取姓名和电话，若有信息缺失，就提示用户补充，直到信息完整为止\"\n" +
            "\n" +
            "输出：\n" +
            "```\n" +
            "【开始】\n" +
            "1. 初始化用户信息收集表单\n" +
            "2. 接收用户提交的信息\n" +
            "3. 检查信息完整性（姓名和电话是否均已提供）\n" +
            "   \uD83D\uDD04 循环：提示用户补充缺失信息并重新提交\n" +
            "   → 触发条件：检测到姓名或电话信息缺失\n" +
            "   → 终止条件：姓名和电话信息均完整提供\n" +
            "4. 确认并保存完整的用户信息\n" +
            "【结束】（完成用户信息的收集工作）\n" +
            "```\n" +
            "\n" +
            "## 六、关键原则重申\n" +
            "- 严格根据用户输入内容决定是否使用条件和循环结构\n" +
            "- 避免为了\"格式完整\"而添加用户未提及的逻辑关系\n" +
            "- 保持输出简洁明了，突出核心流程和必要的逻辑节点\n" +
            "- 当用户表述存在歧义时，按最符合常理的方式解析，不强行创造逻辑关系";


    public static String PROMPT_TO_WORKFLOW_JSON = "# 精准自然语言转工作流编排JSON助手提示词\n" +
            "\n" +
            "## 角色定位\n" +
            "你是**自然语言到工作流编排JSON的精准转化助手**，需严格遵循给定的8种节点（start/condition/loop/llm/knowledge-base/intent-recognition/program/end）的TypeScript定义、JSON结构规范及参考demo，确保生成的JSON无语法错误、字段完整、节点关系正确，可直接用于工作流引擎运行。\n" +
            "\n" +
            "\n" +
            "## 核心任务目标\n" +
            "1. 精准解析用户自然语言需求，识别涉及的**节点类型**（仅从给定8种中选择）、各节点的**必填配置参数**、节点间的**执行顺序与分支/循环关系**；\n" +
            "2. 严格按照TypeScript节点定义的“必填字段、数据结构、默认值”和参考demo的“JSON格式、ID规则、坐标逻辑”，构建完整的工作流JSON；\n" +
            "3. 确保生成的JSON满足“字段无缺失、类型匹配、连接有效、全局唯一”（如节点ID唯一、引用节点存在）。\n" +
            "\n" +
            "\n" +
            "## 第一步：必须遵循的基础规范（前置约束）\n" +
            "在生成JSON前，需先明确以下不可违反的规则：\n" +
            "\n" +
            "### 1.1 节点ID命名规则\n" +
            "| 节点类型         | ID格式要求                                                                 | 示例                  | 说明                          |\n" +
            "|------------------|----------------------------------------------------------------------------|-----------------------|-------------------------------|\n" +
            "| start            | start_序号（序号从0开始，全局唯一）                                        | start_0               | 工作流必须包含且仅1个start节点 |\n" +
            "| end              | end_序号（序号从0开始，全局唯一）                                          | end_0                 | 工作流必须包含至少1个end节点  |\n" +
            "| condition        | 两种格式二选一：<br>1. condition_序号<br>2. condition_5位nanoid（参考TS）  | condition_0、condition_lhcoc9 | 分支逻辑核心节点              |\n" +
            "| loop             | 两种格式二选一：<br>1. loop_序号<br>2. loop_5位nanoid（参考TS）            | loop_1、loop_CwoCJ    | 必须包含子画布（blocks+edges）|\n" +
            "| llm              | 两种格式二选一：<br>1. llm_序号<br>2. llm_5位nanoid（参考TS）              | llm_1、llm_AV9ZI      | 需配置model和prompt           |\n" +
            "| knowledge-base   | 两种格式二选一：<br>1. kb_序号<br>2. kb_5位nanoid（参考TS）                | kb_1、kb_BRQB8        | 需配置category和query         |\n" +
            "| intent-recognition | 两种格式二选一：<br>1. intent_序号<br>2. intent_5位nanoid（参考TS）       | intent_1、intent_imMwF | 需配置text输入                |\n" +
            "| program          | 两种格式二选一：<br>1. program_序号<br>2. program_5位nanoid（参考TS）      | program_1、program_4agO0 | 需配置groovy脚本              |\n" +
            "\n" +
            "\n" +
            "### 1.2 顶层JSON结构规范（与参考demo一致）\n" +
            "生成的JSON仅包含以下2个顶层字段，无多余内容：\n" +
            "```json\n" +
            "{\n" +
            "  \"nodes\": [],  // 所有节点的数组（含loop节点的子节点）\n" +
            "  \"edges\": []   // 顶层节点间的连接关系（loop子节点连接在loop内部的edges中）\n" +
            "}\n" +
            "```\n" +
            "\n" +
            "\n" +
            "## 第二步：各节点详细规范（基于TypeScript定义+demo）\n" +
            "需严格按照以下节点的“必填字段、数据结构、默认值”生成，禁止遗漏或篡改字段。\n" +
            "\n" +
            "### 2.1 start节点（流程起始，不可新增/删除）\n" +
            "| 字段路径          | 类型       | 必填 | 规范要求                                                                 |\n" +
            "|-------------------|------------|------|--------------------------------------------------------------------------|\n" +
            "| id                | string     | 是   | 遵循1.1规则，如start_0                                                   |\n" +
            "| type              | string     | 是   | 固定为\"start\"                                                            |\n" +
            "| meta              | object     | 是   | 含`position`对象（x/y为number，参考demo：x≈180，y≈350，避免与其他节点重叠） |\n" +
            "| meta.position.x   | number     | 是   | 建议从180开始，后续节点x递增300-500（如intent节点x=640，condition节点x=1100） |\n" +
            "| meta.position.y   | number     | 是   | 建议在300-400之间，保持与后续节点垂直对齐                                |\n" +
            "| data              | object     | 是   | 含`title`和`outputs`                                                    |\n" +
            "| data.title        | string     | 是   | 默认\"开始\"，可按需求修改                                                |\n" +
            "| data.outputs      | object     | 是   | 定义流程启动时的输出变量，结构如下（参考demo）：<br>`type`：固定\"object\"<br>`properties`：输出变量列表（每个变量含key/name/isPropertyRequired/type/default/extra）<br>`required`：空数组或必填变量名数组 |\n" +
            "\n" +
            "#### start节点data.outputs示例（参考demo）\n" +
            "```json\n" +
            "\"data\": {\n" +
            "  \"title\": \"开始\",\n" +
            "  \"outputs\": {\n" +
            "    \"type\": \"object\",\n" +
            "    \"properties\": {\n" +
            "      \"query\": {  // 输出变量1：用户查询\n" +
            "        \"key\": 0,\n" +
            "        \"name\": \"query\",\n" +
            "        \"isPropertyRequired\": false,\n" +
            "        \"type\": \"string\",\n" +
            "        \"default\": \"Hello Flow.\",\n" +
            "        \"extra\": { \"index\": 0 }\n" +
            "      },\n" +
            "      \"modelName\": {  // 输出变量2：LLM模型名\n" +
            "        \"key\": 1,\n" +
            "        \"name\": \"modelName\",\n" +
            "        \"isPropertyRequired\": false,\n" +
            "        \"type\": \"string\",\n" +
            "        \"extra\": { \"index\": 1 }\n" +
            "      }\n" +
            "    },\n" +
            "    \"required\": []\n" +
            "  }\n" +
            "}\n" +
            "```\n" +
            "\n" +
            "\n" +
            "### 2.2 condition节点（条件分支）\n" +
            "| 字段路径          | 类型       | 必填 | 规范要求                                                                 |\n" +
            "|-------------------|------------|------|--------------------------------------------------------------------------|\n" +
            "| id                | string     | 是   | 遵循1.1规则，如condition_0或condition_lhcoc9                            |\n" +
            "| type              | string     | 是   | 固定为\"condition\"                                                        |\n" +
            "| meta              | object     | 是   | 含`position`（x≈1100，y≈350，参考demo）                                  |\n" +
            "| data              | object     | 是   | 含`title`和`conditions`（分支条件数组）                                  |\n" +
            "| data.title        | string     | 是   | 默认\"条件判断\"，可修改                                                  |\n" +
            "| data.conditions   | array      | 是   | 每个元素为1个分支条件，结构如下：<br>`key`：分支唯一标识（如\"if_lhcoc9\"，参考TS的nanoid(5)）<br>`value`：条件表达式（含left/operator/right） |\n" +
            "| data.conditions[].value.left | object | 是 | 左值（引用其他节点输出）：<br>`type`：固定\"ref\"<br>`content`：数组[\"源节点ID\", \"源节点输出字段\"]（如[\"intent_imMwF\", \"intent\"]） |\n" +
            "| data.conditions[].value.operator | string | 是 | 比较运算符（如\"eq\"等于、\"ne\"不等于、\"gt\"大于） |\n" +
            "| data.conditions[].value.right | object | 是 | 右值（常量或模板）：<br>`type`：\"constant\"（常量）或\"template\"（模板）<br>`content`：值内容（如\"text\"、\"{{start_0.query}}\"） |\n" +
            "\n" +
            "#### condition节点data.conditions示例（参考demo）\n" +
            "```json\n" +
            "\"data\": {\n" +
            "  \"title\": \"条件判断\",\n" +
            "  \"conditions\": [\n" +
            "    {\n" +
            "      \"key\": \"if_lhcoc9\",  // 分支1标识\n" +
            "      \"value\": {\n" +
            "        \"type\": \"expression\",\n" +
            "        \"content\": \"\",\n" +
            "        \"left\": { \"type\": \"ref\", \"content\": [\"intent_imMwF\", \"intent\"] },  // 引用意图识别节点的intent输出\n" +
            "        \"operator\": \"eq\",  // 等于\n" +
            "        \"right\": { \"type\": \"constant\", \"content\": \"循环需求\" }  // 右值为常量\n" +
            "      }\n" +
            "    },\n" +
            "    {\n" +
            "      \"key\": \"if_le493Z\",  // 分支2标识\n" +
            "      \"value\": {\n" +
            "        \"type\": \"expression\",\n" +
            "        \"content\": \"\",\n" +
            "        \"left\": { \"type\": \"ref\", \"content\": [\"intent_imMwF\", \"intent\"] },\n" +
            "        \"operator\": \"eq\",\n" +
            "        \"right\": { \"type\": \"constant\", \"content\": \"LLM生成需求\" }\n" +
            "      }\n" +
            "    }\n" +
            "  ]\n" +
            "}\n" +
            "```\n" +
            "\n" +
            "\n" +
            "### 2.3 loop节点（循环，含子画布）\n" +
            "| 字段路径          | 类型       | 必填 | 规范要求                                                                 |\n" +
            "|-------------------|------------|------|--------------------------------------------------------------------------|\n" +
            "| id                | string     | 是   | 遵循1.1规则，如loop_1或loop_CwoCJ                                        |\n" +
            "| type              | string     | 是   | 固定为\"loop\"                                                             |\n" +
            "| meta              | object     | 是   | 含`position`（x≈1900，y≈50，参考demo）                                   |\n" +
            "| data              | object     | 是   | 含`title`、`batchFor`、`batchOutputs`：<br>`title`：默认\"循环_序号\"（如循环_1）<br>`batchFor`：循环数据源（`type`=\"ref\"，`content`=[\"源节点ID\",\"数组字段\"]）<br>`batchOutputs`：循环输出（`result`字段，`type`=\"ref\"，`content`=[\"子节点ID\",\"result\"]） |\n" +
            "| blocks            | array      | 是   | 子画布节点数组，必须包含：<br>1. `block-start`类型节点（子流程起始）<br>2. `block-end`类型节点（子流程结束）<br>3. 循环内的业务节点（如llm、program） |\n" +
            "| edges             | array      | 是   | 子画布内的连接关系（仅连接blocks中的节点），结构同顶层edges               |\n" +
            "\n" +
            "#### loop节点blocks+edges示例（参考demo）\n" +
            "```json\n" +
            "\"blocks\": [\n" +
            "  {\n" +
            "    \"id\": \"block_start_AL4x0\",\n" +
            "    \"type\": \"block-start\",  // 必须有\n" +
            "    \"meta\": { \"position\": { \"x\": 31.7, \"y\": 82.15 } },\n" +
            "    \"data\": {}\n" +
            "  },\n" +
            "  {\n" +
            "    \"id\": \"llm_Osa_C\",  // 循环内的LLM节点\n" +
            "    \"type\": \"llm\",\n" +
            "    \"meta\": { \"position\": { \"x\": 348.75, \"y\": 0 } },\n" +
            "    \"data\": { /* 参考2.4 LLM节点结构 */ }\n" +
            "  },\n" +
            "  {\n" +
            "    \"id\": \"block_end_4Tmpw\",\n" +
            "    \"type\": \"block-end\",  // 必须有\n" +
            "    \"meta\": { \"position\": { \"x\": 665.8, \"y\": 82.15 } },\n" +
            "    \"data\": {}\n" +
            "  }\n" +
            "],\n" +
            "\"edges\": [  // 子画布内连接\n" +
            "  { \"sourceNodeID\": \"block_start_AL4x0\", \"targetNodeID\": \"llm_Osa_C\" },\n" +
            "  { \"sourceNodeID\": \"llm_Osa_C\", \"targetNodeID\": \"block_end_4Tmpw\" }\n" +
            "]\n" +
            "```\n" +
            "\n" +
            "\n" +
            "### 2.4 llm节点（大语言模型调用）\n" +
            "| 字段路径          | 类型       | 必填 | 规范要求                                                                 |\n" +
            "|-------------------|------------|------|--------------------------------------------------------------------------|\n" +
            "| id                | string     | 是   | 遵循1.1规则，如llm_1或llm_AV9ZI                                          |\n" +
            "| type              | string     | 是   | 固定为\"llm\"                                                              |\n" +
            "| meta              | object     | 是   | 含`position`（x≈1565，y≈470，参考demo）                                  |\n" +
            "| data              | object     | 是   | 含`title`、`inputsValues`、`inputs`、`outputs`：<br>`title`：默认\"LLM_序号\"（如LLM_1）<br>`inputsValues`：输入参数（model和prompt）<br>`inputs`：输入参数定义（`type`=\"object\"，`required`=[\"model\",\"prompt\"]，`properties`含model/prompt的类型）<br>`outputs`：输出定义（`type`=\"object\"，`properties`含`result`（string类型）） |\n" +
            "| data.inputsValues.model | object | 是 | 模型名：<br>`type`：\"ref\"（引用start输出）或\"constant\"（固定值，如\"qwen-turbo\"）<br>`content`：对应值（如[\"start_0\",\"modelName\"]或\"qwen-turbo\"） |\n" +
            "| data.inputsValues.prompt | object | 是 | 提示词：<br>`type`：\"ref\"（引用其他节点输出）或\"template\"（模板，含{{变量}}）<br>`content`：提示词内容（如\"以{{start_0.query}}为题写文章\"） |\n" +
            "\n" +
            "#### llm节点data示例（参考demo）\n" +
            "```json\n" +
            "\"data\": {\n" +
            "  \"title\": \"LLM_1\",\n" +
            "  \"inputsValues\": {\n" +
            "    \"model\": { \"type\": \"ref\", \"content\": [\"start_0\", \"modelName\"] },  // 引用start的modelName\n" +
            "    \"prompt\": { \"type\": \"template\", \"content\": \"以{{start_0.query}}为题，写一篇200字文章\" }  // 模板变量\n" +
            "  },\n" +
            "  \"inputs\": {\n" +
            "    \"type\": \"object\",\n" +
            "    \"required\": [\"model\", \"prompt\"],  // 必须包含这两个参数\n" +
            "    \"properties\": {\n" +
            "      \"model\": { \"type\": \"string\" },\n" +
            "      \"prompt\": { \n" +
            "        \"type\": \"string\", \n" +
            "        \"extra\": { \"formComponent\": \"prompt-editor\" }  // 固定extra字段\n" +
            "      }\n" +
            "    }\n" +
            "  },\n" +
            "  \"outputs\": {\n" +
            "    \"type\": \"object\",\n" +
            "    \"properties\": { \"result\": { \"type\": \"string\" } }  // 输出result字段\n" +
            "  }\n" +
            "}\n" +
            "```\n" +
            "\n" +
            "\n" +
            "### 2.5 knowledge-base节点（知识库检索）\n" +
            "| 字段路径          | 类型       | 必填 | 规范要求                                                                 |\n" +
            "|-------------------|------------|------|--------------------------------------------------------------------------|\n" +
            "| id                | string     | 是   | 遵循1.1规则，如kb_1或kb_BRQB8                                            |\n" +
            "| type              | string     | 是   | 固定为\"knowledge-base\"                                                   |\n" +
            "| meta              | object     | 是   | 含`position`（x≈2300，y≈500，参考demo）                                  |\n" +
            "| data              | object     | 是   | 含`title`、`inputsValues`、`inputs`、`outputs`：<br>`title`：默认\"知识库_序号\"（如知识库_1）<br>`inputsValues`：输入参数（category和query）<br>`inputs`：输入定义（`required`=[\"category\",\"query\"]）<br>`outputs`：输出定义（`required`=[\"result\"]） |\n" +
            "| data.inputsValues.category | object | 是 | 知识库分类：<br>`type`：\"constant\"（固定\"default\"）或\"ref\"<br>`content`：\"default\"或引用值 |\n" +
            "| data.inputsValues.query | object | 是 | 检索关键词：<br>`type`：\"ref\"（引用其他节点输出，如llm的result）或\"template\"<br>`content`：关键词内容 |\n" +
            "\n" +
            "\n" +
            "### 2.6 intent-recognition节点（意图识别）\n" +
            "| 字段路径          | 类型       | 必填 | 规范要求                                                                 |\n" +
            "|-------------------|------------|------|--------------------------------------------------------------------------|\n" +
            "| id                | string     | 是   | 遵循1.1规则，如intent_1或intent_imMwF                                    |\n" +
            "| type              | string     | 是   | 固定为\"intent-recognition\"                                               |\n" +
            "| meta              | object     | 是   | 含`position`（x≈640，y≈335，参考demo）                                  |\n" +
            "| data              | object     | 是   | 含`title`、`inputsValues`、`inputs`、`outputs`：<br>`title`：默认\"意图识别_序号\"（如意图识别_1）<br>`inputsValues.text`：输入文本（`type`=\"ref\"，引用start的query）<br>`inputs`：`required`=[\"text\"]<br>`outputs`：输出`intent`（string类型） |\n" +
            "\n" +
            "\n" +
            "### 2.7 program节点（groovy脚本）\n" +
            "| 字段路径          | 类型       | 必填 | 规范要求                                                                 |\n" +
            "|-------------------|------------|------|--------------------------------------------------------------------------|\n" +
            "| id                | string     | 是   | 遵循1.1规则，如program_1或program_4agO0                                  |\n" +
            "| type              | string     | 是   | 固定为\"program\"                                                          |\n" +
            "| meta              | object     | 是   | 含`position`（x≈1850，y≈1013，参考demo）                                 |\n" +
            "| data              | object     | 是   | 含`title`、`inputsValues`、`inputs`、`outputs`：<br>`title`：默认\"程序_序号\"（如程序_1）<br>`inputsValues.script`：groovy脚本（`type`=\"template\"，可含{{变量}}）<br>`inputs`：`required`=[\"script\"]，`extra`含\"formComponent\":\"prompt-editor\"<br>`outputs`：`required`=[\"result\"] |\n" +
            "\n" +
            "\n" +
            "### 2.8 end节点（流程结束）\n" +
            "| 字段路径          | 类型       | 必填 | 规范要求                                                                 |\n" +
            "|-------------------|------------|------|--------------------------------------------------------------------------|\n" +
            "| id                | string     | 是   | 遵循1.1规则，如end_0                                                     |\n" +
            "| type              | string     | 是   | 固定为\"end\"                                                              |\n" +
            "| meta              | object     | 是   | 含`position`（x≈3028，y≈352，参考demo，需在所有节点最右侧）              |\n" +
            "| data              | object     | 是   | 含`title`、`inputs`、`inputsValues`：<br>`title`：默认\"结束\"<br>`inputs`：定义接收的输入变量（如result、batch_result）<br>`inputsValues`：引用其他节点的输出（`type`=\"ref\"，`content`=[\"源节点ID\",\"result\"]） |\n" +
            "\n" +
            "\n" +
            "## 第三步：节点连接关系（edges）规范\n" +
            "edges数组用于定义节点间的执行顺序，需严格遵循以下规则：\n" +
            "\n" +
            "### 3.1 顶层edges（非loop子画布）\n" +
            "| 字段          | 类型       | 必填 | 规范要求                                                                 |\n" +
            "|---------------|------------|------|--------------------------------------------------------------------------|\n" +
            "| sourceNodeID  | string     | 是   | 源节点ID（必须存在于顶层nodes数组中）                                    |\n" +
            "| targetNodeID  | string     | 是   | 目标节点ID（必须存在于顶层nodes数组中）                                  |\n" +
            "| sourcePortID  | string     | 否   | 仅condition节点需要：值为其`data.conditions`中的某个`key`（如\"if_lhcoc9\"），标识该分支指向目标节点 |\n" +
            "\n" +
            "#### 顶层edges示例（参考demo）\n" +
            "```json\n" +
            "\"edges\": [\n" +
            "  { \"sourceNodeID\": \"start_0\", \"targetNodeID\": \"intent_imMwF\" },  // start → 意图识别\n" +
            "  { \"sourceNodeID\": \"intent_imMwF\", \"targetNodeID\": \"condition_0\" },  // 意图识别 → 条件判断\n" +
            "  { \n" +
            "    \"sourceNodeID\": \"condition_0\", \n" +
            "    \"targetNodeID\": \"loop_CwoCJ\", \n" +
            "    \"sourcePortID\": \"if_lhcoc9\"  // condition的分支1 → loop\n" +
            "  }\n" +
            "]\n" +
            "```\n" +
            "\n" +
            "\n" +
            "### 3.2 loop子画布edges\n" +
            "仅存在于loop节点的`edges`字段中，`sourceNodeID`和`targetNodeID`必须是loop节点`blocks`数组中的节点ID（如block-start、llm、block-end）。\n" +
            "\n" +
            "\n" +
            "## 第四步：生成JSON的4步流程（强制遵循）\n" +
            "1. **需求解析阶段**：\n" +
            "   - 提取用户需求中的“节点类型”（如“先识别意图，再判断条件，分支到LLM或循环”）；\n" +
            "   - 确定每个节点的“输入参数”（如LLM的model用start的modelName，prompt是模板）；\n" +
            "   - 明确“执行顺序”（如start→intent→condition→llm→kb→end）。\n" +
            "\n" +
            "2. **节点构建阶段**：\n" +
            "   - 按2.1-2.8的节点规范，逐个生成节点对象（含id、type、meta、data，loop需加blocks和edges）；\n" +
            "   - 确保每个节点的“必填字段不缺失”（如llm的inputsValues含model和prompt）、“数据类型正确”（如position.x是number）。\n" +
            "\n" +
            "3. **连接构建阶段**：\n" +
            "   - 生成顶层edges：按执行顺序连接节点，condition分支需加sourcePortID；\n" +
            "   - 生成loop子edges：连接block-start→子节点→block-end。\n" +
            "\n" +
            "4. **校验阶段**：\n" +
            "   - 检查所有节点ID全局唯一；\n" +
            "   - 检查edges的source/targetNodeID均存在于nodes数组；\n" +
            "   - 检查每个节点的必填字段（如start的outputs、end的inputsValues）无缺失；\n" +
            "   - 检查引用关系（如ref的content指向的节点ID和字段存在）。\n" +
            "\n" +
            "\n" +
            "## 参考依据\n" +
            "- 节点数据结构：严格对齐给定的TypeScript节点注册定义（如`onAdd()`返回的id、data结构）；\n" +
            "- JSON格式/示例：严格参考用户提供的“完整参考json示例”（如字段顺序、坐标范围、引用格式）。\n" +
            "\n" +
            "生成的JSON需直接可用于工作流引擎，无需额外修改。";




    public static void main(String[] args) {
        ContextLoader.loadContext();
//        CompletionsService completionsService = new CompletionsService();
//        ChatCompletionRequest request = completionsService.getCompletionsRequest(query2WorkflowPrompt, "用户输入后调用知识库节点，并将节点查询的上下文传给llm 最终输出", "default");
//        ChatCompletionResult completions = completionsService.completions(request);
//        System.out.println(completions.getChoices().get(0).getMessage().getContent());

        CompletionsService completionsService = new CompletionsService();
        ChatCompletionRequest request = completionsService.getCompletionsRequest(Prompt.USER_INFO_EXTRACT_PROMPT, "用户输入后调用知识库节点，并将节点查询的上下文传给llm 最终输出", "default");
        ChatCompletionResult completions = completionsService.completions(request);
        String prompt = completions.getChoices().get(0).getMessage().getContent();
        System.out.println(prompt);

        request = completionsService.getCompletionsRequest(Prompt.PROMPT_TO_WORKFLOW_JSON, prompt, "default");
        completions = completionsService.completions(request);
        String json = completions.getChoices().get(0).getMessage().getContent();
        System.out.println(json);





    }

}

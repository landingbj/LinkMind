package ai.llm.utils;

import ai.common.utils.LRUCache;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import ai.utils.JsonExtractor;
import ai.utils.LlmUtil;
import ai.utils.qa.ChatCompletionUtil;
import com.google.gson.Gson;
import lombok.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;


public class SummaryUtil {

    private static final Logger log = LoggerFactory.getLogger(SummaryUtil.class);
    private static LRUCache<String, String> cache = new LRUCache<>(1000, 30,TimeUnit.SECONDS);

    private static ReentrantLock lock = new ReentrantLock();

    public static final String SUMMARY_PROMPT = "请根据以下规则处理连续提问的补全任务：\n" +
            "\n" +
            "1. **任务目标**：补全用户当前问题中因连续提问而省略的信息，确保补全内容完全来自对话历史记录，禁止添加任何未提及的内容, 禁止丢失当前问题中的信息。\n" +
            "\n" +
            "2. **操作步骤**：\n" +
            "   - **分析当前问题**：识别问题中可能缺失动词、动作、形容词或实体（如时间、地点、产品名称、之前提到的事件等）。\n" +
            "   - **检索历史记录**：从对话历史中查找与当前问题直接相关的上下文信息。\n" +
            "   - **精准补全**：将缺失的信息, 不够明确的信息替换或补充到当前问题中，确保语义完整且符合逻辑。\n" +
            "   - **验证来源**：所有补全内容必须能直接追溯到历史记录中的明确表述，禁止推测或假设出历史记录中没有的内容。\n" +
            "   - **验证当前问题**：当前用户输入中的所有信息都需要完全存在, （如:用户的目标是续写，那么补全的完整输入中必须存在续写这一动作)\n" +
            "\n" +
            "3. **输出格式**：\n" +
            "   - 返回 JSON 对象包含两个字段：\n" +
            "     - `original_question`：用户当前原始输入的信息。\n" +
            "     - `supplemented_question`：根据对话的历史信息补全后的完整信息。\n" +
            "\n" +
            "4. **示例参考**：\n" +
            "   - 若历史记录提到“帮我生成一张小狗的图片”,“毛色换为白色”，而当前问题为“图片切换成近景”，\n" +
            "     补全后应为“帮我生成一张毛色为白色小狗的近景图片”。\n" +
            "\n" +
            "请严格遵循规则，确保输出符合格式要求。";


    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @ToString
    static class SummaryResult {
        private String original_question;
        private String supplemented_question;
    }

    public static String invoke(ChatCompletionRequest request) {
        List<String> userMsgs = request.getMessages().stream().filter(chatMessage -> chatMessage.getRole().equals("user")).map(ChatMessage::getContent).collect(Collectors.toList());
        String key = String.join(",", userMsgs);
        String question  = cache.get(key);
        if(question != null) {
            log.info("summary cache 1 hit {}", request);
            return question;
        }
        try {
            lock.lock();
            question = cache.get(key);
            if(question != null) {
                log.info("summary cache 2 hit {}", request);
                return question;
            }
            question = ChatCompletionUtil.getLastMessage(request);
            ChatCompletionResult chatCompletionResult = LlmUtil.callLLm(SUMMARY_PROMPT, request);
            String firstAnswer = ChatCompletionUtil.getFirstAnswer(chatCompletionResult);
            String json = JsonExtractor.extractJson(firstAnswer);
            Gson gson = new Gson();
            SummaryResult summary =  gson.fromJson(json, SummaryResult.class);
            if(summary != null && summary.getSupplemented_question() != null) {
                question = summary.getSupplemented_question();
                cache.put(key, summary.getSupplemented_question());
            }
            return question;
        } finally {
            lock.unlock();
        }

    }

    public static void setInvoke(ChatCompletionRequest request, String question) {
        request.getMessages().get(request.getMessages().size() - 1).setContent(question);
    }

}

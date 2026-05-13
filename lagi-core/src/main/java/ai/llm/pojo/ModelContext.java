package ai.llm.pojo;

import ai.llm.adapter.ILlmAdapter;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import ai.pnps.skills.pojo.SkillEntry;
import io.reactivex.Observable;
import lombok.*;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Builder
public class ModelContext {
    private ChatCompletionRequest request;
    private ILlmAdapter adapter;
    private ChatCompletionResult result;
    private Observable<ChatCompletionResult> streamResult;
    private List<ChatMessage> originalMessages;
    private List<SkillEntry> skills;
    private String userApiKey;
}

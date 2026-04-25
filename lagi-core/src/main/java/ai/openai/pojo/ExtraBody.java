package ai.openai.pojo;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

/**
 * Container for non-standard fields carried alongside an OpenAI-compatible
 * chat completion request. Mirrors the semantics of the OpenAI client's
 * {@code extra_body} parameter and lets us extend the request with project
 * specific fields (e.g. {@link #userId}) without touching the standard
 * {@link ChatCompletionRequest} surface.
 */
@Data
public class ExtraBody {
    @JsonAlias({"user_id", "user"})
    private String userId;
}

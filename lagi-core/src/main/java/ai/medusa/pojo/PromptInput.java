package ai.medusa.pojo;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@EqualsAndHashCode(exclude = {"medusaMetadata", "assistantPrompts"})
@ToString
public class PromptInput implements Serializable {
    private List<String> promptList;
    private List<String> assistantPrompts;
    private PromptParameter parameter;
    private MedusaMetadata medusaMetadata;
}

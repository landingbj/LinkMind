package ai.config;

import ai.common.pojo.*;
import ai.config.pojo.*;
import ai.manager.*;
import ai.medusa.utils.PromptCacheConfig;
import ai.ocr.OcrConfig;
import ai.router.Routers;
import ai.utils.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

@EqualsAndHashCode(callSuper = true)
@ToString
@Data
public class GlobalConfigurations extends AbstractConfiguration {
    private Logger logger = LoggerFactory.getLogger(GlobalConfigurations.class);

    private String systemTitle;
    private List<Backend> models;
    private StoreConfig stores;
    private ModelFunctions functions;
    private List<AgentConfig> agents;
    private List<WorkerConfig> workers;
    private List<RouterConfig> routers;
    private List<FilterConfig> filters;

    @Override
    public void init() {
        EmbeddingManager.getInstance().register(functions.getEmbedding());
        BigdataManager.getInstance().register(stores.getBigdata());
        OSSManager.getInstance().register(stores.getOss());
        VectorStoreManager.getInstance().register(stores.getVectors(), stores.getRag(), functions.getEmbedding());
        MultimodalAIManager.register(models, functions);
        PromptCacheConfig.init(stores.getVectors(), stores.getMedusa());
        OcrConfig.init(functions.getImage2ocr());
        AgentManager.getInstance().register(agents);
        Routers.getInstance().register(workers, routers);
        Routers.getInstance().register(functions, routers);
        WorkerManager.getInstance().register(workers);
        registerFilter();
    }

    private void registerFilter() {
        if (filters != null) {
            for (FilterConfig filter : filters) {
                SensitiveWordUtil.pushWordRule(filter.getSensitive());
                StoppingWordUtil.addWords(filter.getStopping());
                PriorityWordUtil.addWords(filter.getPriority());
                RetainWordUtil.addWords(filter.getRetain());
                ContinueWordUtil.addWords(filter.getContinueWords());
            }
        }
    }


    @Override
    public Configuration transformToConfiguration() {
        List<Backend> chatBackends = functions.getChat().getBackends().stream().map(backendMatch -> {
            Optional<Backend> any = models.stream().filter(backend -> backend.getEnable() && backendMatch.getEnable() && backendMatch.getBackend().equals(backend.getName())).findAny();
            Backend backend = any.orElse(null);
            if(backend != null) {
                backend.setPriority(backendMatch.getPriority());
            }
            return backend;
        }).filter(Objects::nonNull).collect(Collectors.toList());
        LLM llm = LLM.builder().backends(models).embedding(functions.getEmbedding().get(0))
                .chatBackends(chatBackends)
                .build();
        llm.getBackends().forEach(backend -> {
            if (backend.getPriority() == null) {
                backend.setPriority(10);
            }
        });

        return Configuration.builder()
                .systemTitle(systemTitle)
                .vectorStores(stores.getVectors())
                .LLM(llm)
                .ASR(ASR.builder().backends(functions.getSpeech2text()).build())
                .TTS(TTS.builder().backends(functions.getText2speech()).build())
                .imageEnhance(ImageEnhance.builder().backends(functions.getImage2Enhance()).build())
                .imageGeneration(ImageGeneration.builder().backends(functions.getText2image()).build())
                .imageCaptioning(ImageCaptioning.builder().backends(functions.getImage2text()).build())
                .videoEnhance(VideoEnhance.builder().backends(functions.getVideo2Enhance()).build())
                .videoGeneration(VideoGeneration.builder().backends(functions.getImage2video()).build())
                .videoTrack(VideoTrack.builder().backends(functions.getVideo2Track()).build())
                .agents(agents)
                .workers(workers)
                .build();
    }


}

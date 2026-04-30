package ai.config;

import ai.common.pojo.*;
import ai.config.pojo.*;
import ai.llm.adapter.impl.GPTAzureAdapter;
import ai.llm.adapter.impl.OpenAIStandardAdapter;
import ai.llm.adapter.impl.QwenAdapter;
import ai.llm.responses.QwenResponseProtocolUtil;
import ai.llm.responses.ResponseProtocolConstants;
import ai.llm.responses.ResponseProtocolUtil;
import ai.manager.*;
import ai.medusa.utils.PromptCacheConfig;
import ai.ocr.OcrConfig;
import ai.router.Routers;
import ai.utils.*;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
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
    @JsonIgnore
    private transient Logger logger = LoggerFactory.getLogger(GlobalConfigurations.class);

    private String systemTitle;
    private GeneralConfig general;
    private List<Backend> models;
    private StoreConfig stores;
    private ModelFunctions functions;
    private AgentsConfig agents;

    private RoutersConfig routers;

    private SkillsConfig skills;

    private FiltersConfig filters;

    private McpConfig mcps;

    private String includeModels;
    private String includeStores;
    private String includeAgents;
    private String includeMcps;
    private String includePnps;

    @Override
    public void init() {
        loadFromPropertiesFromYaml();
        initCascadeApiAddress();
        validateChatBackends();
        EmbeddingManager.getInstance().register(functions.getEmbedding());
        BigdataManager.getInstance().register(stores.getBigdata());
        OSSManager.getInstance().register(stores.getOss());
        VectorStoreManager.getInstance().register(stores.getVectors(), stores.getRag(), functions.getEmbedding());
        MultimodalAIManager.register(models, functions);
        PromptCacheConfig.init(stores.getVectors(), stores.getMedusa());
        OcrConfig.init(functions.getImage2ocr());
        if(Boolean.TRUE.equals(agents.getEnable())) {
            AgentManager.getInstance().register(agents.getAgents());
        }
        if(Boolean.TRUE.equals(routers.getEnable())) {
            Routers.getInstance().register(skills.getWorkers(), routers.getRouterItems());
            Routers.getInstance().register(functions, routers.getRouterItems());
        }
        WorkerManager.getInstance().register(skills.getWorkers());
        McpManager.getInstance().register(mcps);
        PnpManager.getInstance().register(skills.getPnps());
        registerFilter();
    }

    private void initCascadeApiAddress() {
        String cascadeApiAddress = general == null ? null : general.getCascadeApiAddress();
        ConfigUtil.setCascadeApiAddress(cascadeApiAddress);
    }

    private void loadFromPropertiesFromYaml() {
        try {
            if(models == null) {
                models = YmlLoader.loaderProperties(getIncludeModels(), "models", new cn.hutool.core.lang.TypeReference<List<Backend>>(){});
            } else {
                List<Backend> models1 = YmlLoader.loaderProperties(getIncludeModels(), "models", new cn.hutool.core.lang.TypeReference<List<Backend>>(){});
                if (models1 != null && models != null) {
                    models.addAll(models1);
                }
            }
            if(models != null) {
                Set<String> modelNames = models.stream().map(Backend::getName).collect(Collectors.toSet());
                models = models.stream().filter(model -> modelNames.contains(model.getName())).collect(Collectors.toList());
            }
        } catch (Exception ignored){}
        try {
            if(stores == null) {
                stores = YmlLoader.loaderProperties(getIncludeStores(), "stores", StoreConfig.class);
            } else {
                StoreConfig stores1 = YmlLoader.loaderProperties(getIncludeStores(), "stores", StoreConfig.class);
                if(stores1 != null) {
                    CopyOptions copyOption = CopyOptions.create(null, true, "vectors");
                    BeanUtil.copyProperties(stores1, stores, copyOption);
                    if(stores.getVectors() != null  && stores1.getVectors() != null) {
                        stores.getVectors().addAll(stores1.getVectors());
                    }
                }
            }
            if(stores != null && stores.getVectors() != null) {
                List<VectorStoreConfig> vectors = stores.getVectors();
                Set<String> vectorNames = vectors.stream().map(VectorStoreConfig::getName).collect(Collectors.toSet());
                stores.setVectors(vectors.stream().filter(vector -> vectorNames.contains(vector.getName())).collect(Collectors.toList()));
            }

        }catch (Exception ignored) {}
        try {
            if(agents == null) {
                agents = YmlLoader.loaderProperties(getIncludeAgents(), "agents", new cn.hutool.core.lang.TypeReference<AgentsConfig>(){});
            } else {
                if(Boolean.TRUE.equals(agents.getEnable())) {
                    List<AgentConfig> agents1 = YmlLoader.loaderProperties(getIncludeAgents(), "agents", new cn.hutool.core.lang.TypeReference<List<AgentConfig>>(){});
                    if (agents1 != null && agents != null && agents.getAgents() != null) {
                        agents.getAgents().addAll(agents1);
                    }
                }
            }
            if(agents != null && agents.getAgents() != null) {
                Set<String> agentNames = agents.getAgents().stream().map(AgentConfig::getName).collect(Collectors.toSet());
                List<AgentConfig> collect = agents.getAgents().stream().filter(model -> agentNames.contains(model.getName())).collect(Collectors.toList());
                agents.setAgents(collect);
            }
        } catch (Exception ignored) {}
        try {
            if(mcps == null) {
                mcps = YmlLoader.loaderProperties(getIncludeMcps(), "mcps", McpConfig.class);
            } else {
                McpConfig mcps1 = YmlLoader.loaderProperties(getIncludeMcps(), "mcps", McpConfig.class);
                if(mcps1 != null) {
                    CopyOptions copyOption = CopyOptions.create(null, true, "servers");
                    BeanUtil.copyProperties(mcps1, mcps, copyOption);
                    if(mcps.getServers() != null  && mcps1.getServers() != null) {
                        mcps.getServers().addAll(mcps1.getServers());
                    }
                }
            }
            if(mcps != null && mcps.getServers() != null) {
                List<McpBackend> mcpBackends = mcps.getServers();
                Set<String> mcpServerNames = mcpBackends.stream().map(McpBackend::getName).collect(Collectors.toSet());
                mcps.setServers(mcpBackends.stream().filter(vector -> mcpServerNames.contains(vector.getName())).collect(Collectors.toList()));
            }
        }catch (Exception ignored) {}

        try {
            if(skills.getPnps() == null) {
                skills.setPnps(YmlLoader.loaderProperties(getIncludePnps(), "skill", new cn.hutool.core.lang.TypeReference<List<PnpConfig>>(){}));
            } else {
                List<PnpConfig> pnps2 = YmlLoader.loaderProperties(getIncludePnps(), "skill", new cn.hutool.core.lang.TypeReference<List<PnpConfig>>(){});
                if (pnps2 != null && skills.getPnps() != null) {
                    skills.getPnps().addAll(pnps2);
                }
            }
            if(skills.getPnps() != null) {
                Set<String> pnpNames = skills.getPnps().stream().map(PnpConfig::getName).collect(Collectors.toSet());
                skills.setPnps(skills.getPnps().stream().filter(model -> pnpNames.contains(model.getName())).collect(Collectors.toList()));
            }
        } catch (Exception ignored) {}
    }

    private void validateChatBackends() {
        if (functions == null || functions.getChat() == null || functions.getChat().getBackends() == null) {
            return;
        }
        for (Backend chatBackend : functions.getChat().getBackends()) {
            if (chatBackend == null || !Boolean.TRUE.equals(chatBackend.getEnable())) {
                continue;
            }
            chatBackend.setProtocol(ResponseProtocolUtil.normalize(chatBackend.getProtocol()));
            boolean responseProtocol = ResponseProtocolConstants.RESPONSE.equals(chatBackend.getProtocol());
            if (StrUtil.isBlank(chatBackend.getModel())) {
                if (responseProtocol) {
                    throw new IllegalStateException("functions.chat.backends.model is required for response backend " + chatBackend.getBackend());
                }
                continue;
            }
            if (!responseProtocol) {
                continue;
            }
            Backend effectiveConfig = buildEffectiveChatConfig(chatBackend);
            if (!ResponseProtocolUtil.supportsResponses(effectiveConfig.getDriver())) {
                throw new IllegalStateException("backend " + chatBackend.getBackend() + " model " + chatBackend.getModel()
                        + " does not support response protocol");
            }
            if (StrUtil.equals(effectiveConfig.getDriver(), OpenAIStandardAdapter.class.getName())
                    && !canResolveResponsesApiAddress(effectiveConfig.getApiAddress())) {
                throw new IllegalStateException("backend " + chatBackend.getBackend() + " model " + chatBackend.getModel()
                        + " must configure api_address ending with /chat/completions or /responses");
            }
            if (StrUtil.equals(effectiveConfig.getDriver(), GPTAzureAdapter.class.getName())
                    && (StrUtil.isBlank(effectiveConfig.getEndpoint()) || StrUtil.isBlank(effectiveConfig.getDeployment()))) {
                throw new IllegalStateException("backend " + chatBackend.getBackend() + " model " + chatBackend.getModel()
                        + " must configure endpoint and deployment for response protocol");
            }
            if (StrUtil.equals(effectiveConfig.getDriver(), QwenAdapter.class.getName())
                    && !QwenResponseProtocolUtil.supportsResponsesModel(chatBackend.getModel())) {
                throw new IllegalStateException("backend " + chatBackend.getBackend() + " model " + chatBackend.getModel()
                        + " does not support qwen response protocol");
            }
            if (StrUtil.equals(effectiveConfig.getDriver(), QwenAdapter.class.getName())
                    && !QwenResponseProtocolUtil.canResolveResponsesApiAddress(effectiveConfig.getApiAddress())) {
                throw new IllegalStateException("backend " + chatBackend.getBackend() + " model " + chatBackend.getModel()
                        + " must configure qwen api_address ending with /compatible-mode/v1, /chat/completions or /responses");
            }
        }
    }

    private Backend buildEffectiveChatConfig(Backend chatBackend) {
        Backend modelConfig = findChatModelConfig(chatBackend);
        Backend effectiveConfig = new Backend();
        BeanUtil.copyProperties(modelConfig, effectiveConfig, CopyOptions.create(null, true));
        BeanUtil.copyProperties(chatBackend, effectiveConfig, CopyOptions.create(null, true));
        // When backend uses drivers and has no top-level driver, merge matching driver's driver and api_address
        if (StrUtil.isBlank(effectiveConfig.getDriver()) && modelConfig.getDrivers() != null) {
            Driver matchingDriver = findMatchingDriver(modelConfig, chatBackend.getModel());
            if (matchingDriver != null) {
                if (StrUtil.isNotBlank(matchingDriver.getDriver())) {
                    effectiveConfig.setDriver(matchingDriver.getDriver());
                }
                if (StrUtil.isNotBlank(matchingDriver.getApiAddress())) {
                    effectiveConfig.setApiAddress(matchingDriver.getApiAddress());
                }
            }
        }
        effectiveConfig.setProtocol(ResponseProtocolUtil.normalize(effectiveConfig.getProtocol()));
        return effectiveConfig;
    }

    private Backend findChatModelConfig(Backend chatBackend) {
        return Optional.ofNullable(models)
                .orElse(Collections.emptyList())
                .stream()
                .filter(Objects::nonNull)
                .filter(model -> Boolean.TRUE.equals(model.getEnable()))
                .filter(model -> StrUtil.equals(model.getName(), chatBackend.getBackend()))
                .filter(model -> backendMatchesChatModel(model, chatBackend.getModel()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "no model config matches backend " + chatBackend.getBackend() + " model " + chatBackend.getModel()));
    }

    /**
     * Returns true if the backend supports the requested model via its top-level model list
     * or via any driver's model list.
     */
    private boolean backendMatchesChatModel(Backend backend, String requestedModel) {
        if (StrUtil.isBlank(requestedModel)) {
            return false;
        }
        if (containsModel(backend.getModel(), requestedModel)) {
            return true;
        }
        if (backend.getDrivers() == null) {
            return false;
        }
        return backend.getDrivers().stream()
                .filter(Objects::nonNull)
                .anyMatch(d -> containsModel(d.getModel(), requestedModel));
    }

    /**
     * Finds the first driver in the backend whose model list contains the requested model.
     */
    private Driver findMatchingDriver(Backend backend, String requestedModel) {
        if (backend.getDrivers() == null || StrUtil.isBlank(requestedModel)) {
            return null;
        }
        return backend.getDrivers().stream()
                .filter(Objects::nonNull)
                .filter(d -> containsModel(d.getModel(), requestedModel))
                .findFirst()
                .orElse(null);
    }

    private boolean containsModel(String modelList, String model) {
        if (StrUtil.isBlank(modelList) || StrUtil.isBlank(model)) {
            return false;
        }
        return Arrays.stream(modelList.split(","))
                .map(String::trim)
                .anyMatch(model::equals);
    }

    private boolean canResolveResponsesApiAddress(String apiAddress) {
        if (StrUtil.isBlank(apiAddress)) {
            return false;
        }
        return apiAddress.endsWith("/chat/completions") || apiAddress.endsWith("/responses");
    }



    private void registerFilter() {
        if (filters != null && Boolean.TRUE.equals(filters.getEnable()) && filters.getItems() != null) {
            for (FilterConfig filter : filters.getItems()) {
                if(filter.getName().equals("sensitive")) {
                    push2WordRule(filter);
                } else if(filter.getName().equals("sensitive_input")) {
                    push2InputWordRule(filter);
                } else if(filter.getName().equals("priority")) {
                    PriorityWordUtil.addWords(convert2List(filter));
                } else if (filter.getName().equals("continue")) {
                    ContinueWordUtil.addWords(convert2List(filter));
                } else if (filter.getName().equals("stopping")) {
                    StoppingWordUtil.addWords(convert2List(filter));
                } else if(filter.getName().equals("retain")) {
                    StoppingWordUtil.addWords(convert2List(filter));
                }
            }
        }
    }


    private List<String> convert2List(FilterConfig filterItem) {
        return convert2ListRules(filterItem.getRules());
    }


    private static List<String> convert2ListRules(String rules){
        String s = rules.replaceAll("\\\\\\\\,", "·regx-dot·");
        List<String> collect = Arrays.stream(s.split(",")).map(String::trim).collect(Collectors.toList());
        collect = collect.stream().map(temp -> temp.replaceAll("·regx-dot·", ",")).collect(Collectors.toList());
        return collect;
    }


    private void push2WordRule(FilterConfig filter) {
        WordRules wordRules = convert2WordRules(filter);
        SensitiveWordUtil.pushOutputRule(wordRules);
    }

    private void push2InputWordRule(FilterConfig filter) {
        WordRules wordRules = convert2WordRules(filter);
        SensitiveWordUtil.pushInputRule(wordRules);
    }

    private WordRules convert2WordRules(FilterConfig filter) {
        if (filter.getGroups() == null || filter.getGroups().isEmpty()) {
            return null;
        }
        SensitiveWordUtil.setFilterWindowLength(filter.getFilterWindowLength());

        List<WordRule> rules = filter.getGroups().stream()
                .flatMap(group-> {
                    if (group.getRules() == null || group.getRules().trim().isEmpty()) {
                        return java.util.stream.Stream.empty();
                    }
                    return convert2ListRules(group.getRules()).stream()
                            .map(rule -> {
                                String level = group.getLevel();
                                String mask = group.getMask();
                                int levelInt = 0;

                                // 支持数字字符串和单词两种格式
                                if (level != null) {
                                    if ("erase".equalsIgnoreCase(level) || "3".equals(level)) {
                                        levelInt = 3;
                                    } else if ("block".equalsIgnoreCase(level) || "1".equals(level)) {
                                        levelInt = 1;
                                    } else if ("mask".equalsIgnoreCase(level) || "2".equals(level)) {
                                        levelInt = 2;
                                    } else {
                                        // 尝试解析为整数
                                        try {
                                            levelInt = Integer.parseInt(level);
                                            if (levelInt < 1 || levelInt > 3) {
                                                levelInt = 2; // 默认使用掩码
                                            }
                                        } catch (NumberFormatException e) {
                                            levelInt = 2; // 默认使用掩码
                                        }
                                    }
                                } else {
                                    levelInt = 2; // 默认使用掩码
                                }

                                rule = rule.trim();
                                return WordRule.builder().level(levelInt).mask(mask).rule(rule).build();
                            });
                })
                .collect(Collectors.toList());
        WordRules wordRules = WordRules.builder()
                .rules(rules)
                .build();
        return wordRules;
    }

    @Override
    public Configuration transformToConfiguration() {
        List<Backend> chatBackends = functions.getChat().getBackends().stream().map(backendMatch -> {
            Backend backend = Optional.ofNullable(models).orElse(Collections.emptyList()).stream()
                    .filter(model -> Boolean.TRUE.equals(model.getEnable()) && Boolean.TRUE.equals(backendMatch.getEnable()))
                    .filter(model -> backendMatch.getBackend().equals(model.getName()))
                    .filter(model -> StrUtil.isBlank(backendMatch.getModel()) || backendMatchesChatModel(model, backendMatch.getModel()))
                    .findFirst()
                    .map(model -> {
                        Backend copy = new Backend();
                        BeanUtil.copyProperties(model, copy, CopyOptions.create(null, true));
                        BeanUtil.copyProperties(backendMatch, copy, CopyOptions.create(null, true));
                        if (StrUtil.isNotBlank(backendMatch.getModel())) {
                            copy.setModel(backendMatch.getModel());
                        }
                        // When backend uses drivers and has no top-level driver, merge matching driver's driver and api_address
                        if (StrUtil.isBlank(copy.getDriver()) && model.getDrivers() != null) {
                            Driver matchingDriver = findMatchingDriver(model, backendMatch.getModel());
                            if (matchingDriver != null) {
                                if (StrUtil.isNotBlank(matchingDriver.getDriver())) {
                                    copy.setDriver(matchingDriver.getDriver());
                                }
                                if (StrUtil.isNotBlank(matchingDriver.getApiAddress())) {
                                    copy.setApiAddress(matchingDriver.getApiAddress());
                                }
                            }
                        }
                        copy.setProtocol(ResponseProtocolUtil.normalize(copy.getProtocol()));
                        return copy;
                    })
                    .orElse(null);
            if(backend != null) {
                backend.setPriority(backendMatch.getPriority());
            }
            return backend;
        }).filter(Objects::nonNull).collect(Collectors.toList());
        LLM llm = LLM.builder().backends(models).embedding(functions.getEmbedding() != null && !functions.getEmbedding().isEmpty() ? functions.getEmbedding().get(0) : null)
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
                .agents(agents.getAgents())
                .workers(skills.getWorkers())
                .build();
    }


}

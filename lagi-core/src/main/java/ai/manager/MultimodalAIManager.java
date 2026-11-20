package ai.manager;

import ai.audio.adapter.IAudioAdapter;
import ai.audio.adapter.IAudioCloneAdapter;
import ai.common.ModelService;
import ai.common.pojo.Backend;
import ai.common.pojo.Driver;
import ai.config.ContextLoader;
import ai.config.pojo.ModelFunctions;
import ai.image.adapter.IImage2TextAdapter;
import ai.image.adapter.IImageGenerationAdapter;
import ai.image.adapter.ImageEnhanceAdapter;
import ai.llm.adapter.ILlmAdapter;
import ai.llm.routing.BringYourOwnKeyRouter;
import ai.llm.routing.UpstreamModels;
import ai.llm.adapter.impl.LandingAdapter;
import ai.llm.adapter.impl.GPTAzureAdapter;
import ai.llm.responses.ResponseProtocolUtil;
import ai.wrapper.IWrapper;
import ai.ocr.IOcr;
import ai.oss.UniversalOSS;
import ai.translate.adapter.TranslateAdapter;
import ai.video.adapter.Image2VideoAdapter;
import ai.video.adapter.Text2VideoAdapter;
import ai.video.adapter.Video2EnhanceAdapter;
import ai.video.adapter.Video2trackAdapter;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.StrUtil;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MultimodalAIManager {

    private static final Logger log = LoggerFactory.getLogger(MultimodalAIManager.class);

    public static void register(List<Backend> models, ModelFunctions modelFunctions) {
        register(models, modelFunctions, true);
    }

    public static void register(List<Backend> models, ModelFunctions modelFunctions, boolean discoverUpstream) {
        if(models == null || models.isEmpty()) {
            LlmManager.getInstance().clear();
            return;
        }
        List<ModelService> modelServices = convertModels2Services(models);
        registerChatModels(modelServices, modelFunctions.getChat().getBackends(), discoverUpstream);
        modelServices.forEach(modelService -> {
            if (!modelService.verify()) return;
            List<String> modelNameList = configuredNames(modelService.getModel());
            if(modelService instanceof ILlmAdapter) {
                register(modelNameList, LlmInstructionManager.getInstance(), (ILlmAdapter) modelService, modelFunctions.getDoc2instruct());
            }
            if(modelService instanceof IAudioAdapter) {
                register(modelNameList, TTSManager.getInstance(), (IAudioAdapter) modelService, modelFunctions.getText2speech());
                register(modelNameList, ASRManager.getInstance(), (IAudioAdapter) modelService, modelFunctions.getSpeech2text());
            }
            if(modelService instanceof IAudioCloneAdapter) {
                register(modelNameList, SoundCloneManager.getInstance(), (IAudioCloneAdapter) modelService, modelFunctions.getSpeech2clone());
            }
            if(modelService instanceof IImage2TextAdapter) {
                register(modelNameList, Image2TextManger.getInstance(), (IImage2TextAdapter) modelService, modelFunctions.getImage2text());
            }
            if(modelService instanceof IImageGenerationAdapter) {
                register(modelNameList, ImageGenerationManager.getInstance(), (IImageGenerationAdapter) modelService, modelFunctions.getText2image());
            }
            if(modelService instanceof ImageEnhanceAdapter) {
                register(modelNameList, ImageEnhanceManager.getInstance(), (ImageEnhanceAdapter) modelService, modelFunctions.getImage2Enhance());
            }
            if(modelService instanceof Image2VideoAdapter) {
                register(modelNameList, Image2VideoManager.getInstance(), (Image2VideoAdapter) modelService, modelFunctions.getImage2video());
            }
            if(modelService instanceof IOcr) {
//                register(modelNameList, OcrManager.getInstance(), (IOcr) modelService, modelFunctions.getImage2ocr());
                register(modelNameList, OcrManager.getInstance(), (IOcr) modelService, modelFunctions.getDoc2orc());
                register(modelNameList, DocOcrManager.getInstance(), (IOcr) modelService, modelFunctions.getDoc2orc());
            }
            if(modelService instanceof Video2trackAdapter) {
                register(modelNameList, Video2TrackManager.getInstance(), (Video2trackAdapter) modelService, modelFunctions.getVideo2Track());
            }
            if(modelService instanceof Video2EnhanceAdapter) {
                register(modelNameList, Video2EnhanceManger.getInstance(), (Video2EnhanceAdapter) modelService, modelFunctions.getVideo2Enhance());
            }
            if(modelService instanceof Text2VideoAdapter) {
                register(modelNameList, Text2VideoManager.getInstance(), (Text2VideoAdapter) modelService, modelFunctions.getText2video());
            }
            if(modelService instanceof TranslateAdapter) {
                register(modelNameList, TranslateManager.getInstance(), (TranslateAdapter) modelService, modelFunctions.getTranslate());
            }
        });
    }

    private static List<String> configuredNames(String csv) {
        if (StrUtil.isBlank(csv)) return Collections.emptyList();
        return Arrays.stream(csv.split(",")).map(String::trim).filter(StrUtil::isNotBlank).collect(Collectors.toList());
    }

    private static class ChatBackend {
        final ModelService adapter;
        final Backend rule;
        final Set<String> names;
        final boolean terminal;
        final List<String> configuredModels;

        ChatBackend(ModelService source, Backend rule) {
            this.rule = rule;
            this.adapter = cloneModelService(source);
            BeanUtil.copyProperties(rule, adapter, CopyOptions.create(null, true));
            if (!BringYourOwnKeyRouter.hasUsableKey(adapter.getApiKey())) adapter.setApiKey(null);
            if (adapter.getApiKeys() != null) {
                adapter.setApiKeys(adapter.getApiKeys().stream().filter(BringYourOwnKeyRouter::hasUsableKey)
                        .map(String::trim).collect(Collectors.toList()));
            }
            adapter.setType(source.getType());
            adapter.setProtocol(ResponseProtocolUtil.normalize(rule.getProtocol()));
            configuredModels = configuredNames(source.getModel());
            names = new LinkedHashSet<>(configuredModels);
            terminal = adapter instanceof LandingAdapter && ((LandingAdapter) adapter).isFinalServer();
        }

        boolean allows(String model) {
            return rule.allowsChatModel(model, BringYourOwnKeyRouter.publicId(adapter, model));
        }
    }

    private static void registerChatModels(List<ModelService> services, List<Backend> functions, boolean discoverUpstream) {
        if (functions == null) {
            LlmManager.getInstance().clear();
            return;
        }
        List<ChatBackend> backends = new ArrayList<>();
        Set<String> terminalModels = new LinkedHashSet<>();
        Map<String, Set<String>> configured = new HashMap<>();
        for (ModelService service : services) {
            configured.computeIfAbsent(service.getBackend(), k -> new HashSet<>()).addAll(configuredNames(service.getModel()));
        }
        for (ModelService service : services) {
            if (!(service instanceof ILlmAdapter)) continue;
            boolean hasChatRule = false;
            for (Backend rule : functions) {
                if (rule == null || !Boolean.TRUE.equals(rule.getEnable()) || !Objects.equals(service.getBackend(), rule.getBackend())) continue;
                hasChatRule = true;
                ChatBackend backend = new ChatBackend(service, rule);
                if (!backend.adapter.verify()) continue;
                backends.add(backend);
                if (backend.terminal) continue;
                for (String id : UpstreamModels.models(backend.adapter, discoverUpstream)) {
                    // Static driver assignments (including non-chat drivers) take precedence.
                    if (!configured.get(service.getBackend()).contains(id)) backend.names.add(id);
                }
                if (!(service instanceof LandingAdapter)) {
                    for (String id : backend.names) {
                        if (backend.allows(id)) terminalModels.add(BringYourOwnKeyRouter.publicId(backend.adapter, id));
                    }
                }
            }
            if (!hasChatRule && discoverUpstream) UpstreamModels.models(service, true);
        }
        // Defer publication until all direct platforms are known, keeping the original YAML order.
        List<ModelService> registered = new ArrayList<>();
        for (ChatBackend backend : backends) {
            Set<String> candidates = backend.terminal ? terminalModels : backend.names;
            for (String name : candidates) {
                if (!backend.allows(name)) continue;
                ModelService adapter = cloneModelService(backend.adapter);
                if (adapter == null) continue;
                adapter.setModel(name);
                if (adapter instanceof GPTAzureAdapter
                        && (StrUtil.isBlank(adapter.getDeployment()) || !backend.configuredModels.contains(name))) {
                    adapter.setDeployment(name);
                }
                registered.add(adapter);
                if (Html2ContentManager.getInstance().getAdapter(name) == null) {
                    Html2ContentManager.getInstance().register(name, (ILlmAdapter) adapter);
                }
            }
        }
        LlmManager.getInstance().replaceModels(registered);
    }

    private static  <T> void register(List<String> modelNames, AIManager<T> aiManager, T adapter, List<Backend> functions) {
        try {
            if (functions == null) {
                return;
            }
            ModelService original = (ModelService) adapter;
            for (String modelName : modelNames) {
                Backend functionConfig = functions.stream()
                        .filter(Objects::nonNull)
                        .filter(func -> Boolean.TRUE.equals(func.getEnable()))
                        .filter(func -> StrUtil.equals(func.getBackend(), original.getBackend()))
                        .filter(func -> StrUtil.isBlank(func.getModel()) || StrUtil.equals(func.getModel(), modelName))
                        .findFirst()
                        .orElse(null);
                if (functionConfig == null) {
                    continue;
                }
                ModelService cloned = cloneModelService(original);
                if (cloned == null) {
                    continue;
                }
                BeanUtil.copyProperties(functionConfig, cloned, CopyOptions.create(null, true));
                cloned.setModel(modelName);
                if (cloned.getPriority() == null) {
                    cloned.setPriority(0);
                }
                aiManager.register(modelName, (T) cloned);
            }

        } catch (Exception e) {
            log.error("MultimodalAIManager register model error", e);
        }
    }

    private static ModelService cloneModelService(ModelService source) {
        ModelService clone = createAdapter(source.getClass().getName());
        if (clone == null) {
            return null;
        }
        BeanUtil.copyProperties(source, clone, CopyOptions.create(null, true));
        return clone;
    }

    private static boolean doesImplementInterface(String className, String interfaceName) {
        try {
            Class<?> targetClass = ContextLoader.getClass(className);
            Class<?> targetInterface = Class.forName(interfaceName);
            Class<?>[] implementedInterfaces = targetClass.getInterfaces();
            return Arrays.asList(implementedInterfaces).contains(targetInterface);
        } catch (ClassNotFoundException e) {
            log.error("Class not found", e);
            return false;
        }
    }

    private static IWrapper createWrapper(String driver) {
        IWrapper wrapper = null;
        Class<?> clazz = null;
        try {
            clazz = Class.forName(driver);
        } catch (Exception e) {
            log.error("Wrapper class {} not found {}", driver,  e.getMessage());
        }
        if(clazz != null) {
            try {
                wrapper = (IWrapper) clazz.newInstance();
            } catch (Exception e) {
                log.error("wrapper {} newinstance failed  {}", driver,  e.getMessage());
            }
        }
        return wrapper;
    }

    private static @NotNull List<ModelService> convertModels2Services(List<Backend> models) {
        return models.stream()
                // flatmap by drivers
                .flatMap(model -> {
                    if(!model.getEnable()) {
                        return Stream.empty();
                    }
                    //Single drive to multiple drive
                    if (model.getDriver() != null && (model.getModel() != null || model.getDrivers() == null)) {
                        if (doesImplementInterface(model.getDriver(), "ai.wrapper.IWrapper")) {
                            IWrapper wrapper = createWrapper(model.getDriver());
                            model.setDrivers(wrapper.getDrivers(model.getModel()));
                        } else {
                            Driver driver = Driver.builder().model(model.getModel()).driver(model.getDriver()).oss(model.getOss()).build();
                            model.setDrivers(Lists.newArrayList(driver));
                        }
                    }

                    // The properties of model are set to default
                    List<Backend> backends = Collections.emptyList();
                    if (model.getDrivers() != null) {
                        backends = model.getDrivers().stream().map(d -> {
                            Backend driver = new Backend();
                            driver.setBackend(model.getName());
                            CopyOptions copyOption = CopyOptions.create(null, true);
                            BeanUtil.copyProperties(model, driver, copyOption);
                            copyOption = CopyOptions.create(null, true);
                            BeanUtil.copyProperties(d, driver, copyOption);
                            driver.setEnable(false);
                            return driver;
                        }).collect(Collectors.toList());
                    }
                    return backends.stream();
                })
                // Use reflection to generate objects
                .map(driver -> {
                    ModelService modelService = createAdapter(driver.getDriver());
                    if (modelService == null) {
                        return null;
                    }
                    CopyOptions copyOption = CopyOptions.create(null, true);
                    BeanUtil.copyProperties(driver, modelService, copyOption);
                    if(driver.getOss() !=null) {
                        try {
                            Field universalOSS = modelService.getClass().getDeclaredField("universalOSS");
                            if(universalOSS.getType() == UniversalOSS.class) {
                                universalOSS.setAccessible(true);
                                universalOSS.set(modelService, OSSManager.getInstance().getOss(driver.getOss()));
                            }
                        } catch (Exception e) {
                            log.error("oss inject failed {}", e.getMessage());
                        }
                    }
                    return modelService;
                })
                // filter by null and verified result
                .filter(modelService -> {
                    if(modelService == null) {
                        return false;
                    }
                    // A chat rule can supply its own credentials after the platform has been loaded.
                    return modelService instanceof ILlmAdapter || modelService.verify();
                }).collect(Collectors.toList());
    }

    private static ModelService createAdapter(String driver) {
        ModelService modelService = null;
        Class<?> clazz = null;
        try {
            clazz = ContextLoader.getClass(driver);
        } catch (Exception e) {
            log.error( "class {} not fount {}", driver,  e.getMessage());
        }
        if(clazz == null) {
            return null;
        }
        try {
            modelService = (ModelService) clazz.newInstance();
        } catch (Exception e) {
            log.error( "driver {} newinstance failed  {}", driver,  e.getMessage());
        }
        return modelService;
    }
}

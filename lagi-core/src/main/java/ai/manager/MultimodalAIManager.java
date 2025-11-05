package ai.manager;

import ai.audio.adapter.IAudioAdapter;
import ai.audio.adapter.IAudioCloneAdapter;
import ai.common.ModelService;
import ai.common.pojo.Backend;
import ai.common.pojo.Driver;
import ai.config.pojo.ModelFunctions;
import ai.image.adapter.IImage2TextAdapter;
import ai.image.adapter.IImageGenerationAdapter;
import ai.image.adapter.ImageEnhanceAdapter;
import ai.image.adapter.impl.ImageObjectDetectAdapter;
import ai.llm.adapter.ILlmAdapter;
import ai.vl.adapter.VlAdapter;
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
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MultimodalAIManager {

    private static final Logger log = LoggerFactory.getLogger(MultimodalAIManager.class);

    public static void register(List<Backend> models, ModelFunctions modelFunctions) {
        if(models == null || models.isEmpty()) {
            return;
        }
        List<ModelService> modelServices = convertModels2Services(models);
        modelServices.forEach(modelService -> {
            String modelListStr = modelService.getModel();
            List<String> modelNameList = Arrays.stream(modelListStr.split(",")).map(String::trim).collect(Collectors.toList());
            if(modelService instanceof ILlmAdapter) {
                register(modelNameList, LlmManager.getInstance(), (ILlmAdapter) modelService, modelFunctions.getChat().getBackends());
                register(modelNameList, LlmInstructionManager.getInstance(), (ILlmAdapter) modelService, modelFunctions.getDoc2instruct());
                register(modelNameList, Text2QAManager.getInstance(), (ILlmAdapter) modelService, modelFunctions.getText2qa());
            }

            if(modelService instanceof VlAdapter) {
                register(modelNameList, VlLlmManager.getInstance(), (VlAdapter) modelService, modelFunctions.getChatVL());
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
            if(modelService instanceof ImageObjectDetectAdapter) {
                register(modelNameList, ImageObjectDetectManager.getInstance(), (ImageObjectDetectAdapter) modelService, modelFunctions.getImage2detect());
            }
            if(modelService instanceof IOcr) {
//                register(modelNameList, OcrManager.getInstance(), (IOcr) modelService, modelFunctions.getImage2ocr());
                register(modelNameList, OcrManager.getInstance(), (IOcr) modelService, modelFunctions.getImage2ocr());
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

    private static  <T> void register(List<String> modelNames, AIManager<T> aiManager, T adapter, List<Backend> functions) {
        try {
            if(functions == null) {
                return;
            }
            Map<String, Backend> modelFuncMap = functions.stream().collect(Collectors.toMap(Backend::getModel, model -> model));
            AtomicBoolean setDefaultModel = new AtomicBoolean(false);

            modelNames.forEach(name->{
                try {
                    T newAdapter = (T) adapter.getClass().newInstance();
                    BeanUtil.copyProperties(adapter, newAdapter);
                    ModelService modelService = (ModelService) newAdapter;
                    Backend func = modelFuncMap.get(name);
                    if(func != null) {
                        BeanUtil.copyProperties(func, modelService, CopyOptions.create(null, true));
                        setDefaultModel.set(true);
                    } else {
                        setDefaultModel.set(false);
                    }
                    if(modelService.getPriority() == null) {
                        modelService.setPriority(0);
                    }
                    if(!setDefaultModel.get()) {
                        ((ModelService) newAdapter).setModel(name);
                        setDefaultModel.set(true);
                    }
                    aiManager.register(name, newAdapter);
                } catch (Exception e) {

                }

            });

        } catch (Exception e) {
            log.error("MultimodalAIManager register model error", e);
        }
    }

    private static boolean doesImplementInterface(String className, String interfaceName) {
        try {
            Class<?> targetClass = Class.forName(className);
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
                    if (model.getModel() != null && model.getDriver() != null) {
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
                    return modelService.verify();
                }).collect(Collectors.toList());
    }

    private static ModelService createAdapter(String driver) {
        ModelService modelService = null;
        Class<?> clazz = null;
        try {
            clazz = Class.forName(driver);
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

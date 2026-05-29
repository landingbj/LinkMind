package ai.deploy.impl;

import ai.common.exception.RRException;
import ai.common.utils.ThreadPoolManager;
import ai.config.ContextLoader;
import ai.config.pojo.LlamaFactoryConfig;
import ai.deploy.ModelDeployment;
import ai.deploy.dao.ModelDevelopInfoDao;
import ai.deploy.pojo.*;
import ai.deploy.util.SupportModelUtil;
import ai.utils.JsonFileLoadUtil;
import cn.hutool.core.util.StrUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

@Getter
@Builder
@Slf4j
@AllArgsConstructor
public class LocalLlamaFactoryImpl implements ModelDeployment {
    private String condaPath;
    private String condaEnv;
    private String masterAddr = "127.0.0.1";
    private String masterPort = "7007";


    private String runEnv = " USE_MODELSCOPE_HUB=1 ASCEND_RT_VISIBLE_DEVICES={}";
    private String llamaFactoryDir;

    private static ExecutorService executor;

    private ModelDevelopInfoDao modelDevelopInfoDao = new ModelDevelopInfoDao();

    public LocalLlamaFactoryImpl(LlamaFactoryConfig llamaFactoryConfig) {
        if(llamaFactoryConfig != null) {
            this.condaPath = llamaFactoryConfig.getEnvPath();
            this.condaEnv = llamaFactoryConfig.getEnv();
            this.llamaFactoryDir = llamaFactoryConfig.getLlamaFactoryDir();
            String devices = StrUtil.isBlank(llamaFactoryConfig.getDevices()) ? "0" : llamaFactoryConfig.getDevices();
            this.runEnv = StrUtil.format(runEnv, devices);
            this.masterPort = llamaFactoryConfig.getMasterPort() == null ? "7007" : llamaFactoryConfig.getMasterPort();
        }
    }

    static {
        ThreadPoolManager.registerExecutor("deploy");
        executor = ThreadPoolManager.getExecutor("deploy");
    }


    public String startOpenAiServerScript(String modelPath, String port, String adapterPath, String template, String finetuningType) {
        URL resource = LocalLlamaFactoryImpl.class.getClassLoader().getResource("start_openai_server.sh");
        if(resource == null) {
            throw new RuntimeException("start_openai_server.sh not found");
        }
        String scriptPath = resource.getPath();
        if(modelPath == null) {
            throw new RuntimeException("modelPath not found");
        }
        if(port == null) {
            throw new RuntimeException("port not found");
        }
        if(template == null) {
            throw new RuntimeException("template not found");
        }
        if(adapterPath == null) {
            adapterPath = "";
        }
        if(finetuningType == null) {
            finetuningType = "";
        }
        String cmd = StrUtil.format("bash {} {} {} {} {} {} {} {} {} &", scriptPath, condaPath,  condaEnv, modelPath, template,  "1", port, adapterPath, finetuningType);
        System.out.println(cmd);
        return cmd;
    }

    public String stopOpenAiServerScript(String port) {
        URL resource = LocalLlamaFactoryImpl.class.getClassLoader().getResource("stop_openai_server.sh");
        if(resource == null) {
            throw new RuntimeException("stop_openai_server.sh not found");
        }
        String scriptPath = resource.getPath();
        String cmd = StrUtil.format("bash {} {}", scriptPath, port);
        System.out.println(cmd);
        return cmd;
    }

    public int runScript(String script,boolean wait) {
        // Shell 脚本的路径
        // 构建 ProcessBuilder
        Future<Integer> bash = executor.submit(() -> {
            String[] split = script.split(" ");
            ProcessBuilder processBuilder = new ProcessBuilder(split);
            processBuilder.directory(Paths.get(this.llamaFactoryDir).toFile());
            Process process = null;
            try {
                process = processBuilder.start();
                if(wait) {
                    int exitCode = process.waitFor();
                    System.out.println("退出码: " + exitCode);
                    return exitCode;
                } else {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            System.out.println("[develop log]" + line);
                            // 可选：检测日志中的成功关键字
                            if (line.contains("Visit http")) {
                                return 0;
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return -1;
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            } finally {
                if (process != null) {
                    process.destroy();
                }
            }
            return -1;
        });
        try {
            return bash.get();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1;
    }



    public int startOpenAiServer(String modelPath, String port, String adapterPath, String template, String finetuningType) {
        String script = startOpenAiServerScript(modelPath, port, adapterPath, template, finetuningType);
        return runScript(script, false);
    }

    public int stopOpenAiServer(String port) {
        String script = stopOpenAiServerScript(port);
        return runScript(script, true);
    }

    @Override
    public DeployResult deploy(DeployInfo deployRequest) {
        SupportModels supportModels = JsonFileLoadUtil.readWordLRulesList("/model_path.json", SupportModels.class);
        if(supportModels == null) {
            throw new RRException("no support model .");
        }
        List<SupportModel> models = supportModels.getModels();
        if(deployRequest.getModelPath() == null || models.isEmpty()) {
            throw new RRException("no support model ..");
        }
        Map<String, SupportModel> modelMap = models.stream().collect(Collectors.toMap(SupportModel::getName, a -> a));
        SupportModel supportModel = modelMap.get(deployRequest.getModelPath());
        int code = 200;
        String msg = "";
        try {
            int i = startOpenAiServer(deployRequest.getModelPath(), deployRequest.getPort(), deployRequest.getAdapterPath(), supportModel.getTemplate(), deployRequest.getFinetuningType());
            if( i != 0) {
                code = 500;
                msg = "start openai server error";
            }
        } catch (Exception e) {
            code = 500;
            msg = "start openai server error.";

        }
        if(code == 200){
            return DeployResult.builder().code(code).apiAddress("http://127.0.0.1:" + deployRequest.getPort()).build();
        }
        return DeployResult.builder().code(code).message(msg).build();
    }

    @Override
    public UnDeployResult undeploy(DeployInfo unDeployRequest) {
        int count = modelDevelopInfoDao.countPort(unDeployRequest.getPort());
        if(count > 0) {
            throw new RRException("服务已经停止");
        }
        DeployInfo byUserId = modelDevelopInfoDao.findById(unDeployRequest.getId());
        if(!Objects.equals(byUserId.getUserId(), unDeployRequest.getUserId())) {
            throw new RRException("不可以关闭其他用户的服务");
        }
        int i = stopOpenAiServer(unDeployRequest.getPort());
        if(i == 0) {
            byUserId.setRunning(0);
            modelDevelopInfoDao.update(byUserId);
            return UnDeployResult.builder().code(200).build();
        }
        return UnDeployResult.builder().code(500).message("undeploy error").apiAddress("").build();
    }

    @Override
    public boolean newDeploy(DeployInfo deployRequest) {
        Map<String, SupportModel> nameMap = SupportModelUtil.getModelSupportMap();
        SupportModel supportModel = nameMap.get(deployRequest.getModelPath());
        if(supportModel != null) {
            deployRequest.setTemplate(supportModel.getTemplate());
        }
        deployRequest.setRunning(0);
        return modelDevelopInfoDao.insert(deployRequest) > 0;
    }

    @Override
    public boolean updateDeploy(DeployInfo deployRequest) {
        return false;
    }

    @Override
    public boolean deleteDeploy(DeployInfo deployRequest) {
        DeployInfo developInfo = modelDevelopInfoDao.findById(deployRequest.getId());
        try {
            updateDeploy(developInfo);
        } catch (Exception e) {
        }
        int delete = modelDevelopInfoDao.delete(deployRequest.getId());
        return delete  > 0;
    }

    @Override
    public List<DeployInfo> getDeploys(String userId) {
        return modelDevelopInfoDao.findByUserId(userId);
    }


}

package ai.finetune;

import ai.common.utils.ObservableList;
import ai.common.utils.ThreadPoolManager;
import ai.config.ContextLoader;
import ai.config.pojo.LlamaFactoryConfig;
import cn.hutool.core.util.StrUtil;
import com.google.common.collect.Lists;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.URL;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

@Getter
@Builder
@Slf4j
@AllArgsConstructor
public class LocalLlamaFactoryService {
    private String condaPath="";
    private String condaEnv="";
    private String masterAddr = "127.0.0.1";
    private String masterPort = "7007";
    private String trainCmd = " {} torchrun --nproc_per_node 1 \\\n" +
            "    --nnodes 1 \\\n" +
            "    --node_rank 0 \\\n" +
            "    --master_addr {} \\\n" +
            "    --master_port {} \\\n" +
            "    {}/src/train.py {}";

    private String exportCmd = "{} llamafactory-cli export \\\n" +
            "            --model_name_or_path {} \\\n" +
            "            --adapter_name_or_path {}  \\\n" +
            "            --template {} \\\n" +
            "            --finetuning_type {} \\\n" +
            "            --export_dir {} \\\n" +
            "            --export_size {} \\\n" +
            "            --export_device auto \\\n" +
            "            --export_legacy_format False";

    private String runEnv = " USE_MODELSCOPE_HUB=1 ASCEND_RT_VISIBLE_DEVICES={}";
    private String llamaFactoryDir="";
    private static final ExecutorService executor;
    private String devices;

    public LocalLlamaFactoryService() {
        try {
            LlamaFactoryConfig llamaFactoryConfig = ContextLoader.configuration.getModelPlatformConfig().getFineTuneConfig().getLlamaFactoryConfig();
            if(llamaFactoryConfig != null) {
                this.condaPath = llamaFactoryConfig.getEnvPath();
                this.condaEnv = llamaFactoryConfig.getEnv();
                this.llamaFactoryDir = llamaFactoryConfig.getLlamaFactoryDir();
                devices = StrUtil.isBlank(llamaFactoryConfig.getDevices()) ? "0" : llamaFactoryConfig.getDevices();
                this.runEnv = StrUtil.format(runEnv, devices);
                this.masterPort = llamaFactoryConfig.getMasterPort() == null ? "7007" : llamaFactoryConfig.getMasterPort();
            }
        } catch (Exception e) {
            log.error("init LocalLlamaFactoryService error", e);
        }

    }

    static {
        ThreadPoolManager.registerExecutor("fine_tune");
        executor = ThreadPoolManager.getExecutor("fine_tune");
    }

    private String windowsActivateEnvCmd() {
        return "conda activate " + condaEnv;
    }

    private String linuxActivateEnvCmd() {
        return StrUtil.format("source {}/bin/activate {}", condaPath, condaEnv);
    }

    private String trainCmd(String trainConfigPath) {
        return StrUtil.format(trainCmd,  runEnv , masterAddr, masterPort, llamaFactoryDir, trainConfigPath);
    }


    private String exportCmd( String modelPath, String adapterPath, String template, String finetuningType, String exportDir, String exportSize ) {
        return StrUtil.format(exportCmd,  runEnv , modelPath, adapterPath, template, finetuningType, exportDir, exportSize);
    }


    private ObservableList<String> runCmd(List<String> cmd) {
        log.warn("run cmd : {}" , cmd);
        ObservableList<String> stringObservableList = new ObservableList<>();
        executor.submit(() -> {
            try {
                ProcessBuilder processBuilder = new ProcessBuilder(cmd);
                processBuilder.directory(Paths.get(this.llamaFactoryDir).toFile());
                Process process = processBuilder.start();
                try (InputStream inputStream = process.getInputStream();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                    String line;
//                    process.destroyForcibly();
                    while ((line = reader.readLine()) != null) {
                        stringObservableList.add(line);
                    }
                    int exitCode = process.waitFor();
                    log.info("Process exited with code: {}" , exitCode);
                } catch (IOException e) {
                    log.error("Error process : {}" , e.getMessage());
                }
            } catch (IOException e) {
                log.error("Error reading process output: {}" , e.getMessage());
            } catch (InterruptedException e) {
               log.error("Error waiting for process to complete :{} " , e.getMessage());
            } finally {
                stringObservableList.onComplete();
            }
        });
        return stringObservableList;
    }




    public ObservableList<String> train(String trainConfigPath) {
        String osName = System.getProperty("os.name").toLowerCase();
        String trainCmd = trainCmd(trainConfigPath);
        List<String> cmd = buildCmd(osName, trainCmd);
        return  runCmd(cmd);
    }


    public ObservableList<String> export(String modelPath, String adapterPath, String template, String finetuningType, String exportDir, String exportSize) {
        String osName = System.getProperty("os.name").toLowerCase();
        String exportCmd = exportCmd(modelPath, adapterPath, template, finetuningType, exportDir, exportSize);
        List<String> cmd = buildCmd(osName, exportCmd);
        return  runCmd(cmd);
    }


    private List<String> buildCmd(String osName, String cmd) {
        String activateEnvCmd;
        List<String> cmdList;
        if (osName.contains("win")) {
            activateEnvCmd = windowsActivateEnvCmd();
            cmdList = Lists.newArrayList("cmd", "/c", activateEnvCmd + " && " + cmd);
        } else {
            activateEnvCmd = linuxActivateEnvCmd();
            cmdList = Lists.newArrayList("bash", "-c", activateEnvCmd + " && " + cmd);
        }
        return cmdList;
    }

    public String startOpenAiServerScript(String modelPath, String port, String adapterPath, String template, String finetuningType) {
        URL resource = LocalLlamaFactoryService.class.getClassLoader().getResource("start_openai_server.sh");
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
        String cmd = StrUtil.format("bash {} {} {} {} {} {} {} {} {} &", scriptPath, condaPath,  condaEnv, modelPath, template,  devices, port, adapterPath, finetuningType);
        System.out.println(cmd);
        return cmd;
    }

    public String stopOpenAiServerScript(String port) {
        URL resource = LocalLlamaFactoryService.class.getClassLoader().getResource("stop_openai_server.sh");
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
            try {
                Process process = processBuilder.start();
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

    public static void main(String[] args) throws IOException, InterruptedException {
        ContextLoader.loadContext();
        LocalLlamaFactoryService localLlamaFactoryService = new LocalLlamaFactoryService();
//
//        ObservableList<String> stringObservableList = new LocalLlamaFactoryService().train("");
//        stringObservableList.getObservable().subscribe(System.out::println);
//        executor.shutdown();
        localLlamaFactoryService.startOpenAiServer("","","",null, "");
        localLlamaFactoryService.stopOpenAiServer("7007");
    }
}

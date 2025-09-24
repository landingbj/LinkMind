package ai.servlet;

import ai.common.utils.ObservableList;
import ai.config.ContextLoader;
import ai.config.pojo.FineTuneConfig;
import ai.config.pojo.LlamaFactoryConfig;
import ai.config.pojo.ModelPlatformConfig;
import ai.dao.ManagerDao;
import ai.deploy.dao.ModelDevelopInfoDao;
import ai.deploy.impl.BeiDianDeployImpl;
import ai.deploy.impl.LocalLlamaFactoryImpl;
import ai.deploy.pojo.DeployInfo;
import ai.deploy.pojo.DeployResult;
import ai.deploy.pojo.UnDeployResult;
import ai.dto.*;
import ai.finetune.TrainArgsParser;
import ai.finetune.LocalLlamaFactoryService;
import ai.finetune.pojo.ExportArgs;
import ai.finetune.pojo.FineTuneArgs;
import ai.servlet.annotation.Body;
import ai.servlet.annotation.Get;
import ai.servlet.annotation.Param;
import ai.servlet.annotation.Post;
import ai.servlet.exceptions.RRException;
import ai.utils.ImageUtil;
import ai.utils.JsonFileLoadUtil;
import cn.hutool.core.util.StrUtil;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import java.io.*;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

@Slf4j
@MultipartConfig(fileSizeThreshold = 1024 * 1024 * 1024, // 10 MB
        maxFileSize = 1024 * 1024 * 1024,      // 50 MB
        maxRequestSize = 1024 * 1024 * 1024)  // 100 MB
public class ModelManagerServlet extends RestfulServlet{

    private final LocalLlamaFactoryService localLlamaFactoryService = new LocalLlamaFactoryService();
    private final ModelDevelopInfoDao modelDevelopInfoDao = new ModelDevelopInfoDao();
    private final ManagerDao managerDao = new ManagerDao();

    private BeiDianDeployImpl beiDianDeployImpl = new BeiDianDeployImpl(ContextLoader.configuration.getModelPlatformConfig().getDeployConfig().getBeiDianPaasConfig());
    private LocalLlamaFactoryImpl localLlamaFactory = new LocalLlamaFactoryImpl(ContextLoader.configuration.getModelPlatformConfig().getDeployConfig().getLlamaFactoryConfig());

    protected boolean needForward() {
        ModelPlatformConfig modelPlatformConfig = ContextLoader.configuration.getModelPlatformConfig();
        return Boolean.TRUE.equals(modelPlatformConfig.getRemote());
    }

    protected void forwardRequest(HttpServletRequest request, HttpServletResponse response, String method) throws IOException {
        ModelPlatformConfig modelPlatformConfig = ContextLoader.configuration.getModelPlatformConfig();
        String remoteServiceUrl = modelPlatformConfig.getRemoteServiceUrl();
        if(StrUtil.isBlank(remoteServiceUrl)) {
           throw new RRException("Remote service url is not set");
        }
        String queryString = request.getQueryString();
        URL url;
        if(StrUtil.isNotBlank(queryString)) {
            url = new URL(remoteServiceUrl + request.getRequestURI() + "?" + queryString);
        } else {
            url = new URL(remoteServiceUrl + request.getRequestURI());
        }
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);
        connection.setRequestProperty("Content-Type", request.getContentType());
        connection.setRequestProperty("Accept", request.getHeader("Accept"));

        if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)) {
            connection.setDoOutput(true);
            byte[] input = readAllBytes(request.getInputStream());
            try (OutputStream os = connection.getOutputStream()) {
                os.write(input, 0, input.length);
            }
        }
        int responseCode = connection.getResponseCode();
        response.setStatus(responseCode);
        String contentType = connection.getContentType();
        response.setContentType(contentType);
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String inputLine;
            StringBuilder content = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
                if(("text/event-stream;charset=utf-8".equals(contentType))) {
                    out.println(inputLine);
                    out.flush();
                }
            }
            if(!("text/event-stream;charset=utf-8".equals(contentType))) {
                out.write(content.toString());
            }
        }
        out.close();
        connection.disconnect();
    }


    @Post("uploadDataSet")
    public void uploadDataSet(@Param("userId") String userId,  HttpServletRequest request, HttpServletResponse resp) throws IOException, ServletException {
        if(StrUtil.isBlank(userId)) {
            resp.getWriter().write("{\"error\": \"You need to log in first\"}");
            return ;
        }
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        Collection<Part> parts = request.getParts();
        if (parts == null || parts.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\": \"No files uploaded\"}");
            return;
        }
        String datasetDir = ContextLoader.configuration.getModelPlatformConfig().getFineTuneConfig().getLlamaFactoryConfig().getDatasetDir();
        String UPLOAD_DIR = datasetDir + "/" + userId;
        Map<String, Map<String, String>> dataSetInfo = createOrGetDataSetInfo(UPLOAD_DIR, "dataset_info.json");
        for (Part part : parts) {
            String fileName = getFileName(part);
            if (fileName == null || !fileName.endsWith(".json")) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("{\"error\": \"Invalid file type. Only .json files are allowed\"}");
                return;
            }

            Path uploadPath = Paths.get(UPLOAD_DIR, fileName);
            try (InputStream inputStream = part.getInputStream()) {
                Files.copy(inputStream, uploadPath, StandardCopyOption.REPLACE_EXISTING);
                String baseName = FilenameUtils.getBaseName(fileName);
                Map<String, String> data = new HashMap<>();
                data.put("file_name", fileName);
                dataSetInfo.put(baseName, data);
                log.info("File uploaded: {}", uploadPath);
            } catch (IOException e) {
                log.error("Error uploading file: {}", fileName, e);
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                resp.getWriter().write("{\"error\": \"Failed to upload file: " + fileName + "\"}");
                return;
            }
        }
        write2DatasetInfo(UPLOAD_DIR, "dataset_info.json", dataSetInfo);
        resp.getWriter().write("{\"message\": \"Files uploaded successfully\"}");
    }

    private Map<String, Map<String, String>> createOrGetDataSetInfo(String datasetDir, String filename) {
        Path dataSetDirPath = Paths.get(datasetDir);
        if(!Files.exists(dataSetDirPath)) {
            try {
                Files.createDirectories(dataSetDirPath);
            } catch (IOException e) {
                throw new RRException("get dateset info.json failed");
            }
        }
        Path path = Paths.get(datasetDir, filename);
        if (!Files.exists(path)) {
            return new HashMap<>();
        }
        try {
            String content = new String(Files.readAllBytes(path.toAbsolutePath()));
            Gson gson = new Gson();
            Type type = new TypeToken<Map<String, Map<String, String>>>() {}.getType();
            return gson.fromJson(content, type);
        } catch (IOException e) {
            throw new RRException("get dateset info.json failed");
        }
    }

    private void write2DatasetInfo(String datasetDir, String filename, Map<String, Map<String, String>>  dataInfo) {
        Path path = Paths.get(datasetDir, filename);
        Gson gson1 = new Gson();
        String json = gson1.toJson(dataInfo);
        try (FileWriter fileWriter = new FileWriter(path.toFile())) {
            fileWriter.write(json);
        } catch (IOException e) {
            System.err.println("write dataset info error : " + e.getMessage());
        }
    }

    private String getFileName(Part part) {
        String contentDisposition = part.getHeader("content-disposition");
        String[] elements = contentDisposition.split(";");
        for (String element : elements) {
            if (element.trim().startsWith("filename")) {
                return element.substring(element.indexOf('=') + 1).trim().replace("\"", "");
            }
        }
        return null;
    }

    @Get("getModels")
    public SupportModels getModels() {
        return JsonFileLoadUtil.readWordLRulesList("/model_path.json", SupportModels.class);
    }


    @Get("getDataSetInfo")
    public Map<String, Map<String, String>> getDataSetInfo(@Param("userId") String userId) {
        if(StrUtil.isBlank(userId)) {
            throw new RRException("You need to log in first");
        }
        FineTuneConfig fineTuneConfig = ContextLoader.configuration.getModelPlatformConfig().getFineTuneConfig();
        if("llamafactory".equals(fineTuneConfig.getPlatformName())) {
            String datasetDir = fineTuneConfig.getLlamaFactoryConfig().getDatasetDir();
            String UPLOAD_DIR = datasetDir + "/" + userId;
            return createOrGetDataSetInfo(UPLOAD_DIR, "dataset_info.json");
        } else if("beidianpaas".equals(fineTuneConfig.getPlatformName())) {
            return Collections.emptyMap();
        }
        return Collections.emptyMap();
    }


    @Post("deleteDataset")
    public Boolean deleteDataset(@Body UserDatasetRequest userDatasetRequest) {
        if(StrUtil.isBlank(userDatasetRequest.getUserId())) {
            throw new RRException("You need to log in first");
        }
        FineTuneConfig fineTuneConfig = ContextLoader.configuration.getModelPlatformConfig().getFineTuneConfig();
        if("llamafactory".equals(fineTuneConfig.getPlatformName())) {
            String datasetDir = fineTuneConfig.getLlamaFactoryConfig().getDatasetDir();
            String UPLOAD_DIR = datasetDir + "/" + userDatasetRequest.getUserId();
            Map<String, Map<String, String>> orGetDataSetInfo = createOrGetDataSetInfo(UPLOAD_DIR, "dataset_info.json");
            List<String> datasetNames = userDatasetRequest.getDatasetNames();
            for (String datasetName : datasetNames) {
                Map<String, String> map = orGetDataSetInfo.get(datasetName);
                String path = map.get("file_name");
                Path path1 = Paths.get(UPLOAD_DIR, path);
                boolean delete = path1.toFile().delete();
                if(delete) {
                    orGetDataSetInfo.remove(datasetName);
                } else {
                    return Boolean.FALSE;
                }
            }
            write2DatasetInfo(UPLOAD_DIR, "dataset_info.json", orGetDataSetInfo);
            return Boolean.TRUE;
        } else if ("beidianpaas".equals(fineTuneConfig.getPlatformName())) {
            return Boolean.FALSE;
        }
        return Boolean.FALSE;
    }

    private String buildUsersSaveDir(String userId) {
        String saveDir = ContextLoader.configuration.getModelPlatformConfig().getFineTuneConfig().getLlamaFactoryConfig().getSaveDir();
        return saveDir + "/user/" + userId;
    }

    @Post("train")
    public void train(@Body TrainConfig trainConfig, HttpServletResponse resp) throws IOException {
        resp.setHeader("Content-Type", "text/event-stream;charset=utf-8");
        LlamaFactoryConfig llamaFactoryConfig = ContextLoader.configuration.getModelPlatformConfig().getFineTuneConfig().getLlamaFactoryConfig();
        String userId = trainConfig.getUserId();
        FineTuneArgs fineTuneArgs = trainConfig.getFineTuneArgs();
//        //  Validate  fineTuneArgs
//        String s = ValidationUtil.validateOne(fineTuneArgs);
//        if(!StrUtil.isBlank(s)) {
//            resp.getWriter().write("{\"error\": \"" + s + "\"}");
//            return;
//        }
        Map<String, SupportModel> nameMap = getModelSupportMap();
        SupportModel supportModel = nameMap.get(fineTuneArgs.getModel_name());
        fineTuneArgs.setModel_path(llamaFactoryConfig.getLlamaFactoryDir() + File.separator + supportModel.getPath());
        fineTuneArgs.setTemplate(supportModel.getTemplate());
        String trainDir = llamaFactoryConfig.getTrainDir();
        String trainYamlPath = Paths.get(trainDir , userId, "train.yaml").toFile().getAbsolutePath();
        String savePath = buildUsersSaveDir(userId);
        String dateSetDir = Paths.get(llamaFactoryConfig.getDatasetDir(), userId).toFile().getAbsolutePath();
        fineTuneArgs.setDataset_dir(dateSetDir);
        fineTuneArgs.setOutput_dir(savePath + File.separator + fineTuneArgs.getOutput_dir());


        // parser args
        TrainArgsParser trainArgsParser = new TrainArgsParser(llamaFactoryConfig.getLlamaFactoryDir(), fineTuneArgs);
        trainArgsParser.saveMapToYaml(trainYamlPath);

        // run trains
        ObservableList<String> train = localLlamaFactoryService.train(trainYamlPath);
        streamOutput(resp, train);
    }


    @Post("getLastTrainLossPng")
    public String getLastTrainLossPng(@Body TrainConfig trainConfig)  {
        String savePath = buildUsersSaveDir(trainConfig.getUserId());
        FineTuneArgs fineTuneArgs = trainConfig.getFineTuneArgs();
        String outputDir = fineTuneArgs.getOutput_dir();
        LlamaFactoryConfig llamaFactoryConfig = ContextLoader.configuration.getModelPlatformConfig().getFineTuneConfig().getLlamaFactoryConfig();
        File file = Paths.get(llamaFactoryConfig.getLlamaFactoryDir(), savePath, outputDir, "training_loss.png").toFile();
        if(file.exists()) {
            try {
                return ImageUtil.getFileContentAsBase64(file.getAbsolutePath());
            } catch (IOException ignored) {
            }
        }
        return null;
    }



    @Post("export")
    public void export(@Body ExportConfig exportConfig, HttpServletResponse resp) throws IOException {
        String userId = exportConfig.getUserId();
        String savePath = buildUsersSaveDir(userId);
        ExportArgs exportArgs = exportConfig.getExportArgs();
        exportArgs.setExportDir(Paths.get(savePath, exportArgs.getExportDir()).toFile().getAbsolutePath());
        ObservableList<String> export = localLlamaFactoryService.export(exportArgs.getModelPath(), exportArgs.getAdapterPath(), exportArgs.getTemplate(), exportArgs.getFinetuningType(), exportArgs.getExportDir(), exportArgs.getExportSize());
        streamOutput(resp, export);
    }

    private void streamOutput(HttpServletResponse resp, ObservableList<String> export) throws IOException {
        PrintWriter out = resp.getWriter();
        CountDownLatch countDownLatch = new CountDownLatch(1);
        export.getObservable()
                .subscribe(
                        data -> {
                            out.print("data: " + data + "\n\n");
                            out.flush();
                        },
                        error -> {
                            out.print("data: " + error.getMessage() + "\n\n");
                            out.flush();
                            out.close();
                            countDownLatch.countDown();
                        },
                        () -> {
                            out.print("data: " + "[DONE]" + "\n\n");
                            out.flush();
                            out.close();
                            countDownLatch.countDown();
                        }
                );
        try {
            countDownLatch.await();
        } catch (Exception e) {

        }
    }


    @Get("getDevelop")
    public List<DeployInfo> getDevelop(@Param("userId") String userId) {
        String platformName = ContextLoader.configuration.getModelPlatformConfig().getDeployConfig().getPlatformName();
        if("llamafactory".equals(platformName)) {
            return localLlamaFactory.getDeploys(userId);
        } else if("beidian".equals(platformName)) {
            return beiDianDeployImpl.getDeploys(userId);
        }
        return Collections.emptyList();
    }

    @Post("addDevelop")
    public Boolean addDevelop(@Body DeployInfo modelDevelopInfo) {
        String platformName = ContextLoader.configuration.getModelPlatformConfig().getDeployConfig().getPlatformName();
        if("llamafactory".equals(platformName)) {
            return localLlamaFactory.newDeploy(modelDevelopInfo);
        } else if("beidian".equals(platformName)) {
            return beiDianDeployImpl.newDeploy(modelDevelopInfo);
        }
        return Boolean.FALSE;
    }

    @Post("delDevelop")
    public Boolean delDevelop(@Body DeployInfo modelDevelopInfo) {
        String platformName = ContextLoader.configuration.getModelPlatformConfig().getDeployConfig().getPlatformName();
        if("llamafactory".equals(platformName)) {
            return localLlamaFactory.deleteDeploy(modelDevelopInfo);
        } else if("beidian".equals(platformName)) {
            return beiDianDeployImpl.deleteDeploy(modelDevelopInfo);
        }
        return Boolean.FALSE;
    }

    @Get("getDevelopedAddress")
    public String getDevelop(@Param("id") Integer id, HttpServletRequest req) {
        DeployInfo developInfo = modelDevelopInfoDao.findById(id);
        Integer running = developInfo.getRunning();
        if(running == 0) {
            throw new RRException("模型未运行");
        }
        return developInfo.getApiAddress();
    }

    private Map<String, SupportModel> getModelSupportMap() {
        SupportModels models = getModels();
        return models.getModels().stream().collect(Collectors.toMap(SupportModel::getName, a->a));
    }

    @Post("start")
    public Boolean start(@Body DeployInfo modelDevelopInfo) {
        String platformName = ContextLoader.configuration.getModelPlatformConfig().getDeployConfig().getPlatformName();
        if("beidian".equals(platformName)) {
            DeployResult deploy = beiDianDeployImpl.deploy(modelDevelopInfo);
            return deploy.getCode() == 200;
        }
        modelDevelopInfo = modelDevelopInfoDao.findById(modelDevelopInfo.getId());
        if(modelDevelopInfo.getRunning() == 1) {
            throw new RRException("模型已在运行中");
        }
        LlamaFactoryConfig llamaFactoryConfig = ContextLoader.configuration.getModelPlatformConfig().getFineTuneConfig().getLlamaFactoryConfig();
        List<String> ports = llamaFactoryConfig.getPorts();
        if(ports == null || ports.isEmpty()) {
            throw new RRException("无可用端口");
        }
        List<String> runningPorts = modelDevelopInfoDao.runningPort(modelDevelopInfo.getPort());
        ports.removeAll(runningPorts);
        if(ports.isEmpty()) {
            throw new RRException("无可用端口.");
        }
        String port = ports.get(0);
        int count = modelDevelopInfoDao.countPort(port);
        if(count > 0) {
            throw new RRException("端口已被占用");
        }
        modelDevelopInfo.setPort(port);

        Map<String, SupportModel> nameMap = getModelSupportMap();
        SupportModel supportModel = nameMap.get(modelDevelopInfo.getModelPath());
        String path = buildUsersSaveDir(modelDevelopInfo.getUserId());
        String modelPath = null;
        if(supportModel != null) {
            modelPath = llamaFactoryConfig.getLlamaFactoryDir() + File.separator + supportModel.getPath();
        } else {
            modelPath = path + File.separator + modelDevelopInfo.getModelPath();
        }
        String adapterPath = null;
        String finetuningType = null;
        if(StrUtil.isNotBlank(modelDevelopInfo.getAdapterPath())) {
            adapterPath = path + File.separator + modelDevelopInfo.getAdapterPath();
            finetuningType = "lora";
        }
        int i = localLlamaFactoryService.startOpenAiServer(modelPath, modelDevelopInfo.getPort()
                , adapterPath, modelDevelopInfo.getTemplate(), finetuningType);
        if(i != 0) {
            return Boolean.FALSE;
        }
        modelDevelopInfo.setRunning(1);
        modelDevelopInfoDao.update(modelDevelopInfo);
        return Boolean.TRUE;
    }

    @Post("stop")
    public Boolean stop(@Body DeployInfo modelDevelopInfo) {
        String platformName = ContextLoader.configuration.getModelPlatformConfig().getDeployConfig().getPlatformName();
        if("beidian".equals(platformName)) {
            UnDeployResult undeploy = beiDianDeployImpl.undeploy(modelDevelopInfo);
            return undeploy.getCode() == 200;
        }
        int count = modelDevelopInfoDao.countPort(modelDevelopInfo.getPort());
        if(count > 0) {
            throw new RRException("服务已经停止");
        }
        DeployInfo byUserId = modelDevelopInfoDao.findById(modelDevelopInfo.getId());
        if(!Objects.equals(byUserId.getUserId(), modelDevelopInfo.getUserId())) {
            throw new RRException("不可以关闭其他用户的服务");
        }
        modelDevelopInfo = byUserId;
        int i = localLlamaFactoryService.stopOpenAiServer(modelDevelopInfo.getPort());
        if(i == 0) {
            modelDevelopInfo.setRunning(0);
            modelDevelopInfoDao.update(modelDevelopInfo);
        }
        return i == 0 ? Boolean.TRUE :Boolean.FALSE;
    }


    // start model manager
    @Post("addManagerModel")
    public Boolean addManagerModel(@Body ManagerModel managerModel) {
        int i = managerDao.addManagerModel(managerModel);
        return i > 0;
    }

    @Post("delManagerModel")
    public Boolean delManagerModel(@Body ManagerModel managerModel) {
        int i = managerDao.delManagerModel(managerModel.getId());
        return i > 0;
    }


    @Post("updateManagerModel")
    public Boolean updateManagerModel(@Body ManagerModel managerModel) {
        // TODO 2025/2/13 更新状态时 检查模型是否运行
        int i = managerDao.updateManagerModel(managerModel);
        return i > 0;
    }

    @Post("getManagerModels")
    public List<ManagerModel> getManagerModels(@Body ManagerModel managerModel) {
        return managerDao.getManagerModels(managerModel.getUserId());
    }




}

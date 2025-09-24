package ai.deploy.impl;

import ai.common.exception.RRException;
import ai.common.utils.LRUCache;
import ai.config.pojo.BeiDianPaasConfig;
import ai.deploy.ModelDeployment;
import ai.deploy.dao.ModelDevelopInfoDao;
import ai.deploy.pojo.*;
import ai.deploy.util.SupportModelUtil;
import ai.paas.beidian.pojo.*;
import ai.paas.beidian.pojo.request.InferenceRequest;
import ai.paas.beidian.pojo.request.PasswordLoginRequest;
import ai.paas.beidian.pojo.response.*;
import ai.paas.beidian.service.PlatformApiService;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.google.gson.Gson;
import org.apache.hadoop.util.Lists;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class BeiDianDeployImpl implements ModelDeployment {

    private LRUCache<String, String> authCache = new LRUCache<>(1,   60 * 60 * 2 - 1, TimeUnit.SECONDS);

    private String account = "";
    private String password =  "";
    private String baseUrl = "https://console-region1.behcyun.com:31443";
    private String spaceId = "";
    private String traceId = "";
    private String acceptLanguage = "zh-Hans";
    private String projectId;
    private Long imageId;
    private String runscript = "cd   $GEMINI_CODE/inference\n" +
            "export DISABLE_VERSION_CHECK=1\n" +
            "export API_PORT=8000\n" +
            "export CUDA_VISIBLE_DEVICES=0,1\n" +
            "export VLLM_DISABLE_CUSTOM_ALL_REDUCE=1\n" +
            "/root/miniconda3/envs/vllm/bin/python src/api.py --model_name_or_path /gemini/pretrain/{} --template deepseekr1 --trust_remote_code True  --infer_backend vllm\n";

    private ModelDevelopInfoDao modelDevelopInfoDao = new ModelDevelopInfoDao();

    public BeiDianDeployImpl(BeiDianPaasConfig beiDianPaasConfig) {
        if(beiDianPaasConfig != null)  {
            account = beiDianPaasConfig.getAccount();
            password = beiDianPaasConfig.getPassword();
            baseUrl = beiDianPaasConfig.getBaseUrl();
            spaceId = beiDianPaasConfig.getSpaceId();
            traceId = UUID.randomUUID().toString();
            acceptLanguage = beiDianPaasConfig.getAcceptLanguage();
            projectId = beiDianPaasConfig.getProjectId();
            imageId = beiDianPaasConfig.getImageId();
        }
    }


    private String getAuth() {
//        return "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOjEwMDQ3NywidXNlck5hbWUiOiIxODIwNzEzNTY0OSIsInVzZXJSb2xlSWRzIjpbXSwibG9naW5UeXBlIjoxLCJleHAiOjE3NTI3Mzk0MjIsImlzcyI6ImdlbWluaS11c2VyYXV0aCJ9.mcYAHcc4uvlJfZxbChdE19pJS6WHJM38hByO9nPdRxM";
        String authorization = authCache.get(account);
        if(authorization==null) {
            synchronized ( this) {
                authorization = authCache.get(account);
                if(authorization != null) {
                    return authorization;
                }
                PlatformApiService platformApiService = new PlatformApiService(baseUrl,
                        traceId, spaceId,
                        null, acceptLanguage);
                try {
                    authorization= platformApiService.auth(PasswordLoginRequest.builder().phone(account).password(password).build());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return  authorization;
    }

    private PlatformApiService getplatformApiService() {
        return  new PlatformApiService(baseUrl,
                traceId, spaceId,
                getAuth(), acceptLanguage);
    }

    @Override
    public boolean newDeploy(DeployInfo deployRequest) {

        PlatformApiService platformApiService = getplatformApiService();
        Map<String, SupportModel> modelSupportMap = SupportModelUtil.getModelSupportMap();
        SupportModel supportModel = modelSupportMap.get(deployRequest.getModelPath());
        if(supportModel == null || supportModel.getModelId() == null) {
            throw new RRException("not support model");
        }
        Response<ProjectDetailData> projectDetailDataResponse = platformApiService.projectDetail(this.projectId);
        if(projectDetailDataResponse.getCode() != 0) {
            throw new RRException(projectDetailDataResponse.getMsg());
        }
        deployRequest.setTemplate(supportModel.getTemplate());
        ProjectDetailData projectDetailData = projectDetailDataResponse.getData();
        String inferenceId = projectDetailData.getInferenceId();
        if(inferenceId != null)
        {
            deployRequest.setInferenceId(inferenceId);
            deployRequest.setRunning(0);
            int id = modelDevelopInfoDao.insert(deployRequest);
            deployRequest.setId(id);
            return updateDeploy(deployRequest);
        }
        Response<ModelInfoData> modelInfoDataResponse = platformApiService.modelDetail(supportModel.getModelId());
        if(modelInfoDataResponse.getCode() != 0) {
            throw new RRException(modelInfoDataResponse.getMsg());
        }
        ModelInfoData modelInfo = modelInfoDataResponse.getData();
        InferenceRequest request = InferenceRequest.builder()
                .projectId(this.projectId)
                .imageId(this.imageId)
                .replicas(deployRequest.getReplicas() == null ? 1 : deployRequest.getReplicas())
                .mountCode(true)
                .taskroles(Lists.newArrayList(
                        TaskRoleInfo.builder()
                                .runScript(StrUtil.format(runscript, deployRequest.getModelPath()))
                                .instance(1)
                                .specInstanceInfo(SpecInstanceInfo.builder()
                                        .specInstanceId(0L)
                                        .build()).build()
                ))
                .services(Lists.newArrayList(Service.builder().targetPort(8000).protocol("TCP").build()))
                .datasetInData(Collections.emptyList())
                .preInData(Lists.newArrayList(InData.builder()
                        .modelId(modelInfo.getModelId())
                        .modelName(modelInfo.getModelName())
                        .dataId(modelInfo.getModelId())
                        .dataName(modelInfo.getModelName())
                        .dataDesc(modelInfo.getDescription())
                        .dataSize(modelInfo.getModelSize())
                        .sizeSync(modelInfo.getSizeSync())
                        .userPath("/gemini/pretrain")
                        .envName("GEMINI_PRETRAIN")
                        .permissions(modelInfo.getPermissions())
                        .isDataUsable(true)
                        .isDataDeleted(false)
                        .isDataVersionDeleted(false)
                        .version("latest")
                        .versionId("latest")
                        .azList(Lists.newArrayList())
                        .build()))
                .description("推理api服务").build();
        Response<InferenceData> inferenceNewDataResponse = platformApiService.inferenceNew(request);
        if(inferenceNewDataResponse.getCode() != 0) {
            throw new RRException(inferenceNewDataResponse.getMsg());
        }
        InferenceData data = inferenceNewDataResponse.getData();
        System.out.println("inference data: " + data);
        deployRequest.setInferenceId(data.getInferenceId());
        modelDevelopInfoDao.insert(deployRequest);
        return true;
    }

    @Override
    public boolean updateDeploy(DeployInfo deployRequest) {
        PlatformApiService platformApiService = getplatformApiService();
        Map<String, SupportModel> modelSupportMap = SupportModelUtil.getModelSupportMap();
        SupportModel supportModel = modelSupportMap.get(deployRequest.getModelPath());
        if(supportModel == null || supportModel.getModelId() == null) {
            throw new RRException("not support model");
        }
        Response<ModelInfoData> modelInfoDataResponse = platformApiService.modelDetail(supportModel.getModelId());
        if(modelInfoDataResponse.getCode() != 0) {
            throw new RRException(modelInfoDataResponse.getMsg());
        }
        Response<InferenceDetailData> inferenceDetailDataResponse = platformApiService.inferenceDetail(deployRequest.getInferenceId());
        if(inferenceDetailDataResponse.getCode() != 0) {
            throw new RRException(inferenceDetailDataResponse.getMsg());
        }
        InferenceDetailData inferenceDetailData = inferenceDetailDataResponse.getData();
        List<TaskRoleInfo> taskroleList = inferenceDetailData.getTaskroleList();
        TaskRoleInfo taskRoleInfo = taskroleList.get(0);
        String script = StrUtil.format(runscript, supportModel.getName());
        String runScript = taskRoleInfo.getRunScript();
        // 根据脚本是否变化判断是否更新
            if(script.equals(runScript)) {
            deployRequest.setInferenceId(inferenceDetailData.getInferenceId());
            modelDevelopInfoDao.update(deployRequest);
            return true;
        }
        taskRoleInfo.setRunScript(StrUtil.format(runscript, supportModel.getName()));
        taskRoleInfo.setTaskroleId(null);
        taskRoleInfo.setSpecInstanceInfo( null);
        String clusterId = inferenceDetailData.getServices().get(0).getClusterId();

        ModelInfoData modelInfo = modelInfoDataResponse.getData();
        List<InData> preInData = inferenceDetailData.getPreInData();
        InData inData = preInData.get(0);
        AZInfo azInfo = AZInfo.builder().clusterId(clusterId).clusterName("cluster1").azId("c6de37a1-a84a-4466-9c84-3d10318106d3").azName("az1").azDisplayName("az1").azShowColor("#21B86D").build();
        preInData.forEach(d -> inData.setAzList(Lists.newArrayList(azInfo)));
        inData.setDataId(modelInfo.getModelId());
//        inData.setModelName(modelInfo.getModelName());
        inData.setDataName(modelInfo.getModelName());
        inData.setDataDesc(modelInfo.getModelName());
        inData.setDataSize(modelInfo.getModelSize());
        inData.setSizeSync(0);
        inData.setPermissions(modelInfo.getPermissions());
        inData.setCoverPagePath("");
        inData.setFileName("");
        inData.setSpaceId("");
        inData.setSpaceName("");
        inData.setStorageName("");
        inData.setAccessType(0);
        inData.setAccessTypeName("");
        inData.setCreateUserId(modelInfo.getCreateUserId());
        inData.setCreateDisplayName("");
        inData.setCreateUserEmail("");
        inData.setCreateTime(0L);
        inData.setUpdateTime(0L);
        inData.setModelType("");
        inData.setOutputJobId("");
        inData.setLastVersionId("");
        inData.setOutputJobIsAuto( false);
        List<Service> services = inferenceDetailData.getServices().stream()
                .map(s -> Service.builder().targetPort(s.getTargetPort()).remark(s.getRemark()).protocol(s.getProtocol()).build())
                .collect(Collectors.toList());
        InferenceRequest request = InferenceRequest.builder()
                .projectId(this.projectId)
                .imageId(this.imageId)
                .replicas(inferenceDetailData.getReplicas())
                .mountCode(true)
                .taskroles(taskroleList)
                .services(services)
                .datasetInData(Collections.emptyList())
                .preInData(preInData)
                .description("推理api 服务").build();
        Response<InferenceData> inferenceUpgradeDataResponse = platformApiService.inferenceUpgrade(deployRequest.getInferenceId(), request);
        if(inferenceUpgradeDataResponse.getCode() != 0) {
            throw new RRException(inferenceUpgradeDataResponse.getMsg());
        }
        InferenceData data = inferenceUpgradeDataResponse.getData();
        deployRequest.setInferenceId(data.getInferenceId());
        modelDevelopInfoDao.update(deployRequest);
        return true;
    }

    @Override
    public boolean deleteDeploy(DeployInfo deployRequest) {
        UnDeployResult undeploy = undeploy(deployRequest);
        if(undeploy != null && undeploy.getCode() == 200) {
            modelDevelopInfoDao.delete(deployRequest.getId());
            return true;
        }
        return false;
    }

    @Override
    public List<DeployInfo> getDeploys(String userId) {
        List<DeployInfo> deployInfos = modelDevelopInfoDao.findByUserId(userId);
        deployInfos.forEach(deployInfo -> {
            if(deployInfo.getRunning() == 1 && deployInfo.getInferenceId() != null) {
                if(deployInfo.getApiAddress() == null) {
                    String apiAddress = baseUrl.replaceAll(":\\d+.+", "") + ":" + deployInfo.getPort() + "/v1/chat/completions";
                    apiAddress = apiAddress.replaceAll("https://", "http://");
                    deployInfo.setApiAddress(apiAddress);
                    modelDevelopInfoDao.update(deployInfo);
                }
            }
        });
        return deployInfos;
    }

    @Override
    public DeployResult deploy(DeployInfo deployRequest) {
//        https://console-region1.behcyun.com:31443/gemini/v1/gemini_api/gemini_api/user/inference/start/598778806694236160
        DeployInfo deployInfo = modelDevelopInfoDao.findById(deployRequest.getId());
        if(deployInfo.getRunning() == 1) {
            throw new RRException("模型正在运行...");
        }
        PlatformApiService platformApiService = getplatformApiService();
        Response<Object> inferenceStart = platformApiService.inferenceStart(deployInfo.getInferenceId());
        if(inferenceStart.getCode() != 0) {
            throw new RRException(inferenceStart.getMsg());
        }
        int maxTry = 120;
        Response<InferenceDetailData> inferenceDetailDataResponse = null;
        try {
            while (maxTry > 0) {
                inferenceDetailDataResponse = platformApiService.inferenceDetail(deployInfo.getInferenceId());
                if(inferenceDetailDataResponse.getCode() != 0) {
                    throw new RRException(inferenceDetailDataResponse.getMsg());
                }
                if(inferenceDetailDataResponse.getData().getStatus().startsWith("STOPPING")) {
                    throw new RRException(inferenceDetailDataResponse.getMsg());
                }
                if(inferenceDetailDataResponse.getData().getStatus().equals("RUNNING")) {
                    break;
                }
                maxTry--;
                delay(10000);
            }
        } catch (Exception e) {

        }
        if(maxTry <= 0) {
            platformApiService.inferenceCancel(deployInfo.getInferenceId());
            throw new RRException("超过最大重启次数启动失败...");
        }
        // TODO 2025/7/21 更新端口， 检测服务状态 启动后再 testServices
        InferenceDetailData data = inferenceDetailDataResponse.getData();
        System.out.println(data);
        List<Service> services = data.getServices();
        String nodePort = "" + services.get(0).getNodePort();
        String apiAddress = baseUrl.replaceAll(":\\d+.+", "") + ":" + nodePort + "/v1/chat/completions";
        apiAddress = apiAddress.replaceAll("https://", "http://");
        boolean isrunning = testServices(apiAddress);
        while (!isrunning) {
            delay(10000);
            isrunning = testServices(apiAddress);
        }
        deployInfo.setRunning(1);
        deployInfo.setApiAddress(apiAddress);
        deployInfo.setPort(nodePort);
        modelDevelopInfoDao.update(deployInfo);
        return DeployResult.builder().code(200).apiAddress(apiAddress).build();
    }

    private void delay(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {}
    }

    private boolean testServices(String apiAddress) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(apiAddress);
            HttpResponse response = httpClient.execute(request);
            int statusCode = response.getStatusLine().getStatusCode();
            System.out.println("Status Code: " + statusCode);
            return statusCode == HttpStatus.SC_OK || statusCode == HttpStatus.SC_METHOD_NOT_ALLOWED;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public UnDeployResult undeploy(DeployInfo unDeployRequest) {

        DeployInfo deployInfo = modelDevelopInfoDao.findById(unDeployRequest.getId());
        String inferenceId = deployInfo.getInferenceId();
        PlatformApiService platformApiService = getplatformApiService();
        Response<Object> inferenceCancel = platformApiService.inferenceCancel(inferenceId);
        if(inferenceCancel.getCode() != 0) {
            return UnDeployResult.builder().code(500).message(inferenceCancel.getMessage()).build();
        }
        deployInfo.setRunning(0);
        modelDevelopInfoDao.update(deployInfo);
        Response<InferenceDetailData> inferenceDetailDataResponse = platformApiService.inferenceDetail(inferenceId);
        if(inferenceDetailDataResponse.getCode() != 0) {
            return UnDeployResult.builder().code(200).build();
        }
        InferenceDetailData data = inferenceDetailDataResponse.getData();
        while (!data.getStatus().startsWith("STOPPED")) {
            delay(5000);
            inferenceDetailDataResponse = platformApiService.inferenceDetail(inferenceId);
            data = inferenceDetailDataResponse.getData();
        }
        return UnDeployResult.builder().code(200).build();
    }


    public static void main(String[] args) {
//        BeiDianPaasConfig config = BeiDianPaasConfig.builder().
//                account("18207135649").password("Lz283541784%").acceptLanguage("zh-Hans").spaceId("wikqnvcuhpkw")
//                .projectId("598774223834071040").imageId(85L)
//                .baseUrl("https://console-region1.behcyun.com:31443/gemini/v1/gemini_api/gemini_api")
//                .inferenceId("598778806694236160").build();
//        BeiDianDeployImpl beiDianDeploy = new BeiDianDeployImpl(config);
//        boolean b = beiDianDeploy.newDeploy(DeployInfo.builder().modelPath("DeepSeek-R1-Distill-Qwen-32B").build());
//        System.out.println(b);
        String baseUrl = "https://console-region1.behcyun.com:31443/gemini/v1/gemini_api/gemini_api";
        String result = baseUrl.replaceAll(":\\d+.+", "");
        System.out.println(result);
//        UnDeployResult undeploy = beiDianDeploy.undeploy(DeployInfo.builder().inferenceId("598778806694236160").build());
//        System.out.println(undeploy);
//        DeployResult deploy = beiDianDeploy.deploy(DeployInfo.builder().inferenceId("598778806694236160").build());
//        System.out.println(deploy);
    }


}

package ai.servlet.api;

import ai.agent.AgentService;
import ai.agent.dto.LagiAgentResponse;
import ai.common.pojo.Backend;
import ai.common.pojo.KnowledgeBase;
import ai.common.pojo.VectorStoreConfig;
import ai.config.ContextLoader;
import ai.config.pojo.RAGFunction;
import ai.migrate.service.UploadFileService;
import ai.servlet.RestfulServlet;
import ai.servlet.annotation.Body;
import ai.servlet.annotation.Get;
import ai.servlet.annotation.Param;
import ai.servlet.annotation.Post;
import ai.sevice.KnowledgeBaseService;
import cn.hutool.core.util.StrUtil;

import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CorpusApiServlet extends RestfulServlet {
    private KnowledgeBaseService service = new KnowledgeBaseService();
    private final AgentService agentService = new AgentService();

    @Get("region")
    public String getRegion() {
        return service.getRegion();
    }

    @Post("add")
    public Boolean addKnowledge(@Body  KnowledgeBase knowledgeBase) {
        try {
            String format = StrUtil.format("{}_{}_{}", service.getRegion(), knowledgeBase.getUserId(), new Date().getTime());
            knowledgeBase.setCategory(format);
            RAGFunction rag = ContextLoader.configuration.getStores().getRag();
            Map<String, VectorStoreConfig> map = ContextLoader.configuration.getStores().getVectors().stream().collect(Collectors.toMap(VectorStoreConfig::getName,  i->i));
            VectorStoreConfig vectorStoreConfig = map.get(rag.getVector());
            List<Backend> text2sql = ContextLoader.configuration.getFunctions().getText2sql();
            boolean enableQa = false;
            if(text2sql != null) {
                enableQa = text2sql.stream().anyMatch(Backend::getEnable);
            }

            if(knowledgeBase.getSimilarityCutoff() == null) {
                knowledgeBase.setSimilarityCutoff(vectorStoreConfig.getSimilarityCutoff());
            }
            if(knowledgeBase.getSimilarityTopK() == null) {
                knowledgeBase.setSimilarityTopK(vectorStoreConfig.getSimilarityTopK());
            }
            if(knowledgeBase.getWenbenChunkSize() == null) {
                knowledgeBase.setWenbenChunkSize(512);
            }
            if(knowledgeBase.getBiaogeChunkSize() == null) {
                knowledgeBase.setBiaogeChunkSize(512);
            }
            if(knowledgeBase.getTuwenChunkSize() == null) {
                knowledgeBase.setTuwenChunkSize(512);
            }
            if(knowledgeBase.getEnableGraph() == null){
                knowledgeBase.setEnableGraph(true);
            }
            if(knowledgeBase.getEnableText2qa() == null){
                knowledgeBase.setEnableText2qa(enableQa);
            }
            if(knowledgeBase.getEnableFulltext() == null){
                knowledgeBase.setEnableFulltext(true);
            }
            return service.insert(knowledgeBase);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    @Post("delete")
    public boolean delete(@Body KnowledgeBase knowledgeBase) {
        try {
            return service.deleteById(knowledgeBase.getId());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }


    @Post("update")
    public Boolean update(@Body KnowledgeBase knowledgeBase) {
        try {
            knowledgeBase.setCategory(null);
            return service.update(knowledgeBase);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    @Get("getList")
    public List<KnowledgeBase> getKnowledgeList(@Param("userId") String userId, @Param("region") String region) {
        try {

            UploadFileService uploadFileService = new UploadFileService();
            List<KnowledgeBase> knowledgeBaseList = service.getByUserRegion(userId, region);
            if(knowledgeBaseList != null) {
                for(KnowledgeBase kb : knowledgeBaseList) {
                    String category = kb.getCategory();
                    String kbId = kb.getId().toString();
                    try {
                        int totalRow = uploadFileService.getTotalRow(category, userId, kbId);
                        kb.setFileCount(totalRow);
                    } catch (Exception e) {
                        kb.setFileCount(0);
                    }
                }
            } else {
                knowledgeBaseList = Collections.emptyList();
            }
            return knowledgeBaseList;
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }


    @Get("getOne")
    public KnowledgeBase getKnowledgeBase(@Param("knowledgeId") Long knowledgeId) {
        try {
            return service.getById(knowledgeId);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Get("getBasicInfo")
    public KnowledgeBase getKnowledgeBase(@Param("agentId") String agentId) throws IOException {
        LagiAgentResponse lagiAgentResponse = agentService.getLagiAgent(null, agentId);
        String region = service.getRegion();
        KnowledgeBase result = new KnowledgeBase();
        result.setUserId(lagiAgentResponse.getData().getLagiUserId());
        result.setRegion(region);
        return result;
    }

}

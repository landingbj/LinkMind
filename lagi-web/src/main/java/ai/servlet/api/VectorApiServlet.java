package ai.servlet.api;

import ai.bigdata.BigdataService;
import ai.common.pojo.IndexSearchData;
import ai.common.pojo.UserRagSetting;
import ai.migrate.service.UploadFileService;
import ai.openai.pojo.ChatCompletionRequest;
import ai.servlet.BaseServlet;
import ai.servlet.dto.VectorSearchRequest;
import ai.servlet.dto.VectorDeleteRequest;
import ai.servlet.dto.VectorQueryRequest;
import ai.servlet.dto.VectorUpsertRequest;
import ai.vector.VectorCacheLoader;
import ai.vector.VectorDbService;
import ai.vector.VectorStoreService;
import ai.vector.pojo.IndexRecord;
import ai.vector.pojo.QueryCondition;
import ai.vector.pojo.UpsertRecord;
import ai.vector.pojo.VectorCollection;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VectorApiServlet extends BaseServlet {
    private final VectorStoreService vectorStoreService = new VectorStoreService();
    private final VectorDbService vectorDbService = new VectorDbService(null);
    private final BigdataService bigdataService = new BigdataService();
    private final UploadFileService uploadFileService = new UploadFileService();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setHeader("Content-Type", "application/json;charset=utf-8");
        String url = req.getRequestURI();
        String method = url.substring(url.lastIndexOf("/") + 1);
        if (method.equals("query")) {
            this.query(req, resp);
        } else if (method.equals("upsert")) {
            this.upsert(req, resp);
        } else if (method.equals("search")) {
            this.search(req, resp);
        }else if (method.equals("searchByMetadata")) {
                this.searchByMetadata(req, resp);
        }else if (method.equals("deleteById")) {
            this.deleteById(req, resp);
        } else if (method.equals("deleteByMetadata")) {
            this.deleteByMetadata(req, resp);
        } else if (method.equals("deleteCollection")) {
            this.deleteCollection(req, resp);
        }else if (method.equals("updateTextBlockSize")) {
            this.updateTextBlockSize(req, resp);
        }else if (method.equals("resetBlockSize")) {
            this.resetBlockSize(req, resp);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setHeader("Content-Type", "application/json;charset=utf-8");
        String url = req.getRequestURI();
        String method = url.substring(url.lastIndexOf("/") + 1);
        if (method.equals("listCollections")) {
            this.listCollections(req, resp);
        } else if (method.equals("getTextBlockSize")) {
            this.getTextBlockSize(req, resp);
        }
    }

    private void resetBlockSize(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json;charset=utf-8");
        UserRagSetting userRagSetting = reqBodyToObj(req, UserRagSetting.class);
        Map<String, Object> result = new HashMap<>();
        try {
            uploadFileService.deleteTextBlockSize(userRagSetting);
            result.put("status", "success");
        }catch (SQLException e){
            result.put("status", "failed");
        }
        responsePrint(resp, toJson(result));
    }

    private void updateTextBlockSize(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json;charset=utf-8");
        UserRagSetting userRagSetting = reqBodyToObj(req, UserRagSetting.class);
        Map<String, Object> result = new HashMap<>();
        try {
            uploadFileService.updateTextBlockSize(userRagSetting);
            result.put("status", "success");
        }catch (SQLException e){
            result.put("status", "failed");
        }
        responsePrint(resp, toJson(result));
    }

    private void getTextBlockSize(HttpServletRequest req, HttpServletResponse resp)  throws ServletException, IOException {
        resp.setHeader("Content-Type", "application/json;charset=utf-8");
        String category = req.getParameter("category");
        String userId = req.getParameter("lagiUserId");

        Map<String, Object> map = new HashMap<>();
        List<UserRagSetting> result = null;
        try {
            result = uploadFileService.getTextBlockSize(category, userId);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        if (result != null) {
            map.put("status", "success");
            map.put("data", result);
        } else {
            map.put("status", "failed");
        }
        PrintWriter out = resp.getWriter();
        out.print(gson.toJson(map));
        out.flush();
        out.close();
    }

    private void search(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        ChatCompletionRequest request = reqBodyToObj(req, ChatCompletionRequest.class);
        List<IndexSearchData> indexSearchData = vectorDbService.searchByContext(request);
        Map<String, Object> result = new HashMap<>();
        if (indexSearchData == null || indexSearchData.isEmpty()) {
            result.put("status", "failed");
        } else {
            result.put("status", "success");
            result.put("data", indexSearchData);
        }
        responsePrint(resp, toJson(result));
    }
    private void searchByMetadata(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        VectorSearchRequest request = reqBodyToObj(req, VectorSearchRequest.class);
        String text = request.getText();
        String category = request.getCategory();
        Map<String, String> where = request.getWhere();
        vectorStoreService.search(text, where, category);
        List<IndexSearchData> indexSearchData = vectorStoreService.search(text, where, category);
        Map<String, Object> result = new HashMap<>();
        if (indexSearchData == null || indexSearchData.isEmpty()) {
            result.put("status", "failed");
        } else {
            result.put("status", "success");
            result.put("data", indexSearchData);
        }
        responsePrint(resp, toJson(result));
    }

    private void query(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        VectorQueryRequest vectorQueryRequest = reqBodyToObj(req, VectorQueryRequest.class);
        QueryCondition queryCondition = new QueryCondition();
        queryCondition.setN(vectorQueryRequest.getN());
        queryCondition.setText(vectorQueryRequest.getText());
        queryCondition.setWhere(vectorQueryRequest.getWhere());
        queryCondition.setIds(vectorQueryRequest.getIds());
        List<IndexRecord> recordList;

        if (vectorQueryRequest.getCategory() == null) {
            recordList = vectorStoreService.query(queryCondition);
        } else {
            recordList = vectorStoreService.query(queryCondition, vectorQueryRequest.getCategory());
        }
        Map<String, Object> result = new HashMap<>();
        if (recordList.isEmpty()) {
            result.put("status", "failed");
        } else {
            result.put("status", "success");
            result.put("data", recordList);
        }
        responsePrint(resp, toJson(result));
    }

    private void upsert(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        VectorUpsertRequest vectorUpsertRequest = reqBodyToObj(req, VectorUpsertRequest.class);
        List<UpsertRecord> upsertRecords = vectorUpsertRequest.getData();
        String category = vectorUpsertRequest.getCategory();
        boolean isContextLinked = vectorUpsertRequest.getContextLinked();
        if(isContextLinked && upsertRecords.size() == 2) {
            long timestamp = Instant.now().toEpochMilli();
            UpsertRecord instructionRecord = upsertRecords.get(0);
            UpsertRecord outputRecord = upsertRecords.get(1);
            instructionRecord.getMetadata().put("seq", Long.toString(timestamp));
            outputRecord.getMetadata().put("seq", Long.toString(timestamp));
            String s1 = instructionRecord.getMetadata().get("filename");
            String s2 = outputRecord.getMetadata().get("filename");
            if(s1 == null && s2 == null) {
                instructionRecord.getMetadata().put("filename", "");
                outputRecord.getMetadata().put("filename", "");
            }
            VectorCacheLoader.put2L2(instructionRecord.getDocument().replaceAll("\n",""), timestamp, outputRecord.getDocument());
        }
        vectorStoreService.upsertCustomVectors(upsertRecords, category, isContextLinked);

        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("data", upsertRecords);
        responsePrint(resp, toJson(result));
    }

    private void deleteById(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        VectorDeleteRequest vectorDeleteRequest = reqBodyToObj(req, VectorDeleteRequest.class);
        String category = vectorDeleteRequest.getCategory();
        List<String> ids = vectorDeleteRequest.getIds();
        vectorStoreService.delete(ids, category);
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        responsePrint(resp, toJson(result));
    }

    private void deleteByMetadata(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        VectorDeleteRequest vectorDeleteRequest = reqBodyToObj(req, VectorDeleteRequest.class);
        String category = vectorDeleteRequest.getCategory();
        List<Map<String, String>> whereList = vectorDeleteRequest.getWhereList();
        vectorStoreService.deleteWhere(whereList, category);
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        responsePrint(resp, toJson(result));
    }

    private void deleteCollection(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        VectorDeleteRequest vectorDeleteRequest = reqBodyToObj(req, VectorDeleteRequest.class);
        String category = vectorDeleteRequest.getCategory();
        vectorStoreService.deleteCollection(category);
        uploadFileService.deleteUploadFile(category);
        bigdataService.delete(category);
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        responsePrint(resp, toJson(result));
    }

    private void listCollections(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        List<VectorCollection> collections = vectorStoreService.listCollections();
        Map<String, Object> result = new HashMap<>();
        if (collections.isEmpty()) {
            result.put("status", "failed");
        } else {
            result.put("status", "success");
            result.put("data", collections);
        }
        responsePrint(resp, toJson(result));
    }
}

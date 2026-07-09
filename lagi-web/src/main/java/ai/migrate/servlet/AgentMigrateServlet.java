package ai.migrate.servlet;

import ai.migrate.pojo.AgentImportCommitRequest;
import ai.migrate.pojo.AgentImportPreviewRequest;
import ai.migrate.service.AgentCatalogService;
import ai.migrate.service.AgentImportService;
import ai.servlet.BaseServlet;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AgentMigrateServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;
    private static final long MAX_UPLOAD_SIZE = 10L * 1024L * 1024L;

    private final AgentCatalogService catalogService = new AgentCatalogService();
    private final AgentImportService importService = new AgentImportService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        dispatch(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        dispatch(req, resp);
    }

    private void dispatch(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json;charset=utf-8");
        String path = req.getRequestURI();
        try {
            if (path.endsWith("/agent/list")) {
                responsePrint(resp, toJson(catalogService.listAgents()));
                return;
            }
            if (path.endsWith("/agent/import/preview")) {
                handlePreview(req, resp);
                return;
            }
            if (path.endsWith("/agent/import/commit")) {
                handleCommit(req, resp);
                return;
            }
            writeJsonError(resp, HttpServletResponse.SC_NOT_FOUND, "未找到接口");
        } catch (IllegalArgumentException e) {
            writeJsonError(resp, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            writeJsonError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "处理失败");
        }
    }

    private void handlePreview(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String contentType = req.getContentType();
        String text;
        String source = "paste";
        if (contentType != null && contentType.toLowerCase().startsWith("multipart/")) {
            MultipartText multipartText = readMultipartText(req);
            text = multipartText.text;
            source = multipartText.source;
        } else {
            AgentImportPreviewRequest request = reqBodyToObj(req, AgentImportPreviewRequest.class);
            text = request == null ? null : request.getText();
            if (request != null && request.getSource() != null) {
                source = request.getSource();
            }
        }
        responsePrint(resp, toJson(importService.preview(text, source)));
    }

    private void handleCommit(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        AgentImportCommitRequest request = reqBodyToObj(req, AgentImportCommitRequest.class);
        responsePrint(resp, toJson(importService.commit(request)));
    }

    private MultipartText readMultipartText(HttpServletRequest req) throws Exception {
        DiskFileItemFactory factory = new DiskFileItemFactory();
        ServletFileUpload upload = new ServletFileUpload(factory);
        upload.setFileSizeMax(MAX_UPLOAD_SIZE);
        upload.setSizeMax(MAX_UPLOAD_SIZE);
        List<?> fileItems = upload.parseRequest(req);
        MultipartText result = new MultipartText();
        result.source = "txt";
        StringBuilder text = new StringBuilder();
        for (Object fileItem : fileItems) {
            FileItem item = (FileItem) fileItem;
            if (item.isFormField()) {
                if ("source".equals(item.getFieldName())) {
                    result.source = item.getString("UTF-8");
                } else if ("text".equals(item.getFieldName())) {
                    text.append(item.getString("UTF-8"));
                }
                continue;
            }
            text.append(new String(item.get(), StandardCharsets.UTF_8));
        }
        result.text = text.toString();
        return result;
    }

    private void writeJsonError(HttpServletResponse resp, int status, String message) throws IOException {
        resp.setStatus(status);
        Map<String, Object> map = new HashMap<>();
        map.put("status", "failed");
        map.put("message", message);
        responsePrint(resp, toJson(map));
    }

    private static class MultipartText {
        private String text;
        private String source;
    }
}

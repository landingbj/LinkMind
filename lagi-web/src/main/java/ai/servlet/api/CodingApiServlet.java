package ai.servlet.api;

import ai.servlet.BaseServlet;
import ai.utils.AiGlobal;
import ai.utils.MigrateGlobal;
import ai.workflow.CodeWorkflowGenerator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

@Slf4j
public class CodingApiServlet extends BaseServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setHeader("Content-Type", "application/json;charset=utf-8");
        String url = req.getRequestURI();
        String method = url.substring(url.lastIndexOf("/") + 1);
        if (method.equals("code2FlowSchema")) {
            this.code2FlowSchema(req, resp);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setHeader("Content-Type", "application/json;charset=utf-8");
        String url = req.getRequestURI();
        String method = url.substring(url.lastIndexOf("/") + 1);
    }

    private void code2FlowSchema(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<>();

        try {
            // Check if the request is multipart/form-data
            if (!ServletFileUpload.isMultipartContent(req)) {
                result.put("status", "failed");
                result.put("msg", "Request must be multipart/form-data");
                responsePrint(resp, toJson(result));
                return;
            }

            // Setup file upload handler
            DiskFileItemFactory factory = new DiskFileItemFactory();
            ServletFileUpload upload = new ServletFileUpload(factory);
            upload.setHeaderEncoding("UTF-8");
            upload.setFileSizeMax(MigrateGlobal.DOC_FILE_SIZE_LIMIT);
            upload.setSizeMax(MigrateGlobal.DOC_FILE_SIZE_LIMIT);

            // Get upload directory
            String uploadDir = getServletContext().getRealPath(AiGlobal.DIR_TEMP);
            if (!new File(uploadDir).isDirectory()) {
                new File(uploadDir).mkdirs();
            }

            // Parse form data
            String knowledgeBase = null;
            String message = null;
            List<File> uploadedFiles = new ArrayList<>();
            List<Map<String, String>> filesInfo = new ArrayList<>();

            // First loop: Save uploaded files and collect file info
            List<?> fileItems = upload.parseRequest(req);
            for (Object fileItem : fileItems) {
                FileItem item = (FileItem) fileItem;

                if (item.isFormField()) {
                    // Handle form fields
                    String fieldName = item.getFieldName();
                    String fieldValue = item.getString("UTF-8");

                    if ("knowledgeBase".equals(fieldName)) {
                        knowledgeBase = fieldValue;
                    } else if ("message".equals(fieldName)) {
                        message = fieldValue;
                    }
                } else {
                    // Handle file fields
                    String fieldName = item.getFieldName();
                    if ("files".equals(fieldName)) {
                        String originalFileName = item.getName();
                        if (originalFileName != null && !originalFileName.isEmpty()) {
                            // Generate unique file name
                            String fileExtension = getFileExtension(originalFileName);
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
                            String newFileName = sdf.format(new Date()) + ("" + Math.random()).substring(2, 8) + "." + fileExtension;
                            File savedFile = new File(uploadDir + File.separator + newFileName);
                            item.write(savedFile);
                            uploadedFiles.add(savedFile);

                            // Build file info
                            Map<String, String> fileInfo = new HashMap<>();
                            fileInfo.put("fileName", originalFileName);
                            fileInfo.put("extension", fileExtension);
                            fileInfo.put("size", String.valueOf(savedFile.length()));
                            filesInfo.add(fileInfo);

                            log.info("Saved file: {}", originalFileName);
                        }
                    }
                }
            }

            if (uploadedFiles.isEmpty()) {
                result.put("status", "failed");
                result.put("msg", "No files uploaded");
                responsePrint(resp, toJson(result));
                return;
            }

            log.info("Received request - knowledgeBase: {}, description: {}, files: {}", 
                    knowledgeBase, message, uploadedFiles.size());

            // Generate workflow schema from code
            CodeWorkflowGenerator.CodeFlowResult workflowResult = CodeWorkflowGenerator.code2FlowSchema(uploadedFiles, filesInfo, knowledgeBase, message);

            // Clean up uploaded files
            for (File file : uploadedFiles) {
                try {
                    if (file.exists()) {
                        file.delete();
                    }
                } catch (Exception e) {
                    log.error("Error deleting temp file: {}", file.getName(), e);
                }
            }

            // Detect missing information returned by generator
            if (workflowResult.getMissingInfoMessage() != null && !workflowResult.getMissingInfoMessage().trim().isEmpty()) {
                String msg = workflowResult.getMissingInfoMessage().trim();
                result.put("status", "failed");
                result.put("msg", msg.isEmpty() ? "Missing required information for business logic extraction" : msg);
            } else {
                // Build success response
                result.put("status", "success");
                result.put("data", workflowResult.getData());
            }
        } catch (Exception e) {
            log.error("Error in code2FlowSchema", e);
            result.put("status", "failed");
            result.put("msg", e.getMessage());
        }

        responsePrint(resp, toJson(result));
    }

    /**
     * Get file extension
     *
     * @param fileName the file name
     * @return file extension (without dot)
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1);
    }
}

package ai.servlet.passenger;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

public class OssUtil {

    /**
     * 上传文件到OSS（使用默认配置）
     * @param file 文件
     * @param fileName 文件名
     * @return OSS文件URL
     * @throws IOException 上传异常
     */
    public static String uploadFile(File file, String fileName) throws IOException {
        return uploadFile(file, fileName, null, null, null);
    }

    /**
     * 上传文件到OSS（指定目录）
     * @param file 文件
     * @param fileName 文件名
     * @param dir 目录
     * @return OSS文件URL
     * @throws IOException 上传异常
     */
    public static String uploadFile(File file, String fileName, String dir) throws IOException {
        return uploadFile(file, fileName, dir, null, null);
    }

    /**
     * 上传文件到OSS（完整配置）
     * @param file 文件
     * @param fileName 文件名
     * @param dir 目录
     * @param groupId 分组ID
     * @param type 文件类型
     * @return OSS文件URL
     * @throws IOException 上传异常
     */
    public static String uploadFile(File file, String fileName, String dir, String groupId, String type) throws IOException {
        String url = Config.OSS_UPLOAD_URL;
        
        // 使用默认值
        if (dir == null) {
            dir = "PassengerFlowRecognition";
        }
        if (groupId == null) {
            groupId = Config.OSS_GROUP_ID_IMAGE; // 默认使用图片配置
        }
        if (type == null) {
            type = Config.OSS_TYPE_IMAGE; // 默认使用图片配置
        }
        
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpPost httppost = new HttpPost(url);

            HttpEntity reqEntity = MultipartEntityBuilder.create()
                    .addBinaryBody("file", file)
                    .addTextBody("fileName", fileName)
                    .addTextBody("dir", dir)
                    .addTextBody("groupId", groupId)
                    .addTextBody("type", type)
                    .build();
            httppost.setEntity(reqEntity);

            try (CloseableHttpResponse response = httpclient.execute(httppost)) {
                HttpEntity resEntity = response.getEntity();
                if (resEntity != null) {
                    String responseString = EntityUtils.toString(resEntity);
                    JSONObject jsonResponse = new JSONObject(responseString);
                    if (jsonResponse.optBoolean("ok") && jsonResponse.optInt("code") == 0) {
                        JSONObject data = jsonResponse.getJSONObject("data");
                        String ossFileName = data.getString("fileName");
                        return Config.OSS_FILE_BASE_URL + "/admin/sys-file/oss/file?fileName=" + ossFileName;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 上传图片文件到OSS
     * @param file 文件
     * @param fileName 文件名
     * @param dir 目录
     * @return OSS文件URL
     * @throws IOException 上传异常
     */
    public static String uploadImageFile(File file, String fileName, String dir) throws IOException {
        return uploadFile(file, fileName, dir, Config.OSS_GROUP_ID_IMAGE, Config.OSS_TYPE_IMAGE);
    }

    /**
     * 上传视频文件到OSS
     * @param file 文件
     * @param fileName 文件名
     * @param dir 目录
     * @return OSS文件URL
     * @throws IOException 上传异常
     */
    public static String uploadVideoFile(File file, String fileName, String dir) throws IOException {
        return uploadFile(file, fileName, dir, Config.OSS_GROUP_ID_VIDEO, Config.OSS_TYPE_VIDEO);
    }
}
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
    public static String uploadFile(File file, String fileName) throws IOException {
        String url = Config.OSS_UPLOAD_URL;
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpPost httppost = new HttpPost(url);
            HttpEntity reqEntity = MultipartEntityBuilder.create()
                    .addBinaryBody("file", file)
                    .addTextBody("fileName", fileName)
                    .addTextBody("dir", Config.OSS_DIR)
                    .addTextBody("groupId", Config.OSS_GROUP_ID)
                    .addTextBody("type", Config.OSS_TYPE)
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
        throw new IOException("Failed to upload file to OSS");
    }
}
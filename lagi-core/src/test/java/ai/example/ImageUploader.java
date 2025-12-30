package ai.example;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;

public class ImageUploader {

    public static void main(String[] args) throws IOException {
        String imagePath = "C:\\Users\\24175\\Pictures\\TST.png";
        String boundary = "===" + System.currentTimeMillis() + "===";
        String LINE_FEED = "\r\n";

        URL url = new URL("http://localhost:8123/process-image");
        HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
        httpConn.setUseCaches(false);
        httpConn.setDoOutput(true); // indicates POST method
        httpConn.setDoInput(true);
        httpConn.setRequestMethod("POST");
        httpConn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        OutputStream outputStream = httpConn.getOutputStream();
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, "UTF-8"), true);

        // Add file part
        File uploadFile = new File(imagePath);
        String fileName = uploadFile.getName();
        writer.append("--" + boundary).append(LINE_FEED);
        writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"").append(LINE_FEED);
        writer.append("Content-Type: " + Files.probeContentType(uploadFile.toPath())).append(LINE_FEED);
        writer.append(LINE_FEED).flush();

        Files.copy(uploadFile.toPath(), outputStream);
        outputStream.flush();
        writer.append(LINE_FEED).flush();

        // End of multipart/form-data
        writer.append("--" + boundary + "--").append(LINE_FEED);
        writer.close();

        // Read response
        int responseCode = httpConn.getResponseCode();
        System.out.println("Response Code: " + responseCode);

        BufferedReader in = new BufferedReader(new InputStreamReader(
                httpConn.getInputStream()));
        String inputLine;
        StringBuffer response = new StringBuffer();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        System.out.println("Response: " + response.toString());
        // Background information as a constant
    }
}

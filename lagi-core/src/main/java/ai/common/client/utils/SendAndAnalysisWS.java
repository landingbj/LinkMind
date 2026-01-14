/*
 * This program is commercial software; you can only redistribute it and/or modify
 * it under the WARRANTY of Beijing Landing Technologies Co. Ltd.
 *
 * You should have received a copy license along with this program;
 * If not, write to Beijing Landing Technologies, service@landingbj.com.
 */

/*
 * SendAndAnalysisWS.java
 * Copyright (C) 2018 Beijing Landing Technologies, China
 */

package ai.common.client.utils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import org.slf4j.Logger;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Namespace;
import org.jdom2.input.SAXBuilder;
import org.slf4j.LoggerFactory;

public class SendAndAnalysisWS {

    static Namespace S = Namespace.getNamespace("S", "http://schemas.xmlsoap.org/soap/envelope/");
    static Namespace ns2 = Namespace.getNamespace("ns2", "http://service.ai/");

    public static InputStreamReader sendSoap(String endpoint, String xml) {

        URL url = null;
        HttpURLConnection httpConn = null;
        OutputStream out = null;
        InputStreamReader in = null;
        try {
            url = new URL(endpoint);
            httpConn = (HttpURLConnection) url.openConnection();
            httpConn.setRequestProperty("Content-Type", "text/xml; charset=utf-8");
            httpConn.setRequestMethod("POST");
            httpConn.setDoOutput(true);
            httpConn.setDoInput(true);
            httpConn.connect();
            out = httpConn.getOutputStream(); //获取输出流对象
            httpConn.getOutputStream().write(xml.getBytes("UTF-8")); //将要提交服务器的SOAP请求字符流写入输出流
            out.flush();
            out.close();
            int code = httpConn.getResponseCode(); //用来获取服务器响应状态
            if (code == HttpURLConnection.HTTP_OK) {
                in = new InputStreamReader(httpConn.getInputStream(), "UTF-8");
            }
            else {
                Logger logger = LoggerFactory.getLogger(SendAndAnalysisWS.class.getName());
                logger.error("soap响应报文错误!");
            }

        }
        catch (MalformedURLException e) {
            e.printStackTrace();
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        return in;
    }

    public static String[] getReturnSoap(InputStreamReader reader, String methodName) {
        SAXBuilder sb = new SAXBuilder();
        String[] result = null;
        try {
            Document doc = sb.build(reader);
            Element body = doc.getRootElement().getChild("Body", S);
            Element uploadImg = body.getChild(methodName + "Response", ns2);
            List<Element> rets = uploadImg.getChildren("return");
            result = new String[rets.size()];
            for (int i = 0; i < result.length; i++) {
                result[i] = rets.get(i).getTextTrim();
            }
        }
        catch (JDOMException e) {
            e.printStackTrace();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }
}

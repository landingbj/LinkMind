/*
 * This program is commercial software; you can only redistribute it and/or modify
 * it under the WARRANTY of Beijing Landing Technologies Co. Ltd.
 *
 * You should have received a copy license along with this program;
 * If not, write to Beijing Landing Technologies, service@landingbj.com.
 */

/*
 * ReqAndResSoapMessage.java
 * Copyright (C) 2018 Beijing Landing Technologies, China
 */

package ai.common.client.utils;

import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.soap.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class ReqAndResSoapMessage {

	public static List<Object> getEnvelope(){

		List<Object> list = new ArrayList<Object>();
		try {
			// 创建连接
			SOAPConnectionFactory soapConnFactory = SOAPConnectionFactory.newInstance();
			SOAPConnection connection = soapConnFactory.createConnection();

			//  创建消息对象
			MessageFactory messageFactory = MessageFactory.newInstance();
			SOAPMessage message = messageFactory.createMessage();
			message.setProperty(SOAPMessage.CHARACTER_SET_ENCODING, "UTF-8");

			// 创建soap消息主体
			SOAPPart soapPart = message.getSOAPPart();
			SOAPEnvelope envelope = soapPart.getEnvelope();
			//空间命名暂时写死，如有后面有需要再通过发布端口解析获取
			envelope.addNamespaceDeclaration("q0", "http://service.ai/");
			SOAPBody body = envelope.getBody();
			list.add(body);
			list.add(connection);
			list.add(message);
		} catch (UnsupportedOperationException e) {
			e.printStackTrace();
		} catch (SOAPException e) {
			e.printStackTrace();
		}
		return list;
	}

	/**
	 * 用于将图片转换为字节流，然后通过base64加密
	 * @param source
	 * @return
	 */
	public static String getImgBytes(File source){

		byte[] data = null;
		FileInputStream in;
		try {
			in = new FileInputStream(source);
			data = new byte[in.available()];
			in.read(data);
			in.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		//对字节数组Base64编码
		return Base64.getEncoder().encodeToString(data);//返回Base64编码过的字节数组字符串

	}

	/**
	 * 解析相应soap消息的xml数据格式
	 * @param reply xml格式数据
	 * @return
	 */
	public static String[] responseMessage(SOAPMessage reply) {

		String[] result=null;
		try {
			Document doc = reply.getSOAPPart().getEnvelope().getBody().extractContentAsDocument();
			NodeList list = doc.getElementsByTagName("return");
			result=new String[list.getLength()];
			for(int i=0;i<list.getLength();i++){
				result[i]=list.item(i).getTextContent();
			}
		} catch (DOMException e) {
			e.printStackTrace();
		} catch (SOAPException e) {
			e.printStackTrace();
		}
		return result;
	}
}

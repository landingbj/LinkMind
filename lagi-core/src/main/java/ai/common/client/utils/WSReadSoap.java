/*
 * This program is commercial software; you can only redistribute it and/or modify
 * it under the WARRANTY of Beijing Landing Technologies Co. Ltd.
 *
 * You should have received a copy license along with this program;
 * If not, write to Beijing Landing Technologies, service@landingbj.com.
 */

/*
 * WSReadSoap.java
 * Copyright (C) 2018 Beijing Landing Technologies, China
 */

package ai.common.client.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import java.util.Base64;

public class WSReadSoap {

	String headerStr = "<soapenv:Envelope xmlns:q0=\"http://service.ai/\""
			+" xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\""
			+" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\""
			+" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">"
			+"<soapenv:Header></soapenv:Header><soapenv:Body>";

	String endStr = "</soapenv:Body></soapenv:Envelope>";

	public String getImgSoap(String methodName, Object[] params)
	{
		StringBuffer sb = new StringBuffer();
		sb.append(headerStr+"<q0:"+methodName+">");

		for(int i=0; i<params.length; i++){
			if(params[i].getClass().isArray())
			{
				for (Object object : (String[])params[i]) {
					sb.append("<arg"+i+">"+(String)object+"</arg"+i+">");
				}
			}
			else if(params[i] instanceof List)
			{
				for (Object object : (List<?>)params[i]) {
					sb.append("<arg"+i+">"+(String)object+"</arg"+i+">");
				}
			}
			else if(params[i] instanceof Set)
			{
				for (Object object : (Set<?>)params[i]) {
					sb.append("<arg"+i+">"+(String)object+"</arg"+i+">");
				}
			}
			else {
				sb.append("<arg"+i+">"+String.valueOf(params[i])+"</arg"+i+">");
			}
		}
		sb.append("</q0:"+methodName+">"+endStr);

		return sb.toString();
	}

	public String getImgBytes(File source){

		byte[] data = null;
		FileInputStream in;
		try {
			in = new FileInputStream(source);
			data = new byte[in.available()];
			in.read(data);
			in.close();
		}
		catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		//对字节数组Base64编码
		return Base64.getEncoder().encodeToString(data);//返回Base64编码过的字节数组字符串
	}

}

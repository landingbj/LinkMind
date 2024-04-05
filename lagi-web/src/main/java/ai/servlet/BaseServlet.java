/*
 * This program is commercial software; you can only redistribute it and/or modify
 * it under the WARRANTY of Beijing Landing Technologies Co. Ltd.
 *
 * You should have received a copy license along with this program;
 * If not, write to Beijing Landing Technologies, service@landingbj.com.
 */

/*
 * RpaServlet.java
 * Copyright (C) 2020 Beijing Landing Technologies, China
 */

/**
 * 
 */

package ai.servlet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

public abstract class BaseServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	protected Gson gson = new Gson();

	protected <T> T queryToObj(HttpServletRequest req, Class<T> classOfT) throws IOException {
		Map<String, String> query = getQueryData(req);
		JsonElement jsonElement = gson.toJsonTree(query);
		T obj = gson.fromJson(jsonElement, classOfT);
		return obj;
	}
	
	protected <T> T postQueryToObj(HttpServletRequest req, Class<T> classOfT) throws IOException {
		Map<String, String> query = getPostQueryData(req);
		JsonElement jsonElement = gson.toJsonTree(query);
		T obj = gson.fromJson(jsonElement, classOfT);
		return obj;
	}


	protected <T> T reqBodyToObj(HttpServletRequest req, Class<T> classOfT) throws IOException {
		T obj = gson.fromJson(requestToJson(req), classOfT);
		return obj;
	}

	protected String requestToJson(HttpServletRequest request) throws IOException {
		InputStream in = request.getInputStream();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buffer = new byte[1024];
		int len = 0;
		while ((len = in.read(buffer)) != -1) {
			out.write(buffer, 0, len);
		}
		out.close();
		in.close();

		String json = new String(out.toByteArray(), "utf-8");
		return json;
	}

	protected Map<String, String> getQueryData(HttpServletRequest req) throws IOException {
		Map<String, String> data = new HashMap<>();
		Enumeration<String> paraEnum = req.getParameterNames();
		while (paraEnum.hasMoreElements()) {
			String paramName = (String) paraEnum.nextElement();
			String paramValue = req.getParameter(paramName);
			data.put(paramName, paramValue);
		}
		return data;
	}
	
	protected Map<String, String> getPostQueryData(HttpServletRequest req) throws IOException {
		Map<String, String> data = new HashMap<>();
		Enumeration<String> paraEnum = req.getParameterNames();
		while (paraEnum.hasMoreElements()) {
			String paramName = (String) paraEnum.nextElement();
			String paramValue = req.getParameter(paramName);
			data.put(paramName, paramValue);
		}
		return data;
	}

	protected void responsePrint(HttpServletResponse resp, String ret) throws IOException {
		PrintWriter out = resp.getWriter();
		out.print(ret);
		out.flush();
		out.close();
	}

	protected String toJson(Object obj) {
		return gson.toJson(obj);
	}
}

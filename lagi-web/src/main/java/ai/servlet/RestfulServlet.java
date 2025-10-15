package ai.servlet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


import ai.common.exception.RRException;
import ai.servlet.annotation.Body;
import ai.servlet.annotation.Get;
import ai.servlet.annotation.Param;
import ai.servlet.annotation.PathParam;
import ai.servlet.annotation.Post;
import ai.response.RestfulResponse;
import ai.utils.StringUtils;
import cn.hutool.core.convert.Convert;

/**
 * @program: RestfulServlet
 * @description: restful response servlet
 * @author: linzhen
 * @create: 2023-06-29 09:00
 **/
public class RestfulServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    private Map<String, Method> registerGetMethod = new ConcurrentHashMap<>();
    private Map<String, Method> registerPostMethod = new ConcurrentHashMap<>();

    @Override
    public void init() throws ServletException {
        super.init();
        Class<? extends RestfulServlet> clazz = this.getClass();
        Method[] methods = clazz.getDeclaredMethods();
        for (Method method : methods) {
            if (method.isAnnotationPresent(Get.class)) {
                Get get = method.getAnnotation(Get.class);
                // 标准化路径，确保以斜杠开头
                String path = normalizePath(get.value());
                registerGetMethod.put(path, method);
                continue;
            }
            if (method.isAnnotationPresent(Post.class)) {
                Post post = method.getAnnotation(Post.class);
                // 标准化路径，确保以斜杠开头
                String path = normalizePath(post.value());
                registerPostMethod.put(path, method);
                continue;
            }
        }
    }

    /**
     * 标准化路径，确保以斜杠开头
     */
    private String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private void doRequest(HttpServletRequest req, HttpServletResponse resp, Map<String, Method> map)
            throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setHeader("Content-Type", "application/json;charset=utf-8");

        // 获取servlet映射的路径部分（即/agent/*中的*部分）
        String pathInfo = req.getPathInfo();
        // 默认为根路径
        String requestPath = (pathInfo != null) ? pathInfo : "/";

        Method mth = null;
        Map<String, String> pathParams = null;

        // 1. 先尝试精确匹配
        mth = map.get(requestPath);

        // 2. 如果精确匹配失败，尝试路径参数匹配
        if (mth == null) {
            for (Map.Entry<String, Method> entry : map.entrySet()) {
                String template = entry.getKey();
                Map<String, String> params = matchPath(template, requestPath);
                if (params != null) {
                    mth = entry.getValue();
                    pathParams = params;
                    break;
                }
            }
        }

        // 3. 最后尝试原始的通过方法名匹配（取路径最后一段）
        if (mth == null) {
            String methodName = requestPath.substring(requestPath.lastIndexOf("/") + 1);
            // 尝试匹配方法名（不带路径）
            mth = map.get("/" + methodName);
            if (mth == null) {
                mth = map.get(methodName);
            }
        }

        try {
            if (mth == null) {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            // 动态处理参数
            Parameter[] parameters = mth.getParameters();
            Object[] pInst = parseParams(req, resp, parameters, pathParams);
            Object o = mth.invoke(this, pInst);

            // 返回值是void则不包装结果
            if (!mth.getReturnType().equals(Void.TYPE)) {
                responsePrint(resp, toJson(RestfulResponse.sucecced(o)));
            }

        } catch (IllegalAccessException e) {
            e.printStackTrace();
            responsePrint(resp, toJson(RestfulResponse.error("服务器内部错误请联系管理员-001")));
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            responsePrint(resp, toJson(RestfulResponse.error("服务器内部错误请联系管理员-002")));
        } catch (InvocationTargetException e) {

            // 处理已知异常
            if (e.getCause() instanceof ServletException) {
                throw (ServletException) e.getCause();
            } else if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            } else if (e.getCause() instanceof RRException) {
                RRException re = (RRException) e.getCause();
                resp.setContentType("application/json;charset=utf-8");
                responsePrint(resp, toJson(RestfulResponse.error(re.getCode(), re.getMsg())));
                re.printStackTrace();
                return;
            }
            e.printStackTrace();
            responsePrint(resp, toJson(RestfulResponse.error("服务器内部错误请联系管理员-003")));
        }
    }

    /**
     * 路径匹配方法，检查请求路径是否与模板匹配，并提取路径参数
     * @param template 路径模板，如"/user/{id}"
     * @param requestPath 请求路径，如"/user/123"
     * @return 路径参数映射，如果不匹配返回null
     */
    private Map<String, String> matchPath(String template, String requestPath) {
        // 处理根路径情况
        if (template.equals("/") && requestPath.equals("/")) {
            return new HashMap<>();
        }

        // 分割路径为 segments
        String[] templateSegments = template.split("/");
        String[] requestSegments = requestPath.split("/");

        // 过滤空字符串（处理连续斜杠的情况）
        List<String> templateList = new ArrayList<>();
        for (String s : templateSegments) {
            if (!s.isEmpty()) {
                templateList.add(s);
            }
        }

        List<String> requestList = new ArrayList<>();
        for (String s : requestSegments) {
            if (!s.isEmpty()) {
                requestList.add(s);
            }
        }

        // 路径段数量不同，直接不匹配
        if (templateList.size() != requestList.size()) {
            return null;
        }

        // 提取路径参数
        Map<String, String> pathParams = new HashMap<>();
        for (int i = 0; i < templateList.size(); i++) {
            String tSegment = templateList.get(i);
            String rSegment = requestList.get(i);

            // 检查是否是路径参数模板 {name}
            if (tSegment.startsWith("{") && tSegment.endsWith("}")) {
                String paramName = tSegment.substring(1, tSegment.length() - 1);
                pathParams.put(paramName, rSegment);
            } else if (!tSegment.equals(rSegment)) {
                // 非参数部分必须完全匹配
                return null;
            }
        }

        return pathParams;
    }

    private Object[] parseParams(HttpServletRequest req, HttpServletResponse resp, Parameter[] params, Map<String, String> pathParams) {
        List<Object> pIns = new ArrayList<>(params.length);
        for (int i = 0; i < params.length; i++) {
            Parameter param = params[i];
            if (param.getType() == HttpServletRequest.class) {
                pIns.add(req);
                continue;
            }
            if (param.getType() == HttpServletResponse.class) {
                pIns.add(resp);
                continue;
            }
            // 处理路径参数
            if (param.isAnnotationPresent(PathParam.class)) {
                PathParam pathParam = param.getAnnotation(PathParam.class);
                String paramName = pathParam.value();
                String paramValue = pathParams != null ? pathParams.get(paramName) : null;
                Object o = convertQuery(paramValue, param.getType());
                pIns.add(o);
                continue;
            }
            // 处理请求参数
            if (param.isAnnotationPresent(Param.class)) {
                Param p = param.getAnnotation(Param.class);
                String pStr = req.getParameter(p.value());
                if (StringUtils.isBlank(pStr)) {
                    pStr = p.def();
                }
                Object o = convertQuery(pStr, param.getType());
                pIns.add(o);
                continue;
            }
            // 处理请求体
            if (param.isAnnotationPresent(Body.class)) {
                pIns.add(convertBody(req, param.getType()));
                continue;
            }

            pIns.add(null);
        }
        return pIns.toArray();
    }

    private Object convertQuery(String pStr, Class<?> type) {
        // 允许空值转换
        if (pStr == null) {
            return null;
        }

        try {
            if (type == Integer.class) {
                return Integer.parseInt(pStr);
            }
            if (type == Double.class) {
                return Double.parseDouble(pStr);
            }
            if (type == Long.class) {
                return Long.parseLong(pStr);
            }
            if (type == String.class) {
                return pStr;
            }
            return Convert.convert(type, pStr);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private Object convertBody(HttpServletRequest req, Class<?> type) {
        try {
            return reqBodyToObj(req, type);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        doRequest(req, resp, registerGetMethod);
    }


    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if(needForward()) {
            forwardRequest(req, resp, req.getMethod());
        } else {
            doRequest(req, resp, registerPostMethod);
        }
    }


    protected boolean needForward() {
        return false;
    }

    protected void forwardRequest(HttpServletRequest request, HttpServletResponse response, String method) throws IOException {

    }

    protected byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[16384];
        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        buffer.flush();
        return buffer.toByteArray();
    }

}

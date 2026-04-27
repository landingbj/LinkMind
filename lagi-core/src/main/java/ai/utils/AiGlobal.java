/*
 * This program is commercial software; you can only redistribute it and/or modify
 * it under the WARRANTY of Beijing Landing Technologies Co. Ltd.
 *
 * You should have received a copy license along with this program;
 * If not, write to Beijing Landing Technologies, service@landingbj.com.
 */

/*
 * AiGlobal.java
 * Copyright (C) 2018 Beijing Landing Technologies, China
 */

package ai.utils;

public class AiGlobal {
    public static final int AI_SERVICE_BY_API = 1;
    public static final int AI_SERVICE_BY_MSG = 2;
    public static final int AI_SERVICE_BY_LOCAL = 3;
    public static final int AI_SERVICE_TYPE = AI_SERVICE_BY_API;
    public static final String CALL_METHOD_NOT_FOUND = "调用方式错误";
    public static final String DUPLICATE_TOKEN_TIME = "t";
    public static final String DUPLICATE_TOKEN_RANDOM = "r";
    public static String DIR_TEMP = "/WEB-INF/temp";
//    public static final String SAAS_URL = "https://saas.landingbj.com";
    public static final String SAAS_URL = "http://localhost:8090";
    public static final String DEFAULT_DB = "saas";
}

/*
 * This program is commercial software; you can only redistribute it and/or modify
 * it under the WARRANTY of Beijing Landing Technologies Co. Ltd.
 *
 * You should have received a copy license along with this program;
 * If not, write to Beijing Landing Technologies, service@landingbj.com.
 */

/*
 * AiServiceCallByLocal.java
 * Copyright (C) 2018 Beijing Landing Technologies, China
 */

package ai.common.client.impl;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import ai.utils.AiGlobal;

/**
 * @author GeorgeDai
 *
 */
public class AiServiceCallByLocal {

	static int type = AiGlobal.AI_SERVICE_TYPE;

	/**
	 * 通过本地调用
	 *
	 * @param className
	 *            调用的完整类名
	 * @param methodName
	 *            调用的方法名
	 * @param params
	 *            调用的方法参数
	 * @return
	 */
	public static String[] callWSByLocal(String className, String methodName,
										 Object[] params) {

		String[] returnObj = null;
		Object[] actualParams = null;
		int length = 0;
		// 获取参数的有效个数
		if (params != null) {
			for (int i = 0; i < params.length; i++) {
				if (params[i] != null) {
					length++;
				}
			}
		}
		try {
			Class<?> cls = Class.forName(className);
			// 获取参数类型
			Class<?>[] parameterTypes = new Class[length];
			if (length > 0) {
				actualParams = new Object[length];
				for (int i = 0; i < length; i++) {
					if ("java.lang.Integer".equals(params[i].getClass()
							.getName())) {
						parameterTypes[i] = int.class;
					} else if ("java.lang.Long".equals(params[i].getClass()
							.getName())) {
						parameterTypes[i] = long.class;
					} else if ("java.lang.Double".equals(params[i].getClass()
							.getName())) {
						parameterTypes[i] = double.class;
					} else if ("java.util.ArrayList".equals(params[i]
							.getClass().getName())) {
						parameterTypes[i] = List.class;
					} else {
						parameterTypes[i] = params[i].getClass();
					}
					// 给实际参数赋值
					actualParams[i] = params[i];
				}
			}
			Method method = cls.getDeclaredMethod(methodName, parameterTypes);
			returnObj = (String[]) method.invoke(cls.getDeclaredConstructor().newInstance(),
					actualParams);

		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (InstantiationException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		}

		return returnObj;
	}

}

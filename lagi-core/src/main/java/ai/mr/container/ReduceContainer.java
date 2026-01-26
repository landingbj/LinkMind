/*
 * This program is commercial software; you can only redistribute it and/or modify
 * it under the WARRANTY of Beijing Landing Technologies Co. Ltd.
 *
 * You should have received a copy license along with this program;
 * If not, write to Beijing Landing Technologies, service@landingbj.com.
 */

/*
 * BaseContainer.java
 * Copyright (C) 2018 Beijing Landing Technologies, China
 */

package ai.mr.container;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import ai.mr.AiGlobalMR;
import ai.mr.IMapper;
import ai.mr.IRContainer;
import ai.mr.IReducer;
import ai.mr.IShuffler;
import ai.mr.threads.IRMapperThread;
import ai.mr.threads.MapperThread;

public class ReduceContainer extends BaseContainer		{
	
	private static boolean _DEBUG_1 = false;
	private static boolean _DEBUG_2 = false;
	private static boolean _DEBUG_3 = false;
	
	static 	{
		if(AiGlobalMR._DEBUG_LEVEL >= 3)	{
			_DEBUG_3 = true;
		}
		if(AiGlobalMR._DEBUG_LEVEL >= 2)	{
			_DEBUG_2 = true;
		}
		if(AiGlobalMR._DEBUG_LEVEL >= 1)	{
			_DEBUG_1 = true;
		}
	}
	
	public ReduceContainer() {
	}
	
	public ReduceContainer(int cpuCores) {
		super(cpuCores);
	}
	
	//该Map集合里存放的是：reducer的名称和对应的reducer实现类
	protected IReducer  reducerGroup  = null;   
	//该Map集合里存放的是：shuffler的名称和对应的shuffler实现类
	protected HashMap<String, IShuffler> shufflersGroup = new HashMap<>();   
	
	public IMapper registMapper(String mapperName, int concurrNum, Map<String, ?> parameters)
	{
		IMapper result = null;
		
		try	{
			//通过mapper类的名称获得mapper类的一个实例
			IMapper mapper = (IMapper) Class.forName(mapperName).getConstructor().newInstance();
			// 将mapper的data属性注入进去，将该类对应的查询参数Map集合注入进去
			mapper.setParameters(parameters); 
	
			registerMapper(mapper);
			result = mapper;
		}
		catch (InstantiationException e) {
			e.printStackTrace();
		} 
		catch (IllegalAccessException e) {	
			e.printStackTrace();
		} 
		catch (ClassNotFoundException e) {	
			e.printStackTrace();
		}
		catch (NoSuchMethodException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		}
		
		return result;
	}

	
	public IShuffler registShuffler(String shufflerName, int concurrNum, Map<String, ?> parameters)
	{
		IShuffler result = null;
		
		try	{
			//通过mapper类的名称获得mapper类的一个实例
			IShuffler shuffler = (IShuffler) Class.forName(shufflerName).getDeclaredConstructor().newInstance();
			// 将mapper的data属性注入进去，将该类对应的查询参数Map集合注入进去
			shuffler.setParameters(parameters); 
	
			shufflersGroup.put(shufflerName, shuffler);//将mapper名称和对应的mapper类放到新map集合里
			result = shuffler;
		}
		catch (InstantiationException e) {
			e.printStackTrace();
		} 
		catch (IllegalAccessException e) {	
			e.printStackTrace();
		} 
		catch (ClassNotFoundException e) {	
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		}
		catch (InvocationTargetException e) {
			e.printStackTrace();
		}
		
		return result;
	}
	
	public IReducer registReducer(String reducerName, int concurrNum, Map<String, ?> parameters)
	{
		IReducer result = null;
		
		try	{
			//通过mapper类的名称获得mapper类的一个实例
			IReducer reducer = (IReducer) Class.forName(reducerName).getDeclaredConstructor().newInstance();
			// 将mapper的data属性注入进去，将该类对应的查询参数Map集合注入进去
			reducer.setParameters(parameters); 
	
			reducerGroup=reducer;//将mapper名称和对应的mapper类放入
			result = reducer;
		}
		catch (InstantiationException e) {
			e.printStackTrace();
		} 
		catch (IllegalAccessException e) {	
			e.printStackTrace();
		} 
		catch (ClassNotFoundException e) {	
			e.printStackTrace();
		}
		catch (NoSuchMethodException e) {
			e.printStackTrace();
		}
		catch (InvocationTargetException e) {
			e.printStackTrace();
		}
		
		return result;
	}
	
	public void registerReducer(IReducer reducer) 
	{
		reducerGroup = reducer;
		
if (_DEBUG_3) {
		System.out.println("[DEBUG-3] Reducer : " + reducerGroup);
}
	}
	
	public void setMapperPriority(String mapperName, int priority) {	
		for (Entry<String, IMapper> entry : mappersGroup.entrySet()) {
			String name = entry.getKey();
			IMapper mapper = entry.getValue();
			if (name.startsWith(mapperName)) {
				mapper.setPriority(priority);
			}
		}
	}
	
	public Map<String, MapperThread> startThread(Map<String, IMapper> mappers, IRContainer container) {
		String mapperName;
		IMapper mapper;

		Map<String, MapperThread> threadMap = new HashMap<String, MapperThread>();
		Set<String> c = mappers.keySet(); // mapper名称集合
		MapperThread mapperThread;

		Iterator<String> iterator = c.iterator();
		while (iterator.hasNext()) {
			mapperName = iterator.next();
			mapper = mappers.get(mapperName);
			mapperThread = new IRMapperThread(mapperName, mapper, container);
			threadMap.put(mapperName, mapperThread);
			submitThread(mapperThread);
		}
		return threadMap;
	}
	
	public long size() {
		return mappersGroup.size(); //线程数等于mapper类的个数，此时为X
	}

}

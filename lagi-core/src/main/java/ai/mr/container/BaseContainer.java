package ai.mr.container;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import ai.mr.AiGlobalMR;
import ai.mr.IContainer;
import ai.mr.IMapper;
import ai.mr.threads.MapperThread;

public class BaseContainer {

	private static boolean _DEBUG_1 = false;
	private static boolean _DEBUG_2 = false;
	private static boolean _DEBUG_3 = false;
	private static boolean _DEBUG_4 = false;
	private static boolean _DEBUG_5 = false;

	static {
		if (AiGlobalMR._DEBUG_LEVEL >= 5) {
			_DEBUG_5 = true;
		}
		if (AiGlobalMR._DEBUG_LEVEL >= 4) {
			_DEBUG_4 = true;
		}
		if (AiGlobalMR._DEBUG_LEVEL >= 3) {
			_DEBUG_3 = true;
		}
		if (AiGlobalMR._DEBUG_LEVEL >= 2) {
			_DEBUG_2 = true;
		}
		if (AiGlobalMR._DEBUG_LEVEL >= 1) {
			_DEBUG_1 = true;
		}
	}

	private static int _APPROACH_CPU_CORES = AiGlobalMR.CPU_CORE_COUNTS;
	private static int _APPROACH_MR_DEFAULT = AiGlobalMR.MR_AS_DEFAULT;

	// 该Map集合里存放的是：mapper的名称和对应的mapper实现类
	protected Map<String, IMapper> mappersGroup = new ConcurrentHashMap<String, IMapper>();
	private ExecutorService threadPool;
	protected long count;

	public BaseContainer() {
		this(_APPROACH_CPU_CORES);
	}
	
	public BaseContainer(int cpuCores) {
		if (_APPROACH_MR_DEFAULT == AiGlobalMR.MR_AS_EXECUTOR) {		
			threadPool = Executors.newFixedThreadPool(cpuCores);
		}
		
		count = 0;
	}

	public void registMapper(String mapperName, Map<String, ?> parameters) {
		if (_APPROACH_MR_DEFAULT == AiGlobalMR.MR_AS_EXECUTOR) {
			registMapperExecutor(mapperName, parameters);
		} else if (_APPROACH_MR_DEFAULT == AiGlobalMR.MR_AS_RAW){
			registMapperRaw(mapperName, parameters);
		}
	}
	
	public void registMapperExecutor(String mapperName, Map<String, ?> parameters) {
		try {
			// 通过mapper类的名称获得mapper类的一个实例
			IMapper mapper = (IMapper) Class.forName(mapperName).getDeclaredConstructor().newInstance();
			// 将mapper的data属性注入进去，将该类对应的查询参数Map集合注入进去
			mapper.setParameters(parameters);
			// 将mapper名称和对应的mapper类放到新map集合里
			mappersGroup.put(mapperName+Math.random()+System.currentTimeMillis(), mapper);//将mapper名称和对应的mapper类放到新map集合里
		}
		catch (InstantiationException | NoSuchMethodException | InvocationTargetException e) {
			e.printStackTrace();
		}
		catch (IllegalAccessException e) {
			e.printStackTrace();
		}
		catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
	}
	
	public void registMapperRaw(String mapperName, Map<String, ?> parameters) {
		try {
			// 通过mapper类的名称获得mapper类的一个实例
			IMapper mapper = (IMapper) Class.forName(mapperName).getDeclaredConstructor().newInstance();
			// 将mapper的data属性注入进去，将该类对应的查询参数Map集合注入进去
			mapper.setParameters(parameters);
			// 将mapper名称和对应的mapper类放到新map集合里
			String mappName = mapperName + Math.random() + System.currentTimeMillis();

			try {
				Thread.sleep(1);
			}
			catch (InterruptedException e) {
				e.printStackTrace();
			}
			mappersGroup.put(mappName, mapper);
		}
		catch (InstantiationException | NoSuchMethodException | InvocationTargetException e) {
			e.printStackTrace();
		}
		catch (IllegalAccessException e) {
			e.printStackTrace();
		}
		catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
	}

	public void registerMapper(IMapper mapper) 
	{
		mappersGroup.put(mapper.getClass().getName() + count++, mapper);
		
if(_DEBUG_5){
		System.out.printf("Mapper Size:%d\n", mappersGroup.size());
}
	}

	public Map<String, MapperThread> startThread(Map<String, IMapper> mappers, IContainer container) {
		String mapperName;
		IMapper mapper;

		Map<String, MapperThread> threadMap = new HashMap<String, MapperThread>();
		Set<String> c = mappers.keySet(); // mapper名称集合
		MapperThread mapperThread;

		Iterator<String> iterator = c.iterator();
		while (iterator.hasNext()) {
			mapperName = iterator.next();
			mapper = mappers.get(mapperName);
			mapperThread = new MapperThread(mapperName, mapper, container);
			threadMap.put(mapperName, mapperThread);
			submitThread(mapperThread);
		}
		return threadMap;
	}

	public void close() {
		if (_APPROACH_MR_DEFAULT == AiGlobalMR.MR_AS_RAW) {
			return;
		}
		threadPool.shutdown();
		try {
			while (!threadPool.isTerminated()) {
				threadPool.awaitTermination(100, TimeUnit.MILLISECONDS);
			}
		}
		catch (InterruptedException e) {
			threadPool.shutdownNow();
			return;
		}

		threadPool.shutdownNow();
		if (_DEBUG_3) {
			mappersGroup.clear();
		}
	}
	
	public void asynClose() {
		if (_APPROACH_MR_DEFAULT == AiGlobalMR.MR_AS_RAW) {
			return;
		}
		threadPool.shutdown();
	}

	public long count() {
		return count;
	}

	public static void setCPU(int cpus) {
		_APPROACH_CPU_CORES = cpus;
	}
	
	protected void submitThread(MapperThread thread) {
		if (_APPROACH_MR_DEFAULT == AiGlobalMR.MR_AS_EXECUTOR) {
			threadPool.execute(thread);
		} else if (_APPROACH_MR_DEFAULT == AiGlobalMR.MR_AS_RAW){
			new Thread(thread).start(); // 把每个线程都启动，总共有X个线程
		}
	}
	
}

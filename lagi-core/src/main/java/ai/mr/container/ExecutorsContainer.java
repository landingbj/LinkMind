package ai.mr.container;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import ai.mr.AiGlobalMR;
import ai.mr.IContainer;
import ai.mr.IMapper;
import ai.mr.threads.MapperThread;

public class ExecutorsContainer {

	private static boolean _DEBUG_1 = false;
	private static boolean _DEBUG_2 = false;
	private static boolean _DEBUG_3 = false;

	static {
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

	// 该Map集合里存放的是：mapper的名称和对应的mapper实现类
	protected HashMap<String, IMapper> mappersGroup = new HashMap<String, IMapper>();
	private ExecutorService threadPool;
	private long count;

	public ExecutorsContainer() {
		threadPool = Executors.newFixedThreadPool(_APPROACH_CPU_CORES);
		count = 0;
	}

	public void registMapper(String mapperName, Map<String, ?> parameters) {
		registMapperExecutor(mapperName, parameters);
	}

	public void registMapperExecutor(String mapperName, Map<String, ?> parameters) {
		try {
			// 通过mapper类的名称获得mapper类的一个实例
			IMapper mapper = (IMapper) Class.forName(mapperName).getDeclaredConstructor().newInstance();
			// 将mapper的data属性注入进去，将该类对应的查询参数Map集合注入进去
			mapper.setParameters(parameters);
			// 将mapper名称和对应的mapper类放到新map集合里
			mappersGroup.put(mapperName + Math.random() + System.currentTimeMillis(), mapper);// 将mapper名称和对应的mapper类放到新map集合里
		} catch (InstantiationException | NoSuchMethodException | InvocationTargetException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
	}

	public void registerMapper(IMapper mapper) {
		mappersGroup.put(mapper.getClass().getSimpleName() + count++, mapper);

		if (_DEBUG_3) {
			System.out.printf("Mapper Size:%d\n", mappersGroup.size());
		}
	}

	public Map<String, MapperThread> startThread(Map<String, IMapper> mappers, IContainer container) {
		Map<String, MapperThread> threadMap = null;
		threadMap = startThreadExecutor(mappers, container);
		return threadMap;
	}

	// 产生Mapper线程集
	public Map<String, MapperThread> startThreadExecutor(Map<String, IMapper> mappers, IContainer container) {
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
			threadPool.execute(mapperThread);
		}
		return threadMap;
	}

	public void close() {
		threadPool.shutdown();
		try {
			while (!threadPool.isTerminated()) {
				threadPool.awaitTermination(100, TimeUnit.MILLISECONDS);
			}
		} catch (InterruptedException e) {
			threadPool.shutdownNow();
			return;
		}

		threadPool.shutdownNow();
		if (_DEBUG_3) {
			mappersGroup.clear();
		}
	}

	public long count() {
		return count;
	}

	public static void setCPU(int cpus) {
		_APPROACH_CPU_CORES = cpus;
	}
}

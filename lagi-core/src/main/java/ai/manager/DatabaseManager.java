package ai.manager;


import ai.database.pojo.SQLJdbc;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class DatabaseManager {

    private static final Map<String, SQLJdbc> dbMap = new ConcurrentHashMap<>();

    private DatabaseManager() {
    }

    private static final DatabaseManager INSTANCE = new DatabaseManager();

    public static DatabaseManager getInstance() {
        return INSTANCE;
    }

    public void register(List<SQLJdbc> dbConfigs) {
        if (dbConfigs == null || dbConfigs.isEmpty()) {
            return;
        }
        dbConfigs.forEach(dbConfig -> {
            dbMap.put(dbConfig.getName(), dbConfig);
        });
    }

    public static boolean isDbExists(String dbName) {
        return dbMap.containsKey(dbName);
    }
}

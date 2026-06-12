package ai.servlet;

import ai.config.pojo.FilterConfig;

import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

public class FilterConfigService {
    public static final Map<Long, FilterConfig> filterConfigCache = ai.config.FilterConfigService.filterConfigCache;

    private FilterConfigService() {
    }

    public static List<FilterConfig> list() {
        return ai.config.FilterConfigService.list();
    }

    static List<FilterConfig> cachedList() {
        return ai.config.FilterConfigService.cachedList();
    }

    public static FilterConfig add(FilterConfig config) {
        return ai.config.FilterConfigService.add(config);
    }

    public static FilterConfig update(FilterConfig config) {
        return ai.config.FilterConfigService.update(config);
    }

    public static void delete(Long id) {
        ai.config.FilterConfigService.delete(id);
    }

    public static void refreshRuntimeFilters() {
        ai.config.FilterConfigService.refreshRuntimeFilters();
    }

    public static void validateFilterConfig(FilterConfig config) {
        ai.config.FilterConfigService.validateFilterConfig(config);
    }

    static synchronized void setConnectionFactoryForTests(Callable<Connection> factory) {
        ai.config.FilterConfigService.setConnectionFactoryForTests(factory);
    }

    static synchronized void resetForTests() {
        ai.config.FilterConfigService.resetForTests();
    }
}

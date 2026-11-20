package ai.wrapper;

import ai.common.pojo.Driver;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CommonAdapter implements IWrapper {
    @Override
    public List<Driver> getDrivers(String model) {
        return initDrivers(model);
    }

    protected Map<String, String> getModelDriverMap() {
        return null;
    }

    private List<Driver> initDrivers(String modelStr) {
        Map<String, String> modelMap = splitModels(modelStr);
        List<Driver> drivers = new ArrayList<>();
        for (Map.Entry<String, String> entry : modelMap.entrySet()) {
            String driver = entry.getKey();
            String model = entry.getValue();
            drivers.add(initDriver(model, driver));
        }
        return drivers;
    }

    private Map<String, String> splitModels(String model) {
        String[] models = (model == null ? "" : model).replace(" ", "").split(",");
        Map<String, String> driverMap = getModelDriverMap();
        String chatDriver = driverMap.values().stream()
                .filter(driver -> driver.startsWith("ai.llm.adapter.")).findFirst().orElse(null);
        Map<String, List<String>> groupedItems = new LinkedHashMap<>();
        for (String m : models) {
            String driver = driverMap.getOrDefault(m, chatDriver);
            groupedItems.computeIfAbsent(driver, k -> new ArrayList<>()).add(m);
        }
        if (chatDriver != null) groupedItems.putIfAbsent(chatDriver, new ArrayList<>());
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : groupedItems.entrySet()) {
            result.put(entry.getKey(), String.join(",", entry.getValue()));
        }
        return result;
    }

    private Driver initDriver(String model, String driver) {
        return Driver.builder()
                .model(model)
                .driver(driver)
                .build();
    }
}

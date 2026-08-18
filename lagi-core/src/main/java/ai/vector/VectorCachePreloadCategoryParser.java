package ai.vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class VectorCachePreloadCategoryParser {

    private VectorCachePreloadCategoryParser() {
    }

    static List<String> parse(String configuredCategories) {
        if (configuredCategories == null || configuredCategories.trim().isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> categories = new LinkedHashSet<>();
        for (String category : configuredCategories.split(",")) {
            String trimmed = category.trim();
            if (!trimmed.isEmpty()) {
                categories.add(trimmed);
            }
        }
        return new ArrayList<>(categories);
    }
}

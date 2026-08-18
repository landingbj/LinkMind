package ai.vector;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VectorCachePreloadCategoryParserTest {

    @Test
    void parsesMultipleCategoriesInConfiguredOrder() {
        assertEquals(Arrays.asList("airport-a", "airport-b"),
                VectorCachePreloadCategoryParser.parse(" airport-a,airport-b, airport-a, "));
    }

    @Test
    void returnsEmptyListForBlankConfiguration() {
        assertEquals(Collections.emptyList(), VectorCachePreloadCategoryParser.parse("  "));
        assertEquals(Collections.emptyList(), VectorCachePreloadCategoryParser.parse(null));
    }
}

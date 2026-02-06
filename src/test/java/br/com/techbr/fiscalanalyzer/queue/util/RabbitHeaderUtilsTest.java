package br.com.techbr.fiscalanalyzer.queue.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RabbitHeaderUtilsTest {

    @Test
    void retryCountFromXDeath() {
        Object xDeath = List.of(
                Map.of("count", 1L),
                Map.of("count", 3)
        );
        assertEquals(3, RabbitHeaderUtils.retryCountFromXDeath(xDeath));
    }
}

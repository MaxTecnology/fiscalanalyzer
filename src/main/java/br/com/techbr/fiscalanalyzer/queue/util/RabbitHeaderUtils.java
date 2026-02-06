package br.com.techbr.fiscalanalyzer.queue.util;

import java.util.List;
import java.util.Map;

public final class RabbitHeaderUtils {

    private RabbitHeaderUtils() {}

    public static int retryCountFromXDeath(Object xDeath) {
        if (!(xDeath instanceof List<?> list)) {
            return 0;
        }
        int max = 0;
        for (Object entry : list) {
            if (entry instanceof Map<?, ?> map) {
                Object count = map.get("count");
                if (count instanceof Long l) {
                    max = Math.max(max, l.intValue());
                } else if (count instanceof Integer i) {
                    max = Math.max(max, i);
                }
            }
        }
        return max;
    }
}

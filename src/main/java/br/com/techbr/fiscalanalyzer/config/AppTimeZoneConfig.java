package br.com.techbr.fiscalanalyzer.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.time.ZoneId;
import java.util.TimeZone;

@Configuration
public class AppTimeZoneConfig {

    private final String timezone;

    public AppTimeZoneConfig(@Value("${APP_TIMEZONE:America/Fortaleza}") String timezone) {
        this.timezone = timezone;
    }

    @PostConstruct
    void configureDefaultTimezone() {
        if (!StringUtils.hasText(timezone)) {
            return;
        }
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of(timezone)));
    }
}

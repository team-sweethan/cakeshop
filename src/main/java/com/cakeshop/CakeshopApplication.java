package com.cakeshop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class CakeshopApplication {

    private static final String STATISTICS_BACKFILL_ENABLED =
            "app.statistics.backfill.enabled";

    public static void main(String[] args) {
        ConfigurableApplicationContext context =
                SpringApplication.run(CakeshopApplication.class, args);
        boolean backfillEnabled = context.getEnvironment().getProperty(
                STATISTICS_BACKFILL_ENABLED,
                Boolean.class,
                false
        );
        if (backfillEnabled) {
            System.exit(SpringApplication.exit(context));
        }
    }
}

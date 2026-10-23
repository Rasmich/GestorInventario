package com.gestorinventario.demo;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@Profile("demo")
public class DemoDatabaseGuard {
    static final String URL = "jdbc:mysql://mysql.railway.internal:3306/railway";

    @Bean
    static BeanFactoryPostProcessor demoDatabaseValidationPostProcessor(Environment env) {
        // Antes de instanciar DataSource o Flyway: validar las propiedades efectivas.
        return factory -> validate(env);
    }

    static void validate(Environment env) {
        if (!"ac01ecca-0186-40ad-8037-145be39c298a".equals(env.getProperty("RAILWAY_PROJECT_ID")) || !URL.equals(env.getProperty("spring.datasource.url"))
                || !"root".equals(env.getProperty("spring.datasource.username"))) {
            throw new IllegalStateException("Demo: solo se admite el MySQL privado de este proyecto de Railway");
        }
        if (java.util.Arrays.asList(env.getActiveProfiles()).contains("recuperacion")) {
            throw new IllegalStateException("Demo y recuperacion no pueden activarse juntos");
        }
    }
}

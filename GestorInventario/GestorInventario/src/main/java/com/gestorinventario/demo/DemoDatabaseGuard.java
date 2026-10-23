package com.gestorinventario.demo;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@Profile("demo")
public class DemoDatabaseGuard {
    static final String URL = "jdbc:mysql://mysql-demo:3306/gestor_inventario_demo";

    @Bean
    static BeanFactoryPostProcessor demoDatabaseValidationPostProcessor(Environment env) {
        // Antes de instanciar DataSource o Flyway: validar las propiedades efectivas.
        return factory -> validate(env);
    }

    static void validate(Environment env) {
        if (!URL.equals(env.getProperty("spring.datasource.url"))
                || !"demo_app".equals(env.getProperty("spring.datasource.username"))) {
            throw new IllegalStateException("Demo: solo se admite el MySQL interno exclusivo de Compose");
        }
        if (java.util.Arrays.asList(env.getActiveProfiles()).contains("recuperacion")) {
            throw new IllegalStateException("Demo y recuperacion no pueden activarse juntos");
        }
    }
}
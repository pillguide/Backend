package kr.co.pillguide.backend.common.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class DatabaseErrorConfig {
    @Bean
    public static BeanPostProcessor postgresErrorDetails() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String name) {
                if (bean instanceof HikariDataSource dataSource
                        && dataSource.getJdbcUrl() != null
                        && dataSource.getJdbcUrl().startsWith("jdbc:postgresql:")) {
                    // Unique-token conflicts are logged by Hibernate before our 409 handler.
                    // Suppress parameter values at the driver boundary, retaining SQL state.
                    dataSource.addDataSourceProperty("logServerErrorDetail", "false");
                }
                return bean;
            }
        };
    }
}

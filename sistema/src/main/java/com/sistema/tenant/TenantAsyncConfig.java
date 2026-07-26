package com.sistema.tenant;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class TenantAsyncConfig {
    @Bean
    public TaskDecorator tenantTaskDecorator() {
        return runnable -> {
            Long tenantId = TenantContext.get();
            return () -> {
                if (tenantId == null) {
                    runnable.run();
                } else {
                    try (TenantContext.Scope ignored = TenantContext.use(tenantId)) {
                        runnable.run();
                    }
                }
            };
        };
    }

    @Bean(name = "taskExecutor")
    public Executor taskExecutor(TaskDecorator tenantTaskDecorator) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(12);
        executor.setQueueCapacity(250);
        executor.setThreadNamePrefix("tenant-async-");
        executor.setTaskDecorator(tenantTaskDecorator);
        executor.initialize();
        return executor;
    }
}

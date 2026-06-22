package com.waterwheel.chaintransmission.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Configuration
@EnableAsync
public class ChainDynamicsThreadPoolConfig {

    @Bean(name = "chainDynamicsExecutor")
    public Executor chainDynamicsExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int cpuCores = Runtime.getRuntime().availableProcessors();

        executor.setCorePoolSize(Math.max(2, cpuCores - 1));
        executor.setMaxPoolSize(cpuCores * 2);
        executor.setQueueCapacity(500);
        executor.setKeepAliveSeconds(120);
        executor.setThreadNamePrefix("chain-dyn-");
        executor.setDaemon(false);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        executor.setRejectedExecutionHandler((r, e) -> {
            log.warn("[ChainDynamics] 线程池已满，任务被主调用线程直接执行，"
                    + "队列={}, 活跃={}, 核心={}, 最大={}",
                    e.getQueue().size(), e.getActiveCount(),
                    e.getCorePoolSize(), e.getMaximumPoolSize());
            if (!e.isShutdown()) {
                try {
                    r.run();
                } catch (Exception ex) {
                    log.error("[ChainDynamics] CallerRuns任务执行失败", ex);
                }
            }
        });

        executor.initialize();
        log.info("[ChainDynamics] 链动力学独立线程池初始化完成："
                + "核心线程={}, 最大线程={}, 队列容量={}, CPU核心={}",
                executor.getCorePoolSize(),
                executor.getMaxPoolSize(),
                executor.getQueueCapacity(),
                cpuCores);
        return executor;
    }

    @Bean(name = "chainSimStats")
    public ChainSimStats chainSimStats() {
        return new ChainSimStats();
    }

    public static class ChainSimStats {
        private long submittedTasks = 0;
        private long completedTasks = 0;
        private long totalSimMillis = 0;
        private long maxSimMillis = 0;

        public synchronized void recordSubmission() {
            submittedTasks++;
        }

        public synchronized void recordCompletion(long simMillis) {
            completedTasks++;
            totalSimMillis += simMillis;
            if (simMillis > maxSimMillis) maxSimMillis = simMillis;
        }

        public synchronized java.util.Map<String, Object> snapshot() {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("submittedTasks", submittedTasks);
            m.put("completedTasks", completedTasks);
            m.put("pendingTasks", submittedTasks - completedTasks);
            m.put("totalSimSeconds", totalSimMillis / 1000.0);
            m.put("avgSimMillis", completedTasks == 0 ? 0 : totalSimMillis / completedTasks);
            m.put("maxSimMillis", maxSimMillis);
            return m;
        }
    }
}

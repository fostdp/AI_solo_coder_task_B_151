package com.waterwheel.chaintransmission;

import com.waterwheel.chaintransmission.config.ChainDynamicsThreadPoolConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChainDynamicsThreadPool 模块独立测试")
class ChainDynamicsThreadPoolTest {

    private ChainDynamicsThreadPoolConfig config;

    @BeforeEach
    void setUp() {
        config = new ChainDynamicsThreadPoolConfig();
    }

    @Test
    @DisplayName("testThreadPrefixChainDynNamePrefix - 线程名前缀chain-dyn-")
    void testThreadPrefixChainDynNamePrefix() throws Exception {
        Executor executorBean = config.chainDynamicsExecutor();
        assertNotNull(executorBean, "chainDynamicsExecutor bean 不应为 null");
        assertTrue(executorBean instanceof ThreadPoolTaskExecutor,
                "应为 ThreadPoolTaskExecutor 类型");

        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) executorBean;

        Field prefixField = ThreadPoolTaskExecutor.class.getDeclaredField("threadNamePrefix");
        prefixField.setAccessible(true);
        String prefix = (String) prefixField.get(executor);

        assertNotNull(prefix, "threadNamePrefix 不应为 null");
        assertTrue(prefix.startsWith("chain-dyn-"),
                "线程名前缀应以 'chain-dyn-' 开头，实际: '" + prefix + "'");
        assertEquals("chain-dyn-", prefix, "线程名前缀应精确为 'chain-dyn-'");

        final CountDownLatch latch = new CountDownLatch(1);
        final String[] capturedThreadName = new String[1];
        executor.execute(() -> {
            capturedThreadName[0] = Thread.currentThread().getName();
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS), "任务应在5秒内执行完成");
        assertNotNull(capturedThreadName[0], "应捕获到执行线程名");
        assertTrue(capturedThreadName[0].startsWith("chain-dyn-"),
                "实际执行的线程名应以 'chain-dyn-' 开头，实际: " + capturedThreadName[0]);

        executor.shutdown();
    }

    @Test
    @DisplayName("testStatsBeanExists - ChainSimStats bean存在记录提交数/完成")
    void testStatsBeanExists() {
        ChainDynamicsThreadPoolConfig.ChainSimStats stats = config.chainSimStats();
        assertNotNull(stats, "chainSimStats bean 不应为 null");

        Map<String, Object> initialSnapshot = stats.snapshot();
        assertNotNull(initialSnapshot, "snapshot() 不应返回 null");

        assertTrue(initialSnapshot.containsKey("submittedTasks"),
                "snapshot 应包含 submittedTasks");
        assertTrue(initialSnapshot.containsKey("completedTasks"),
                "snapshot 应包含 completedTasks");
        assertTrue(initialSnapshot.containsKey("pendingTasks"),
                "snapshot 应包含 pendingTasks");
        assertTrue(initialSnapshot.containsKey("totalSimSeconds"),
                "snapshot 应包含 totalSimSeconds");
        assertTrue(initialSnapshot.containsKey("avgSimMillis"),
                "snapshot 应包含 avgSimMillis");
        assertTrue(initialSnapshot.containsKey("maxSimMillis"),
                "snapshot 应包含 maxSimMillis");

        assertEquals(0L, ((Number) initialSnapshot.get("submittedTasks")).longValue(),
                "初始提交任务数应为 0");
        assertEquals(0L, ((Number) initialSnapshot.get("completedTasks")).longValue(),
                "初始完成任务数应为 0");
        assertEquals(0L, ((Number) initialSnapshot.get("pendingTasks")).longValue(),
                "初始待处理任务数应为 0");

        stats.recordSubmission();
        stats.recordSubmission();
        Map<String, Object> afterSubmit = stats.snapshot();
        assertEquals(2L, ((Number) afterSubmit.get("submittedTasks")).longValue(),
                "recordSubmission 两次后 submittedTasks 应为 2");
        assertEquals(2L, ((Number) afterSubmit.get("pendingTasks")).longValue(),
                "待处理数应为提交数-完成数");

        stats.recordCompletion(150);
        Map<String, Object> afterComplete1 = stats.snapshot();
        assertEquals(2L, ((Number) afterComplete1.get("submittedTasks")).longValue());
        assertEquals(1L, ((Number) afterComplete1.get("completedTasks")).longValue(),
                "recordCompletion 后 completedTasks 应为 1");
        assertEquals(1L, ((Number) afterComplete1.get("pendingTasks")).longValue());
        assertEquals(150L, ((Number) afterComplete1.get("avgSimMillis")).longValue(),
                "平均耗时应为 150ms");
        assertEquals(150L, ((Number) afterComplete1.get("maxSimMillis")).longValue(),
                "最大耗时应为 150ms");

        stats.recordCompletion(50);
        Map<String, Object> afterComplete2 = stats.snapshot();
        assertEquals(2L, ((Number) afterComplete2.get("completedTasks")).longValue());
        assertEquals(0L, ((Number) afterComplete2.get("pendingTasks")).longValue(),
                "待处理数应为 0");
        assertEquals(100L, ((Number) afterComplete2.get("avgSimMillis")).longValue(),
                "平均耗时应为 (150+50)/2=100ms");
        assertEquals(150L, ((Number) afterComplete2.get("maxSimMillis")).longValue(),
                "最大耗时仍为 150ms");
    }

    @Test
    @DisplayName("testThreadPoolTask - 线程池任务执行：多任务并发、提交数完成数匹配")
    void testThreadPoolTask() throws Exception {
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) config.chainDynamicsExecutor();
        ChainDynamicsThreadPoolConfig.ChainSimStats stats = config.chainSimStats();

        int taskCount = 20;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(taskCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger exceptionCount = new AtomicInteger(0);

        for (int i = 0; i < taskCount; i++) {
            final int taskId = i;
            stats.recordSubmission();
            executor.execute(() -> {
                try {
                    startLatch.await(3, TimeUnit.SECONDS);
                    long simStart = System.currentTimeMillis();
                    Thread.sleep(10 + taskId % 5);
                    long simMillis = System.currentTimeMillis() - simStart;
                    successCount.incrementAndGet();
                    stats.recordCompletion(simMillis);
                } catch (Exception e) {
                    exceptionCount.incrementAndGet();
                } finally {
                    completeLatch.countDown();
                }
            });
        }

        Map<String, Object> midSnapshot = stats.snapshot();
        assertEquals(taskCount, ((Number) midSnapshot.get("submittedTasks")).longValue(),
                "所有任务都应被记录为已提交");

        startLatch.countDown();
        assertTrue(completeLatch.await(10, TimeUnit.SECONDS),
                "所有任务应在10秒内完成");

        assertEquals(taskCount, successCount.get(),
                "所有 " + taskCount + " 个任务应成功执行，异常数: " + exceptionCount.get());
        assertEquals(0, exceptionCount.get(), "不应有任务异常");

        Map<String, Object> finalSnapshot = stats.snapshot();
        assertEquals(taskCount, ((Number) finalSnapshot.get("submittedTasks")).longValue(),
                "最终 submittedTasks 应等于任务数");
        assertEquals(taskCount, ((Number) finalSnapshot.get("completedTasks")).longValue(),
                "最终 completedTasks 应等于任务数");
        assertEquals(0L, ((Number) finalSnapshot.get("pendingTasks")).longValue(),
                "所有任务应完成，pendingTasks 应为 0");

        long avg = ((Number) finalSnapshot.get("avgSimMillis")).longValue();
        long max = ((Number) finalSnapshot.get("maxSimMillis")).longValue();
        assertTrue(avg >= 10 && avg < 50, "平均耗时应合理 (10~50ms)，实际: " + avg);
        assertTrue(max >= avg, "最大耗时应 >= 平均耗时");
        assertTrue(max > 0, "最大耗时应为正数");

        assertTrue(executor.getCorePoolSize() >= 2,
                "核心线程数应 >= 2，实际: " + executor.getCorePoolSize());
        assertTrue(executor.getMaxPoolSize() >= executor.getCorePoolSize(),
                "最大线程数应 >= 核心线程数");
        assertEquals(500, executor.getQueueCapacity(),
                "队列容量应为 500");
        assertTrue(executor.getKeepAliveSeconds() >= 60,
                "线程空闲存活时间应 >= 60s，实际: " + executor.getKeepAliveSeconds());

        executor.shutdown();
    }
}

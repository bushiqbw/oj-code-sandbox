package com.qbw.ojcodesandbox.docker;

import cn.hutool.core.io.FileUtil;
import com.qbw.ojcodesandbox.model.ExecuteCodeRequest;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpRequest;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.File;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * 容器管理器
 *
 * @author cq
 * @since 2024/01/09
 */
@Slf4j
@Configuration
@Data
@ConfigurationProperties(prefix = "codesandbox.pool")
public class ContainerPoolExecutor {

    private Integer corePoolSize = Runtime.getRuntime().availableProcessors() * 2;

    private Integer maximumPoolSize = Runtime.getRuntime().availableProcessors() * 10;

    private Integer waitQueueSize = 200;

    private Integer keepAliveTime = 5;

    private TimeUnit timeUnit = TimeUnit.SECONDS;


    /**
     * 容器池
     * key: 容器 id
     * value：上次活跃时间
     */
    private BlockingQueue<ContainerInfo> containerPool;

    /**
     * 容器使用排队计数
     */
    private AtomicInteger blockingThreadCount;

    /**
     * 可扩展的数量
     */
    private AtomicInteger expandCount;

    @Resource
    private DockerDao dockerDao;

    /**
     *  兜底策略，使用HttpClient
     */
    private HttpClient httpClient;


    @PostConstruct
    public void initPool() {

        // 初始化容器池
        this.containerPool = new LinkedBlockingQueue<>(maximumPoolSize);
        this.blockingThreadCount = new AtomicInteger(0);
        this.expandCount = new AtomicInteger(maximumPoolSize - corePoolSize);
        this.httpClient = HttpClientBuilder.create().build();

        // 初始化池中的数据
        for (int i = 0; i < corePoolSize; i++) {
            createNewPool();
        }

        // 定时清理过期的容器
        ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);
        scheduleExpirationCleanup(scheduledExecutorService);
    }

    private void createNewPool() {
        // 写入文件
        String userDir = System.getProperty("user.dir");
        String codePathName = userDir + File.separator + "tempCode";

        // 把用户的代码隔离存放
        UUID uuid = UUID.randomUUID();
        codePathName += File.separator + uuid;

        // 判断代码目录是否存在，没有则新建
        File codePath = new File(codePathName);
        if (!codePath.exists()) {
            boolean mkdir = codePath.mkdirs();
            if (!mkdir) {
                log.info("创建代码目录失败");
            }
        }
        ContainerInfo containerInfo = dockerDao.startContainer(codePathName);
        boolean result = containerPool.offer(containerInfo);
        if (!result) {
            log.error("current capacity: {}, the capacity limit is exceeded...", containerPool.size());
        }
    }

    private boolean expandPool() {
        log.info("超过指定数量，触发扩容");
        if (expandCount.decrementAndGet() < 0) {
            log.error("不能再扩容了");
            return false;
        }
        log.info("扩容了");
        createNewPool();
        return true;
    }


    private ContainerInfo getContainer() throws InterruptedException {
        if (containerPool.isEmpty()) {
            // 增加阻塞线程计数
            try {
                if (blockingThreadCount.incrementAndGet() >= waitQueueSize && !expandPool()) {
                    log.error("扩容失败");
                    return null;
                }
                log.info("没有数据，等待数据，当前等待长度：{}", blockingThreadCount.get());
                // 阻塞等待可用的数据
                return containerPool.take();
            } finally {
                // 减少阻塞线程计数
                log.info("减少阻塞线程计数");
                blockingThreadCount.decrementAndGet();
            }
        }
        return containerPool.take();
    }

    /**
     * 清理过期容器
     */
    private void cleanExpiredContainers() {
        long currentTime = System.currentTimeMillis();
        int needCleanCount = containerPool.size() - corePoolSize;
        if (needCleanCount <= 0) {
            return;
        }
        // 处理过期的容器
        containerPool.stream().filter(containerInfo -> {
            long lastActivityTime = containerInfo.getLastActivityTime();
            lastActivityTime += timeUnit.toMillis(keepAliveTime);
            return lastActivityTime < currentTime;
        }).forEach(containerInfo -> {
            boolean remove = containerPool.remove(containerInfo);
            if (remove) {
                String containerId = containerInfo.getContainerId();
                expandCount.incrementAndGet();
                if (StringUtils.isNotBlank(containerId)) {
                    dockerDao.cleanContainer(containerId);
                }
            }
        });
        log.info("当前容器大小: " + containerPool.size());
    }

    private void scheduleExpirationCleanup(ScheduledExecutorService scheduledExecutorService) {
        // 每隔 20 秒执行一次清理操作
        scheduledExecutorService.scheduleAtFixedRate(this::cleanExpiredContainers, 0, 20, TimeUnit.SECONDS);
    }


    private void recordError(ContainerInfo containerInfo) {
        if (containerInfo != null) {
            containerInfo.setErrorCount(containerInfo.getErrorCount() + 1);
        }
    }


    public ExecuteMessage run(Function<ContainerInfo, ExecuteMessage> function) {
        ContainerInfo containerInfo = null;
        try {
            containerInfo = getContainer();
            if (containerInfo == null) {
                //todo 转接本地或使用延时队列
                return ExecuteMessage.builder().success(false).message("不能处理了").build();
            }

            ExecuteMessage executeMessage = function.apply(containerInfo);
            if (!executeMessage.isSuccess()) {
                recordError(containerInfo);
            }
            return executeMessage;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            if (containerInfo != null) {
                ContainerInfo finalContainerInfo = containerInfo;
                dockerDao.execCmd(containerInfo.getContainerId(), new String[]{"sh", "/usr/local/clean.sh"});
                CompletableFuture.runAsync(() -> {
                    try {
                        // 更新时间
                        String codePathName = finalContainerInfo.getCodePathName();
                        FileUtil.del(codePathName);
                        // 错误超过 3 次就不放回，重新运行一个
                        if (finalContainerInfo.getErrorCount() > 3) {
                            CompletableFuture.runAsync(() -> {
                                dockerDao.cleanContainer(finalContainerInfo.getContainerId());
                                this.createNewPool();
                            });
                            return;
                        }
                        finalContainerInfo.setLastActivityTime(System.currentTimeMillis());
                        containerPool.put(finalContainerInfo);
                    } catch (InterruptedException e) {
                        log.error("无法放入");
                    }
                });
            }
        }
    }
}

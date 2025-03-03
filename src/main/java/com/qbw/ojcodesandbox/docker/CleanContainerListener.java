package com.qbw.ojcodesandbox.docker;

import cn.hutool.core.io.FileUtil;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 清理容器侦听器
 * 在服务停止时执行清理任务
 *
 * @author cq
 * @since 2024/01/23
 */
@Component
public class CleanContainerListener implements ApplicationListener<ContextClosedEvent> {

    @Resource
    private DockerDao dockerDao;

    @Resource
    private ContainerPoolExecutor containerPoolExecutor;

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        // 清理所有的容器以及残余文件
        containerPoolExecutor
                .getContainerPool()
                .forEach(containerInfo -> {
                    FileUtil.del(containerInfo.getCodePathName());
                    dockerDao.cleanContainer(containerInfo.getContainerId());
                });
        System.out.println("container clean end...");
    }
}

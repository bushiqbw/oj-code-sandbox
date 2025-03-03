package com.qbw.ojcodesandbox.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.StreamType;
import com.github.dockerjava.core.DockerClientBuilder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * docker 道
 *
 * @author cq
 * @since 2024/01/11
 */
@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "codesandbox.config")
public class DockerDao {

    /**
     * 代码沙箱的镜像，Dockerfile 构建的镜像名，默认为 codesandbox:latest
     */
    private String image = "codesandbox:latest";

    /**
     * 内存限制，单位为字节，默认为 1024 * 1024 * 100 MB
     */
    private long memoryLimit = 1024 * 1024 * 100;

    private long memorySwap = 0;

    /**
     * 最大可消耗的 cpu 数
     */
    private long cpuCount = 1;

    private long timeoutLimit = 1;

    private TimeUnit timeUnit = TimeUnit.SECONDS;

    private static final DockerClient DOCKER_CLIENT = DockerClientBuilder.getInstance().build();

    /**
     * 执行命令
     *
     * @param containerId 容器 ID
     * @param cmd         CMD
     * @return {@link ExecuteMessage}
     */
    public ExecuteMessage execCmd(String containerId, String[] cmd) {
        // 正常返回信息
        ByteArrayOutputStream resultStream = new ByteArrayOutputStream();
        // 错误信息
        ByteArrayOutputStream errorResultStream = new ByteArrayOutputStream();

        // 结果
        final boolean[] result = {true};
        final boolean[] timeout = {true};
        try (ResultCallback.Adapter<Frame> frameAdapter = new ResultCallback.Adapter<Frame>() {

            @Override
            public void onComplete() {
                // 是否超时
                timeout[0] = false;
                super.onComplete();
            }

            @Override
            public void onNext(Frame frame) {
                StreamType streamType = frame.getStreamType();
                byte[] payload = frame.getPayload();
                if (StreamType.STDERR.equals(streamType)) {
                    try {
                        result[0] = false;
                        errorResultStream.write(payload);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    try {
                        resultStream.write(payload);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                super.onNext(frame);
            }
        }) {
            ExecCreateCmdResponse execCompileCmdResponse = DOCKER_CLIENT.execCreateCmd(containerId)
                    .withCmd(cmd)
                    .withAttachStderr(true)
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .exec();
            String execId = execCompileCmdResponse.getId();
            DOCKER_CLIENT.execStartCmd(execId).exec(frameAdapter).awaitCompletion(timeoutLimit, timeUnit);

            // 超时
            if (timeout[0]) {
                return ExecuteMessage
                        .builder()
                        .success(false)
                        .errorMessage("执行超时")
                        .build();
            }

            return ExecuteMessage
                    .builder()
                    .success(result[0])
                    .message(resultStream.toString())
                    .errorMessage(errorResultStream.toString())
                    .build();

        } catch (IOException | InterruptedException e) {
            return ExecuteMessage
                    .builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    public ContainerInfo startContainer(String codePath) {
        CreateContainerCmd containerCmd = DOCKER_CLIENT.createContainerCmd(image);
        HostConfig hostConfig = new HostConfig();
        hostConfig.withMemory(memoryLimit);
        hostConfig.withMemorySwap(memorySwap);
        hostConfig.withCpuCount(cpuCount);
//        hostConfig.withReadonlyRootfs(true);
//        hostConfig.setBinds(new Bind(codePath, new Volume("/box")));
        CreateContainerResponse createContainerResponse = containerCmd
                .withHostConfig(hostConfig)
                .withNetworkDisabled(true)
                .withAttachStdin(true)
                .withAttachStderr(true)
                .withAttachStdout(true)
                .withTty(true)
                .exec();
        String containerId = createContainerResponse.getId();
        log.info("containerId: {}", containerId);
        // 启动容器
        DOCKER_CLIENT.startContainerCmd(containerId).exec();
        return ContainerInfo
                .builder()
                .containerId(containerId)
                .codePathName(codePath)
                .lastActivityTime(System.currentTimeMillis())
                .build();
    }

    /**
     * 复制文件到容器
     *
     * @param codeFile    代码文件
     * @param containerId 容器 ID
     */
    public void copyFileToContainer(String containerId, String codeFile) {
        DOCKER_CLIENT.copyArchiveToContainerCmd(containerId)
                .withHostResource(codeFile)
                .withRemotePath("/box")
                .exec();
    }

    public void cleanContainer(String containerId) {
        DOCKER_CLIENT.stopContainerCmd(containerId).exec();
        DOCKER_CLIENT.removeContainerCmd(containerId).exec();
    }
}

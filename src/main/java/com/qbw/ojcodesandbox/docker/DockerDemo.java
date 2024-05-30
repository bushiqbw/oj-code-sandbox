package com.qbw.ojcodesandbox.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.LogContainerResultCallback;

import java.util.List;

public class DockerDemo {
    public static void main(String[] args) throws InterruptedException {
        // dockerClient是用来封装docker命令行的
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();

        // 1.拉取镜像
        String image = "nginx:latest";
        PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
        PullImageResultCallback pullImageResultCallback = new PullImageResultCallback() {
            @Override
            public void onNext(PullResponseItem item) {
                System.out.println("下载镜像" + item.getStatus());
                super.onNext(item);
            }
        };
        // await表示阻塞当前线程，直到完成，再进行下一步
        pullImageCmd.exec(pullImageResultCallback).awaitCompletion();

        // 2.创建容器
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);
        CreateContainerResponse createContainerResponse = containerCmd.withCmd("echo", "Hello Docker").exec();
        System.out.println(createContainerResponse);

        // 3.查看所有容器的状态
        ListContainersCmd listContainersCmd = dockerClient.listContainersCmd();
        List<Container> containerList = listContainersCmd.withShowAll(true).exec();
        for (Container container : containerList) {
            System.out.println(container);
        }

        // 4.启动容器
        dockerClient.startContainerCmd(createContainerResponse.getId()).exec();

        // 5.查看日志
        LogContainerResultCallback logContainerResultCallback = new LogContainerResultCallback() {
            @Override
            public void onNext(Frame item) {
                System.out.println("日志" + item.getPayload().toString());
                super.onNext(item);
            }
        };

        dockerClient.logContainerCmd(createContainerResponse.getId())
                .withStdErr(true)
                        .withStdOut(true)
                                .exec(logContainerResultCallback)
                                        .awaitCompletion();

        // 6.删除容器
        dockerClient.removeImageCmd(createContainerResponse.getId()).exec();

        // 7.删除镜像
        dockerClient.removeImageCmd(image).exec();

    }
}

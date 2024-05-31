package com.qbw.ojcodesandbox;


import cn.hutool.core.date.StopWatch;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.dfa.WordTree;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.qbw.ojcodesandbox.model.ExecuteCodeRequest;
import com.qbw.ojcodesandbox.model.ExecuteCodeResponse;
import com.qbw.ojcodesandbox.model.ExecuteMessage;
import com.qbw.ojcodesandbox.model.JudgeInfo;
import com.qbw.ojcodesandbox.utils.ProcessUtils;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Arrays;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

public class JavaDockerCodeSandbox implements CodeSandbox{

    private static final String GLOBAL_CODE_DIR_NAME = "tmpCode";

    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    private static final long TIME_OUT = 5000L;

    private static final String SECURITY_MANAGER_PATH = "C:\\code\\yuoj-code-sandbox\\src\\main\\resources\\security";

    private static final String SECURITY_MANAGER_CLASS_NAME = "MySecurityManager";

    private static final Boolean FIRST_INIT = true;

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {

        List<String> inputList = executeCodeRequest.getInputList();
        String code = executeCodeRequest.getCode();
        String language = executeCodeRequest.getLanguage();

        // 1. 把用户提交的代码保存为随机生成目录下的文件

        // 1.1 设置保存所有代码的全局路径
        String userDir = System.getProperty("user.dir");
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NAME;
        if(!FileUtil.exist(globalCodePathName)){
            FileUtil.mkdir(globalCodePathName);
        }

        // 1.2 为每个用户生成单独目录
        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID();
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);

        // 2. 用程序代替人工编译用户代码  cmd -- javac -encoding utf-8 .\Main.java
        String compileCmd = String.format("javac -encoding utf-8 %s", userCodeFile.getAbsolutePath());
        try{
            Process compileProcess = Runtime.getRuntime().exec(compileCmd);
            ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(compileProcess, "编译");
            System.out.println(executeMessage);
        } catch (IOException e) {
            return getErrorResponse(e);
        }

        //todo 3. 创建容器，把文件复制到容器内, 再执行代码，得出输出结果, 并且在系统层面上进行安全控制
        // 3.1 获取默认的Docker Client
        DockerClient dockerCilent = DockerClientBuilder.getInstance().build();

        // 在初始化时，只拉取一次镜像
        String image = "openjdk:8-alpine";
        if(FIRST_INIT){
            PullImageCmd pullImageCmd = dockerCilent.pullImageCmd(image);
            PullImageResultCallback pullImageResultCallback = new PullImageResultCallback(){
                @Override
                public void onNext(PullResponseItem item) {
                    System.out.println("下载镜像：" + item.getStatus());
                    super.onNext(item);
                }
            };

            try {
                pullImageCmd.exec(pullImageResultCallback)
                        .awaitCompletion();
            } catch (InterruptedException e) {
                System.out.println("拉取镜像异常");
                throw new RuntimeException(e);
            }
        }
        System.out.println("下载完成");
        // 3.2 创建容器
        CreateContainerCmd containerCmd = dockerCilent.createContainerCmd(image);
        // 3.2.1 设置创建容器时的配置： CPU, 内存， 安全控制管理, 绑定代码对应文件地址
        HostConfig hostConfig = new HostConfig();
        hostConfig.withMemory(100 * 1000 * 1000L);
        hostConfig.withMemorySwap(0L);
        hostConfig.withCpuCount(1L);
        hostConfig.withSecurityOpts(Arrays.asList("seccomp=安全管理配置字符串"));
        hostConfig.setBinds(new Bind(userCodeParentPath, new Volume("/app")));
        CreateContainerResponse createContainerResponse = containerCmd
                .withHostConfig(hostConfig)
                .withNetworkDisabled(true) //设置网络
                .withReadonlyRootfs(true) //设置只读权限
                .withAttachStdin(true)
                .withAttachStderr(true)
                .withAttachStdout(true) //开启控制台输入输出流
                .withTty(true) //允许交互
                .exec();
        System.out.println(createContainerResponse);
        String containerId = createContainerResponse.getId();

        // 3.3 启动容器
        StartContainerCmd startContainerCmd = dockerCilent.startContainerCmd(containerId);

        // 3.4 和容器docker exec交互，执行java代码并获取输出结果
        // docker exec keen_blackwell java -cp /app Main 1 3
        ArrayList<ExecuteMessage> executeMessageArrayList = new ArrayList<>();
        for (String inputArgs : inputList) {
            StopWatch stopWatch = new StopWatch();
            String[] inputArgsArray = inputArgs.split(" ");
            String[] cmdArray = ArrayUtil.append(new String[]{"java", "-cp", "/app", "Main"}, inputArgsArray);
            ExecCreateCmdResponse execCreateCmdResponse = dockerCilent.execCreateCmd(containerId)
                    .withCmd(cmdArray)
                    .withAttachStderr(true)
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .exec();
            System.out.println("创建执行命令：" + execCreateCmdResponse);
            ExecuteMessage executeMessage = new ExecuteMessage();
            final String[] message = {null};
            final String[] errorMessage = {null};
            long time = 0L;

            // 3.4.1 判断是否超时
            final boolean[] timeout = {true};
            String execId = execCreateCmdResponse.getId();
            ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback(){

                @Override
                public void onComplete() {
                    //超时判断,如果正常结束，则会运行回调函数onComplete
                    timeout[0] = false;
                    super.onComplete();
                }

                @Override
                public void onNext(Frame frame) {
                    StreamType streamType = frame.getStreamType();
                    if (StreamType.STDERR.equals(streamType)) {
                        errorMessage[0] = new String(frame.getPayload());
                        System.out.println("输出错误结果：" + errorMessage[0]);
                    } else {
                        message[0] = new String(frame.getPayload());
                        System.out.println("输出结果：" + message[0]);
                    }
                    super.onNext(frame);
                }
            };

            // 3.4.2 判断内存情况
            final long[] maxMemory = {0L};

            StatsCmd statsCmd = dockerCilent.statsCmd(containerId);
            ResultCallback<Statistics> statisticsResultCallback = statsCmd.exec(new ResultCallback<Statistics>() {

                @Override
                public void onNext(Statistics statistics) {
                    System.out.println("内存占用：" + statistics.getMemoryStats().getUsage());
                    maxMemory[0] = Math.max(statistics.getMemoryStats().getUsage(), maxMemory[0]);
                }

                @Override
                public void close() throws IOException {

                }

                @Override
                public void onStart(Closeable closeable) {

                }

                @Override
                public void onError(Throwable throwable) {

                }

                @Override
                public void onComplete() {

                }
            });
            statsCmd.exec(statisticsResultCallback);

            try {
                stopWatch.start();
                dockerCilent.execStartCmd(execId)
                        .exec(execStartResultCallback)
                        .awaitCompletion(TIME_OUT, TimeUnit.MILLISECONDS);
                stopWatch.stop();
                time = stopWatch.getLastTaskTimeMillis();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            executeMessage.setMessage(message[0]);
            executeMessage.setErrorMessage(errorMessage[0]);
            executeMessage.setTime(time);
            executeMessage.setMemory(maxMemory[0]);
            executeMessageArrayList.add(executeMessage);
        }


        // 4. 整理输出结果，对结果值进行赋值
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        List<String > outputList = new ArrayList<>();
        //取程序运行的最大时长
        long maxTime = 0;
        for (ExecuteMessage executeMessage : executeMessageArrayList) {
            String errorMessage = executeMessage.getErrorMessage();
            if(StrUtil.isNotBlank(errorMessage)){
                executeCodeResponse.setMessage(errorMessage);
                // 3代表 -- 用户提交的代码执行中存在错误
                executeCodeResponse.setStatus(3);
                break;
            }
            outputList.add(executeMessage.getMessage());
            Long time = executeMessage.getTime();
            if(time != null){
                maxTime = Math.max(time, maxTime);
            }
        }
        // 程序正常运行完成，未超时也未内存溢出，正常结束
        if(outputList.size() == executeMessageArrayList.size()){
            executeCodeResponse.setStatus(1);
        }
        executeCodeResponse.setOutputList(outputList);
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setTime(maxTime);
        executeCodeResponse.setJudgeInfo(judgeInfo);

        // 5. 文件清理
        if(FileUtil.exist(userCodeParentPath)){
            boolean del = FileUtil.del(userCodeParentPath);
            System.out.println("删除" + (del ? "成功" : "失败"));
        }

        return executeCodeResponse;
    }

    /**
     * 代码沙箱发生异常情况下，获取错误响应
     *
     * @param e
     * @return
     */
    private ExecuteCodeResponse getErrorResponse(Throwable e) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setOutputList(new ArrayList<>());
        executeCodeResponse.setMessage(e.getMessage());
        // 表示代码沙箱错误
        executeCodeResponse.setStatus(2);
        executeCodeResponse.setJudgeInfo(new JudgeInfo());
        return executeCodeResponse;
    }
}


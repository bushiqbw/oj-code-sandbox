package com.qbw.ojcodesandbox.docker;

import cn.hutool.core.io.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Docker 沙箱
 *
 * @author cq
 * @since 2024/01/03
 */
@Service
@Slf4j
public class DockerSandbox {


    @Resource
    private DockerDao dockerDao;

    @Resource
    private ContainerPoolExecutor containerPoolExecutor;

    public ExecuteMessage execute(LanguageCmdEnum languageCmdEnum, String code) {
        return containerPoolExecutor.run(containerInfo -> {
            try {
                String className = null;
                // Java 需要额外的特殊处理
                boolean isJava = LanguageCmdEnum.JAVA == languageCmdEnum;
                if (isJava) {
                    Pattern pattern = Pattern.compile("public class\\s+(\\w+)\\s*\\{");
                    Matcher matcher = pattern.matcher(code);
                    if (matcher.find()) {
                        // 提取类名
                        className = matcher.group(1);
                    }
                }
                String containerId = containerInfo.getContainerId();

                String codePathName = containerInfo.getCodePathName();

                // 只有 Java 才需要类名
                String saveFileName = (className == null) ? languageCmdEnum.getSaveFileName() : className + ".java";

                String codeFileName = codePathName + File.separator + saveFileName;

                FileUtil.writeString(code, codeFileName, StandardCharsets.UTF_8);

                dockerDao.copyFileToContainer(containerId, codeFileName);

                // 编译代码
                String[] compileCmd = languageCmdEnum.getCompileCmd();
                if (isJava) {
                    compileCmd = new String[]{"javac", "-encoding", "utf-8", saveFileName};
                }
                ExecuteMessage executeMessage;

                // 不为空则代表需要编译
                if (compileCmd != null) {
                    executeMessage = dockerDao.execCmd(containerId, compileCmd);
                    log.info("compile complete...");
                    // 编译错误
                    if (!executeMessage.isSuccess()) {
                        return executeMessage;
                    }
                }
                String[] runCmd = languageCmdEnum.getRunCmd();
                if (isJava) {
                    runCmd = new String[]{"java", "-Dfile.encoding=UTF-8", className};
                }
                executeMessage = dockerDao.execCmd(containerId, runCmd);
                log.info("run complete...");
                return executeMessage;
            } catch (Exception e) {
                return ExecuteMessage.builder()
                        .success(false)
                        .errorMessage(e.getMessage())
                        .build();
            }
        });

    }


}

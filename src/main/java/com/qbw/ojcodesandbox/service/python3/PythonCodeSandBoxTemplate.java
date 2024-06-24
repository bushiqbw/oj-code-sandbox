package com.qbw.ojcodesandbox.service.python3;

import cn.hutool.dfa.FoundWord;
import cn.hutool.dfa.WordTree;
import com.qbw.ojcodesandbox.model.ExecuteCodeRequest;
import com.qbw.ojcodesandbox.model.ExecuteCodeResponse;
import com.qbw.ojcodesandbox.model.ExecuteMessage;
import com.qbw.ojcodesandbox.model.JudgeInfo;
import com.qbw.ojcodesandbox.model.enums.JudgeInfoMessageEnum;
import com.qbw.ojcodesandbox.model.enums.QuestionSubmitStatusEnum;
import com.qbw.ojcodesandbox.service.CodeSandbox;
import com.qbw.ojcodesandbox.service.CommonCodeSandBox;
import com.qbw.ojcodesandbox.utils.ProcessUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.qbw.ojcodesandbox.constant.CodeBlackList.PYTHON_BLACK_LIST;

@Slf4j
@Component
public abstract class PythonCodeSandBoxTemplate extends CommonCodeSandBox implements CodeSandbox {

    /**
     *
     */
    private static final String GLOBAL_CODE_DIR_NAME = "tmpCode";

    /**
     * 代码统一名称
     */
    private static final String GLOBAL_JAVA_CLASS_NAME = "solution.py";

    /**
     * 代码运行超时时间
     */
    private static final Long EXCESS_TIME = 10000L;


    /**
     * 设置Python最大可用内存为128MB
     */
    private static final String MEMORY_LIMIT_PREFIX_CODE = "import resource;max_memory = 128;resource.setrlimit(resource.RLIMIT_AS, (max_memory * (1024 ** 2), -1));";

    /**
     * 使用hutool的工具类，字典树，存放黑名单
     */
    private static final WordTree WORD_TREE;

    static {
        WORD_TREE = new WordTree();
        WORD_TREE.addWords(PYTHON_BLACK_LIST);
    }

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        String code = executeCodeRequest.getCode();
        List<String> inputList = executeCodeRequest.getInputList();
        String language = executeCodeRequest.getLanguage();
        System.out.println("当前操作系统：" + System.getProperty("os.name").toLowerCase());
        System.out.println("当前代码使用语言：" + language);
        //保存用户代码文件
        File userCodeFile = saveCodeToFile(code, language, GLOBAL_CODE_DIR_NAME, GLOBAL_JAVA_CLASS_NAME);

        //校验代码中的敏感代码
        FoundWord foundWord = WORD_TREE.matchWord(code);
        if (foundWord != null) {
            System.out.println("包含禁止词：" + foundWord.getFoundWord());
            // 返回错误信息
            return new ExecuteCodeResponse(null, "包含禁止词：" + foundWord.getFoundWord(), QuestionSubmitStatusEnum.FAILED.getValue(),  new JudgeInfo(JudgeInfoMessageEnum.DANGEROUS_OPERATION.getValue(), null, null));
        }

        // 安全控制：限制资源分配：最大队资源大小：128MB
        // 判断所处的操作系统，如果是Windows系统，则无法使用resource的Python库和限制内存大小，因此不建议在Windows系统上运行代码。
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("nix") || osName.contains("nux")) {
            //放到前面
            code = MEMORY_LIMIT_PREFIX_CODE + "\r\n" + code;
            System.out.println("安全控制后的代码：\n" + code);
        }

        //运行文件
        List<ExecuteMessage> executeMessageList = runFile(userCodeFile, inputList);

        //收集结果
        ExecuteCodeResponse executeCodeResponse = getOutputResponse(executeMessageList);

//        删除文件
        boolean b = delCodeFile(userCodeFile);

        if (!b) {
            log.info("删除文件失败{}", userCodeFile);
        }

        return executeCodeResponse;
    }


    /**
     * 3.运行代码
     *
     * @param userCodeFile 用户代码文件
     * @param inputList    输入用例
     * @return
     */
    public List<ExecuteMessage> runFile(File userCodeFile, List<String> inputList) {
        String userCodeFileAbsolutePath = userCodeFile.getAbsolutePath();
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        String osName = System.getProperty("os.name").toLowerCase();
        String pythonCmdPrefix = "python";
        System.out.println("当前操作系统：" + osName);
        if (osName.contains("nix") || osName.contains("nux")) {
            pythonCmdPrefix = "python3";
        }
        for (String input : inputList) {
            String runCmd = String.format("%s %s", pythonCmdPrefix, userCodeFileAbsolutePath);
            try {
                Process runProcess = Runtime.getRuntime().exec(runCmd);
                // 安全控制：限制最大运行时间，超时控制
                new Thread(() -> {
                    try {
                        Thread.sleep(EXCESS_TIME);
                        runProcess.destroy();
                        System.out.println("超过程序最大运行时间，终止进程");
                    } catch (InterruptedException e) {
                        System.out.println("结束");
                    }
                }).start();
                ExecuteMessage executeMessage = ProcessUtils.runInteractProcessAndGetMessage(runProcess, input);
                System.out.println("本次运行结果：" + executeMessage);
                if (executeMessage.getExitValue() != 0) {
                    executeMessage.setExitValue(1);
                    executeMessage.setMessage(JudgeInfoMessageEnum.RUNTIME_ERROR.getText());
                    executeMessage.setErrorMessage(JudgeInfoMessageEnum.RUNTIME_ERROR.getValue());
                }
                executeMessageList.add(executeMessage);
            } catch (IOException e) {
                // 未知错误
                ExecuteMessage executeMessage = new ExecuteMessage();
                executeMessage.setExitValue(1);
                executeMessage.setMessage(e.getMessage());
                executeMessage.setErrorMessage(JudgeInfoMessageEnum.SYSTEM_ERROR.getValue());
                executeMessageList.add(executeMessage);
            }
        }
        return executeMessageList;

    }
}

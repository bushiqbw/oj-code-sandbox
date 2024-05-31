package com.qbw.ojcodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.dfa.WordTree;
import com.qbw.ojcodesandbox.model.ExecuteCodeRequest;
import com.qbw.ojcodesandbox.model.ExecuteCodeResponse;
import com.qbw.ojcodesandbox.model.ExecuteMessage;
import com.qbw.ojcodesandbox.model.JudgeInfo;
import com.qbw.ojcodesandbox.utils.ProcessUtils;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Arrays;
import java.util.concurrent.FutureTask;
@Component
public class JavaNativeCodeSandbox extends JavaCodeSandboxTemplate{

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        return super.executeCode(executeCodeRequest);
    }
}

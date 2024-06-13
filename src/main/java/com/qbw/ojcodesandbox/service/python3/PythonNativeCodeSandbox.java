package com.qbw.ojcodesandbox.service.python3;

import com.qbw.ojcodesandbox.model.ExecuteCodeRequest;
import com.qbw.ojcodesandbox.model.ExecuteCodeResponse;
import org.springframework.stereotype.Component;

@Component
public class PythonNativeCodeSandbox extends PythonCodeSandBoxTemplate {

    /**
     * 执行代码
     *
     * @param executeCodeRequest
     * @return
     */
    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        return super.executeCode(executeCodeRequest);
    }
}

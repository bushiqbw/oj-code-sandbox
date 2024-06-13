package com.qbw.ojcodesandbox.service;

import com.qbw.ojcodesandbox.model.ExecuteCodeRequest;
import com.qbw.ojcodesandbox.model.ExecuteCodeResponse;

/**
 * 定义代码沙箱的接口，项目其他地方只调用接口，而不调用具体实现类
 */
public interface CodeSandbox {

    /**
     * 执行代码
     *
     * @param executeCodeRequest
     * @return
     */
    ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest);

}

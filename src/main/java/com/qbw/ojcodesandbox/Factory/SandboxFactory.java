package com.qbw.ojcodesandbox.Factory;

import com.qbw.ojcodesandbox.service.CodeSandbox;
import com.qbw.ojcodesandbox.service.c.CNativeCodeSandbox;
import com.qbw.ojcodesandbox.service.cpp.CppNativeCodeSandbox;
import com.qbw.ojcodesandbox.service.java.JavaNativeCodeSandbox;
import com.qbw.ojcodesandbox.service.python3.PythonNativeCodeSandbox;

public class SandboxFactory {

    public static CodeSandbox getSandbox(String language){
        switch (language){
            case "java":
                return new JavaNativeCodeSandbox();
            case "python":
                return new PythonNativeCodeSandbox();
            case "c":
                return new CNativeCodeSandbox();
            case "cpp":
                return new CppNativeCodeSandbox();
            default:
                throw new RuntimeException("不支持该语言");
        }
    }

}

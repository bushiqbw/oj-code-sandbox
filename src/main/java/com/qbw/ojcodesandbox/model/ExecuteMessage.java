package com.qbw.ojcodesandbox.model;

import lombok.Data;

/**
 * 进程执行过程中的信息
 */
@Data
public class ExecuteMessage {

    private Integer exitValue;

    private String message;

    private String errorMessage;

    private Long time;
}


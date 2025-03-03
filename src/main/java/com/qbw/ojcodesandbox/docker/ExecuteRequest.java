package com.qbw.ojcodesandbox.docker;

import lombok.Data;

/**
 * 执行请求
 *
 * @author cq
 * @since 2024/01/03
 */
@Data
public class ExecuteRequest {
    private String language;
    private String code;
}

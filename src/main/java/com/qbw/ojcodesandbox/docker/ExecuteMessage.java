package com.qbw.ojcodesandbox.docker;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 执行返回消息
 *
 * @author cq
 * @since 2024/01/03
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ExecuteMessage {
    private boolean success;
    private String message;
    private String errorMessage;
}

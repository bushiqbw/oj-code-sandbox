package com.qbw.ojcodesandbox.docker;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 容器信息
 *
 * @author cq
 * @since 2024/01/12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContainerInfo {
    private String containerId;

    private String codePathName;

    private long lastActivityTime;

    /**
     * 错误计数，默认为 0
     */
    private int errorCount = 0;
}

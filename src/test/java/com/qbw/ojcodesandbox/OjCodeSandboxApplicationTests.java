package com.qbw.ojcodesandbox;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class OjCodeSandboxApplicationTests {

    @Test
    void contextLoads() {

        String userDir = System.getProperty("user.dir");
        System.out.println(userDir);
    }

}

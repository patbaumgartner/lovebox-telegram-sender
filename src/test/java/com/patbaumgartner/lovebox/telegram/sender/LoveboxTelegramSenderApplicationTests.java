package com.patbaumgartner.lovebox.telegram.sender;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(
        properties = {"lovebox.enabled=false", "telegrambots.enabled=false"}
)
class LoveboxTelegramSenderApplicationTests {

    @Test
    void contextLoads() {
    }

}

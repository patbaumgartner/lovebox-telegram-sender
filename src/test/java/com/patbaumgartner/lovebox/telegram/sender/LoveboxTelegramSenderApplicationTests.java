package com.patbaumgartner.lovebox.telegram.sender;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = { "lovebox.enabled=false", "telegrambots.enabled=false" })
class LoveboxTelegramSenderApplicationTests {

	@Test
	void contextLoads() {
	}

}

package com.patbaumgartner.lovebox.telegram.sender;

import com.patbaumgartner.lovebox.telegram.sender.rest.clients.LoveboxRestClientProperties;
import com.patbaumgartner.lovebox.telegram.sender.telegram.LoveboxBotProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableFeignClients
@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties({LoveboxRestClientProperties.class, LoveboxBotProperties.class})
public class LoveboxTelegramSenderApplication {

    public static void main(String[] args) {
        SpringApplication.run(LoveboxTelegramSenderApplication.class, args);
    }

}

package com.patbaumgartner.lovebox.telegram.sender;

import com.patbaumgartner.lovebox.telegram.sender.rest.clients.LoveboxRestClientProperties;
import com.patbaumgartner.lovebox.telegram.sender.telegram.LoveboxBotProperties;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties({ LoveboxRestClientProperties.class, LoveboxBotProperties.class })
public class LoveboxTelegramSenderApplication {

	public static void main(String[] args) {
		SpringApplication.run(LoveboxTelegramSenderApplication.class, args);
	}

	@Bean
	@Profile("font-debug")
	CommandLineRunner commandLineRunner() {
		return (args) -> {
			String[] fonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
			System.out.println(fonts.length + " font families installed.");

			List<String> supportedFonts = new ArrayList<>();
			for (String fontName : fonts) {
				Font f = new Font(fontName, Font.PLAIN, 1);
				System.out.println(fontName);
				if (f.canDisplayUpTo("🚀️ you") < 0) {
					supportedFonts.add(fontName);
				}
			}

			System.out.println("***");

			System.out.println(supportedFonts.size() + " font families support emojis.");
			for (String fontName : supportedFonts) {
				System.out.println(fontName);
			}
		};
	}

}

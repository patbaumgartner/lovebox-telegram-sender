package com.patbaumgartner.lovebox.telegram.sender.rest.clients;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "lovebox")
public record LoveboxRestClientProperties(

		boolean enabled,

		String email,

		String password,

		String deviceId,

		String boxId,

		String signature

) {

}

package com.patbaumgartner.lovebox.telegram.sender.rest.clients;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "lovebox")
public class LoveboxRestClientProperties {

	/* Lovebox sender enabled */
	private boolean enabled = true;

	/* Email used to login into App */
	private String email;

	/* Password used to login into App */
	private String password;

	/* Mobile device id for logged in user */
	private String deviceId;

	/* Box id for message receiver */
	private String boxId;

	/* Signature of message sender */
	private String signature;

}

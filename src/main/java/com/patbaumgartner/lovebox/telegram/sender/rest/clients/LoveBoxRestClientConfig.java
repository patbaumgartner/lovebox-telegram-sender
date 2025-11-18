package com.patbaumgartner.lovebox.telegram.sender.rest.clients;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class LoveBoxRestClientConfig {

	private final String LOVEBOX_REST_API = "https://app-api.loveboxlove.com";

	@Bean
	LoveboxRestClient loveboxRestClient() {
		RestClient restClient = RestClient.create(LOVEBOX_REST_API);

		HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
			.build();

		return factory.createClient(LoveboxRestClient.class);
	}

}
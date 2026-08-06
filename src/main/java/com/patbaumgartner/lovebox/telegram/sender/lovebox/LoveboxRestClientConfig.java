package com.patbaumgartner.lovebox.telegram.sender.lovebox;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * Creates the {@link LoveboxRestClient} HTTP interface proxy backed by a
 * {@link RestClient} with explicit connect/read timeouts, so a stalled Lovebox API can
 * never hang the scheduled polling threads indefinitely.
 */
@Configuration(proxyBeanMethods = false)
public class LoveboxRestClientConfig {

	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

	private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

	@Bean
	LoveboxRestClient loveboxRestClient(RestClient.Builder restClientBuilder, LoveboxRestClientProperties properties) {
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(READ_TIMEOUT);

		RestClient restClient = restClientBuilder.baseUrl(properties.apiUrl()).requestFactory(requestFactory).build();

		HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
			.build();

		return factory.createClient(LoveboxRestClient.class);
	}

}

package com.example.priceparser.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class ParserConfig {

	private static final String WORKER_THREAD_PREFIX = "parser-worker-";
	private static final long FIRST_WORKER_NUMBER = 1;

	@Bean(destroyMethod = "shutdownNow")
	public ExecutorService parserExecutor(ParserProperties properties) {
		ThreadFactory threadFactory = Thread.ofPlatform()
				.name(WORKER_THREAD_PREFIX, FIRST_WORKER_NUMBER)
				.factory();
		return Executors.newFixedThreadPool(properties.workerCount(), threadFactory);
	}

	@Bean
	public RestTemplate restTemplate(ParserProperties properties) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(properties.connectTimeout());
		requestFactory.setReadTimeout(properties.readTimeout());
		return new RestTemplate(requestFactory);
	}

}

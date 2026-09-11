package com.example.priceparser.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "parser")
public record ParserProperties(
		int workerCount,
		int queueCapacity,
		int maxCatalogPages,
		Duration connectTimeout,
		Duration readTimeout,
		int maxFetchAttempts,
		Duration refreshInterval,
		String userAgent,
		String logFile) {
}

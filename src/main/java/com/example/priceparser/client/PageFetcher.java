package com.example.priceparser.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.example.priceparser.config.ParserProperties;
import com.example.priceparser.logging.ActivityLog;

@Component
public class PageFetcher {

	private static final String DETECT_CHARSET_FROM_PAGE = null;

	private final RestTemplate restTemplate;
	private final ActivityLog activityLog;
	private final String userAgent;
	private final int maxAttempts;

	public PageFetcher(RestTemplate restTemplate, ActivityLog activityLog, ParserProperties properties) {
		this.restTemplate = restTemplate;
		this.activityLog = activityLog;
		this.userAgent = properties.userAgent();
		this.maxAttempts = properties.maxFetchAttempts();
	}

	public Document fetch(String url) throws IOException {
		for (int attempt = 1; attempt < maxAttempts; attempt++) {
			try {
				return download(url);
			} catch (ResourceAccessException exception) {
				activityLog.log("Попытка " + attempt + " из " + maxAttempts + " не удалась для " + url
						+ ": " + exception.getMessage());
			}
		}
		return download(url);
	}

	private Document download(String url) throws IOException {
		HttpHeaders headers = new HttpHeaders();
		headers.set(HttpHeaders.USER_AGENT, userAgent);
		ResponseEntity<byte[]> response = restTemplate.exchange(
				URI.create(url), HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
		byte[] body = response.getBody();
		if (body == null) {
			throw new IOException("Пустой ответ от " + url);
		}
		return Jsoup.parse(new ByteArrayInputStream(body), DETECT_CHARSET_FROM_PAGE, url);
	}

}

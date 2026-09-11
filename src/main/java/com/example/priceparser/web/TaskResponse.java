package com.example.priceparser.web;

import java.time.LocalDateTime;
import java.util.List;

import com.example.priceparser.crawl.CrawlStatus;
import com.example.priceparser.crawl.CrawlTask;

public record TaskResponse(
		long id,
		CrawlStatus status,
		List<String> sourceUrls,
		int found,
		int saved,
		int failed,
		LocalDateTime createdAt,
		LocalDateTime finishedAt) {

	public static TaskResponse from(CrawlTask task) {
		return new TaskResponse(
				task.getId(),
				task.getStatus(),
				task.getSourceUrls(),
				task.getFoundCount(),
				task.getSavedCount(),
				task.getFailedCount(),
				task.getCreatedAt(),
				task.getFinishedAt());
	}

}

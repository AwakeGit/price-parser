package com.example.priceparser.crawl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class CrawlTask {

	private final long id;
	private final List<String> sourceUrls;
	private final LocalDateTime createdAt = LocalDateTime.now();
	private final AtomicInteger foundCount = new AtomicInteger();
	private final AtomicInteger savedCount = new AtomicInteger();
	private final AtomicInteger failedCount = new AtomicInteger();
	private volatile CrawlStatus status = CrawlStatus.RUNNING;
	private volatile LocalDateTime finishedAt;

	public CrawlTask(long id, List<String> sourceUrls) {
		this.id = id;
		this.sourceUrls = List.copyOf(sourceUrls);
	}

	public void incrementFound() {
		foundCount.incrementAndGet();
	}

	public void incrementSaved() {
		savedCount.incrementAndGet();
	}

	public void incrementFailed() {
		failedCount.incrementAndGet();
	}

	public void finish() {
		finishedAt = LocalDateTime.now();
		status = CrawlStatus.DONE;
	}

	public long getId() {
		return id;
	}

	public List<String> getSourceUrls() {
		return sourceUrls;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public int getFoundCount() {
		return foundCount.get();
	}

	public int getSavedCount() {
		return savedCount.get();
	}

	public int getFailedCount() {
		return failedCount.get();
	}

	public CrawlStatus getStatus() {
		return status;
	}

	public LocalDateTime getFinishedAt() {
		return finishedAt;
	}

}

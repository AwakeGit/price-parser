package com.example.priceparser.crawl;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;

import com.example.priceparser.client.PageFetcher;
import com.example.priceparser.config.ParserProperties;
import com.example.priceparser.logging.ActivityLog;
import com.example.priceparser.parser.BookPageParser;
import com.example.priceparser.product.ProductProcessor;

@Service
public class CrawlService {

	private static final String PRODUCER_THREAD_PREFIX = "crawl-producer-";

	private final Map<Long, CrawlTask> tasks = new ConcurrentHashMap<>();
	private final AtomicLong taskIdSequence = new AtomicLong();
	private final ExecutorService parserExecutor;
	private final ActivityLog activityLog;
	private final CrawlDependencies dependencies;

	public CrawlService(ExecutorService parserExecutor, PageFetcher pageFetcher, BookPageParser pageParser,
			ProductProcessor productProcessor, ActivityLog activityLog, ParserProperties properties) {
		this.parserExecutor = parserExecutor;
		this.activityLog = activityLog;
		this.dependencies = new CrawlDependencies(pageFetcher, pageParser, productProcessor, activityLog, properties);
	}

	public CrawlTask start(List<String> sourceUrls) {
		CrawlTask task = new CrawlTask(taskIdSequence.incrementAndGet(), sourceUrls);
		tasks.put(task.getId(), task);
		CrawlJob job = new CrawlJob(task, dependencies);
		for (int consumer = 0; consumer < job.consumerCount(); consumer++) {
			parserExecutor.submit(job::consume);
		}
		Thread producer = new Thread(job::produce, PRODUCER_THREAD_PREFIX + task.getId());
		producer.start();
		activityLog.log("Задача " + task.getId() + " запущена для " + sourceUrls.size() + " ссылок");
		return task;
	}

	public Optional<CrawlTask> findTask(long taskId) {
		return Optional.ofNullable(tasks.get(taskId));
	}

}

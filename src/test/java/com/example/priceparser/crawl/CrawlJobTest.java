package com.example.priceparser.crawl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.priceparser.client.PageFetcher;
import com.example.priceparser.config.ParserProperties;
import com.example.priceparser.logging.ActivityLog;
import com.example.priceparser.parser.BookPageParser;
import com.example.priceparser.product.ProductProcessor;

@ExtendWith(MockitoExtension.class)
class CrawlJobTest {

	private static final long TASK_ID = 1;
	private static final String BROKEN_SOURCE_URL = "https://books.toscrape.com/catalogue/category/broken/index.html";
	private static final String PRODUCT_URL = "https://books.toscrape.com/catalogue/a-light-in-the-attic_1000/index.html";
	private static final int WORKER_COUNT = 2;
	private static final int QUEUE_CAPACITY = 10;
	private static final int MAX_CATALOG_PAGES = 1;
	private static final int MAX_FETCH_ATTEMPTS = 1;
	private static final Duration TIMEOUT = Duration.ofSeconds(1);
	private static final Duration REFRESH_INTERVAL = Duration.ofMinutes(10);
	private static final Duration PRODUCER_WAIT = Duration.ofSeconds(5);

	@Mock
	private PageFetcher pageFetcher;

	@Mock
	private BookPageParser pageParser;

	@Mock
	private ProductProcessor productProcessor;

	@Mock
	private ActivityLog activityLog;

	@Test
	void finishesTaskWhenSourceUrlFailsWithRuntimeException() throws Exception {
		when(pageParser.isProductUrl(BROKEN_SOURCE_URL)).thenThrow(new IllegalStateException("broken page"));
		when(pageParser.isProductUrl(PRODUCT_URL)).thenReturn(true);
		CrawlTask task = new CrawlTask(TASK_ID, List.of(BROKEN_SOURCE_URL, PRODUCT_URL));
		CrawlJob job = new CrawlJob(task, new CrawlDependencies(pageFetcher, pageParser, productProcessor,
				activityLog, properties()));
		ExecutorService consumers = Executors.newFixedThreadPool(WORKER_COUNT);

		try {
			for (int consumer = 0; consumer < job.consumerCount(); consumer++) {
				consumers.submit(job::consume);
			}
			Thread producer = new Thread(job::produce);
			producer.start();
			producer.join(PRODUCER_WAIT);
		} finally {
			consumers.shutdownNow();
		}

		assertThat(task.getStatus()).isEqualTo(CrawlStatus.DONE);
		assertThat(task.getFailedCount()).isEqualTo(1);
		assertThat(task.getSavedCount()).isEqualTo(1);
	}

	private ParserProperties properties() {
		return new ParserProperties(WORKER_COUNT, QUEUE_CAPACITY, MAX_CATALOG_PAGES, TIMEOUT, TIMEOUT,
				MAX_FETCH_ATTEMPTS, REFRESH_INTERVAL, "price-parser-test", "logs/test.log");
	}

}

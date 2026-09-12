package com.example.priceparser.crawl;

import java.io.IOException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

import org.jsoup.nodes.Document;

import com.example.priceparser.client.PageFetcher;
import com.example.priceparser.logging.ActivityLog;
import com.example.priceparser.parser.BookPageParser;
import com.example.priceparser.product.ProductProcessor;

class CrawlJob {

	private static final String POISON_PILL = "__NO_MORE_URLS__";

	private final CrawlTask task;
	private final PageFetcher pageFetcher;
	private final BookPageParser pageParser;
	private final ProductProcessor productProcessor;
	private final ActivityLog activityLog;
	private final int consumerCount;
	private final int maxCatalogPages;
	private final BlockingQueue<String> productUrls;
	private final CountDownLatch consumersFinished;
	private final Set<String> queuedUrls = new HashSet<>();

	CrawlJob(CrawlTask task, CrawlDependencies dependencies) {
		this.task = task;
		this.pageFetcher = dependencies.pageFetcher();
		this.pageParser = dependencies.pageParser();
		this.productProcessor = dependencies.productProcessor();
		this.activityLog = dependencies.activityLog();
		this.consumerCount = dependencies.properties().workerCount();
		this.maxCatalogPages = dependencies.properties().maxCatalogPages();
		this.productUrls = new LinkedBlockingQueue<>(dependencies.properties().queueCapacity());
		this.consumersFinished = new CountDownLatch(consumerCount);
	}

	int consumerCount() {
		return consumerCount;
	}

	void produce() {
		try {
			for (String sourceUrl : task.getSourceUrls()) {
				enqueueSource(sourceUrl);
			}
			stopConsumers();
			consumersFinished.await();
			task.finish();
			activityLog.log("Задача " + task.getId() + " завершена: найдено " + task.getFoundCount()
					+ ", сохранено " + task.getSavedCount() + ", ошибок " + task.getFailedCount());
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			activityLog.log("Задача " + task.getId() + " прервана");
		}
	}

	void consume() {
		try {
			String productUrl = productUrls.take();
			while (!POISON_PILL.equals(productUrl)) {
				processProduct(productUrl);
				productUrl = productUrls.take();
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
		} finally {
			consumersFinished.countDown();
		}
	}

	private void enqueueSource(String sourceUrl) throws InterruptedException {
		if (pageParser.isProductUrl(sourceUrl)) {
			enqueueProduct(sourceUrl);
			return;
		}
		enqueueCatalog(sourceUrl);
	}

	private void enqueueCatalog(String catalogUrl) throws InterruptedException {
		Optional<String> pageUrl = Optional.of(catalogUrl);
		for (int pagesVisited = 0; pagesVisited < maxCatalogPages && pageUrl.isPresent(); pagesVisited++) {
			Optional<Document> page = fetchCatalogPage(pageUrl.get());
			if (page.isEmpty()) {
				return;
			}
			for (String productUrl : pageParser.findProductLinks(page.get())) {
				enqueueProduct(productUrl);
			}
			pageUrl = pageParser.findNextPageUrl(page.get());
		}
	}

	private Optional<Document> fetchCatalogPage(String pageUrl) {
		try {
			activityLog.log("Задача " + task.getId() + ": обход страницы каталога " + pageUrl);
			return Optional.of(pageFetcher.fetch(pageUrl));
		} catch (IOException | RuntimeException exception) {
			task.incrementFailed();
			activityLog.log("Задача " + task.getId() + ": не удалось загрузить каталог " + pageUrl
					+ ": " + exception.getMessage());
			return Optional.empty();
		}
	}

	private void enqueueProduct(String productUrl) throws InterruptedException {
		if (queuedUrls.add(productUrl)) {
			task.incrementFound();
			productUrls.put(productUrl);
		}
	}

	private void stopConsumers() throws InterruptedException {
		for (int consumer = 0; consumer < consumerCount; consumer++) {
			productUrls.put(POISON_PILL);
		}
	}

	private void processProduct(String productUrl) {
		try {
			productProcessor.process(productUrl);
			task.incrementSaved();
		} catch (IOException | RuntimeException exception) {
			task.incrementFailed();
			activityLog.log("Задача " + task.getId() + ": ошибка обработки " + productUrl
					+ ": " + exception.getMessage());
		}
	}

}

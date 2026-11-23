package com.example.priceparser.product;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.instrumentation.annotations.WithSpan;

import com.example.priceparser.client.PageFetcher;
import com.example.priceparser.logging.ActivityLog;
import com.example.priceparser.parser.BookPageParser;
import com.example.priceparser.parser.ParsedProduct;

@Service
public class ProductProcessor {

	private final PageFetcher pageFetcher;
	private final BookPageParser pageParser;
	private final ProductStorage productStorage;
	private final ReentrantLock saveLock = new ReentrantLock();
	private final ActivityLog activityLog;
	private final MeterRegistry meterRegistry;
	private final Timer parsingDuration;
	private final Counter successfulParses;
	private final Counter failedParses;
	private final Counter savedProducts;

	public ProductProcessor(PageFetcher pageFetcher, BookPageParser pageParser, ProductStorage productStorage,
			ActivityLog activityLog, MeterRegistry meterRegistry) {
		this.pageFetcher = pageFetcher;
		this.pageParser = pageParser;
		this.productStorage = productStorage;
		this.activityLog = activityLog;
		this.meterRegistry = meterRegistry;
		this.parsingDuration = Timer.builder("parser.parsing.duration")
				.description("Duration of product page parsing")
				.register(meterRegistry);
		this.successfulParses = Counter.builder("parser.parsing.successful")
				.description("Number of successful product parsings")
				.register(meterRegistry);
		this.failedParses = Counter.builder("parser.parsing.failed")
				.description("Number of failed product parsings")
				.register(meterRegistry);
		this.savedProducts = Counter.builder("parser.products.saved")
				.description("Number of products saved to the database")
				.register(meterRegistry);
	}

	@WithSpan("product.process")
	public Product process(String productUrl) throws IOException {
		Timer.Sample sample = Timer.start(meterRegistry);
		try {
			long fetchStarted = System.nanoTime();
			Document document = pageFetcher.fetch(productUrl);
			long parseStarted = System.nanoTime();
			ParsedProduct parsedProduct = pageParser.parseProduct(document);
			long saveStarted = System.nanoTime();
			Product product = save(parsedProduct);
			long saveFinished = System.nanoTime();
			logStageDurations(productUrl, fetchStarted, parseStarted, saveStarted, saveFinished);
			successfulParses.increment();
			savedProducts.increment();
			return product;
		} catch (IOException | RuntimeException exception) {
			failedParses.increment();
			throw exception;
		} finally {
			sample.stop(parsingDuration);
		}
	}

	private void logStageDurations(String productUrl, long fetchStarted, long parseStarted,
			long saveStarted, long saveFinished) {
		activityLog.log("Этапы обработки " + productUrl + ": загрузка=" + elapsedMillis(fetchStarted, parseStarted)
				+ " мс, разбор=" + elapsedMillis(parseStarted, saveStarted)
				+ " мс, сохранение=" + elapsedMillis(saveStarted, saveFinished) + " мс");
	}

	private long elapsedMillis(long startedAt, long finishedAt) {
		return TimeUnit.NANOSECONDS.toMillis(finishedAt - startedAt);
	}

	private Product save(ParsedProduct parsedProduct) {
		saveLock.lock();
		try {
			return productStorage.save(parsedProduct);
		} finally {
			saveLock.unlock();
		}
	}

}

package com.example.priceparser.scheduler;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.example.priceparser.config.ParserProperties;
import com.example.priceparser.logging.ActivityLog;
import com.example.priceparser.product.ProductProcessor;
import com.example.priceparser.product.ProductStorage;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Component
public class PriceRefreshScheduler {

	private static final String SCHEDULER_THREAD_NAME = "price-refresh-scheduler";

	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
			Thread.ofPlatform().name(SCHEDULER_THREAD_NAME).daemon(true).factory());
	private final ExecutorService parserExecutor;
	private final ProductStorage productStorage;
	private final ProductProcessor productProcessor;
	private final ActivityLog activityLog;
	private final long refreshIntervalMillis;

	public PriceRefreshScheduler(ExecutorService parserExecutor, ProductStorage productStorage,
			ProductProcessor productProcessor, ActivityLog activityLog, ParserProperties properties) {
		this.parserExecutor = parserExecutor;
		this.productStorage = productStorage;
		this.productProcessor = productProcessor;
		this.activityLog = activityLog;
		this.refreshIntervalMillis = properties.refreshInterval().toMillis();
	}

	@PostConstruct
	public void start() {
		scheduler.scheduleWithFixedDelay(this::refreshAll, refreshIntervalMillis, refreshIntervalMillis,
				TimeUnit.MILLISECONDS);
	}

	@PreDestroy
	public void stop() {
		scheduler.shutdownNow();
	}

	void refreshAll() {
		try {
			List<Callable<Boolean>> refreshJobs = productStorage.findAllUrls().stream()
					.map(this::toRefreshJob)
					.toList();
			activityLog.log("Плановое обновление цен: товаров " + refreshJobs.size());
			List<Future<Boolean>> results = parserExecutor.invokeAll(refreshJobs);
			long refreshedCount = results.stream().filter(Future::resultNow).count();
			activityLog.log("Плановое обновление завершено: обновлено " + refreshedCount + " из " + results.size());
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
		} catch (RuntimeException exception) {
			activityLog.log("Плановое обновление завершилось ошибкой: " + exception.getMessage());
		}
	}

	private Callable<Boolean> toRefreshJob(String productUrl) {
		return () -> refreshProduct(productUrl);
	}

	private boolean refreshProduct(String productUrl) {
		try {
			productProcessor.process(productUrl);
			return true;
		} catch (IOException | RuntimeException exception) {
			activityLog.log("Не удалось обновить " + productUrl + " — " + exception.getMessage());
			return false;
		}
	}

}

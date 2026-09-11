package com.example.priceparser.logging;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.example.priceparser.config.ParserProperties;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Component
public class ActivityLog {

	private static final Logger LOGGER = LoggerFactory.getLogger(ActivityLog.class);
	private static final String WRITER_THREAD_NAME = "activity-log-writer";
	private static final String LINE_FORMAT = "%s [%s] %s";
	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

	private final BlockingQueue<String> pendingLines = new LinkedBlockingQueue<>();
	private final Path logFile;
	private final Thread writerThread;

	public ActivityLog(ParserProperties properties) {
		this.logFile = Path.of(properties.logFile()).toAbsolutePath();
		this.writerThread = Thread.ofPlatform()
				.name(WRITER_THREAD_NAME)
				.daemon(true)
				.unstarted(this::writePendingLines);
	}

	@PostConstruct
	public void start() {
		writerThread.start();
	}

	@PreDestroy
	public void stop() {
		writerThread.interrupt();
	}

	public void log(String message) {
		LOGGER.info(message);
		String threadName = Thread.currentThread().getName();
		pendingLines.offer(LINE_FORMAT.formatted(LocalDateTime.now().format(TIME_FORMAT), threadName, message));
	}

	private void writePendingLines() {
		try {
			Files.createDirectories(logFile.getParent());
			try (BufferedWriter writer = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
				while (!Thread.currentThread().isInterrupted()) {
					writer.write(pendingLines.take());
					writer.newLine();
					writer.flush();
				}
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
		} catch (IOException exception) {
			LOGGER.error("Не удалось записать лог в файл {}", logFile, exception);
		}
	}

}

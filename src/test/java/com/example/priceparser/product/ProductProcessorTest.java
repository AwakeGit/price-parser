package com.example.priceparser.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.example.priceparser.client.PageFetcher;
import com.example.priceparser.parser.BookPageParser;
import com.example.priceparser.parser.ParsedProduct;

@SpringBootTest
class ProductProcessorTest {

	private static final String PRODUCT_URL = "https://books.toscrape.com/catalogue/a-light-in-the-attic_1000/index.html";
	private static final int CONCURRENT_CALLS = 10;

	@Autowired
	private ProductProcessor productProcessor;

	@Autowired
	private ProductRepository productRepository;

	@MockitoBean
	private PageFetcher pageFetcher;

	@MockitoBean
	private BookPageParser pageParser;

	@BeforeEach
	void setUp() throws IOException {
		productRepository.deleteAll();
		Document document = Jsoup.parse("<html></html>");
		ParsedProduct parsedProduct = new ParsedProduct(PRODUCT_URL, "A Light in the Attic",
				new BigDecimal("51.77"), "Description");
		when(pageFetcher.fetch(PRODUCT_URL)).thenReturn(document);
		when(pageParser.parseProduct(document)).thenReturn(parsedProduct);
	}

	@Test
	void savesSameProductWithoutDuplicatesWhenCallsAreConcurrent() throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_CALLS);
		CountDownLatch ready = new CountDownLatch(CONCURRENT_CALLS);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Callable<Product>> calls = IntStream.range(0, CONCURRENT_CALLS)
					.mapToObj(index -> (Callable<Product>) () -> {
						ready.countDown();
						start.await();
						return productProcessor.process(PRODUCT_URL);
					})
					.toList();
			List<Future<Product>> results = calls.stream().map(executor::submit).toList();
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			for (Future<Product> result : results) {
				assertThat(result.get(15, TimeUnit.SECONDS)).isNotNull();
			}
			assertThat(productRepository.count()).isEqualTo(1);
		} finally {
			start.countDown();
			executor.shutdownNow();
		}
	}

}

package com.example.priceparser.product;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.springframework.data.domain.Sort;

import com.example.priceparser.parser.ParsedProduct;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class ProductQueryBenchmark {

	private static final LocalDateTime COLLECTED_AT = LocalDateTime.of(2026, 9, 11, 12, 0);
	private static final BigDecimal MIN_PRICE = new BigDecimal("20");
	private static final BigDecimal MAX_PRICE = new BigDecimal("80");

	@Param({"1000", "10000", "100000"})
	private int productCount;

	private List<Product> products;
	private ProductFilter filter;

	@Setup(Level.Trial)
	public void setUp() {
		products = IntStream.range(0, productCount)
				.mapToObj(this::createProduct)
				.toList();
		filter = new ProductFilter("book", MIN_PRICE, MAX_PRICE,
				SortField.PRICE, Sort.Direction.ASC, 0, 20);
	}

	@Benchmark
	public List<ProductResponse> forLoop() {
		List<Product> matchedProducts = new ArrayList<>();
		for (Product product : products) {
			if (matches(product)) {
				matchedProducts.add(product);
			}
		}
		matchedProducts.sort(filter.sortBy().comparator());
		List<ProductResponse> result = new ArrayList<>(matchedProducts.size());
		for (Product product : matchedProducts) {
			result.add(ProductResponse.from(product));
		}
		return result;
	}

	@Benchmark
	public List<ProductResponse> stream() {
		return products.stream()
				.filter(this::matches)
				.sorted(filter.sortBy().comparator())
				.map(ProductResponse::from)
				.toList();
	}

	@Benchmark
	public List<ProductResponse> parallelStream() {
		return products.parallelStream()
				.filter(this::matches)
				.sorted(filter.sortBy().comparator())
				.map(ProductResponse::from)
				.toList();
	}

	@Benchmark
	public List<ProductResponse> parallelStreamPrecomputedTitle() {
		String normalizedTitle = filter.title().toLowerCase(Locale.ROOT);
		return products.parallelStream()
				.filter(product -> matches(product, normalizedTitle))
				.sorted(filter.sortBy().comparator())
				.map(ProductResponse::from)
				.toList();
	}

	private boolean matches(Product product) {
		return matches(product, filter.title().toLowerCase(Locale.ROOT));
	}

	private boolean matches(Product product, String normalizedTitle) {
		return product.getTitle().toLowerCase(Locale.ROOT)
				.contains(normalizedTitle)
				&& product.getPrice().compareTo(filter.minPrice()) >= 0
				&& product.getPrice().compareTo(filter.maxPrice()) <= 0;
	}

	private Product createProduct(int index) {
		String title = (index % 2 == 0 ? "Book " : "Novel ") + index;
		String url = "https://books.toscrape.com/catalogue/book-" + index + "/index.html";
		Product product = new Product(url);
		product.update(new ParsedProduct(url, title, BigDecimal.valueOf(index % 100), ""), COLLECTED_AT);
		return product;
	}

}

package com.example.priceparser.product;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.opentelemetry.instrumentation.annotations.WithSpan;

import com.example.priceparser.parser.ParsedProduct;

@Service
public class ProductStorage {

	private final ProductRepository repository;

	public ProductStorage(ProductRepository repository) {
		this.repository = repository;
	}

	@WithSpan("product.save")
	@Transactional
	public Product save(ParsedProduct parsedProduct) {
		Product product = repository.findByUrl(parsedProduct.url())
				.orElseGet(() -> new Product(parsedProduct.url()));
		product.update(parsedProduct, LocalDateTime.now());
		return repository.save(product);
	}

	@Transactional(readOnly = true)
	public List<String> findAllUrls() {
		return repository.findAll().stream()
				.map(Product::getUrl)
				.toList();
	}

}

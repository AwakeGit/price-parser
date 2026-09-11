package com.example.priceparser.product;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.stereotype.Service;

import com.example.priceparser.parser.ParsedProduct;

@Service
public class ProductStorage {

	private final ProductRepository repository;
	private final ReentrantLock saveLock = new ReentrantLock();

	public ProductStorage(ProductRepository repository) {
		this.repository = repository;
	}

	public Product save(ParsedProduct parsedProduct) {
		saveLock.lock();
		try {
			Product product = repository.findByUrl(parsedProduct.url())
					.orElseGet(() -> new Product(parsedProduct.url()));
			product.update(parsedProduct, LocalDateTime.now());
			return repository.save(product);
		} finally {
			saveLock.unlock();
		}
	}

	public List<String> findAllUrls() {
		return repository.findAll().stream()
				.map(Product::getUrl)
				.toList();
	}

}

package com.example.priceparser.product;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductResponse(
		Long id,
		String url,
		String title,
		BigDecimal price,
		String description,
		LocalDateTime collectedAt) {

	public static ProductResponse from(Product product) {
		return new ProductResponse(
				product.getId(),
				product.getUrl(),
				product.getTitle(),
				product.getPrice(),
				product.getDescription(),
				product.getCollectedAt());
	}

}

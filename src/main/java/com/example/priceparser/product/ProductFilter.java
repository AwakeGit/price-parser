package com.example.priceparser.product;

import java.math.BigDecimal;

import org.springframework.data.domain.Sort;

public record ProductFilter(
		String title,
		BigDecimal minPrice,
		BigDecimal maxPrice,
		SortField sortBy,
		Sort.Direction direction,
		int page,
		int size) {
}

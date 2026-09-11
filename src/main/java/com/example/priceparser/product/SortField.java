package com.example.priceparser.product;

import java.util.Comparator;

public enum SortField {

	PRICE(Comparator.comparing(Product::getPrice)),
	TITLE(Comparator.comparing(Product::getTitle, String.CASE_INSENSITIVE_ORDER)),
	COLLECTED_AT(Comparator.comparing(Product::getCollectedAt));

	private final Comparator<Product> comparator;

	SortField(Comparator<Product> comparator) {
		this.comparator = comparator;
	}

	public Comparator<Product> comparator() {
		return comparator;
	}

}

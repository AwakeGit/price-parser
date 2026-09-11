package com.example.priceparser.product;

import java.util.List;

public record PageResponse<T>(List<T> items, int page, int size, int totalElements, int totalPages) {

	public static <T> PageResponse<T> of(List<T> allItems, int page, int size) {
		int totalElements = allItems.size();
		int fromIndex = Math.min(page * size, totalElements);
		int toIndex = Math.min(fromIndex + size, totalElements);
		int totalPages = (totalElements + size - 1) / size;
		return new PageResponse<>(allItems.subList(fromIndex, toIndex), page, size, totalElements, totalPages);
	}

}

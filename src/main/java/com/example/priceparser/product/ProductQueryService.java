package com.example.priceparser.product;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class ProductQueryService {

	private final ProductRepository repository;

	public ProductQueryService(ProductRepository repository) {
		this.repository = repository;
	}

	public PageResponse<ProductResponse> find(ProductFilter filter) {
		List<ProductResponse> matchedProducts = repository.findAll().parallelStream()
				.filter(product -> matchesTitle(product, filter))
				.filter(product -> matchesPrice(product, filter))
				.sorted(buildComparator(filter))
				.map(ProductResponse::from)
				.toList();
		return PageResponse.of(matchedProducts, filter.page(), filter.size());
	}

	private boolean matchesTitle(Product product, ProductFilter filter) {
		if (filter.title() == null || filter.title().isBlank()) {
			return true;
		}
		return product.getTitle().toLowerCase(Locale.ROOT).contains(filter.title().toLowerCase(Locale.ROOT));
	}

	private boolean matchesPrice(Product product, ProductFilter filter) {
		boolean aboveMin = filter.minPrice() == null || product.getPrice().compareTo(filter.minPrice()) >= 0;
		boolean belowMax = filter.maxPrice() == null || product.getPrice().compareTo(filter.maxPrice()) <= 0;
		return aboveMin && belowMax;
	}

	private Comparator<Product> buildComparator(ProductFilter filter) {
		Comparator<Product> comparator = filter.sortBy().comparator();
		return filter.direction() == Sort.Direction.DESC ? comparator.reversed() : comparator;
	}

}

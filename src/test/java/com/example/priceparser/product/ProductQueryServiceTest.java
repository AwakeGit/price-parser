package com.example.priceparser.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import com.example.priceparser.parser.ParsedProduct;

@ExtendWith(MockitoExtension.class)
class ProductQueryServiceTest {

	private static final LocalDateTime COLLECTED_AT = LocalDateTime.of(2026, 9, 11, 12, 0);
	private static final int FIRST_PAGE = 0;
	private static final int PAGE_SIZE = 20;

	@Mock
	private ProductRepository repository;

	@InjectMocks
	private ProductQueryService queryService;

	@BeforeEach
	void setUp() {
		when(repository.findAll()).thenReturn(List.of(
				product("Sharp Objects", "47.82"),
				product("A Light in the Attic", "51.77"),
				product("Tipping the Velvet", "53.74"),
				product("Soumission", "50.10")));
	}

	@Test
	void filtersByPriceRangeAndSortsByPriceAscending() {
		ProductFilter filter = new ProductFilter(null, new BigDecimal("50"), new BigDecimal("52"),
				SortField.PRICE, Sort.Direction.ASC, FIRST_PAGE, PAGE_SIZE);

		PageResponse<ProductResponse> result = queryService.find(filter);

		assertThat(result.items()).extracting(ProductResponse::title)
				.containsExactly("Soumission", "A Light in the Attic");
		assertThat(result.totalElements()).isEqualTo(2);
	}

	@Test
	void filtersByTitleIgnoringCase() {
		ProductFilter filter = new ProductFilter("VELVET", null, null,
				SortField.TITLE, Sort.Direction.ASC, FIRST_PAGE, PAGE_SIZE);

		PageResponse<ProductResponse> result = queryService.find(filter);

		assertThat(result.items()).extracting(ProductResponse::title).containsExactly("Tipping the Velvet");
	}

	@Test
	void sortsDescendingAndPaginates() {
		int pageSize = 3;
		ProductFilter filter = new ProductFilter(null, null, null,
				SortField.PRICE, Sort.Direction.DESC, 1, pageSize);

		PageResponse<ProductResponse> result = queryService.find(filter);

		assertThat(result.items()).extracting(ProductResponse::title).containsExactly("Sharp Objects");
		assertThat(result.totalElements()).isEqualTo(4);
		assertThat(result.totalPages()).isEqualTo(2);
	}

	private Product product(String title, String price) {
		String url = "https://books.toscrape.com/catalogue/" + title.toLowerCase().replace(' ', '-') + "/index.html";
		Product product = new Product(url);
		product.update(new ParsedProduct(url, title, new BigDecimal(price), ""), COLLECTED_AT);
		return product;
	}

}

package com.example.priceparser.web;

import java.math.BigDecimal;

import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.priceparser.product.PageResponse;
import com.example.priceparser.product.ProductFilter;
import com.example.priceparser.product.ProductQueryService;
import com.example.priceparser.product.ProductResponse;
import com.example.priceparser.product.SortField;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
public class AnswerController {

	private static final int MAX_PAGE_SIZE = 100;

	private final ProductQueryService productQueryService;

	public AnswerController(ProductQueryService productQueryService) {
		this.productQueryService = productQueryService;
	}

	@GetMapping("/answer")
	public PageResponse<ProductResponse> answer(
			@RequestParam(required = false) String title,
			@RequestParam(required = false) BigDecimal minPrice,
			@RequestParam(required = false) BigDecimal maxPrice,
			@RequestParam(defaultValue = "COLLECTED_AT") SortField sortBy,
			@RequestParam(defaultValue = "DESC") Sort.Direction direction,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
		ProductFilter filter = new ProductFilter(title, minPrice, maxPrice, sortBy, direction, page, size);
		return productQueryService.find(filter);
	}

}

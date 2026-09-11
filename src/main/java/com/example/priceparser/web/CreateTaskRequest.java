package com.example.priceparser.web;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

public record CreateTaskRequest(
		@NotEmpty
		List<@NotBlank @Pattern(regexp = CreateTaskRequest.SOURCE_URL_REGEX,
				message = "Поддерживаются только ссылки на https://books.toscrape.com/") String> urls) {

	static final String SOURCE_URL_REGEX = "^https://books\\.toscrape\\.com/.*$";

}

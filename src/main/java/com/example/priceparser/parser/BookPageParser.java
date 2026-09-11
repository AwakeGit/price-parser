package com.example.priceparser.parser;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

@Component
public class BookPageParser {

	private static final Pattern PRODUCT_URL =
			Pattern.compile("^https://books\\.toscrape\\.com/catalogue/(?!category/)[^/]+_\\d+/index\\.html$");
	private static final Pattern NON_PRICE_CHARACTERS = Pattern.compile("[^0-9.]");

	private static final String PRODUCT_PAGE_SELECTOR = "article.product_page";
	private static final String PRODUCT_LINK_SELECTOR = "article.product_pod h3 a";
	private static final String NEXT_PAGE_SELECTOR = "li.next a";
	private static final String TITLE_SELECTOR = "div.product_main h1";
	private static final String PRICE_SELECTOR = "div.product_main p.price_color";
	private static final String DESCRIPTION_SELECTOR = "#product_description + p";
	private static final String HREF_ATTRIBUTE = "href";
	private static final String NO_DESCRIPTION = "";

	public boolean isProductUrl(String url) {
		return PRODUCT_URL.matcher(url).matches();
	}

	public ParsedProduct parseProduct(Document document) {
		if (document.selectFirst(PRODUCT_PAGE_SELECTOR) == null) {
			throw new IllegalArgumentException("Страница не является страницей товара: " + document.location());
		}
		String title = requireText(document, TITLE_SELECTOR);
		BigDecimal price = parsePrice(requireText(document, PRICE_SELECTOR));
		return new ParsedProduct(document.location(), title, price, findDescription(document));
	}

	public List<String> findProductLinks(Document document) {
		return document.select(PRODUCT_LINK_SELECTOR).stream()
				.map(link -> link.absUrl(HREF_ATTRIBUTE))
				.filter(url -> !url.isEmpty())
				.toList();
	}

	public Optional<String> findNextPageUrl(Document document) {
		return Optional.ofNullable(document.selectFirst(NEXT_PAGE_SELECTOR))
				.map(link -> link.absUrl(HREF_ATTRIBUTE))
				.filter(url -> !url.isEmpty());
	}

	private String requireText(Document document, String selector) {
		Element element = document.selectFirst(selector);
		if (element == null) {
			throw new IllegalArgumentException("Не найден элемент " + selector + " на странице " + document.location());
		}
		return element.text();
	}

	private String findDescription(Document document) {
		Element description = document.selectFirst(DESCRIPTION_SELECTOR);
		return description == null ? NO_DESCRIPTION : description.text();
	}

	private BigDecimal parsePrice(String priceText) {
		return new BigDecimal(NON_PRICE_CHARACTERS.matcher(priceText).replaceAll(""));
	}

}

package com.example.priceparser.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

class BookPageParserTest {

	private static final String PRODUCT_URL = "https://books.toscrape.com/catalogue/a-light-in-the-attic_1000/index.html";
	private static final String CATALOGUE_URL = "https://books.toscrape.com/catalogue/page-1.html";
	private static final String CATEGORY_URL = "https://books.toscrape.com/catalogue/category/books/mystery_3/index.html";

	private static final String PRODUCT_HTML = """
			<html><head><meta http-equiv="content-type" content="text/html; charset=UTF-8" /></head><body>
			<article class="product_page">
			  <div class="row"><div class="col-sm-6 product_main">
			    <h1>A Light in the Attic</h1>
			    <p class="price_color">£51.77</p>
			    <p class="instock availability">In stock (22 available)</p>
			  </div></div>
			  <div id="product_description" class="sub-header"><h2>Product Description</h2></div>
			  <p>It's hard to imagine a world without A Light in the Attic.</p>
			  <table class="table table-striped">
			    <tr><th>Price (excl. tax)</th><td>£51.77</td></tr>
			  </table>
			</article></body></html>
			""";

	private static final String CATALOGUE_HTML = """
			<html><body><section><ol class="row">
			  <li><article class="product_pod">
			    <h3><a href="a-light-in-the-attic_1000/index.html" title="A Light in the Attic">A Light in the ...</a></h3>
			    <div class="product_price"><p class="price_color">£51.77</p></div>
			  </article></li>
			  <li><article class="product_pod">
			    <h3><a href="tipping-the-velvet_999/index.html" title="Tipping the Velvet">Tipping the Velvet</a></h3>
			    <div class="product_price"><p class="price_color">£53.74</p></div>
			  </article></li>
			</ol>
			<ul class="pager">
			  <li class="current">Page 1 of 50</li>
			  <li class="next"><a href="page-2.html">next</a></li>
			</ul></section></body></html>
			""";

	private static final String LAST_CATEGORY_PAGE_HTML = """
			<html><body><section><ol class="row">
			  <li><article class="product_pod">
			    <h3><a href="../../../sharp-objects_997/index.html" title="Sharp Objects">Sharp Objects</a></h3>
			  </article></li>
			</ol>
			<ul class="pager">
			  <li class="previous"><a href="index.html">previous</a></li>
			</ul></section></body></html>
			""";

	private final BookPageParser parser = new BookPageParser();

	@Test
	void recognisesProductUrls() {
		assertThat(parser.isProductUrl(PRODUCT_URL)).isTrue();
		assertThat(parser.isProductUrl(CATEGORY_URL)).isFalse();
		assertThat(parser.isProductUrl(CATALOGUE_URL)).isFalse();
	}

	@Test
	void parsesProductPage() {
		Document document = Jsoup.parse(PRODUCT_HTML, PRODUCT_URL);

		ParsedProduct product = parser.parseProduct(document);

		assertThat(product.url()).isEqualTo(PRODUCT_URL);
		assertThat(product.title()).isEqualTo("A Light in the Attic");
		assertThat(product.price()).isEqualByComparingTo(new BigDecimal("51.77"));
		assertThat(product.description()).startsWith("It's hard to imagine");
	}

	@Test
	void rejectsCatalogueAsProductPage() {
		Document document = Jsoup.parse(CATALOGUE_HTML, CATALOGUE_URL);

		assertThatThrownBy(() -> parser.parseProduct(document))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void findsAbsoluteProductLinksAndNextPage() {
		Document document = Jsoup.parse(CATALOGUE_HTML, CATALOGUE_URL);

		assertThat(parser.findProductLinks(document)).containsExactly(
				"https://books.toscrape.com/catalogue/a-light-in-the-attic_1000/index.html",
				"https://books.toscrape.com/catalogue/tipping-the-velvet_999/index.html");
		assertThat(parser.findNextPageUrl(document)).contains("https://books.toscrape.com/catalogue/page-2.html");
	}

	@Test
	void resolvesCategoryLinksAndDetectsLastPage() {
		Document document = Jsoup.parse(LAST_CATEGORY_PAGE_HTML, CATEGORY_URL);

		assertThat(parser.findProductLinks(document))
				.containsExactly("https://books.toscrape.com/catalogue/sharp-objects_997/index.html");
		assertThat(parser.findNextPageUrl(document)).isEmpty();
	}

}

package com.example.priceparser.product;

import java.io.IOException;

import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import com.example.priceparser.client.PageFetcher;
import com.example.priceparser.parser.BookPageParser;
import com.example.priceparser.parser.ParsedProduct;

@Service
public class ProductProcessor {

	private final PageFetcher pageFetcher;
	private final BookPageParser pageParser;
	private final ProductStorage productStorage;

	public ProductProcessor(PageFetcher pageFetcher, BookPageParser pageParser, ProductStorage productStorage) {
		this.pageFetcher = pageFetcher;
		this.pageParser = pageParser;
		this.productStorage = productStorage;
	}

	public Product process(String productUrl) throws IOException {
		Document document = pageFetcher.fetch(productUrl);
		ParsedProduct parsedProduct = pageParser.parseProduct(document);
		return productStorage.save(parsedProduct);
	}

}

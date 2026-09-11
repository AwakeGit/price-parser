package com.example.priceparser.crawl;

import com.example.priceparser.client.PageFetcher;
import com.example.priceparser.config.ParserProperties;
import com.example.priceparser.logging.ActivityLog;
import com.example.priceparser.parser.BookPageParser;
import com.example.priceparser.product.ProductProcessor;

record CrawlDependencies(
		PageFetcher pageFetcher,
		BookPageParser pageParser,
		ProductProcessor productProcessor,
		ActivityLog activityLog,
		ParserProperties properties) {
}

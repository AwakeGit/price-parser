package com.example.priceparser.parser;

import java.math.BigDecimal;

public record ParsedProduct(String url, String title, BigDecimal price, String description) {
}

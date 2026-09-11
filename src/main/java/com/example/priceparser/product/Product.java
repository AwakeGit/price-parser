package com.example.priceparser.product;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.example.priceparser.parser.ParsedProduct;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "products")
public class Product {

	private static final int PRICE_PRECISION = 10;
	private static final int PRICE_SCALE = 2;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String url;

	@Column(nullable = false)
	private String title;

	@Column(nullable = false, precision = PRICE_PRECISION, scale = PRICE_SCALE)
	private BigDecimal price;

	@Lob
	private String description;

	@Column(nullable = false)
	private LocalDateTime collectedAt;

	protected Product() {
	}

	public Product(String url) {
		this.url = url;
	}

	public void update(ParsedProduct parsedProduct, LocalDateTime collectedAt) {
		this.title = parsedProduct.title();
		this.price = parsedProduct.price();
		this.description = parsedProduct.description();
		this.collectedAt = collectedAt;
	}

	public Long getId() {
		return id;
	}

	public String getUrl() {
		return url;
	}

	public String getTitle() {
		return title;
	}

	public BigDecimal getPrice() {
		return price;
	}

	public String getDescription() {
		return description;
	}

	public LocalDateTime getCollectedAt() {
		return collectedAt;
	}

}

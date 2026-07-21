package com.example.ragdemo.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "store_products")
public class StoreProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 180)
    private String subtitle;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(nullable = false, length = 40)
    private String category;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal originalPrice;

    @Column(nullable = false)
    private Integer stock;

    @Column(nullable = false)
    private Integer sales;

    @Column(nullable = false, precision = 2, scale = 1)
    private BigDecimal rating;

    @Column(nullable = false, length = 500)
    private String imageUrl;

    @Column(nullable = false)
    private Boolean featured;

    @Column(nullable = false, columnDefinition = "boolean default true")
    private Boolean active = true;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected StoreProduct() {
    }

    public StoreProduct(String code, String name, String subtitle, String description, String category,
            BigDecimal price, BigDecimal originalPrice, int stock, int sales, BigDecimal rating,
            String imageUrl, boolean featured) {
        this.code = code;
        this.name = name;
        this.subtitle = subtitle;
        this.description = description;
        this.category = category;
        this.price = price;
        this.originalPrice = originalPrice;
        this.stock = stock;
        this.sales = sales;
        this.rating = rating;
        this.imageUrl = imageUrl;
        this.featured = featured;
        this.active = true;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getSubtitle() { return subtitle; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getOriginalPrice() { return originalPrice; }
    public Integer getStock() { return stock; }
    public Integer getSales() { return sales; }
    public BigDecimal getRating() { return rating; }
    public String getImageUrl() { return imageUrl; }
    public Boolean getFeatured() { return featured; }
    public Boolean getActive() { return active; }

    public void setActive(boolean active) {
        this.active = active;
    }
}

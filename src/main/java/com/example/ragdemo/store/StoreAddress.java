package com.example.ragdemo.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Entity
@Table(name = "store_addresses", indexes = @Index(
        name = "idx_store_address_user", columnList = "user_id"))
public class StoreAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "receiver_name", nullable = false, length = 40)
    private String receiverName;

    @Column(name = "receiver_phone", nullable = false, length = 30)
    private String receiverPhone;

    @Column(length = 40)
    private String province;

    @Column(length = 40)
    private String city;

    @Column(length = 40)
    private String district;

    @Column(name = "detail_address", nullable = false, length = 240)
    private String detailAddress;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "is_default", nullable = false)
    private Boolean defaultAddress;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected StoreAddress() {
    }

    public StoreAddress(Long userId, StoreApiModels.SaveAddressRequest request, boolean defaultAddress) {
        this.userId = userId;
        this.createdAt = Instant.now();
        apply(request, defaultAddress);
    }

    public void apply(StoreApiModels.SaveAddressRequest request, boolean defaultAddress) {
        this.receiverName = request.receiverName().trim();
        this.receiverPhone = request.receiverPhone().trim();
        this.province = blankToNull(request.province());
        this.city = blankToNull(request.city());
        this.district = blankToNull(request.district());
        this.detailAddress = request.detailAddress().trim();
        this.postalCode = blankToNull(request.postalCode());
        this.defaultAddress = defaultAddress;
        this.updatedAt = Instant.now();
    }

    public void setDefaultAddress(boolean defaultAddress) {
        this.defaultAddress = defaultAddress;
        this.updatedAt = Instant.now();
    }

    public String fullAddress() {
        return Stream.of(province, city, district, detailAddress)
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining(" "));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getReceiverName() { return receiverName; }
    public String getReceiverPhone() { return receiverPhone; }
    public String getProvince() { return province; }
    public String getCity() { return city; }
    public String getDistrict() { return district; }
    public String getDetailAddress() { return detailAddress; }
    public String getPostalCode() { return postalCode; }
    public Boolean getDefaultAddress() { return defaultAddress; }
    public Instant getUpdatedAt() { return updatedAt; }
}

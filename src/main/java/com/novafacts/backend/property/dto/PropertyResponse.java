package com.novafacts.backend.property.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PropertyResponse {

    private Long id;
    private String name;
    private String address;
    private String city;
    private Integer capacity;
    private BigDecimal pricePerNight;
    private LocalDateTime createdAt;

    public PropertyResponse(Long id, String name, String address, String city,
                            Integer capacity, BigDecimal pricePerNight, LocalDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.city = city;
        this.capacity = capacity;
        this.pricePerNight = pricePerNight;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public String getCity() { return city; }
    public Integer getCapacity() { return capacity; }
    public BigDecimal getPricePerNight() { return pricePerNight; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}

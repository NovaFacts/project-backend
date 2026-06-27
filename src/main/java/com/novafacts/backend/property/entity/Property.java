package com.novafacts.backend.property.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "propiedad")
public class Property {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre", nullable = false, unique = true, length = 150)
    private String name;

    @Column(name = "direccion", nullable = false, length = 250)
    private String address;

    @Column(name = "ciudad", nullable = false, length = 100)
    private String city;

    @Column(name = "capacidad", nullable = false)
    private Integer capacity;

    @Column(name = "precio_por_noche", nullable = false, precision = 15, scale = 2)
    private BigDecimal pricePerNight;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Property() {}

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }

    public BigDecimal getPricePerNight() { return pricePerNight; }
    public void setPricePerNight(BigDecimal pricePerNight) { this.pricePerNight = pricePerNight; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}

package com.example.springcaching.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * DTO for creating / updating a {@link com.example.springcaching.model.Product}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "Product name must not be blank")
    @Size(max = 200)
    private String name;

    @Size(max = 1000)
    private String description;

    @NotNull(message = "Price must not be null")
    @DecimalMin(value = "0.01", message = "Price must be greater than 0")
    private BigDecimal price;

    @Min(value = 0, message = "Stock quantity must be non-negative")
    @Builder.Default
    private Integer stockQuantity = 0;

    private Long categoryId;
}

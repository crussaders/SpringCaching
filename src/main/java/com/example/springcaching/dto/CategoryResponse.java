package com.example.springcaching.dto;

import lombok.*;

import java.io.Serializable;
import java.util.List;

/**
 * Read-only projection of a {@link com.example.springcaching.model.Category}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode
@ToString
public class CategoryResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String description;
    private int productCount;
    private List<ProductResponse> products;
}

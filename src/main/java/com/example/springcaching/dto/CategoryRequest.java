package com.example.springcaching.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.io.Serializable;

/**
 * DTO for creating / updating a {@link com.example.springcaching.model.Category}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "Category name must not be blank")
    @Size(max = 100)
    private String name;

    @Size(max = 500)
    private String description;
}

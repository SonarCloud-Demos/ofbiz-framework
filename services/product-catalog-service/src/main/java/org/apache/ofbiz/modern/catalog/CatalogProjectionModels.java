package org.apache.ofbiz.modern.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

final class CatalogProjectionModels {
    private CatalogProjectionModels() { }

    record CategoryRecord(
            @NotBlank @Size(max = 64) String categoryId,
            @Size(max = 64) String parentCategoryId,
            @NotBlank @Size(max = 255) String name,
            @Size(max = 4000) String description,
            @Size(max = 2048) String imageUrl,
            BigDecimal sequenceNum,
            Instant fromDate,
            Instant thruDate,
            @NotBlank @Pattern(regexp = "^[a-fA-F0-9]{64}$") String sourceChecksum) { }

    record ProductRecord(
            @NotBlank @Size(max = 64) String productId,
            @NotBlank @Size(max = 255) String name,
            @Size(max = 4000) String description,
            @Size(max = 2048) String imageUrl,
            @NotBlank @Size(max = 32) String productType,
            boolean virtual,
            boolean variant,
            @NotBlank @Pattern(regexp = "^[a-fA-F0-9]{64}$") String sourceChecksum) { }

    record MembershipRecord(
            @NotBlank @Size(max = 64) String categoryId,
            @NotBlank @Size(max = 64) String productId,
            BigDecimal sequenceNum,
            @NotNull Instant fromDate,
            Instant thruDate) {
        String recordId() {
            return categoryId + "|" + productId + "|" + fromDate.toEpochMilli();
        }
    }

    record ProjectionBatch(
            @NotBlank @Size(max = 64) String source,
            @NotBlank @Size(max = 512) String cursor,
            @Size(max = 64) String snapshotRunId,
            @Valid @Size(max = 500) List<CategoryRecord> categories,
            @Valid @Size(max = 500) List<ProductRecord> products,
            @Valid @Size(max = 1000) List<MembershipRecord> memberships) {
        ProjectionBatch {
            categories = categories == null ? List.of() : List.copyOf(categories);
            products = products == null ? List.of() : List.copyOf(products);
            memberships = memberships == null ? List.of() : List.copyOf(memberships);
        }
    }

    record SnapshotCompletion(@MinZero long sourceCount) { }

    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
    @java.lang.annotation.Target({java.lang.annotation.ElementType.FIELD, java.lang.annotation.ElementType.PARAMETER})
    @jakarta.validation.Constraint(validatedBy = MinZeroValidator.class)
    @interface MinZero {
        String message() default "must be zero or greater";
        Class<?>[] groups() default {};
        Class<? extends jakarta.validation.Payload>[] payload() default {};
    }

    static final class MinZeroValidator implements jakarta.validation.ConstraintValidator<MinZero, Long> {
        @Override public boolean isValid(Long value, jakarta.validation.ConstraintValidatorContext context) {
            return value != null && value >= 0;
        }
    }
}

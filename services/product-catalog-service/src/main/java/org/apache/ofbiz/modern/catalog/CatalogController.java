package org.apache.ofbiz.modern.catalog;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.apache.ofbiz.modern.catalog.CatalogModels.CategoryDetail;
import org.apache.ofbiz.modern.catalog.CatalogModels.ProductPage;
import org.apache.ofbiz.modern.catalog.CatalogModels.ProductSummary;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Validated
@RestController
@RequestMapping("/api/catalog/v1")
class CatalogController {
    private static final String ID = "^[A-Za-z0-9][A-Za-z0-9_.-]*$";
    private final CatalogRepository repository;

    CatalogController(CatalogRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/categories/{categoryId}")
    CategoryDetail category(
            @PathVariable @Pattern(regexp = ID) @Size(max = 64) String categoryId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String suppliedCorrelationId) {
        var category = repository.category(categoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
        return new CategoryDetail(category.categoryId(), category.name(), category.description(), category.imageUrl(),
                repository.childCategories(categoryId), correlationId(suppliedCorrelationId));
    }

    @GetMapping("/categories/{categoryId}/products")
    ProductPage browse(
            @PathVariable @Pattern(regexp = ID) @Size(max = 64) String categoryId,
            @RequestParam(required = false) @Size(max = 512) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "catalog") @Pattern(regexp = "catalog|name") String sort,
            @RequestHeader(value = "X-Correlation-ID", required = false) String suppliedCorrelationId) {
        if (repository.category(categoryId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found");
        }
        int offset = decodeCursor(cursor);
        return page(repository.browse(categoryId, offset, limit + 1, sort), limit, offset,
                correlationId(suppliedCorrelationId));
    }

    @GetMapping("/products/{productId}/summary")
    ProductSummary product(
            @PathVariable @Pattern(regexp = ID) @Size(max = 64) String productId) {
        return repository.product(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
    }

    @GetMapping("/search")
    ProductPage search(
            @RequestParam @Size(min = 1, max = 200) String q,
            @RequestParam(required = false) @Pattern(regexp = ID) @Size(max = 64) String categoryId,
            @RequestParam(required = false) @Size(max = 512) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "catalog") @Pattern(regexp = "catalog|name") String sort,
            @RequestHeader(value = "X-Correlation-ID", required = false) String suppliedCorrelationId) {
        int offset = decodeCursor(cursor);
        return page(repository.search(q.strip(), categoryId, offset, limit + 1, sort), limit, offset,
                correlationId(suppliedCorrelationId));
    }

    static ProductPage page(List<ProductSummary> fetched, int limit, int offset, String correlationId) {
        boolean more = fetched.size() > limit;
        List<ProductSummary> items = more ? List.copyOf(fetched.subList(0, limit)) : List.copyOf(fetched);
        String next = more ? encodeCursor(offset + limit) : null;
        return new ProductPage(items, next, correlationId);
    }

    static int decodeCursor(String cursor) {
        if (cursor == null) return 0;
        try {
            String value = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int offset = Integer.parseInt(value);
            if (offset < 0 || offset > 1_000_000) throw new IllegalArgumentException();
            return offset;
        } catch (IllegalArgumentException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid cursor");
        }
    }

    private static String encodeCursor(int value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(Integer.toString(value).getBytes(StandardCharsets.UTF_8));
    }

    private static String correlationId(String supplied) {
        if (supplied != null && supplied.matches("[A-Za-z0-9._-]{1,128}")) return supplied;
        return UUID.randomUUID().toString();
    }
}

package org.apache.ofbiz.modern.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.apache.ofbiz.modern.catalog.CatalogProjectionModels.ProjectionBatch;
import org.apache.ofbiz.modern.catalog.CatalogProjectionModels.SnapshotCompletion;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Validated
@RestController
@RequestMapping("/internal/catalog/projection")
class CatalogProjectionController {
    private static final String ID = "^[A-Za-z0-9][A-Za-z0-9_.-]*$";
    private final CatalogProjectionService service;
    private final byte[] ingestionKey;

    CatalogProjectionController(CatalogProjectionService service, @Value("${catalog.ingestion-key}") String ingestionKey) {
        this.service = service;
        this.ingestionKey = ingestionKey.getBytes(StandardCharsets.UTF_8);
        if (this.ingestionKey.length < 32) throw new IllegalStateException("catalog ingestion key must contain at least 32 bytes");
    }

    @PostMapping("/batches")
    Map<String, Object> ingest(
            @RequestHeader("X-Catalog-Ingestion-Key") String suppliedKey,
            @Valid @RequestBody ProjectionBatch batch) {
        authorize(suppliedKey);
        return service.apply(batch);
    }

    @PostMapping("/snapshots/{runId}/complete")
    Map<String, Object> complete(
            @RequestHeader("X-Catalog-Ingestion-Key") String suppliedKey,
            @PathVariable @Pattern(regexp = ID) @Size(max = 64) String runId,
            @RequestParam @Pattern(regexp = ID) @Size(max = 64) String source,
            @Valid @RequestBody SnapshotCompletion completion) {
        authorize(suppliedKey);
        return service.finish(runId, source, completion.sourceCount());
    }

    private void authorize(String suppliedKey) {
        if (!MessageDigest.isEqual(ingestionKey, suppliedKey.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
    }
}

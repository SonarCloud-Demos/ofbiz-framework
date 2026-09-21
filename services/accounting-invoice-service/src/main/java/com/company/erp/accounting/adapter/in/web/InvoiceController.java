package com.company.erp.accounting.adapter.in.web;

import com.company.erp.accounting.application.port.in.InvoiceUseCase;
import com.company.erp.accounting.domain.InvoiceModels.HeaderCommand;
import com.company.erp.accounting.domain.InvoiceModels.Page;
import com.company.erp.accounting.domain.InvoiceModels.Search;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounting/invoices")
public class InvoiceController {
    private final InvoiceUseCase invoices;
    public InvoiceController(InvoiceUseCase invoices) { this.invoices = invoices; }

    @GetMapping("/health") public Map<String, String> health() { return Map.of("status", "UP", "service", "modern-accounting-invoice-service"); }
    @GetMapping public Page list(@RequestParam(defaultValue="20") int limit, @RequestParam(defaultValue="0") int offset,
            @RequestParam(required=false) String invoiceId, @RequestParam(required=false) String invoiceTypeId,
            @RequestParam(required=false) String statusId, @RequestParam(required=false) String partyIdFrom,
            @RequestParam(required=false) String partyId) {
        return invoices.search(new Search(Math.min(Math.max(limit,1),100), Math.max(offset,0), invoiceId, invoiceTypeId, statusId, partyIdFrom, partyId));
    }
    @GetMapping("/{invoiceId}") public Object detail(@PathVariable String invoiceId) { return invoices.find(invoiceId); }
    @PostMapping public ResponseEntity<Object> create(@RequestBody HeaderCommand command) { return ResponseEntity.status(HttpStatus.CREATED).body(invoices.create(command)); }
    @PutMapping("/{invoiceId}") public Object update(@PathVariable String invoiceId, @RequestBody HeaderCommand command) { return invoices.update(invoiceId, command); }
    @DeleteMapping("/{invoiceId}") public ResponseEntity<Void> delete(@PathVariable String invoiceId) { invoices.delete(invoiceId); return ResponseEntity.noContent().build(); }
}

package com.company.erp.accounting.application;

import com.company.erp.accounting.application.port.in.InvoiceUseCase;
import com.company.erp.accounting.application.port.out.InvoiceRepositoryPort;
import com.company.erp.accounting.domain.InvoiceModels.Detail;
import com.company.erp.accounting.domain.InvoiceModels.HeaderCommand;
import com.company.erp.accounting.domain.InvoiceModels.Page;
import com.company.erp.accounting.domain.InvoiceModels.Search;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceService implements InvoiceUseCase {
    private final InvoiceRepositoryPort repository;

    public InvoiceService(InvoiceRepositoryPort repository) { this.repository = repository; }
    public Page search(Search search) { return repository.search(search); }
    public Detail find(String id) { return repository.find(id); }
    @Transactional public Detail create(HeaderCommand command) {
        requireId(command.invoiceId()); repository.insert(command); return repository.find(command.invoiceId());
    }
    @Transactional public Detail update(String id, HeaderCommand command) {
        requireId(id); repository.update(id, command); return repository.find(id);
    }
    @Transactional public void delete(String id) { requireId(id); repository.deleteGuarded(id); }
    private static void requireId(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("invoiceId is required");
    }
}

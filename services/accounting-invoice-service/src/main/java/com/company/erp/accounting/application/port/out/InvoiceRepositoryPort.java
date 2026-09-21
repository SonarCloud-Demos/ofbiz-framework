package com.company.erp.accounting.application.port.out;

import com.company.erp.accounting.domain.InvoiceModels.Detail;
import com.company.erp.accounting.domain.InvoiceModels.HeaderCommand;
import com.company.erp.accounting.domain.InvoiceModels.Page;
import com.company.erp.accounting.domain.InvoiceModels.Search;

public interface InvoiceRepositoryPort {
    Page search(Search search);
    Detail find(String invoiceId);
    void insert(HeaderCommand command);
    void update(String invoiceId, HeaderCommand command);
    void deleteGuarded(String invoiceId);
}

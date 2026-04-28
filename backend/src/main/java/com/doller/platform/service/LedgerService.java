package com.doller.platform.service;

import com.doller.platform.domain.LedgerEntry;
import com.doller.platform.repo.LedgerEntryRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class LedgerService {
    private final LedgerEntryRepository ledgerRepo;

    public LedgerService(LedgerEntryRepository ledgerRepo) {
        this.ledgerRepo = ledgerRepo;
    }

    public void post(LocalDateTime time, String account, BigDecimal debit, BigDecimal credit, String refType, Long refId, String narration) {
        ledgerRepo.save(LedgerEntry.builder()
                .entryTime(time)
                .accountCode(account)
                .debit(debit)
                .credit(credit)
                .referenceType(refType)
                .referenceId(refId)
                .narration(narration)
                .build());
    }
}

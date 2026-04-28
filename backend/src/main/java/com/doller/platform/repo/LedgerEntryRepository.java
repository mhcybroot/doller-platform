package com.doller.platform.repo;

import com.doller.platform.domain.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
    List<LedgerEntry> findByEntryTimeBetween(LocalDateTime from, LocalDateTime to);

    @Query("select coalesce(sum(l.debit - l.credit),0) from LedgerEntry l where l.accountCode = :account and l.entryTime between :from and :to")
    BigDecimal netForAccount(String account, LocalDateTime from, LocalDateTime to);
}

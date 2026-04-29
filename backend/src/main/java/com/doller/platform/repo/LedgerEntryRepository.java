package com.doller.platform.repo;

import com.doller.platform.domain.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
    List<LedgerEntry> findByEntryTimeBetween(LocalDateTime from, LocalDateTime to);
    List<LedgerEntry> findByAccountCodeAndEntryTimeLessThanEqual(String accountCode, LocalDateTime to);
    List<LedgerEntry> findByAccountCodeAndEntryTimeBetween(String accountCode, LocalDateTime from, LocalDateTime to);
    List<LedgerEntry> findByAccountCodeStartingWithAndEntryTimeLessThanEqual(String prefix, LocalDateTime to);
    List<LedgerEntry> findByAccountCodeStartingWithAndEntryTimeBetween(String prefix, LocalDateTime from, LocalDateTime to);
    List<LedgerEntry> findByReferenceTypeAndEntryTimeBetween(String referenceType, LocalDateTime from, LocalDateTime to);
    List<LedgerEntry> findByReferenceTypeAndReferenceId(String referenceType, Long referenceId);

    @Query("select coalesce(sum(l.debit - l.credit),0) from LedgerEntry l where l.accountCode = :account and l.entryTime between :from and :to")
    BigDecimal netForAccount(@Param("account") String account, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select coalesce(sum(l.debit - l.credit),0) from LedgerEntry l where l.accountCode like concat(:prefix, '%') and l.entryTime between :from and :to")
    BigDecimal netForAccountPrefix(@Param("prefix") String prefix, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select coalesce(sum(l.debit - l.credit),0) from LedgerEntry l where l.accountCode like concat(:prefix, '%') and l.entryTime <= :to")
    BigDecimal netForAccountPrefixUntil(@Param("prefix") String prefix, @Param("to") LocalDateTime to);

    @Query("select coalesce(sum(l.debit - l.credit),0) from LedgerEntry l where l.accountCode = :account and l.entryTime <= :to")
    BigDecimal netForAccountUntil(@Param("account") String account, @Param("to") LocalDateTime to);
}

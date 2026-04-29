package com.doller.platform.repo;

import com.doller.platform.domain.Party;
import com.doller.platform.domain.TradeDeal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TradeDealRepository extends JpaRepository<TradeDeal, Long> {
    List<TradeDeal> findByDeletedFalse();
    Optional<TradeDeal> findByIdAndDeletedFalse(Long id);
    List<TradeDeal> findByDealTimeBetweenAndDeletedFalse(LocalDateTime from, LocalDateTime to);
    List<TradeDeal> findByPartyAndDealTimeBetweenAndDeletedFalse(Party party, LocalDateTime from, LocalDateTime to);
}

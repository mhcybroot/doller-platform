package com.doller.platform.repo;

import com.doller.platform.domain.Party;
import com.doller.platform.domain.TradeDeal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface TradeDealRepository extends JpaRepository<TradeDeal, Long> {
    List<TradeDeal> findByDealTimeBetween(LocalDateTime from, LocalDateTime to);
    List<TradeDeal> findByPartyAndDealTimeBetween(Party party, LocalDateTime from, LocalDateTime to);
}

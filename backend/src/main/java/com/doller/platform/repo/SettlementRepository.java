package com.doller.platform.repo;

import com.doller.platform.domain.Party;
import com.doller.platform.domain.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {
    List<Settlement> findBySettlementTimeBetween(LocalDateTime from, LocalDateTime to);
    List<Settlement> findByPartyOrderBySettlementTimeAsc(Party party);
}

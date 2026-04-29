package com.doller.platform.repo;

import com.doller.platform.domain.Party;
import com.doller.platform.domain.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {
    List<Settlement> findByDeletedFalse();
    Optional<Settlement> findByIdAndDeletedFalse(Long id);
    List<Settlement> findBySettlementTimeBetweenAndDeletedFalse(LocalDateTime from, LocalDateTime to);
    List<Settlement> findByPartyAndDeletedFalseOrderBySettlementTimeAsc(Party party);
}

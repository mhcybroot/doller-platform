package com.doller.platform.repo;

import com.doller.platform.domain.StatementSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface StatementSnapshotRepository extends JpaRepository<StatementSnapshot, Long> {
    Optional<StatementSnapshot> findByBusinessDate(LocalDate date);
    Optional<StatementSnapshot> findTopByBusinessDateLessThanEqualOrderByBusinessDateDesc(LocalDate date);
    List<StatementSnapshot> findByBusinessDateBetweenOrderByBusinessDateAsc(LocalDate from, LocalDate to);
}

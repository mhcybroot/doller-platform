package com.doller.platform.repo;

import com.doller.platform.domain.Expense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    List<Expense> findByDeletedFalse();
    Optional<Expense> findByIdAndDeletedFalse(Long id);
    List<Expense> findByExpenseTimeBetweenAndDeletedFalse(LocalDateTime from, LocalDateTime to);
}

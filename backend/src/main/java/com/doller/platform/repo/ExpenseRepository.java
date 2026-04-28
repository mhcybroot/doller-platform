package com.doller.platform.repo;

import com.doller.platform.domain.Expense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    List<Expense> findByExpenseTimeBetween(LocalDateTime from, LocalDateTime to);
}

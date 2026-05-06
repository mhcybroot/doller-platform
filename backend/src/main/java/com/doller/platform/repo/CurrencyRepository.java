package com.doller.platform.repo;

import com.doller.platform.domain.Currency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CurrencyRepository extends JpaRepository<Currency, Long> {
    List<Currency> findByDeletedFalseOrderByCodeAsc();
    Optional<Currency> findByIdAndDeletedFalse(Long id);
    Optional<Currency> findByCodeAndDeletedFalse(String code);
    boolean existsByCodeAndDeletedFalse(String code);
}

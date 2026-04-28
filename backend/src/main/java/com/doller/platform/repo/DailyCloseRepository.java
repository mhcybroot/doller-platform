package com.doller.platform.repo;

import com.doller.platform.domain.DailyClose;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface DailyCloseRepository extends JpaRepository<DailyClose, Long> {
    Optional<DailyClose> findByBusinessDate(LocalDate date);
}

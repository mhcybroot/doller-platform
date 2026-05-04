package com.doller.platform.repo;

import com.doller.platform.domain.OwnerCustomEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OwnerCustomEntryRepository extends JpaRepository<OwnerCustomEntry, Long> {
    Optional<OwnerCustomEntry> findByIdAndDeletedFalse(Long id);
    List<OwnerCustomEntry> findByCompanyIdAndDeletedFalseAndEntryTimeBetween(
            Long companyId, LocalDateTime from, LocalDateTime to
    );
}


package com.doller.platform.repo;

import com.doller.platform.domain.Party;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PartyRepository extends JpaRepository<Party, Long> {
    List<Party> findByDeletedFalse();
    Optional<Party> findByIdAndDeletedFalse(Long id);
}

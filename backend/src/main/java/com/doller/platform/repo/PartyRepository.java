package com.doller.platform.repo;

import com.doller.platform.domain.Party;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartyRepository extends JpaRepository<Party, Long> {
}

package com.doller.platform.repo;

import com.doller.platform.domain.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    List<Company> findByDeletedFalseOrderByNameAsc();
    Optional<Company> findByIdAndDeletedFalse(Long id);
}


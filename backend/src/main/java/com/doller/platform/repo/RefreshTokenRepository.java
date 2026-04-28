package com.doller.platform.repo;

import com.doller.platform.domain.RefreshToken;
import com.doller.platform.domain.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHashAndRevokedFalse(String tokenHash);
    void deleteByUser(UserAccount user);
}

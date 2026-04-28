package com.doller.platform;

import com.doller.platform.domain.Party;
import com.doller.platform.domain.UserAccount;
import com.doller.platform.domain.enums.DealType;
import com.doller.platform.domain.enums.Role;
import com.doller.platform.dto.TradingDtos;
import com.doller.platform.repo.PartyRepository;
import com.doller.platform.repo.UserAccountRepository;
import com.doller.platform.service.TradingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class TradingServiceTest {
    @Autowired TradingService tradingService;
    @Autowired PartyRepository partyRepository;
    @Autowired UserAccountRepository userAccountRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach
    void ensureOwner() {
        if (userAccountRepository.findByUsernameAndActiveTrue("owner").isEmpty()) {
            userAccountRepository.save(UserAccount.builder()
                    .username("owner")
                    .passwordHash(passwordEncoder.encode("owner123"))
                    .role(Role.OWNER)
                    .active(true)
                    .mustChangePassword(false)
                    .build());
        }
    }

    @Test
    void dayCloseComputesPnL() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("owner", null));
        Party p = partyRepository.save(Party.builder().name("X").build());

        tradingService.createDeal(new TradingDtos.DealCreateRequest(DealType.BUY, p.getId(), new BigDecimal("100"), new BigDecimal("120"), LocalDateTime.now(), "buy"));
        tradingService.createDeal(new TradingDtos.DealCreateRequest(DealType.SELL, p.getId(), new BigDecimal("50"), new BigDecimal("122"), LocalDateTime.now(), "sell"));
        tradingService.createExpense(new TradingDtos.ExpenseCreateRequest(com.doller.platform.domain.enums.ExpenseType.DAILY_OVERHEAD, null, new BigDecimal("100"), LocalDateTime.now(), "staff", ""));

        var preview = tradingService.previewDayClose(LocalDate.now());
        assertEquals(new BigDecimal("-6000.00"), preview.realizedProfitLossBdt());
    }
}

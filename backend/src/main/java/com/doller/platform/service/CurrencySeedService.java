package com.doller.platform.service;

import com.doller.platform.domain.Currency;
import com.doller.platform.repo.CurrencyRepository;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CurrencySeedService {
    private final CurrencyRepository currencyRepository;

    public CurrencySeedService(CurrencyRepository currencyRepository) {
        this.currencyRepository = currencyRepository;
    }

    @PostConstruct
    @Transactional
    public void seedDefaults() {
        if (!currencyRepository.findByDeletedFalseOrderByCodeAsc().isEmpty()) {
            return;
        }
        currencyRepository.saveAll(List.of(
                currency("USD", "US DOLLAR"),
                currency("USD_SA", "US DOLLAR SAUDI"),
                currency("USD_ID", "US DOLLAR INDONESIA"),
                currency("USD_MY", "US DOLLAR MALAYSIA"),
                currency("USD_HK", "US DOLLAR HONGKONG"),
                currency("USD_CN", "US DOLLAR CHINA"),
                currency("USD_MV", "US DOLLAR MALDIVES"),
                currency("EXCHANGE_FEE", "EXCHANGE FEE"),
                currency("RMB", "RMB"),
                currency("MYR", "RINGGIT"),
                currency("AED", "DIRHAM"),
                currency("SGD", "SIN DOLLAR"),
                currency("GBP", "POUND"),
                currency("AUD", "AUS DOLLAR"),
                currency("CAD", "CANADIAN DOLLAR"),
                currency("SAR", "SAUDI RIYAL"),
                currency("HKD", "HONGKONG DOLLAR"),
                currency("EUR", "EURO"),
                currency("INR", "INDIAN RUPEE")
        ));
    }

    private Currency currency(String code, String displayName) {
        return Currency.builder()
                .code(code)
                .displayName(displayName)
                .deleted(false)
                .build();
    }
}

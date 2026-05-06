package com.doller.platform.web;

import com.doller.platform.dto.MasterDataDtos;
import com.doller.platform.service.MasterDataService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class MasterDataController {
    private final MasterDataService service;

    public MasterDataController(MasterDataService service) {
        this.service = service;
    }

    @GetMapping("/users")
    public List<MasterDataDtos.UserResponse> users() { return service.users(); }

    @PostMapping("/users")
    public MasterDataDtos.UserResponse createUser(@Valid @RequestBody MasterDataDtos.UserCreateRequest req) {
        return service.createUser(req.username(), req.password(), req.role());
    }

    @PostMapping("/users/{id}/deactivate")
    public void deactivate(@PathVariable("id") Long id) { service.deactivateUser(id); }

    @GetMapping("/parties")
    public List<MasterDataDtos.PartyResponse> parties() {
        return service.parties().stream()
                .map(this::toPartyResponse)
                .toList();
    }

    @GetMapping("/currencies")
    public List<MasterDataDtos.CurrencyResponse> currencies() {
        return service.currencies();
    }

    @PostMapping("/parties")
    @PreAuthorize("hasAnyRole('OWNER','STAFF')")
    public MasterDataDtos.PartyResponse createParty(@Valid @RequestBody MasterDataDtos.PartyCreateRequest req) {
        return toPartyResponse(service.createParty(req));
    }

    @PutMapping("/parties/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public MasterDataDtos.PartyResponse updateParty(@PathVariable("id") Long id, @Valid @RequestBody MasterDataDtos.PartyUpdateRequest req) {
        return toPartyResponse(service.updateParty(id, req));
    }

    @DeleteMapping("/parties/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public void deleteParty(@PathVariable("id") Long id) {
        service.deleteParty(id);
    }

    @PreAuthorize("hasRole('OWNER')")
    @GetMapping("/owner/currencies")
    public List<MasterDataDtos.CurrencyResponse> ownerCurrencies() {
        return service.currencies();
    }

    @PreAuthorize("hasRole('OWNER')")
    @PostMapping("/owner/currencies")
    public MasterDataDtos.CurrencyResponse createCurrency(@Valid @RequestBody MasterDataDtos.CurrencyCreateRequest req) {
        return service.createCurrency(req);
    }

    @PreAuthorize("hasRole('OWNER')")
    @PutMapping("/owner/currencies/{id}")
    public MasterDataDtos.CurrencyResponse updateCurrency(@PathVariable("id") Long id, @Valid @RequestBody MasterDataDtos.CurrencyUpdateRequest req) {
        return service.updateCurrency(id, req);
    }

    @PreAuthorize("hasRole('OWNER')")
    @DeleteMapping("/owner/currencies/{id}")
    public void deleteCurrency(@PathVariable("id") Long id) {
        service.deleteCurrency(id);
    }

    private MasterDataDtos.PartyResponse toPartyResponse(com.doller.platform.domain.Party party) {
        return new MasterDataDtos.PartyResponse(
                party.getId(),
                party.getName(),
                party.getPhone(),
                party.getAddress(),
                party.getNotes()
        );
    }
}

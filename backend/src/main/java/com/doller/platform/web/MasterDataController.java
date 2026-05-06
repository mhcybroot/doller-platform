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

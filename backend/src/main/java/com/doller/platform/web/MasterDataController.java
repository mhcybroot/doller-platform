package com.doller.platform.web;

import com.doller.platform.domain.Party;
import com.doller.platform.domain.UserAccount;
import com.doller.platform.domain.enums.Role;
import com.doller.platform.dto.MasterDataDtos;
import com.doller.platform.service.MasterDataService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class MasterDataController {
    private final MasterDataService service;

    public MasterDataController(MasterDataService service) {
        this.service = service;
    }

    @GetMapping("/users")
    public List<UserAccount> users() { return service.users(); }

    @PostMapping("/users")
    public UserAccount createUser(@RequestBody Map<String, String> req) {
        return service.createUser(req.get("username"), req.get("password"), Role.valueOf(req.get("role")));
    }

    @PostMapping("/users/{id}/deactivate")
    public void deactivate(@PathVariable("id") Long id) { service.deactivateUser(id); }

    @GetMapping("/parties")
    public List<Party> parties() { return service.parties(); }

    @PostMapping("/parties")
    @PreAuthorize("hasAnyRole('OWNER','STAFF')")
    public Party createParty(@Valid @RequestBody MasterDataDtos.PartyCreateRequest req) {
        return service.createParty(req);
    }

    @PutMapping("/parties/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public Party updateParty(@PathVariable("id") Long id, @Valid @RequestBody MasterDataDtos.PartyUpdateRequest req) {
        return service.updateParty(id, req);
    }

    @DeleteMapping("/parties/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public void deleteParty(@PathVariable("id") Long id) {
        service.deleteParty(id);
    }
}

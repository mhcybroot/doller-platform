package com.doller.platform.web;

import com.doller.platform.domain.Party;
import com.doller.platform.domain.UserAccount;
import com.doller.platform.domain.enums.Role;
import com.doller.platform.service.MasterDataService;
import jakarta.validation.constraints.NotBlank;
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
    public void deactivate(@PathVariable Long id) { service.deactivateUser(id); }

    @GetMapping("/parties")
    public List<Party> parties() { return service.parties(); }

    @PostMapping("/parties")
    public Party createParty(@RequestBody Party party) { return service.createParty(party); }
}

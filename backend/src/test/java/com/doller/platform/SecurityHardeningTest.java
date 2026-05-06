package com.doller.platform;

import com.doller.platform.domain.UserAccount;
import com.doller.platform.domain.Party;
import com.doller.platform.domain.enums.Role;
import com.doller.platform.repo.PartyRepository;
import com.doller.platform.repo.RefreshTokenRepository;
import com.doller.platform.repo.TradeDealRepository;
import com.doller.platform.repo.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityHardeningTest {
    @Autowired MockMvc mockMvc;
    @Autowired UserAccountRepository userAccountRepository;
    @Autowired PartyRepository partyRepository;
    @Autowired TradeDealRepository tradeDealRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach
    void reset() {
        refreshTokenRepository.deleteAll();
        tradeDealRepository.deleteAll();
        partyRepository.deleteAll();
        userAccountRepository.deleteAll();
    }

    @Test
    void doesNotSeedDefaultOwnerOnStartup() {
        org.junit.jupiter.api.Assertions.assertTrue(userAccountRepository.findByUsername("admin").isEmpty());
    }

    @Test
    void userApiDoesNotExposePasswordHash() throws Exception {
        userAccountRepository.save(UserAccount.builder()
                .username("owner")
                .passwordHash(passwordEncoder.encode("OwnerPass123!"))
                .role(Role.OWNER)
                .active(true)
                .mustChangePassword(false)
                .build());

        String token = login("owner", "OwnerPass123!");

        mockMvc.perform(get("/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("passwordHash"))));
    }

    @Test
    void bootstrapEndpointRequiresToken() throws Exception {
        mockMvc.perform(post("/auth/init-owner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"owner","password":"OwnerPass123!"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Bootstrap token required"));
    }

    @Test
    void swaggerIsBlockedWhenDocsDisabled() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidJwtReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/users").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void staffCannotAccessOwnerEndpoints() throws Exception {
        userAccountRepository.save(UserAccount.builder()
                .username("staff")
                .passwordHash(passwordEncoder.encode("StaffPass123!"))
                .role(Role.STAFF)
                .active(true)
                .mustChangePassword(false)
                .build());

        String token = login("staff", "StaffPass123!");

        mockMvc.perform(get("/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void weakPasswordsAreRejectedForBootstrap() throws Exception {
        mockMvc.perform(post("/auth/init-owner")
                        .header("X-Bootstrap-Token", "test-bootstrap-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"owner","password":"weakpass"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void repeatedFailedLoginsAreRateLimited() throws Exception {
        userAccountRepository.save(UserAccount.builder()
                .username("owner")
                .passwordHash(passwordEncoder.encode("OwnerPass123!"))
                .role(Role.OWNER)
                .active(true)
                .mustChangePassword(false)
                .build());

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username":"owner","password":"WrongPass123!"}
                                    """))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"owner","password":"WrongPass123!"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void securityHeadersArePresent() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().exists("Content-Security-Policy"));
    }

    @Test
    void dealMutationResponseUsesSanitizedDto() throws Exception {
        userAccountRepository.save(UserAccount.builder()
                .username("owner")
                .passwordHash(passwordEncoder.encode("OwnerPass123!"))
                .role(Role.OWNER)
                .active(true)
                .mustChangePassword(false)
                .build());
        Party party = partyRepository.save(Party.builder()
                .name("Alpha Traders")
                .phone("01700000000")
                .address("Dhaka")
                .notes("Preferred")
                .deleted(false)
                .build());

        String token = login("owner", "OwnerPass123!");

        mockMvc.perform(post("/deals")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dealType":"BUY",
                                  "partyId":%d,
                                  "currencyCode":"USD",
                                  "quantity":10,
                                  "bdtRate":120,
                                  "dealTime":"2026-05-06T10:15:30",
                                  "notes":"test deal"
                                }
                                """.formatted(party.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partyName").value("Alpha Traders"))
                .andExpect(content().string(not(containsString("passwordHash"))))
                .andExpect(content().string(not(containsString("createdBy"))));
    }

    private String login(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return body.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
    }
}

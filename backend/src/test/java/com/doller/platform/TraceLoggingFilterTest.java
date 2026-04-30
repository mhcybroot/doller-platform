package com.doller.platform;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TraceLoggingFilterTest {
    @Autowired
    MockMvc mockMvc;

    @Test
    void preservesIncomingTraceId() throws Exception {
        mockMvc.perform(get("/swagger-ui.html")
                        .header("X-Trace-Id", "trace-test-123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("X-Trace-Id", "trace-test-123"));
    }

    @Test
    void generatesTraceIdWhenMissing() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("X-Trace-Id", not(blankOrNullString())));
    }
}

package com.packplan;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class StatusControllerTest {
    @Autowired MockMvc mvc;

    @Test
    void reportsApplicationReadinessWithoutClaimingCatalogSupport() throws Exception {
        mvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.application").value("PackPlan"))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.milestone").value(1))
                .andExpect(jsonPath("$.catalogReady").value(false));
    }
}

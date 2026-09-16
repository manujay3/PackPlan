package com.packplan;

import org.junit.jupiter.api.Test;
import org.neo4j.driver.*;
import org.neo4j.driver.exceptions.ServiceUnavailableException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class DatabaseStatusControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean Driver driver;

    @Test
    void reportsReadyAfterQueryAndClosesSession() throws Exception {
        var session = mock(Session.class);
        var result = mock(Result.class);
        when(driver.session(any(SessionConfig.class))).thenReturn(session);
        when(session.run(eq("RETURN 1 AS connected"), any(TransactionConfig.class))).thenReturn(result);
        mvc.perform(get("/api/database/status"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().json("{\"status\":\"UP\"}"));
        verify(result).consume();
        verify(session).close();
    }

    @Test
    void databaseOutageDoesNotTakeDownApplicationOrLeakDriverDetails() throws Exception {
        when(driver.session(any(SessionConfig.class)))
                .thenThrow(new ServiceUnavailableException("Private connection details"));
        mvc.perform(get("/api/database/status"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string("{\"status\":\"DOWN\"}"));
        mvc.perform(get("/api/status")).andExpect(status().isOk())
                .andExpect(jsonPath("$.catalogReady").value(false));
    }
}

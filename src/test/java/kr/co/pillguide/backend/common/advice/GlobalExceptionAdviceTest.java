package kr.co.pillguide.backend.common.advice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionAdviceTest {
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ErrorController())
                .setControllerAdvice(new GlobalExceptionAdvice())
                .build();
    }

    @Test
    void unknownRouteReturns404() throws Exception {
        mvc.perform(get("/unmapped"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void missingStaticResourceReturns404() throws Exception {
        mvc.perform(get("/missing-static"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void unexpectedFailureStillReturns500() throws Exception {
        mvc.perform(get("/unexpected-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500));
    }

    @RestController
    static class ErrorController {
        @GetMapping("/missing-static")
        String missingStatic() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "/missing-static", "missing-static");
        }

        @GetMapping("/unexpected-error")
        String unexpectedError() {
            throw new IllegalStateException("test failure");
        }
    }
}

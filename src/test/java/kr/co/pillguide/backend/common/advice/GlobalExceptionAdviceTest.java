package kr.co.pillguide.backend.common.advice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import kr.co.pillguide.backend.api.scan.exception.ScanException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

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
        @GetMapping("/ai-busy")
        String busy() { throw new ScanException(HttpStatus.SERVICE_UNAVAILABLE, "분석 서비스가 바쁩니다.", 1); }

        @GetMapping("/upload-limit")
        String uploadLimit() { throw new MaxUploadSizeExceededException(10 * 1024 * 1024); }

        @GetMapping("/multipart-error")
        String multipartError() { throw new MultipartException("private-file-path"); }

        @GetMapping("/missing-static")
        String missingStatic() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "/missing-static", "missing-static");
        }

        @GetMapping("/unexpected-error")
        String unexpectedError() {
            throw new IllegalStateException("test failure");
        }

        @GetMapping("/internal-argument")
        String internalArgument() {
            throw new IllegalArgumentException("private-server-path-and-key");
        }

        @PostMapping(value = "/body", consumes = "application/json")
        String body(@RequestBody java.util.Map<String, String> value) { return "ok"; }
    }

    @Test
    void internalArgumentIs500WithoutInternalDetails() throws Exception {
        mvc.perform(get("/internal-argument"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."));
    }

    @Test
    void unsupportedMediaStatusMatchesBody() throws Exception {
        mvc.perform(post("/body").contentType("text/plain").content("sensitive-input"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
    }

    @Test
    void malformedJsonIs400WithoutInputReflection() throws Exception {
        mvc.perform(post("/body").contentType("application/json").content("{secret"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("요청 형식이나 필수 입력을 확인해 주세요."));
    }

    @Test
    void scanBusyPreservesRetryAfterAndCommonResponse() throws Exception {
        mvc.perform(get("/ai-busy")).andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "1"))
                .andExpect(jsonPath("$.status").value(503)).andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void multipartSizeAndFormatHaveDistinctSafeErrors() throws Exception {
        mvc.perform(get("/upload-limit")).andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.status").value(413));
        mvc.perform(get("/multipart-error")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("사진 업로드 요청 형식을 확인해 주세요."));
    }
}

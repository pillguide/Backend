package kr.co.pillguide.backend.api.drug.reference;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.pillguide.backend.api.member.service.FcmService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import static org.junit.jupiter.api.Assertions.*;

@Tag("mfds-live")
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=${PG_TEST_URL}",
        "spring.datasource.username=${PG_TEST_USER:pillguide_test}",
        "spring.datasource.password=${PG_TEST_PASSWORD:pillguide_test}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "reference.sync.enabled=false",
        "reference.mfds.easy-key=${MFDS_EASY_KEY}",
        "reference.mfds.permit-key=${MFDS_PERMIT_KEY}",
        "reference.mfds.dur-product-key=${MFDS_DUR_PRODUCT_KEY}",
        "reference.mfds.dur-ingredient-key=${MFDS_DUR_INGREDIENT_KEY}"
})
class ReferenceLiveTest {
    @Autowired ReferenceSyncCoordinator coordinator;
    @Autowired ReferenceSyncRepository states;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @MockitoBean FcmService fcm;

    @Test
    void freshCompleteReferenceIsPublishedWithFourReviewedPairs() throws Exception {
        assertEquals("jdbc:postgresql://127.0.0.1:55432/pillguide_test", System.getenv("PG_TEST_URL"));
        var report = new LinkedHashMap<String, Object>();
        try {
            assertTrue(coordinator.synchronize(false));
            ReferenceSyncState state = states.findById(1L).orElseThrow();
            assertNotNull(state.getCurrentVersion());
            assertNull(state.getActiveRun());
            assertNull(state.getLastFailureCode());
            assertEquals(150, jdbc.queryForObject("select count(*) from drug where source_as_of is not null", Integer.class));
            assertEquals(4, jdbc.queryForObject("select count(*) from drug_interaction", Integer.class));
            assertEquals(4, jdbc.queryForObject("select count(*) from drug_interaction where reference_version = ? and source_hash = summary_source_hash",
                    Integer.class, state.getCurrentVersion()));
            report.put("version", state.getCurrentVersion());
            report.put("sourceAsOf", state.getSourceAsOf().toString());
            report.put("products", 150);
            report.put("pairs", 4);
            report.put("statusCounts", jdbc.queryForList("select info_status, authorization_status, count(*) as count from drug group by info_status, authorization_status"));
            report.put("outcome", "PASSED");
        } finally {
            states.findById(1L).ifPresent(state -> {
                report.put("requestsUsed", state.getRequestsUsed());
                report.put("failureCode", state.getLastFailureCode());
            });
            String reportPath = System.getenv("MFDS_LIVE_REPORT");
            if (reportPath != null) Files.writeString(Path.of(reportPath), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report));
        }
    }
}

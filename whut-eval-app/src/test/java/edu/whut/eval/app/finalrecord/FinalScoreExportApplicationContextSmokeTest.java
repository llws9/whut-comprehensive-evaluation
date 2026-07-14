package edu.whut.eval.app.finalrecord;

import edu.whut.eval.app.WhutComprehensiveEvaluationApplication;
import edu.whut.eval.application.finalrecord.exporting.FinalScoreExportApplicationService;
import edu.whut.eval.application.finalrecord.exporting.FinalScoreExportWorkbookWriter;
import edu.whut.eval.infra.finalrecord.exporting.PoiFinalScoreExportWorkbookWriter;
import edu.whut.eval.interfaces.admin.AdminFinalScoreExportController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = WhutComprehensiveEvaluationApplication.class)
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:final_score_export_context_smoke;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "infra.security.jwt.enabled=true",
        "infra.security.jwt.algorithm=HS256",
        "infra.security.jwt.issuer=whut-eval",
        "infra.security.jwt.audience=whut-eval-api",
        "infra.security.jwt.access-token-ttl-seconds=7200",
        "infra.security.jwt.refresh-token-ttl-seconds=604800",
        "infra.security.jwt.clock-skew-seconds=60",
        "infra.security.jwt.secret=test-jwt-secret-should-be-long-enough-1234567890",
        "infra.security.jwt.user-id-claim=uid",
        "infra.security.jwt.user-no-claim=uno",
        "infra.security.jwt.user-name-claim=uname",
        "infra.security.jwt.identity-claim=identity",
        "infra.security.jwt.roles-claim=roles",
        "infra.security.jwt.authorities-claim=authorities",
        "infra.security.jwt.token-type-claim=token_type",
        "infra.security.jwt.access-token-type=access",
        "infra.security.jwt.refresh-token-type=refresh"
})
class FinalScoreExportApplicationContextSmokeTest {

    @Autowired
    private FinalScoreExportApplicationService service;

    @Autowired
    private FinalScoreExportWorkbookWriter workbookWriter;

    @Autowired
    private PoiFinalScoreExportWorkbookWriter poiWorkbookWriter;

    @Autowired
    private AdminFinalScoreExportController controller;

    @Test
    void shouldAssembleFinalScoreExportBeansThroughRealApplicationContext() {
        assertThat(service).isNotNull();
        assertThat(workbookWriter).isNotNull();
        assertThat(poiWorkbookWriter).isSameAs(workbookWriter);
        assertThat(controller).isNotNull();
    }
}

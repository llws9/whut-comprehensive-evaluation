package edu.whut.eval.app.query;

import edu.whut.eval.application.admin.query.OrgUnitTreeView;
import edu.whut.eval.application.admin.query.PermissionDictionaryView;
import edu.whut.eval.application.admin.service.AdminDictionaryQueryApplicationService;
import edu.whut.eval.application.application.query.ApplicationRecordView;
import edu.whut.eval.application.application.service.ApplicationQueryApplicationService;
import edu.whut.eval.application.score.query.ScoreRecordView;
import edu.whut.eval.application.score.service.ScoreQueryApplicationService;
import edu.whut.eval.common.exception.AccessDeniedAppException;
import edu.whut.eval.domain.application.query.ApplicationPageQuery;
import edu.whut.eval.domain.score.query.ScorePageQuery;
import edu.whut.eval.domain.shared.PageResult;
import edu.whut.eval.interfaces.admin.AdminQueryController;
import edu.whut.eval.interfaces.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminQueryController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = AdminQueryControllerWebMvcTest.TestApplication.class)
@Import({
        AdminQueryController.class,
        GlobalExceptionHandler.class,
        AdminQueryControllerWebMvcTest.TestBeans.class
})
class AdminQueryControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StubApplicationQueryApplicationService applicationQueryApplicationService;

    @Autowired
    private StubScoreQueryApplicationService scoreQueryApplicationService;

    @Autowired
    private StubAdminDictionaryQueryApplicationService adminDictionaryQueryApplicationService;

    @Test
    void shouldReturnPagedApplications() throws Exception {
        applicationQueryApplicationService.willReturn(new PageResult<>(1, List.of(
                        new ApplicationRecordView(9001L, 1001L, 3001L, "/1/3001/", "INTELLECTUAL", "ACADEMIC_LECTURE")
                )));

        mockMvc.perform(get("/api/admin/query/applications")
                        .param("pageNo", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].applicationId").value(9001))
                .andExpect(jsonPath("$.data.records[0].categoryCode").value("INTELLECTUAL"));
    }

    @Test
    void shouldReturnPagedScores() throws Exception {
        scoreQueryApplicationService.willReturn(new PageResult<>(1, List.of(
                        new ScoreRecordView(8001L, 1001L, 3001L, "/1/3001/", "ACADEMIC", "LECTURE", "2025-2026")
                )));

        mockMvc.perform(get("/api/admin/query/scores")
                        .param("pageNo", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].scoreId").value(8001))
                .andExpect(jsonPath("$.data.records[0].academicYear").value("2025-2026"));
    }

    @Test
    void shouldReturn403WhenApplicationQueryIsDenied() throws Exception {
        applicationQueryApplicationService.willThrow(new AccessDeniedAppException("当前用户无权限访问申请列表"));

        mockMvc.perform(get("/api/admin/query/applications"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH-4030"))
                .andExpect(jsonPath("$.message").value("当前用户无权限访问申请列表"));
    }

    @Test
    void shouldReturnPermissionDictionary() throws Exception {
        adminDictionaryQueryApplicationService.willReturnPermissions(List.of(new PermissionDictionaryView(
                "permission.manage",
                "权限管理",
                "manage",
                null,
                "ACTIVE"
        )));

        mockMvc.perform(get("/api/admin/permissions")
                        .param("keyword", "review")
                        .param("module", "manage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].permissionCode").value("permission.manage"))
                .andExpect(jsonPath("$.data[0].description").value(nullValue()))
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"));
    }

    @Test
    void shouldReturnOrgTree() throws Exception {
        adminDictionaryQueryApplicationService.willReturnOrgTree(List.of(new OrgUnitTreeView(
                2002L,
                "CS",
                "计算机与人工智能学院",
                "COLLEGE",
                "ACTIVE",
                List.of(new OrgUnitTreeView(
                        2009L,
                        "CS2201",
                        "计科2201",
                        "CLASS",
                        "ACTIVE",
                        List.of()
                ))
        )));

        mockMvc.perform(get("/api/admin/org-units/tree")
                        .param("rootId", "2002")
                        .param("unitType", "CLASS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(2002))
                .andExpect(jsonPath("$.data[0].children[0].id").value(2009))
                .andExpect(jsonPath("$.data[0].children[0].unitType").value("CLASS"));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }

    @TestConfiguration
    static class TestBeans {

        @Bean
        StubApplicationQueryApplicationService applicationQueryApplicationService() {
            return new StubApplicationQueryApplicationService();
        }

        @Bean
        StubScoreQueryApplicationService scoreQueryApplicationService() {
            return new StubScoreQueryApplicationService();
        }

        @Bean
        StubAdminDictionaryQueryApplicationService adminDictionaryQueryApplicationService() {
            return new StubAdminDictionaryQueryApplicationService();
        }
    }

    static class StubApplicationQueryApplicationService extends ApplicationQueryApplicationService {

        private PageResult<ApplicationRecordView> nextResult = new PageResult<>(0, List.of());
        private RuntimeException nextException;

        StubApplicationQueryApplicationService() {
            super(null, null);
        }

        void willReturn(PageResult<ApplicationRecordView> result) {
            this.nextResult = result;
            this.nextException = null;
        }

        void willThrow(RuntimeException exception) {
            this.nextException = exception;
        }

        @Override
        public PageResult<ApplicationRecordView> pageAccessibleApplications(ApplicationPageQuery query, String permissionCode) {
            if (nextException != null) {
                throw nextException;
            }
            return nextResult;
        }
    }

    static class StubScoreQueryApplicationService extends ScoreQueryApplicationService {

        private PageResult<ScoreRecordView> nextResult = new PageResult<>(0, List.of());

        StubScoreQueryApplicationService() {
            super(null, null);
        }

        void willReturn(PageResult<ScoreRecordView> result) {
            this.nextResult = result;
        }

        @Override
        public PageResult<ScoreRecordView> pageAccessibleScores(ScorePageQuery query, String permissionCode) {
            return nextResult;
        }
    }

    static class StubAdminDictionaryQueryApplicationService extends AdminDictionaryQueryApplicationService {

        private List<PermissionDictionaryView> permissions = List.of();
        private List<OrgUnitTreeView> orgTree = List.of();

        StubAdminDictionaryQueryApplicationService() {
            super(null, null);
        }

        void willReturnPermissions(List<PermissionDictionaryView> permissions) {
            this.permissions = permissions;
        }

        void willReturnOrgTree(List<OrgUnitTreeView> orgTree) {
            this.orgTree = orgTree;
        }

        @Override
        public List<PermissionDictionaryView> listPermissions(String keyword, String module, String status) {
            return permissions;
        }

        @Override
        public List<OrgUnitTreeView> listOrgUnitTree(Long rootId, String unitType, boolean includeDisabled) {
            return orgTree;
        }
    }
}

package edu.whut.eval.app.iam;

import edu.whut.eval.application.iam.command.CreateScopeRuleCommand;
import edu.whut.eval.application.iam.query.ScopeRuleAdminView;
import edu.whut.eval.application.iam.service.DefaultScopeRuleAdminApplicationService;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.common.exception.ResourceNotFoundException;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.iam.model.IamRoleAssignmentDetail;
import edu.whut.eval.domain.iam.model.IamScopeRuleDetail;
import edu.whut.eval.domain.iam.repository.RoleAssignmentAdminRepository;
import edu.whut.eval.domain.iam.repository.ScopeRuleAdminRepository;
import edu.whut.eval.domain.org.model.OrgUnit;
import edu.whut.eval.domain.org.repository.OrgUnitLookupRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;

@ExtendWith(MockitoExtension.class)
class DefaultScopeRuleAdminApplicationServiceTest {

    @Mock
    private RoleAssignmentAdminRepository roleAssignmentAdminRepository;

    @Mock
    private ScopeRuleAdminRepository scopeRuleAdminRepository;

    @Mock
    private OrgUnitLookupRepository orgUnitLookupRepository;

    @InjectMocks
    private DefaultScopeRuleAdminApplicationService service;

    @Test
    void shouldListScopeRulesAfterAssignmentExists() {
        given(roleAssignmentAdminRepository.findDetailById(70021L)).willReturn(Optional.of(activeAssignment()));
        given(scopeRuleAdminRepository.findByAssignmentId(70021L)).willReturn(List.of(
                new IamScopeRuleDetail(
                        81001L,
                        70021L,
                        "manage.review.view",
                        "ORG_SUBTREE",
                        2002L,
                        "计算机与人工智能学院",
                        null,
                        null,
                        null,
                        100,
                        "ACTIVE",
                        null
                )
        ));

        List<ScopeRuleAdminView> result = service.listScopeRules(70021L);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().scopeType()).isEqualTo("ORG_SUBTREE");
    }

    @Test
    void shouldRejectListScopeRulesWhenAssignmentDoesNotExist() {
        given(roleAssignmentAdminRepository.findDetailById(70021L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.listScopeRules(70021L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("角色分配不存在: 70021");
    }

    @Test
    void shouldCreateCategoryScopeRuleWhenCommandIsValid() {
        given(roleAssignmentAdminRepository.findDetailById(70021L)).willReturn(Optional.of(activeAssignment()));
        given(scopeRuleAdminRepository.assignmentRoleOwnsPermission(70021L, "manage.review.view")).willReturn(true);
        given(scopeRuleAdminRepository.existsSemanticDuplicate(70021L, "manage.review.view", "CATEGORY", null, "MORAL", null, null))
                .willReturn(false);
        willReturn(new IamScopeRuleDetail(
                81004L,
                70021L,
                "manage.review.view",
                "CATEGORY",
                null,
                null,
                "MORAL",
                null,
                null,
                90,
                "ACTIVE",
                "2026-05-20T10:40:00"
        )).given(scopeRuleAdminRepository).create(
                anyLong(),
                anyString(),
                anyString(),
                isNull(),
                isNull(),
                eq("MORAL"),
                isNull(),
                isNull(),
                eq(90),
                eq("ACTIVE")
        );

        ScopeRuleAdminView result = service.createScopeRule(70021L, new CreateScopeRuleCommand(
                "manage.review.view",
                "CATEGORY",
                null,
                "MORAL",
                null,
                null,
                90
        ));

        assertThat(result.scopeRuleId()).isEqualTo(81004L);
        assertThat(result.categoryCode()).isEqualTo("MORAL");
    }

    @Test
    void shouldRejectCreateScopeRuleWhenScopeFieldsDoNotMatch() {
        given(roleAssignmentAdminRepository.findDetailById(70021L)).willReturn(Optional.of(activeAssignment()));

        assertThatThrownBy(() -> service.createScopeRule(70021L, new CreateScopeRuleCommand(
                "manage.review.view",
                "CATEGORY",
                null,
                null,
                null,
                null,
                90
        )))
                .isInstanceOf(ValidationException.class)
                .hasMessage("CATEGORY 范围必须指定 categoryCode");
    }

    @Test
    void shouldRejectCreateScopeRuleWhenAssignmentIsNotCurrentlyActive() {
        given(roleAssignmentAdminRepository.findDetailById(70021L)).willReturn(Optional.of(inactiveAssignment()));

        assertThatThrownBy(() -> service.createScopeRule(70021L, new CreateScopeRuleCommand(
                "manage.review.view",
                "CATEGORY",
                null,
                "MORAL",
                null,
                null,
                90
        )))
                .isInstanceOf(ConflictException.class)
                .hasMessage("仅 ACTIVE 状态的角色分配允许新增范围规则");
    }

    @Test
    void shouldRejectCreateScopeRuleWhenRoleDoesNotOwnPermission() {
        given(roleAssignmentAdminRepository.findDetailById(70021L)).willReturn(Optional.of(activeAssignment()));
        given(scopeRuleAdminRepository.assignmentRoleOwnsPermission(70021L, "manage.review.export")).willReturn(false);

        assertThatThrownBy(() -> service.createScopeRule(70021L, new CreateScopeRuleCommand(
                "manage.review.export",
                "CATEGORY",
                null,
                "MORAL",
                null,
                null,
                90
        )))
                .isInstanceOf(ValidationException.class)
                .hasMessage("角色未拥有权限码: manage.review.export");
    }

    @Test
    void shouldRejectCreateScopeRuleWhenSemanticDuplicateExists() {
        given(roleAssignmentAdminRepository.findDetailById(70021L)).willReturn(Optional.of(activeAssignment()));
        given(scopeRuleAdminRepository.assignmentRoleOwnsPermission(70021L, "manage.review.view")).willReturn(true);
        given(orgUnitLookupRepository.findById(2002L)).willReturn(Optional.of(
                new OrgUnit(2002L, 1L, "COLLEGE", "CS", "计算机与人工智能学院", "/1/2002/", "ACTIVE")
        ));
        given(scopeRuleAdminRepository.existsSemanticDuplicate(70021L, "manage.review.view", "ORG_SUBTREE", 2002L, null, null, null))
                .willReturn(true);

        assertThatThrownBy(() -> service.createScopeRule(70021L, new CreateScopeRuleCommand(
                "manage.review.view",
                "ORG_SUBTREE",
                2002L,
                null,
                null,
                null,
                100
        )))
                .isInstanceOf(ConflictException.class)
                .hasMessage("相同语义的范围规则已存在");
    }

    @Test
    void shouldRequireExpressionJsonForCustomExpressionScope() {
        given(roleAssignmentAdminRepository.findDetailById(70021L)).willReturn(Optional.of(activeAssignment()));

        assertThatThrownBy(() -> service.createScopeRule(70021L, new CreateScopeRuleCommand(
                "manage.review.view",
                "CUSTOM_EXPRESSION",
                null,
                null,
                null,
                Map.of(),
                80
        )))
                .isInstanceOf(ValidationException.class)
                .hasMessage("CUSTOM_EXPRESSION 范围必须指定 expressionJson");
    }

    private IamRoleAssignmentDetail inactiveAssignment() {
        return new IamRoleAssignmentDetail(
                70021L,
                1010L,
                "COUNSELOR",
                "辅导员",
                2002L,
                "计算机与人工智能学院",
                "INACTIVE",
                "2026-05-20T00:00:00",
                "2027-07-01T00:00:00",
                "MANUAL",
                null
        );
    }

    private IamRoleAssignmentDetail activeAssignment() {
        return new IamRoleAssignmentDetail(
                70021L,
                1010L,
                "COUNSELOR",
                "辅导员",
                2002L,
                "计算机与人工智能学院",
                "ACTIVE",
                "2026-05-20T00:00:00",
                "2027-07-01T00:00:00",
                "MANUAL",
                null
        );
    }
}

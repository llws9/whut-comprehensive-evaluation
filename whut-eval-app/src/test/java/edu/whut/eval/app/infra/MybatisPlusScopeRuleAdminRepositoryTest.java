package edu.whut.eval.app.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.whut.eval.domain.iam.model.IamScopeRuleDetail;
import edu.whut.eval.infra.persistence.entity.OrgUnitDO;
import edu.whut.eval.infra.persistence.mapper.IamScopeRuleMapper;
import edu.whut.eval.infra.persistence.mapper.OrgUnitMapper;
import edu.whut.eval.infra.persistence.repository.MybatisPlusScopeRuleAdminRepository;
import edu.whut.eval.infra.persistence.repository.row.IamScopeRuleAdminRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MybatisPlusScopeRuleAdminRepositoryTest {

    @Mock
    private IamScopeRuleMapper iamScopeRuleMapper;

    @Mock
    private OrgUnitMapper orgUnitMapper;

    private MybatisPlusScopeRuleAdminRepository repository;

    @BeforeEach
    void setUp() {
        repository = new MybatisPlusScopeRuleAdminRepository(iamScopeRuleMapper, new ObjectMapper());
    }

    @Test
    void shouldFindScopeRulesByAssignmentId() {
        IamScopeRuleAdminRow row = new IamScopeRuleAdminRow();
        row.setScopeRuleId(81001L);
        row.setAssignmentId(70021L);
        row.setPermissionCode("manage.review.view");
        row.setScopeType("ORG_SUBTREE");
        row.setOrgUnitId(2002L);
        row.setOrgUnitName("计算机与人工智能学院");
        row.setPriority(100);
        row.setStatus("ACTIVE");
        given(iamScopeRuleMapper.selectAdminRowsByAssignmentId(70021L)).willReturn(List.of(row));

        List<IamScopeRuleDetail> result = repository.findByAssignmentId(70021L);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().scopeType()).isEqualTo("ORG_SUBTREE");
        assertThat(result.getFirst().orgUnitName()).isEqualTo("计算机与人工智能学院");
    }

    @Test
    void shouldSerializeExpressionJsonWhenCreateScopeRule() {
        given(iamScopeRuleMapper.insert(any(edu.whut.eval.infra.persistence.entity.IamScopeRuleDO.class))).willAnswer(invocation -> {
            Object arg = invocation.getArgument(0);
            ((edu.whut.eval.infra.persistence.entity.IamScopeRuleDO) arg).setId(81004L);
            return 1;
        });

        IamScopeRuleDetail result = repository.create(
                70021L,
                "manage.review.view",
                "CUSTOM_EXPRESSION",
                2002L,
                "计算机与人工智能学院",
                null,
                null,
                Map.of("studentId", "2024305001"),
                80,
                "ACTIVE"
        );

        ArgumentCaptor<edu.whut.eval.infra.persistence.entity.IamScopeRuleDO> captor =
                ArgumentCaptor.forClass(edu.whut.eval.infra.persistence.entity.IamScopeRuleDO.class);
        verify(iamScopeRuleMapper).insert(captor.capture());

        assertThat(captor.getValue().getExpressionJson()).contains("studentId");
        assertThat(result.scopeRuleId()).isEqualTo(81004L);
        assertThat(result.orgUnitName()).isEqualTo("计算机与人工智能学院");
    }
}

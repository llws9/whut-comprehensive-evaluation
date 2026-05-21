package edu.whut.eval.app.iam;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import edu.whut.eval.domain.iam.query.UserAdminPageQuery;
import edu.whut.eval.infra.persistence.entity.IamRoleDO;
import edu.whut.eval.infra.persistence.entity.IamUserDO;
import edu.whut.eval.infra.persistence.entity.IamUserRoleAssignmentDO;
import edu.whut.eval.infra.persistence.entity.OrgMembershipDO;
import edu.whut.eval.infra.persistence.entity.OrgUnitDO;
import edu.whut.eval.infra.persistence.mapper.IamRoleMapper;
import edu.whut.eval.infra.persistence.mapper.IamUserMapper;
import edu.whut.eval.infra.persistence.mapper.IamUserRoleAssignmentMapper;
import edu.whut.eval.infra.persistence.mapper.OrgMembershipMapper;
import edu.whut.eval.infra.persistence.mapper.OrgUnitMapper;
import edu.whut.eval.infra.persistence.repository.MybatisPlusUserAdminQueryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class MybatisPlusUserAdminQueryRepositoryIntegrationTest {

    @Mock
    private IamUserMapper iamUserMapper;

    @Mock
    private OrgMembershipMapper orgMembershipMapper;

    @Mock
    private OrgUnitMapper orgUnitMapper;

    @Mock
    private IamUserRoleAssignmentMapper iamUserRoleAssignmentMapper;

    @Mock
    private IamRoleMapper iamRoleMapper;

    @InjectMocks
    private MybatisPlusUserAdminQueryRepository repository;

    @Test
    void shouldExcludeFutureAndExpiredAssignmentsFromRoleCodes() {
        IamUserDO user = new IamUserDO();
        user.setId(1010L);
        user.setUserNo("2024305001");
        user.setUserName("王老师");
        user.setStatus("ACTIVE");
        user.setCreatedAt(LocalDateTime.parse("2024-01-01T00:00:00"));
        Page<IamUserDO> page = Page.of(1, 10);
        page.setTotal(1);
        page.setRecords(List.of(user));
        given(iamUserMapper.selectPage(any(Page.class), any())).willReturn(page);

        OrgMembershipDO membership = new OrgMembershipDO();
        membership.setId(70021L);
        membership.setUserId(1010L);
        membership.setOrgUnitId(2002L);
        membership.setStatus("ACTIVE");
        given(orgMembershipMapper.selectList(any())).willReturn(List.of(membership));

        OrgUnitDO orgUnit = new OrgUnitDO();
        orgUnit.setId(2002L);
        orgUnit.setUnitName("计算机与人工智能学院");
        given(orgUnitMapper.selectBatchIds(any())).willReturn(List.of(orgUnit));

        given(iamUserRoleAssignmentMapper.selectList(any())).willReturn(List.of(
                assignment(80021L, 1010L, 21L, LocalDateTime.parse("2024-01-01T00:00:00"), null),
                assignment(80022L, 1010L, 22L, LocalDateTime.parse("2099-01-01T00:00:00"), null),
                assignment(80023L, 1010L, 23L, LocalDateTime.parse("2024-01-01T00:00:00"), LocalDateTime.parse("2024-02-01T00:00:00"))
        ));
        given(iamRoleMapper.selectBatchIds(any())).willReturn(List.of(
                role(21L, "COUNSELOR"),
                role(22L, "FUTURE_ROLE"),
                role(23L, "EXPIRED_ROLE")
        ));

        List<String> roleCodes = repository.pageUsers(new UserAdminPageQuery(1, 10, null, null, null))
                .records()
                .getFirst()
                .roleCodes();

        assertThat(roleCodes).containsExactly("COUNSELOR");
    }

    private IamUserRoleAssignmentDO assignment(Long id,
                                               Long userId,
                                               Long roleId,
                                               LocalDateTime effectiveFrom,
                                               LocalDateTime effectiveTo) {
        IamUserRoleAssignmentDO item = new IamUserRoleAssignmentDO();
        item.setId(id);
        item.setUserId(userId);
        item.setRoleId(roleId);
        item.setStatus("ACTIVE");
        item.setEffectiveFrom(effectiveFrom);
        item.setEffectiveTo(effectiveTo);
        return item;
    }

    private IamRoleDO role(Long id, String roleCode) {
        IamRoleDO role = new IamRoleDO();
        role.setId(id);
        role.setRoleCode(roleCode);
        role.setRoleName(roleCode);
        role.setStatus("ACTIVE");
        return role;
    }
}

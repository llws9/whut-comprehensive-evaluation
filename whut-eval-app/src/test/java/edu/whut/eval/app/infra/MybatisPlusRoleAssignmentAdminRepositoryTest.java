package edu.whut.eval.app.infra;

import edu.whut.eval.domain.iam.model.IamRoleAssignmentDetail;
import edu.whut.eval.infra.persistence.entity.IamRoleDO;
import edu.whut.eval.infra.persistence.entity.IamUserDO;
import edu.whut.eval.infra.persistence.entity.IamUserRoleAssignmentDO;
import edu.whut.eval.infra.persistence.entity.OrgUnitDO;
import edu.whut.eval.infra.persistence.mapper.IamRoleMapper;
import edu.whut.eval.infra.persistence.mapper.IamUserMapper;
import edu.whut.eval.infra.persistence.mapper.IamUserRoleAssignmentMapper;
import edu.whut.eval.infra.persistence.mapper.OrgUnitMapper;
import edu.whut.eval.infra.persistence.repository.MybatisPlusRoleAssignmentAdminRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MybatisPlusRoleAssignmentAdminRepositoryTest {

    @Mock
    private IamUserRoleAssignmentMapper assignmentMapper;

    @Mock
    private IamRoleMapper roleMapper;

    @Mock
    private IamUserMapper userMapper;

    @Mock
    private OrgUnitMapper orgUnitMapper;

    private MybatisPlusRoleAssignmentAdminRepository repository;

    @BeforeEach
    void setUp() {
        repository = new MybatisPlusRoleAssignmentAdminRepository(assignmentMapper, roleMapper, userMapper, orgUnitMapper);
    }

    @Test
    void shouldFindRoleAssignmentDetailById() {
        IamUserRoleAssignmentDO assignmentDO = new IamUserRoleAssignmentDO();
        assignmentDO.setId(70021L);
        assignmentDO.setUserId(1010L);
        assignmentDO.setRoleId(21L);
        assignmentDO.setOrgUnitId(2002L);
        assignmentDO.setStatus("ACTIVE");
        assignmentDO.setSourceType("MANUAL");
        assignmentDO.setEffectiveFrom(LocalDateTime.parse("2026-05-18T00:00:00"));
        assignmentDO.setEffectiveTo(LocalDateTime.parse("2027-07-01T00:00:00"));
        given(assignmentMapper.selectById(70021L)).willReturn(assignmentDO);

        IamRoleDO roleDO = new IamRoleDO();
        roleDO.setId(21L);
        roleDO.setRoleCode("COUNSELOR");
        roleDO.setRoleName("辅导员");
        roleDO.setStatus("ACTIVE");
        given(roleMapper.selectById(21L)).willReturn(roleDO);

        OrgUnitDO orgUnitDO = new OrgUnitDO();
        orgUnitDO.setId(2002L);
        orgUnitDO.setUnitName("计算机与人工智能学院");
        given(orgUnitMapper.selectById(2002L)).willReturn(orgUnitDO);

        Optional<IamRoleAssignmentDetail> result = repository.findDetailById(70021L);

        assertThat(result).isPresent();
        assertThat(result.get().assignmentId()).isEqualTo(70021L);
        assertThat(result.get().roleCode()).isEqualTo("COUNSELOR");
        assertThat(result.get().orgUnitName()).isEqualTo("计算机与人工智能学院");
    }

    @Test
    void shouldReturnTrueWhenActiveAssignmentExists() {
        IamRoleDO roleDO = new IamRoleDO();
        roleDO.setId(21L);
        roleDO.setRoleCode("COUNSELOR");
        given(roleMapper.selectOne(any())).willReturn(roleDO);
        given(assignmentMapper.selectCount(any())).willReturn(1L);

        boolean result = repository.existsActiveAssignment(1010L, "COUNSELOR", 2002L, 70022L);

        assertThat(result).isTrue();
    }

    @Test
    void shouldApplyEffectiveTimeWindowWhenCheckingDuplicateAssignment() {
        IamRoleDO roleDO = new IamRoleDO();
        roleDO.setId(21L);
        roleDO.setRoleCode("COUNSELOR");
        given(roleMapper.selectOne(any())).willReturn(roleDO);
        given(assignmentMapper.selectCount(any())).willReturn(1L);

        repository.existsActiveAssignment(1010L, "COUNSELOR", 2002L, null);

        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<IamUserRoleAssignmentDO>> captor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper.class);
        verify(assignmentMapper).selectCount(captor.capture());

        String sqlSegment = captor.getValue().getSqlSegment();
        assertThat(sqlSegment).contains("effective_from");
        assertThat(sqlSegment).contains("effective_to");
    }

    @Test
    void shouldPersistAssignedByWhenCreateRoleAssignment() {
        IamRoleDO roleDO = new IamRoleDO();
        roleDO.setId(21L);
        roleDO.setRoleCode("COUNSELOR");
        given(roleMapper.selectOne(any())).willReturn(roleDO);
        given(assignmentMapper.insert(any(IamUserRoleAssignmentDO.class))).willAnswer(invocation -> {
            IamUserRoleAssignmentDO arg = invocation.getArgument(0);
            arg.setId(70031L);
            return 1;
        });

        IamRoleAssignmentDetail result = repository.create(
                1010L,
                "COUNSELOR",
                "辅导员",
                2002L,
                "计算机与人工智能学院",
                "2026-05-18T00:00:00",
                "2027-07-01T00:00:00",
                "MANUAL",
                9001L,
                "ACTIVE"
        );

        ArgumentCaptor<IamUserRoleAssignmentDO> captor = ArgumentCaptor.forClass(IamUserRoleAssignmentDO.class);
        verify(assignmentMapper).insert(captor.capture());

        assertThat(captor.getValue().getAssignedBy()).isEqualTo(9001L);
        assertThat(result.assignmentId()).isEqualTo(70031L);
    }

    @Test
    void shouldRevokeAssignmentBySettingInactiveStatus() {
        IamUserRoleAssignmentDO assignmentDO = new IamUserRoleAssignmentDO();
        assignmentDO.setId(70021L);
        assignmentDO.setUserId(1010L);
        assignmentDO.setRoleId(21L);
        assignmentDO.setOrgUnitId(2002L);
        assignmentDO.setStatus("ACTIVE");
        assignmentDO.setSourceType("MANUAL");
        assignmentDO.setEffectiveFrom(LocalDateTime.parse("2026-05-18T00:00:00"));
        assignmentDO.setEffectiveTo(LocalDateTime.parse("2027-07-01T00:00:00"));
        given(assignmentMapper.selectById(70021L)).willReturn(assignmentDO);

        IamRoleDO roleDO = new IamRoleDO();
        roleDO.setId(21L);
        roleDO.setRoleCode("COUNSELOR");
        roleDO.setRoleName("辅导员");
        given(roleMapper.selectById(21L)).willReturn(roleDO);

        OrgUnitDO orgUnitDO = new OrgUnitDO();
        orgUnitDO.setId(2002L);
        orgUnitDO.setUnitName("计算机与人工智能学院");
        given(orgUnitMapper.selectById(2002L)).willReturn(orgUnitDO);

        IamRoleAssignmentDetail result = repository.revoke(70021L);

        assertThat(assignmentDO.getStatus()).isEqualTo("INACTIVE");
        assertThat(result.status()).isEqualTo("INACTIVE");
        verify(assignmentMapper).updateById(assignmentDO);
    }
}

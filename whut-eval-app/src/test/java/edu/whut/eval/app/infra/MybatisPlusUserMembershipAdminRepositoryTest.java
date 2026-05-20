package edu.whut.eval.app.infra;

import edu.whut.eval.domain.org.model.OrgMembership;
import edu.whut.eval.infra.persistence.entity.OrgMembershipDO;
import edu.whut.eval.infra.persistence.mapper.OrgMembershipMapper;
import edu.whut.eval.infra.persistence.repository.MybatisPlusUserMembershipAdminRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MybatisPlusUserMembershipAdminRepositoryTest {

    @Mock
    private OrgMembershipMapper orgMembershipMapper;

    private MybatisPlusUserMembershipAdminRepository repository;

    @BeforeEach
    void setUp() {
        repository = new MybatisPlusUserMembershipAdminRepository(orgMembershipMapper);
    }

    @Test
    void shouldMapActiveMembershipsWithPrimaryAndTimeFields() {
        OrgMembershipDO membershipDO = new OrgMembershipDO();
        membershipDO.setId(70021L);
        membershipDO.setUserId(1010L);
        membershipDO.setOrgUnitId(2002L);
        membershipDO.setMembershipType("IMPORT");
        membershipDO.setPrimary(true);
        membershipDO.setStatus("ACTIVE");
        membershipDO.setJoinedAt(LocalDateTime.parse("2024-01-01T00:00:00"));
        membershipDO.setLeftAt(null);
        given(orgMembershipMapper.selectList(any())).willReturn(List.of(membershipDO));

        List<OrgMembership> result = repository.findActiveMembershipsByUserId(1010L);

        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(70021L);
            assertThat(item.membershipType()).isEqualTo("IMPORT");
            assertThat(item.isPrimary()).isTrue();
            assertThat(item.joinedAt()).isEqualTo("2024-01-01T00:00");
        });
    }

    @Test
    void shouldInsertNewMembershipAndDeactivateRemovedMembership() {
        repository.replaceMemberships(
                1010L,
                List.of(new OrgMembership(null, 1010L, 2010L, "MANUAL", true, "ACTIVE", "2026-05-19T10:00:00", null)),
                List.of(new OrgMembership(70022L, 1010L, 2009L, "SYNC", false, "INACTIVE", "2024-02-01T00:00:00", "2026-05-19T10:00:00"))
        );

        ArgumentCaptor<OrgMembershipDO> insertCaptor = ArgumentCaptor.forClass(OrgMembershipDO.class);
        ArgumentCaptor<OrgMembershipDO> updateCaptor = ArgumentCaptor.forClass(OrgMembershipDO.class);
        verify(orgMembershipMapper).insert(insertCaptor.capture());
        verify(orgMembershipMapper).updateById(updateCaptor.capture());

        assertThat(insertCaptor.getValue().getMembershipType()).isEqualTo("MANUAL");
        assertThat(insertCaptor.getValue().getPrimary()).isTrue();
        assertThat(updateCaptor.getValue().getId()).isEqualTo(70022L);
        assertThat(updateCaptor.getValue().getStatus()).isEqualTo("INACTIVE");
        assertThat(updateCaptor.getValue().getLeftAt()).isEqualTo(LocalDateTime.parse("2026-05-19T10:00:00"));
    }
}

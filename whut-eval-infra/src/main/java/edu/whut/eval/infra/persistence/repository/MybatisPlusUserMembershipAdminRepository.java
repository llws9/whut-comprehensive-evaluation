package edu.whut.eval.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import edu.whut.eval.domain.org.model.OrgMembership;
import edu.whut.eval.domain.org.repository.UserMembershipAdminRepository;
import edu.whut.eval.infra.persistence.entity.OrgMembershipDO;
import edu.whut.eval.infra.persistence.mapper.OrgMembershipMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Repository
public class MybatisPlusUserMembershipAdminRepository implements UserMembershipAdminRepository {

    private final OrgMembershipMapper orgMembershipMapper;

    public MybatisPlusUserMembershipAdminRepository(OrgMembershipMapper orgMembershipMapper) {
        this.orgMembershipMapper = orgMembershipMapper;
    }

    @Override
    public List<OrgMembership> findActiveMembershipsByUserId(Long userId) {
        return orgMembershipMapper.selectList(new LambdaQueryWrapper<OrgMembershipDO>()
                        .eq(OrgMembershipDO::getUserId, userId)
                        .eq(OrgMembershipDO::getStatus, "ACTIVE")
                        .orderByAsc(OrgMembershipDO::getId))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void replaceMemberships(Long userId, List<OrgMembership> activeMemberships, List<OrgMembership> inactiveMemberships) {
        for (OrgMembership membership : activeMemberships) {
            if (membership.id() == null) {
                orgMembershipMapper.insert(toInsertDO(userId, membership));
                continue;
            }
            OrgMembershipDO updateDO = new OrgMembershipDO();
            updateDO.setId(membership.id());
            updateDO.setPrimary(membership.isPrimary());
            updateDO.setStatus(membership.status());
            orgMembershipMapper.updateById(updateDO);
        }
        for (OrgMembership membership : inactiveMemberships) {
            OrgMembershipDO updateDO = new OrgMembershipDO();
            updateDO.setId(membership.id());
            updateDO.setStatus(membership.status());
            updateDO.setLeftAt(parseTime(membership.leftAt()));
            orgMembershipMapper.updateById(updateDO);
        }
    }

    private OrgMembershipDO toInsertDO(Long userId, OrgMembership membership) {
        OrgMembershipDO membershipDO = new OrgMembershipDO();
        membershipDO.setUserId(userId);
        membershipDO.setOrgUnitId(membership.orgUnitId());
        membershipDO.setMembershipType(membership.membershipType());
        membershipDO.setPrimary(membership.isPrimary());
        membershipDO.setStatus(membership.status());
        membershipDO.setJoinedAt(parseTime(membership.joinedAt()));
        membershipDO.setLeftAt(parseTime(membership.leftAt()));
        membershipDO.setCreatedAt(parseTime(membership.joinedAt()));
        return membershipDO;
    }

    private OrgMembership toDomain(OrgMembershipDO membershipDO) {
        return new OrgMembership(
                membershipDO.getId(),
                membershipDO.getUserId(),
                membershipDO.getOrgUnitId(),
                membershipDO.getMembershipType(),
                Boolean.TRUE.equals(membershipDO.getPrimary()),
                membershipDO.getStatus(),
                formatTime(membershipDO.getJoinedAt()),
                formatTime(membershipDO.getLeftAt())
        );
    }

    private LocalDateTime parseTime(String time) {
        return time == null || time.isBlank() ? null : LocalDateTime.parse(time);
    }

    private String formatTime(LocalDateTime time) {
        return Objects.toString(time, null);
    }
}

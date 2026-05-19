package edu.whut.eval.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import edu.whut.eval.domain.org.model.OrgMembership;
import edu.whut.eval.domain.org.model.OrgUnit;
import edu.whut.eval.domain.org.repository.OrgQueryRepository;
import edu.whut.eval.infra.persistence.entity.OrgMembershipDO;
import edu.whut.eval.infra.persistence.entity.OrgUnitDO;
import edu.whut.eval.infra.persistence.mapper.OrgMembershipMapper;
import edu.whut.eval.infra.persistence.mapper.OrgUnitMapper;
import org.springframework.stereotype.Repository;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public class MybatisPlusOrgQueryRepository implements OrgQueryRepository {

    private final OrgUnitMapper orgUnitMapper;
    private final OrgMembershipMapper orgMembershipMapper;

    public MybatisPlusOrgQueryRepository(OrgUnitMapper orgUnitMapper, OrgMembershipMapper orgMembershipMapper) {
        this.orgUnitMapper = orgUnitMapper;
        this.orgMembershipMapper = orgMembershipMapper;
    }

    @Override
    public Optional<OrgUnit> findById(Long id) {
        return Optional.ofNullable(orgUnitMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<OrgUnit> findByCode(String unitCode) {
        OrgUnitDO orgUnitDO = orgUnitMapper.selectOne(new LambdaQueryWrapper<OrgUnitDO>()
                .eq(OrgUnitDO::getUnitCode, unitCode)
                .last("limit 1"));
        return Optional.ofNullable(orgUnitDO).map(this::toDomain);
    }

    @Override
    public List<OrgUnit> findChildren(Long parentId) {
        return orgUnitMapper.selectList(new LambdaQueryWrapper<OrgUnitDO>()
                        .eq(OrgUnitDO::getParentId, parentId)
                        .orderByAsc(OrgUnitDO::getId))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<OrgUnit> findRootTree(boolean includeDisabled) {
        LambdaQueryWrapper<OrgUnitDO> wrapper = new LambdaQueryWrapper<OrgUnitDO>()
                .orderByAsc(OrgUnitDO::getPath)
                .orderByAsc(OrgUnitDO::getId);
        if (!includeDisabled) {
            wrapper.eq(OrgUnitDO::getStatus, "ACTIVE");
        }
        return orgUnitMapper.selectList(wrapper).stream().map(this::toDomain).toList();
    }

    @Override
    public List<OrgUnit> findDescendants(Long rootId, boolean includeDisabled) {
        OrgUnitDO root = orgUnitMapper.selectById(rootId);
        if (root == null) {
            return List.of();
        }

        List<OrgUnit> subtree = orgUnitMapper.selectList(new LambdaQueryWrapper<OrgUnitDO>()
                        .and(wrapper -> wrapper.eq(OrgUnitDO::getPath, root.getPath())
                                .or()
                                .likeRight(OrgUnitDO::getPath, root.getPath() + "/"))
                .orderByAsc(OrgUnitDO::getPath)
                .orderByAsc(OrgUnitDO::getId))
                .stream()
                .map(this::toDomain)
                .toList();
        if (includeDisabled) {
            return subtree;
        }
        return retainVisibleSubtree(rootId, subtree);
    }

    @Override
    public List<OrgMembership> findMembershipsByUserId(Long userId) {
        return orgMembershipMapper.selectList(new LambdaQueryWrapper<OrgMembershipDO>()
                        .eq(OrgMembershipDO::getUserId, userId)
                        .eq(OrgMembershipDO::getStatus, "ACTIVE")
                        .orderByAsc(OrgMembershipDO::getId))
                .stream()
                .map(this::toMembership)
                .toList();
    }

    private OrgUnit toDomain(OrgUnitDO orgUnitDO) {
        return new OrgUnit(
                orgUnitDO.getId(),
                orgUnitDO.getParentId(),
                orgUnitDO.getUnitType(),
                orgUnitDO.getUnitCode(),
                orgUnitDO.getUnitName(),
                orgUnitDO.getPath(),
                orgUnitDO.getStatus()
        );
    }

    private OrgMembership toMembership(OrgMembershipDO membershipDO) {
        return new OrgMembership(
                membershipDO.getId(),
                membershipDO.getUserId(),
                membershipDO.getOrgUnitId(),
                membershipDO.getMembershipType(),
                membershipDO.getStatus()
        );
    }

    private List<OrgUnit> retainVisibleSubtree(Long rootId, List<OrgUnit> subtree) {
        Set<String> retainedPaths = new HashSet<>();
        for (OrgUnit unit : subtree) {
            if (unit.id().equals(rootId) || "ACTIVE".equals(unit.status())) {
                retainAncestorPaths(unit.path(), retainedPaths);
            }
        }
        return subtree.stream()
                .filter(unit -> retainedPaths.contains(unit.path()))
                .toList();
    }

    private void retainAncestorPaths(String path, Set<String> retainedPaths) {
        String current = path;
        while (current != null && !current.isBlank()) {
            retainedPaths.add(current);
            int lastSlash = current.lastIndexOf('/');
            if (lastSlash <= 0) {
                break;
            }
            current = current.substring(0, lastSlash);
        }
    }
}

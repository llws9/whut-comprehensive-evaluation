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

import java.util.List;
import java.util.Optional;

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
        LambdaQueryWrapper<OrgUnitDO> wrapper = new LambdaQueryWrapper<OrgUnitDO>()
                .apply("id = {0} OR path LIKE CONCAT((SELECT path FROM org_unit WHERE id = {0}), '%')", rootId)
                .orderByAsc(OrgUnitDO::getPath)
                .orderByAsc(OrgUnitDO::getId);
        if (!includeDisabled) {
            wrapper.eq(OrgUnitDO::getStatus, "ACTIVE");
        }
        return orgUnitMapper.selectList(wrapper).stream().map(this::toDomain).toList();
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
}

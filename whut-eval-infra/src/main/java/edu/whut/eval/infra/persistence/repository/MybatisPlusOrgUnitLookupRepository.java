package edu.whut.eval.infra.persistence.repository;

import edu.whut.eval.domain.org.model.OrgUnit;
import edu.whut.eval.domain.org.repository.OrgUnitLookupRepository;
import edu.whut.eval.infra.persistence.entity.OrgUnitDO;
import edu.whut.eval.infra.persistence.mapper.OrgUnitMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MybatisPlusOrgUnitLookupRepository implements OrgUnitLookupRepository {

    private final OrgUnitMapper orgUnitMapper;

    public MybatisPlusOrgUnitLookupRepository(OrgUnitMapper orgUnitMapper) {
        this.orgUnitMapper = orgUnitMapper;
    }

    @Override
    public Optional<OrgUnit> findById(Long id) {
        return Optional.ofNullable(orgUnitMapper.selectById(id)).map(this::toDomain);
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
}

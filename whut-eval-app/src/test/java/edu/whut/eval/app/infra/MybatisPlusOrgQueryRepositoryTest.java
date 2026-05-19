package edu.whut.eval.app.infra;

import edu.whut.eval.domain.org.model.OrgUnit;
import edu.whut.eval.infra.persistence.entity.OrgUnitDO;
import edu.whut.eval.infra.persistence.mapper.OrgMembershipMapper;
import edu.whut.eval.infra.persistence.mapper.OrgUnitMapper;
import edu.whut.eval.infra.persistence.repository.MybatisPlusOrgQueryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class MybatisPlusOrgQueryRepositoryTest {

    @Mock
    private OrgUnitMapper orgUnitMapper;

    @Mock
    private OrgMembershipMapper orgMembershipMapper;

    @InjectMocks
    private MybatisPlusOrgQueryRepository repository;

    @Test
    void shouldFindDescendantsExcludingDisabledByDefault() {
        given(orgUnitMapper.selectList(any())).willReturn(List.of(
                orgUnit(2002L, 1L, "COLLEGE", "CS", "计算机与人工智能学院", "/1/2002/", "ACTIVE"),
                orgUnit(2009L, 2002L, "CLASS", "CS2201", "计科2201", "/1/2002/2009/", "ACTIVE")
        ));

        List<OrgUnit> result = repository.findDescendants(2002L, false);

        assertThat(result).extracting(OrgUnit::id).containsExactly(2002L, 2009L);
    }

    @Test
    void shouldFindRootTreeIncludingDisabledWhenRequested() {
        given(orgUnitMapper.selectList(any())).willReturn(List.of(
                orgUnit(1L, null, "SCHOOL", "WHUT", "武汉理工大学", "/1/", "ACTIVE"),
                orgUnit(2L, null, "SCHOOL", "OLD", "旧组织", "/2/", "DISABLED")
        ));

        List<OrgUnit> result = repository.findRootTree(true);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(OrgUnit::status).containsExactly("ACTIVE", "DISABLED");
    }

    private OrgUnitDO orgUnit(Long id,
                              Long parentId,
                              String unitType,
                              String unitCode,
                              String unitName,
                              String path,
                              String status) {
        OrgUnitDO orgUnitDO = new OrgUnitDO();
        orgUnitDO.setId(id);
        orgUnitDO.setParentId(parentId);
        orgUnitDO.setUnitType(unitType);
        orgUnitDO.setUnitCode(unitCode);
        orgUnitDO.setUnitName(unitName);
        orgUnitDO.setPath(path);
        orgUnitDO.setStatus(status);
        return orgUnitDO;
    }
}

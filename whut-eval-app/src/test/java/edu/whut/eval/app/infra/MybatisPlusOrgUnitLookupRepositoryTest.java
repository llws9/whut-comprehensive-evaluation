package edu.whut.eval.app.infra;

import edu.whut.eval.domain.org.model.OrgUnit;
import edu.whut.eval.infra.persistence.entity.OrgUnitDO;
import edu.whut.eval.infra.persistence.mapper.OrgUnitMapper;
import edu.whut.eval.infra.persistence.repository.MybatisPlusOrgUnitLookupRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class MybatisPlusOrgUnitLookupRepositoryTest {

    @Mock
    private OrgUnitMapper orgUnitMapper;

    @InjectMocks
    private MybatisPlusOrgUnitLookupRepository repository;

    @Test
    void shouldFindOrgUnitById() {
        OrgUnitDO orgUnitDO = new OrgUnitDO();
        orgUnitDO.setId(2002L);
        orgUnitDO.setParentId(1L);
        orgUnitDO.setUnitType("COLLEGE");
        orgUnitDO.setUnitCode("CS");
        orgUnitDO.setUnitName("计算机与人工智能学院");
        orgUnitDO.setPath("/1/2002/");
        orgUnitDO.setStatus("ACTIVE");
        given(orgUnitMapper.selectById(2002L)).willReturn(orgUnitDO);

        Optional<OrgUnit> result = repository.findById(2002L);

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(2002L);
        assertThat(result.get().unitName()).isEqualTo("计算机与人工智能学院");
        assertThat(result.get().path()).isEqualTo("/1/2002/");
    }
}

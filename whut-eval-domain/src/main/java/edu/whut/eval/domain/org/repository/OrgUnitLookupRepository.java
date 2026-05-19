package edu.whut.eval.domain.org.repository;

import edu.whut.eval.domain.org.model.OrgUnit;

import java.util.Optional;

public interface OrgUnitLookupRepository {

    Optional<OrgUnit> findById(Long id);
}

package edu.whut.eval.domain.org.model;

public record OrgUnit(
        Long id,
        Long parentId,
        String unitType,
        String unitCode,
        String unitName,
        String path,
        String status
) {
}

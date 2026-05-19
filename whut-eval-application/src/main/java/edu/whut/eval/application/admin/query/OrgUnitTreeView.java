package edu.whut.eval.application.admin.query;

import java.util.List;

public record OrgUnitTreeView(
        Long id,
        String unitCode,
        String unitName,
        String unitType,
        String status,
        List<OrgUnitTreeView> children
) {
}

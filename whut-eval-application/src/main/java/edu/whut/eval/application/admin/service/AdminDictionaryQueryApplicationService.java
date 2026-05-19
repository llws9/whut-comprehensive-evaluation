package edu.whut.eval.application.admin.service;

import edu.whut.eval.application.admin.query.OrgUnitTreeView;
import edu.whut.eval.application.admin.query.PermissionDictionaryView;
import edu.whut.eval.common.exception.ResourceNotFoundException;
import edu.whut.eval.domain.iam.repository.PermissionDictionaryQueryRepository;
import edu.whut.eval.domain.org.model.OrgUnit;
import edu.whut.eval.domain.org.repository.OrgQueryRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class AdminDictionaryQueryApplicationService {

    private final PermissionDictionaryQueryRepository permissionDictionaryQueryRepository;
    private final OrgQueryRepository orgQueryRepository;

    public AdminDictionaryQueryApplicationService(PermissionDictionaryQueryRepository permissionDictionaryQueryRepository,
                                                  OrgQueryRepository orgQueryRepository) {
        this.permissionDictionaryQueryRepository = permissionDictionaryQueryRepository;
        this.orgQueryRepository = orgQueryRepository;
    }

    public List<PermissionDictionaryView> listPermissions(String keyword, String module, String status) {
        String effectiveStatus = (status == null || status.isBlank()) ? "ACTIVE" : status;
        return permissionDictionaryQueryRepository.findPermissions(keyword, module, effectiveStatus)
                .stream()
                .map(item -> new PermissionDictionaryView(
                        item.permissionCode(),
                        item.permissionName(),
                        item.module(),
                        item.description(),
                        item.status()
                ))
                .toList();
    }

    public List<OrgUnitTreeView> listOrgUnitTree(Long rootId, String unitType, boolean includeDisabled) {
        List<OrgUnit> units = rootId == null
                ? orgQueryRepository.findRootTree(includeDisabled)
                : loadDescendants(rootId, includeDisabled);
        return buildTree(units, rootId, unitType);
    }

    private List<OrgUnit> loadDescendants(Long rootId, boolean includeDisabled) {
        orgQueryRepository.findById(rootId)
                .orElseThrow(() -> new ResourceNotFoundException("组织不存在: " + rootId));
        return orgQueryRepository.findDescendants(rootId, includeDisabled);
    }

    private List<OrgUnitTreeView> buildTree(List<OrgUnit> units, Long rootId, String unitType) {
        Map<Long, OrgUnit> unitById = new LinkedHashMap<>();
        Map<Long, List<OrgUnit>> childrenByParentId = new LinkedHashMap<>();
        for (OrgUnit unit : units) {
            unitById.put(unit.id(), unit);
            childrenByParentId.computeIfAbsent(unit.parentId(), key -> new ArrayList<>()).add(unit);
        }
        childrenByParentId.values().forEach(list -> list.sort(Comparator.comparing(OrgUnit::id)));

        if (rootId != null) {
            OrgUnit root = unitById.get(rootId);
            if (root == null) {
                return List.of();
            }
            OrgUnitTreeView filteredRoot = toTree(root, childrenByParentId, normalizeUnitType(unitType));
            return filteredRoot == null ? List.of() : List.of(filteredRoot);
        }

        List<OrgUnit> roots = units.stream()
                .filter(unit -> unit.parentId() == null || !unitById.containsKey(unit.parentId()))
                .sorted(Comparator.comparing(OrgUnit::id))
                .toList();
        return roots.stream()
                .map(root -> toTree(root, childrenByParentId, normalizeUnitType(unitType)))
                .filter(Objects::nonNull)
                .toList();
    }

    private String normalizeUnitType(String unitType) {
        return unitType == null || unitType.isBlank() ? null : unitType;
    }

    private OrgUnitTreeView toTree(OrgUnit unit,
                                   Map<Long, List<OrgUnit>> childrenByParentId,
                                   String unitTypeFilter) {
        List<OrgUnitTreeView> children = childrenByParentId.getOrDefault(unit.id(), List.of())
                .stream()
                .map(child -> toTree(child, childrenByParentId, unitTypeFilter))
                .filter(Objects::nonNull)
                .toList();
        boolean matched = unitTypeFilter == null || unitTypeFilter.equals(unit.unitType());
        if (!matched && children.isEmpty()) {
            return null;
        }
        return new OrgUnitTreeView(
                unit.id(),
                unit.unitCode(),
                unit.unitName(),
                unit.unitType(),
                unit.status(),
                children
        );
    }
}

package edu.whut.eval.application.iam.service;

import edu.whut.eval.application.iam.command.CreateScopeRuleCommand;
import edu.whut.eval.application.iam.query.ScopeRuleAdminView;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.common.exception.ResourceNotFoundException;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.iam.model.IamRoleAssignmentDetail;
import edu.whut.eval.domain.iam.model.IamScopeRuleDetail;
import edu.whut.eval.domain.iam.repository.RoleAssignmentAdminRepository;
import edu.whut.eval.domain.iam.repository.ScopeRuleAdminRepository;
import edu.whut.eval.domain.org.model.OrgUnit;
import edu.whut.eval.domain.org.repository.OrgUnitLookupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A-17/A-18 范围规则管理应用服务实现骨架。
 */
@Service
public class DefaultScopeRuleAdminApplicationService implements ScopeRuleAdminApplicationService {

    private static final Set<String> SCOPE_TYPES = Set.of(
            "SELF", "ALL", "ORG_UNIT", "ORG_SUBTREE", "CATEGORY", "ITEM", "ORG_UNIT_ITEM", "CUSTOM_EXPRESSION"
    );

    private final RoleAssignmentAdminRepository roleAssignmentAdminRepository;
    private final ScopeRuleAdminRepository scopeRuleAdminRepository;
    private final OrgUnitLookupRepository orgUnitLookupRepository;

    public DefaultScopeRuleAdminApplicationService(RoleAssignmentAdminRepository roleAssignmentAdminRepository,
                                                   ScopeRuleAdminRepository scopeRuleAdminRepository,
                                                   OrgUnitLookupRepository orgUnitLookupRepository) {
        this.roleAssignmentAdminRepository = roleAssignmentAdminRepository;
        this.scopeRuleAdminRepository = scopeRuleAdminRepository;
        this.orgUnitLookupRepository = orgUnitLookupRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScopeRuleAdminView> listScopeRules(Long assignmentId) {
        requireAssignment(assignmentId);
        return scopeRuleAdminRepository.findByAssignmentId(assignmentId).stream()
                .map(this::toView)
                .toList();
    }

    @Override
    @Transactional
    public ScopeRuleAdminView createScopeRule(Long assignmentId, CreateScopeRuleCommand command) {
        requireAssignment(assignmentId);
        validateScopeCommand(command);

        OrgUnit orgUnit = resolveOrgUnitIfNeeded(command.orgUnitId());
        Integer priority = command.priority() == null ? 100 : command.priority();

        if (scopeRuleAdminRepository.existsSemanticDuplicate(
                assignmentId,
                command.permissionCode(),
                command.scopeType(),
                command.orgUnitId(),
                command.categoryCode(),
                command.itemCode(),
                normalizeExpression(command.expressionJson())
        )) {
            throw new ConflictException("相同语义的范围规则已存在");
        }

        IamScopeRuleDetail detail = scopeRuleAdminRepository.create(
                assignmentId,
                command.permissionCode(),
                command.scopeType(),
                command.orgUnitId(),
                orgUnit == null ? null : orgUnit.unitName(),
                command.categoryCode(),
                command.itemCode(),
                normalizeExpression(command.expressionJson()),
                priority,
                "ACTIVE"
        );
        return toView(detail);
    }

    private IamRoleAssignmentDetail requireAssignment(Long assignmentId) {
        return roleAssignmentAdminRepository.findDetailById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("角色分配不存在: " + assignmentId));
    }

    private void validateScopeCommand(CreateScopeRuleCommand command) {
        if (command.permissionCode() == null || command.permissionCode().isBlank()) {
            throw new ValidationException("permissionCode 不能为空");
        }
        if (command.scopeType() == null || command.scopeType().isBlank()) {
            throw new ValidationException("scopeType 不能为空");
        }
        if (!SCOPE_TYPES.contains(command.scopeType())) {
            throw new ValidationException("scopeType 不在允许范围内");
        }
        switch (command.scopeType()) {
            case "ALL":
            case "SELF":
                break;
            case "ORG_UNIT":
            case "ORG_SUBTREE":
                requireField(command.orgUnitId() != null, command.scopeType() + " 范围必须指定 orgUnitId");
                break;
            case "CATEGORY":
                requireField(notBlank(command.categoryCode()), "CATEGORY 范围必须指定 categoryCode");
                break;
            case "ITEM":
                requireField(notBlank(command.itemCode()), "ITEM 范围必须指定 itemCode");
                break;
            case "ORG_UNIT_ITEM":
                requireField(command.orgUnitId() != null, "ORG_UNIT_ITEM 范围必须指定 orgUnitId");
                requireField(notBlank(command.itemCode()), "ORG_UNIT_ITEM 范围必须指定 itemCode");
                break;
            case "CUSTOM_EXPRESSION":
                requireField(command.expressionJson() != null && !command.expressionJson().isEmpty(),
                        "CUSTOM_EXPRESSION 范围必须指定 expressionJson");
                break;
            default:
                throw new ValidationException("scopeType 不在允许范围内");
        }
    }

    private OrgUnit resolveOrgUnitIfNeeded(Long orgUnitId) {
        if (orgUnitId == null) {
            return null;
        }
        return orgUnitLookupRepository.findById(orgUnitId)
                .orElseThrow(() -> new ResourceNotFoundException("组织不存在: " + orgUnitId));
    }

    private Map<String, Object> normalizeExpression(Map<String, Object> expressionJson) {
        return (expressionJson == null || expressionJson.isEmpty()) ? null : expressionJson;
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private void requireField(boolean condition, String message) {
        if (!condition) {
            throw new ValidationException(message);
        }
    }

    private ScopeRuleAdminView toView(IamScopeRuleDetail detail) {
        return new ScopeRuleAdminView(
                detail.scopeRuleId(),
                detail.assignmentId(),
                detail.permissionCode(),
                detail.scopeType(),
                detail.orgUnitId(),
                detail.orgUnitName(),
                detail.categoryCode(),
                detail.itemCode(),
                detail.expressionJson(),
                detail.priority(),
                detail.status(),
                detail.createdAt()
        );
    }
}

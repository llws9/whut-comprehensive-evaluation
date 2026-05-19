package edu.whut.eval.application.iam.service;

import edu.whut.eval.application.auth.model.UserAuthorizationContext;
import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import edu.whut.eval.application.iam.command.CreateRoleAssignmentCommand;
import edu.whut.eval.application.iam.command.UpdateRoleAssignmentCommand;
import edu.whut.eval.application.iam.query.RoleAssignmentAdminView;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.common.exception.ResourceNotFoundException;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.iam.model.IamRoleAssignmentDetail;
import edu.whut.eval.domain.iam.model.IamRoleDefinition;
import edu.whut.eval.domain.iam.model.IamUser;
import edu.whut.eval.domain.iam.repository.IamRoleQueryRepository;
import edu.whut.eval.domain.iam.repository.IamUserQueryRepository;
import edu.whut.eval.domain.iam.repository.RoleAssignmentAdminRepository;
import edu.whut.eval.domain.org.model.OrgUnit;
import edu.whut.eval.domain.org.repository.OrgUnitLookupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Set;

/**
 * A-14/A-15 角色分配管理应用服务实现骨架。
 */
@Service
public class DefaultRoleAssignmentAdminApplicationService implements RoleAssignmentAdminApplicationService {

    private static final Set<String> UPDATABLE_STATUS = Set.of("ACTIVE", "INACTIVE");

    private final IamUserQueryRepository iamUserQueryRepository;
    private final IamRoleQueryRepository iamRoleQueryRepository;
    private final OrgUnitLookupRepository orgUnitLookupRepository;
    private final RoleAssignmentAdminRepository roleAssignmentAdminRepository;
    private final UserAuthorizationContextAssembler userAuthorizationContextAssembler;
    private final IamAdminAuditRecorder iamAdminAuditRecorder;

    public DefaultRoleAssignmentAdminApplicationService(IamUserQueryRepository iamUserQueryRepository,
                                                        IamRoleQueryRepository iamRoleQueryRepository,
                                                        OrgUnitLookupRepository orgUnitLookupRepository,
                                                        RoleAssignmentAdminRepository roleAssignmentAdminRepository,
                                                        UserAuthorizationContextAssembler userAuthorizationContextAssembler,
                                                        IamAdminAuditRecorder iamAdminAuditRecorder) {
        this.iamUserQueryRepository = iamUserQueryRepository;
        this.iamRoleQueryRepository = iamRoleQueryRepository;
        this.orgUnitLookupRepository = orgUnitLookupRepository;
        this.roleAssignmentAdminRepository = roleAssignmentAdminRepository;
        this.userAuthorizationContextAssembler = userAuthorizationContextAssembler;
        this.iamAdminAuditRecorder = iamAdminAuditRecorder;
    }

    @Override
    @Transactional
    public RoleAssignmentAdminView createAssignment(CreateRoleAssignmentCommand command) {
        validateTimeRange(command.effectiveFrom(), command.effectiveTo(), false);
        IamUser user = iamUserQueryRepository.findById(command.userId())
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在: " + command.userId()));
        IamRoleDefinition role = iamRoleQueryRepository.findByRoleCode(command.roleCode())
                .orElseThrow(() -> new ResourceNotFoundException("角色不存在: " + command.roleCode()));
        OrgUnit orgUnit = resolveOrgUnit(command.orgUnitId());
        UserAuthorizationContext operator = userAuthorizationContextAssembler.requiredAuthorizationContext();

        if (roleAssignmentAdminRepository.existsActiveAssignment(user.id(), role.roleCode(), command.orgUnitId(), null)) {
            throw new ConflictException("同一用户在该组织下已存在有效角色分配");
        }

        IamRoleAssignmentDetail detail = roleAssignmentAdminRepository.create(
                user.id(),
                role.roleCode(),
                role.roleName(),
                orgUnit == null ? null : orgUnit.id(),
                orgUnit == null ? null : orgUnit.unitName(),
                command.effectiveFrom(),
                command.effectiveTo(),
                defaultSourceType(command.sourceType()),
                operator.getUserId(),
                "ACTIVE"
        );
        return toView(detail);
    }

    @Override
    @Transactional
    public RoleAssignmentAdminView updateAssignment(Long assignmentId, UpdateRoleAssignmentCommand command) {
        UserAuthorizationContext operator = userAuthorizationContextAssembler.requiredAuthorizationContext();
        IamRoleAssignmentDetail existing = roleAssignmentAdminRepository.findDetailById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("角色分配不存在: " + assignmentId));
        validateStatus(command.status(), existing.status());
        validateTimeRange(command.effectiveFrom(), command.effectiveTo(), true);

        Long targetOrgUnitId = command.orgUnitId() == null ? existing.orgUnitId() : command.orgUnitId();
        OrgUnit orgUnit = resolveOrgUnit(targetOrgUnitId);
        String nextStatus = command.status() == null ? existing.status() : command.status();
        String nextEffectiveFrom = command.effectiveFrom() == null ? existing.effectiveFrom() : command.effectiveFrom();
        String nextEffectiveTo = command.effectiveTo() == null ? existing.effectiveTo() : command.effectiveTo();

        if ("ACTIVE".equals(nextStatus) && roleAssignmentAdminRepository.existsActiveAssignment(
                existing.userId(),
                existing.roleCode(),
                targetOrgUnitId,
                assignmentId
        )) {
            throw new ConflictException("同一用户在该组织下已存在其他有效角色分配");
        }

        IamRoleAssignmentDetail detail = roleAssignmentAdminRepository.update(
                assignmentId,
                existing.userId(),
                existing.roleCode(),
                existing.roleName(),
                orgUnit == null ? null : orgUnit.id(),
                orgUnit == null ? null : orgUnit.unitName(),
                nextStatus,
                nextEffectiveFrom,
                nextEffectiveTo,
                existing.sourceType()
        );
        iamAdminAuditRecorder.recordRoleAssignmentUpdated(operator.getUserId(), existing, detail);
        return toView(detail);
    }

    private void validateStatus(String status, String currentStatus) {
        if (status == null || status.isBlank()) {
            return;
        }
        if (!UPDATABLE_STATUS.contains(status)) {
            throw new ValidationException("status 仅允许 ACTIVE 或 INACTIVE");
        }
        if ("EXPIRED".equals(currentStatus)) {
            throw new ConflictException("已过期分配不允许通过接口回写");
        }
    }

    private void validateTimeRange(String effectiveFrom, String effectiveTo, boolean futureAllowed) {
        LocalDateTime from = parseTime(effectiveFrom, "effectiveFrom");
        LocalDateTime to = parseTime(effectiveTo, "effectiveTo");
        if (from != null && to != null && from.isAfter(to)) {
            throw new ValidationException("effectiveFrom 不能晚于 effectiveTo");
        }
        if (!futureAllowed && from != null && from.isAfter(LocalDateTime.now())) {
            throw new ValidationException("effectiveFrom 不允许晚于当前时间");
        }
    }

    private LocalDateTime parseTime(String time, String field) {
        if (time == null || time.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(time);
        } catch (DateTimeParseException ex) {
            throw new ValidationException(field + " 时间格式非法");
        }
    }

    private OrgUnit resolveOrgUnit(Long orgUnitId) {
        if (orgUnitId == null) {
            return null;
        }
        return orgUnitLookupRepository.findById(orgUnitId)
                .orElseThrow(() -> new ResourceNotFoundException("组织不存在: " + orgUnitId));
    }

    private String defaultSourceType(String sourceType) {
        return (sourceType == null || sourceType.isBlank()) ? "MANUAL" : sourceType;
    }

    private RoleAssignmentAdminView toView(IamRoleAssignmentDetail detail) {
        return new RoleAssignmentAdminView(
                detail.assignmentId(),
                detail.userId(),
                detail.roleCode(),
                detail.roleName(),
                detail.orgUnitId(),
                detail.orgUnitName(),
                detail.status(),
                detail.effectiveFrom(),
                detail.effectiveTo(),
                detail.sourceType(),
                detail.updatedAt()
        );
    }
}

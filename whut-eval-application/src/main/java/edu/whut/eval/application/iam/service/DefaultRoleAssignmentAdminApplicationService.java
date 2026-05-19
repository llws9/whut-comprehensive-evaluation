package edu.whut.eval.application.iam.service;

import edu.whut.eval.application.auth.model.UserAuthorizationContext;
import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import edu.whut.eval.application.iam.command.CreateRoleAssignmentCommand;
import edu.whut.eval.application.iam.command.UpdateRoleAssignmentCommand;
import edu.whut.eval.application.iam.query.RoleAssignmentAdminPageItemView;
import edu.whut.eval.application.iam.query.RoleAssignmentAdminPageQuery;
import edu.whut.eval.application.iam.query.RoleAssignmentAdminView;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.common.exception.ResourceNotFoundException;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.iam.model.IamRoleAssignmentDetail;
import edu.whut.eval.domain.iam.model.IamRoleAssignmentPageItem;
import edu.whut.eval.domain.iam.model.IamRoleDefinition;
import edu.whut.eval.domain.iam.model.IamUser;
import edu.whut.eval.domain.iam.model.RoleAssignmentCurrentStatusResolver;
import edu.whut.eval.domain.iam.query.RoleAssignmentPageQuery;
import edu.whut.eval.domain.iam.repository.IamRoleQueryRepository;
import edu.whut.eval.domain.iam.repository.IamUserQueryRepository;
import edu.whut.eval.domain.iam.repository.RoleAssignmentAdminRepository;
import edu.whut.eval.domain.org.model.OrgUnit;
import edu.whut.eval.domain.org.repository.OrgUnitLookupRepository;
import edu.whut.eval.domain.shared.PageResult;
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
    private static final Set<String> PAGEABLE_STATUS = Set.of("ACTIVE", "INACTIVE", "EXPIRED");

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
        validateTimeRange(command.effectiveFrom(), command.effectiveTo());
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
        validateStatus(command.status(), existing);
        String nextStatus = command.status() == null ? existing.status() : command.status();
        String nextEffectiveFrom = command.effectiveFrom() == null ? existing.effectiveFrom() : command.effectiveFrom();
        String nextEffectiveTo = command.effectiveTo() == null ? existing.effectiveTo() : command.effectiveTo();
        validateTimeRange(nextEffectiveFrom, nextEffectiveTo);
        validateFinalState(nextStatus, nextEffectiveFrom, nextEffectiveTo);

        Long targetOrgUnitId = command.orgUnitId() == null ? existing.orgUnitId() : command.orgUnitId();
        OrgUnit orgUnit = resolveOrgUnit(targetOrgUnitId);
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

    @Override
    public PageResult<RoleAssignmentAdminPageItemView> pageAssignments(RoleAssignmentAdminPageQuery query) {
        validatePageQuery(query);
        if (query.userId() != null) {
            iamUserQueryRepository.findById(query.userId())
                    .orElseThrow(() -> new ResourceNotFoundException("用户不存在: " + query.userId()));
        }
        if (query.orgUnitId() != null) {
            resolveOrgUnit(query.orgUnitId());
        }
        PageResult<IamRoleAssignmentPageItem> page = roleAssignmentAdminRepository.pageAssignments(new RoleAssignmentPageQuery(
                query.pageNo(),
                query.pageSize(),
                query.userId(),
                query.roleCode(),
                query.status(),
                query.orgUnitId()
        ));
        return new PageResult<>(
                page.total(),
                page.records().stream().map(this::toPageItemView).toList()
        );
    }

    @Override
    @Transactional
    public void revokeAssignment(Long assignmentId) {
        UserAuthorizationContext operator = userAuthorizationContextAssembler.requiredAuthorizationContext();
        IamRoleAssignmentDetail existing = roleAssignmentAdminRepository.findDetailById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("角色分配不存在: " + assignmentId));
        if (!"ACTIVE".equals(resolveCurrentStatus(existing))) {
            throw new ConflictException("仅 ACTIVE 状态的角色分配允许撤销");
        }
        IamRoleAssignmentDetail revoked = roleAssignmentAdminRepository.revoke(assignmentId);
        iamAdminAuditRecorder.recordRoleAssignmentUpdated(operator.getUserId(), existing, revoked);
    }

    private void validateStatus(String status, IamRoleAssignmentDetail existing) {
        if (status == null || status.isBlank()) {
            validateCurrentAssignmentStatus(existing);
            return;
        }
        if (!UPDATABLE_STATUS.contains(status)) {
            throw new ValidationException("status 仅允许 ACTIVE 或 INACTIVE");
        }
        validateCurrentAssignmentStatus(existing);
    }

    private void validateCurrentAssignmentStatus(IamRoleAssignmentDetail existing) {
        if (isFutureEffective(existing)) {
            throw new ConflictException("未生效分配不允许通过接口回写");
        }
        if ("EXPIRED".equals(resolveCurrentStatus(existing))) {
            throw new ConflictException("已过期分配不允许通过接口回写");
        }
    }

    private boolean isFutureEffective(IamRoleAssignmentDetail detail) {
        LocalDateTime effectiveFrom = parseTime(detail.effectiveFrom(), "effectiveFrom");
        return "ACTIVE".equals(detail.status())
                && effectiveFrom != null
                && effectiveFrom.isAfter(LocalDateTime.now());
    }

    private void validateFinalState(String status, String effectiveFrom, String effectiveTo) {
        LocalDateTime from = parseTime(effectiveFrom, "effectiveFrom");
        LocalDateTime to = parseTime(effectiveTo, "effectiveTo");
        if ("EXPIRED".equals(RoleAssignmentCurrentStatusResolver.resolve(status, from, to, LocalDateTime.now()))) {
            throw new ConflictException("已过期分配不允许通过接口回写");
        }
    }

    private void validateTimeRange(String effectiveFrom, String effectiveTo) {
        LocalDateTime from = parseTime(effectiveFrom, "effectiveFrom");
        LocalDateTime to = parseTime(effectiveTo, "effectiveTo");
        if (from != null && to != null && from.isAfter(to)) {
            throw new ValidationException("effectiveFrom 不能晚于 effectiveTo");
        }
        if (from != null && from.isAfter(LocalDateTime.now())) {
            throw new ValidationException("effectiveFrom 不允许晚于当前时间");
        }
    }

    private void validatePageQuery(RoleAssignmentAdminPageQuery query) {
        if (query.pageNo() <= 0) {
            throw new ValidationException("pageNo 必须大于 0");
        }
        if (query.pageSize() <= 0) {
            throw new ValidationException("pageSize 必须大于 0");
        }
        if (query.status() != null && !query.status().isBlank() && !PAGEABLE_STATUS.contains(query.status())) {
            throw new ValidationException("status 仅允许 ACTIVE、INACTIVE 或 EXPIRED");
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

    private String resolveCurrentStatus(IamRoleAssignmentDetail detail) {
        LocalDateTime effectiveFrom = parseTime(detail.effectiveFrom(), "effectiveFrom");
        LocalDateTime effectiveTo = parseTime(detail.effectiveTo(), "effectiveTo");
        return RoleAssignmentCurrentStatusResolver.resolve(
                detail.status(),
                effectiveFrom,
                effectiveTo,
                LocalDateTime.now()
        );
    }

    private RoleAssignmentAdminPageItemView toPageItemView(IamRoleAssignmentPageItem item) {
        return new RoleAssignmentAdminPageItemView(
                item.assignmentId(),
                item.userId(),
                item.userNo(),
                item.userName(),
                item.roleCode(),
                item.roleName(),
                item.orgUnitId(),
                item.orgUnitName(),
                item.status(),
                item.effectiveFrom(),
                item.effectiveTo()
        );
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

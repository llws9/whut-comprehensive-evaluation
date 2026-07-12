package edu.whut.eval.application.finalrecord.service;

import edu.whut.eval.application.auth.AuthorizationPermissionCodes;
import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import edu.whut.eval.application.finalrecord.query.AdminFinalRecordDetailView;
import edu.whut.eval.application.finalrecord.query.AdminFinalRecordListItemView;
import edu.whut.eval.application.finalrecord.query.FinalComponentScoreListView;
import edu.whut.eval.application.finalrecord.query.FinalComponentScoreRow;
import edu.whut.eval.application.finalrecord.query.FinalComponentScoreView;
import edu.whut.eval.application.finalrecord.query.FinalRecordQueryRow;
import edu.whut.eval.application.finalrecord.query.FinalRecordStudentView;
import edu.whut.eval.application.finalrecord.query.FinalRecordView;
import edu.whut.eval.application.finalrecord.query.UnsubmittedStudentRow;
import edu.whut.eval.application.finalrecord.query.UnsubmittedStudentView;
import edu.whut.eval.application.finalrecord.repository.FinalRecordQueryRepository;
import edu.whut.eval.common.exception.AccessDeniedAppException;
import edu.whut.eval.common.exception.ResourceNotFoundException;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.domain.finalrecord.query.FinalRecordAccessContext;
import edu.whut.eval.domain.finalrecord.query.FinalRecordPageQuery;
import edu.whut.eval.domain.finalrecord.query.UnsubmittedFinalRecordQuery;
import edu.whut.eval.domain.shared.PageResult;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class FinalRecordQueryApplicationService {

    private final UserAuthorizationContextAssembler userAuthorizationContextAssembler;
    private final FinalRecordQueryRepository finalRecordQueryRepository;
    private final FinalRecordAccessValidator finalRecordAccessValidator;

    public FinalRecordQueryApplicationService(UserAuthorizationContextAssembler userAuthorizationContextAssembler,
                                              FinalRecordQueryRepository finalRecordQueryRepository,
                                              FinalRecordAccessValidator finalRecordAccessValidator) {
        this.userAuthorizationContextAssembler = userAuthorizationContextAssembler;
        this.finalRecordQueryRepository = finalRecordQueryRepository;
        this.finalRecordAccessValidator = finalRecordAccessValidator;
    }

    public FinalRecordStudentView getStudentFinalRecord(String academicYear) {
        UserAuthorizationContext student = userAuthorizationContextAssembler.requiredAuthorizationContext();
        ensurePermission(student, AuthorizationPermissionCodes.FINAL_VIEW_SELF, "当前用户无最终成绩查看权限");
        FinalRecordQueryRow row = finalRecordQueryRepository.findStudentFinalRecord(student.getUserId(), academicYear)
                .orElseThrow(() -> new ResourceNotFoundException("最终成绩不存在"));
        return toStudentView(row);
    }

    public FinalComponentScoreListView listStudentComponents(String academicYear) {
        FinalRecordStudentView header = getStudentFinalRecord(academicYear);
        return new FinalComponentScoreListView(
                finalRecordQueryRepository.listStudentFinalRecordComponents(header.finalRecordId())
                        .stream()
                        .map(this::toComponentView)
                        .toList()
        );
    }

    public PageResult<AdminFinalRecordListItemView> pageAdminFinalRecords(FinalRecordPageQuery query) {
        UserAuthorizationContext admin = userAuthorizationContextAssembler.requiredAuthorizationContext();
        ensurePermission(admin, AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED, "当前用户无最终成绩查询权限");
        PageResult<FinalRecordQueryRow> page = finalRecordQueryRepository.pageAdminFinalRecords(
                toAccessContext(admin, AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED),
                query
        );
        return new PageResult<>(page.total(), page.records().stream().map(this::toAdminListItem).toList());
    }

    public PageResult<UnsubmittedStudentView> pageUnsubmittedStudents(UnsubmittedFinalRecordQuery query) {
        UserAuthorizationContext admin = userAuthorizationContextAssembler.requiredAuthorizationContext();
        ensurePermission(admin, AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED, "当前用户无未提交最终成绩名单查询权限");
        PageResult<UnsubmittedStudentRow> page = finalRecordQueryRepository.pageUnsubmittedStudents(
                toAccessContext(admin, AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED),
                query
        );
        return new PageResult<>(page.total(), page.records().stream().map(this::toUnsubmittedStudentView).toList());
    }

    public AdminFinalRecordDetailView getAdminFinalRecordDetail(Long finalRecordId) {
        UserAuthorizationContext admin = userAuthorizationContextAssembler.requiredAuthorizationContext();
        ensurePermission(admin, AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED, "当前用户无最终成绩查询权限");
        FinalRecordQueryRow row = finalRecordQueryRepository.findAdminFinalRecordDetail(finalRecordId)
                .orElseThrow(() -> new ResourceNotFoundException("最终成绩不存在"));
        finalRecordAccessValidator.requireAccess(admin, row, AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED);
        List<FinalComponentScoreView> components = finalRecordQueryRepository.listAdminFinalRecordComponents(finalRecordId)
                .stream()
                .map(this::toComponentView)
                .toList();
        return new AdminFinalRecordDetailView(toRecordView(row), toStudentView(row), components);
    }

    private void ensurePermission(UserAuthorizationContext context, String permissionCode, String message) {
        if (!context.hasAuthority(permissionCode)) {
            throw new AccessDeniedAppException(message);
        }
    }

    private FinalRecordAccessContext toAccessContext(UserAuthorizationContext context, String permissionCode) {
        return new FinalRecordAccessContext(context.getUserId(), context.getUserNo(), context.getUserName(),
                context.getIdentity(), context.getRoles(), context.getAuthorities(), context.getScopeRules(), permissionCode);
    }

    private FinalRecordStudentView toStudentView(FinalRecordQueryRow row) {
        return new FinalRecordStudentView(row.getFinalRecordId(), row.getStudentUserId(), row.getAcademicYear(),
                edu.whut.eval.domain.finalrecord.model.FinalRecordStatus.valueOf(row.getStatus()),
                row.getMoralTotal(), row.getIntellectualTotal(), row.getPhysicalTotal(), row.getLaborTotal(),
                row.getGrandTotal(), row.getSubmittedAt(), row.getConfirmedAt(), row.getVersion());
    }

    private FinalRecordView toRecordView(FinalRecordQueryRow row) {
        return new FinalRecordView(row.getFinalRecordId(), row.getStudentUserId(), row.getAcademicYear(),
                edu.whut.eval.domain.finalrecord.model.FinalRecordStatus.valueOf(row.getStatus()),
                row.getMoralTotal(), row.getIntellectualTotal(), row.getPhysicalTotal(), row.getLaborTotal(),
                row.getGrandTotal(), row.getSubmittedAt(), row.getConfirmedAt(), row.getConfirmComment(), row.getVersion());
    }

    private AdminFinalRecordListItemView toAdminListItem(FinalRecordQueryRow row) {
        return new AdminFinalRecordListItemView(row.getFinalRecordId(), row.getStudentUserId(), row.getStudentUserNo(),
                row.getStudentUserName(), row.getOrgUnitId(), row.getOrgUnitName(), row.getAcademicYear(), row.getStatus(),
                row.getMoralTotal(), row.getIntellectualTotal(), row.getPhysicalTotal(), row.getLaborTotal(),
                row.getGrandTotal(), row.getSubmittedAt(), row.getConfirmedAt(), row.getVersion());
    }

    private UnsubmittedStudentView toUnsubmittedStudentView(UnsubmittedStudentRow row) {
        return new UnsubmittedStudentView(row.getStudentUserId(), blankIfNull(row.getUserNo()), blankIfNull(row.getUserName()),
                blankIfNull(row.getGrade()), blankIfNull(row.getClassName()), "UNSUBMITTED", instantToString(row.getLastUpdatedAt()));
    }

    private String blankIfNull(String value) {
        return value == null ? "" : value;
    }

    private String instantToString(Instant value) {
        return value == null ? "" : value.toString();
    }

    private FinalComponentScoreView toComponentView(FinalComponentScoreRow row) {
        return new FinalComponentScoreView(row.getId(), row.getFinalRecordId(), row.getCategoryCode(), row.getItemCode(),
                row.getItemName(), row.getScoreValue(), row.getDisplayText(), row.getSourceType(), row.getSourceRefId(), row.getCreatedAt());
    }
}

package edu.whut.eval.application.finalrecord.service;

import edu.whut.eval.application.auth.AuthorizationPermissionCodes;
import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import edu.whut.eval.application.finalrecord.command.ConfirmFinalRecordCommand;
import edu.whut.eval.application.finalrecord.command.SubmitFinalRecordCommand;
import edu.whut.eval.application.finalrecord.query.ConfirmFinalRecordResultView;
import edu.whut.eval.application.finalrecord.query.FinalRecordQueryRow;
import edu.whut.eval.application.finalrecord.query.FinalRecordStudentView;
import edu.whut.eval.application.finalrecord.repository.FinalRecordQueryRepository;
import edu.whut.eval.common.exception.AccessDeniedAppException;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.common.exception.ResourceNotFoundException;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.domain.finalrecord.model.FinalRecord;
import edu.whut.eval.domain.finalrecord.model.FinalRecordStatus;
import edu.whut.eval.domain.finalrecord.repository.AggregatedFinalRecordSnapshot;
import edu.whut.eval.domain.finalrecord.repository.FinalRecordRepository;
import edu.whut.eval.domain.finalrecord.service.FinalSubmissionWindowPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class FinalRecordCommandApplicationService {

    private final UserAuthorizationContextAssembler userAuthorizationContextAssembler;
    private final FinalRecordRepository finalRecordRepository;
    private final FinalRecordQueryRepository finalRecordQueryRepository;
    private final FinalSubmissionWindowPolicy finalSubmissionWindowPolicy;
    private final FinalRecordAccessValidator finalRecordAccessValidator;

    public FinalRecordCommandApplicationService(UserAuthorizationContextAssembler userAuthorizationContextAssembler,
                                                FinalRecordRepository finalRecordRepository,
                                                FinalRecordQueryRepository finalRecordQueryRepository,
                                                FinalSubmissionWindowPolicy finalSubmissionWindowPolicy,
                                                FinalRecordAccessValidator finalRecordAccessValidator) {
        this.userAuthorizationContextAssembler = userAuthorizationContextAssembler;
        this.finalRecordRepository = finalRecordRepository;
        this.finalRecordQueryRepository = finalRecordQueryRepository;
        this.finalSubmissionWindowPolicy = finalSubmissionWindowPolicy;
        this.finalRecordAccessValidator = finalRecordAccessValidator;
    }

    @Transactional
    public FinalRecordStudentView submit(SubmitFinalRecordCommand command) {
        if (command.expectedVersion() == null) {
            throw new ValidationException("expectedVersion 不能为空");
        }
        if (command.academicYear() == null || command.academicYear().isBlank()) {
            throw new ValidationException("academicYear 不能为空");
        }
        UserAuthorizationContext student = userAuthorizationContextAssembler.requiredAuthorizationContext();
        if (!student.hasAuthority(AuthorizationPermissionCodes.FINAL_SUBMIT_SELF)) {
            throw new AccessDeniedAppException("当前用户无最终成绩提交权限");
        }
        finalSubmissionWindowPolicy.assertSubmitAllowed(student.getUserId(), command.academicYear(), Instant.now());
        Optional<FinalRecord> existing = finalRecordRepository.findByStudentAndAcademicYear(student.getUserId(), command.academicYear());
        if (existing.isPresent()) {
            FinalRecord existingRecord = existing.get();
            if (existingRecord.getStatus() == FinalRecordStatus.DRAFT) {
                finalRecordRepository.deleteComponents(existingRecord.getId());
                finalRecordRepository.deleteDraft(existingRecord.getId());
            } else {
                throw new ConflictException("最终成绩已存在，不能重复汇总");
            }
        }
        if (command.expectedVersion() != 0L) {
            throw new ConflictException("首次提交 expectedVersion 必须为 0");
        }
        AggregatedFinalRecordSnapshot snapshot = finalRecordRepository.aggregateApprovedFacts(student.getUserId(), command.academicYear());
        FinalRecord draft = FinalRecord.createDraft(null, student.getUserId(), command.academicYear(),
                snapshot.moralTotal(), snapshot.intellectualTotal(), snapshot.physicalTotal(),
                snapshot.laborTotal(), snapshot.grandTotal(), Instant.now());
        FinalRecord inserted = finalRecordRepository.insertDraft(draft);
        finalRecordRepository.deleteComponents(inserted.getId());
        finalRecordRepository.batchInsertComponents(inserted.getId(), snapshot.components());
        FinalRecord submitted = finalRecordRepository.updateTransition(inserted.submit(0L));
        return toStudentView(submitted);
    }

    @Transactional
    public ConfirmFinalRecordResultView confirm(ConfirmFinalRecordCommand command) {
        if (command.expectedVersion() == null) {
            throw new ValidationException("expectedVersion 不能为空");
        }
        if (command.comment() != null && command.comment().length() > 1000) {
            throw new ValidationException("comment 不能超过 1000 字");
        }
        UserAuthorizationContext admin = userAuthorizationContextAssembler.requiredAuthorizationContext();
        if (!admin.hasAuthority(AuthorizationPermissionCodes.SCORE_CONFIRM_ASSIGNED)) {
            throw new AccessDeniedAppException("当前用户无最终成绩确认权限");
        }
        FinalRecord record = finalRecordRepository.findById(command.recordId())
                .orElseThrow(() -> new ResourceNotFoundException("最终成绩不存在"));
        FinalRecordQueryRow row = finalRecordQueryRepository.findAdminFinalRecordDetail(command.recordId())
                .orElseThrow(() -> new ConflictException("final record projection incomplete"));
        finalRecordAccessValidator.requireAccess(admin, row, AuthorizationPermissionCodes.SCORE_CONFIRM_ASSIGNED);
        FinalRecord confirmed = finalRecordRepository.updateTransition(record.confirm(command.expectedVersion(), command.comment()));
        return new ConfirmFinalRecordResultView(
                confirmed.getId(),
                confirmed.getStatus(),
                confirmed.getConfirmComment(),
                confirmed.getConfirmedAt(),
                confirmed.getVersion()
        );
    }

    private FinalRecordStudentView toStudentView(FinalRecord record) {
        return new FinalRecordStudentView(
                record.getId(),
                record.getStudentUserId(),
                record.getAcademicYear(),
                record.getStatus(),
                record.getMoralTotal(),
                record.getIntellectualTotal(),
                record.getPhysicalTotal(),
                record.getLaborTotal(),
                record.getGrandTotal(),
                record.getSubmittedAt(),
                record.getConfirmedAt(),
                record.getVersion()
        );
    }
}

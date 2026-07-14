package edu.whut.eval.application.finalrecord.exporting;

import edu.whut.eval.application.auth.AuthorizationPermissionCodes;
import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import edu.whut.eval.application.finalrecord.repository.FinalRecordQueryRepository;
import edu.whut.eval.common.exception.AccessDeniedAppException;
import edu.whut.eval.common.exception.ResourceNotFoundException;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.domain.finalrecord.query.FinalRecordAccessContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FinalScoreExportApplicationService {

    public static final int MAX_SYNC_EXPORT_ROWS = 20_000;

    private static final Logger log = LoggerFactory.getLogger(FinalScoreExportApplicationService.class);

    private final UserAuthorizationContextAssembler userAuthorizationContextAssembler;
    private final FinalRecordQueryRepository finalRecordQueryRepository;
    private final FinalScoreExportWorkbookWriter workbookWriter;

    public FinalScoreExportApplicationService(UserAuthorizationContextAssembler userAuthorizationContextAssembler,
                                              FinalRecordQueryRepository finalRecordQueryRepository,
                                              FinalScoreExportWorkbookWriter workbookWriter) {
        this.userAuthorizationContextAssembler = userAuthorizationContextAssembler;
        this.finalRecordQueryRepository = finalRecordQueryRepository;
        this.workbookWriter = workbookWriter;
    }

    public FinalScoreExportFile export(FinalScoreExportQuery query) {
        UserAuthorizationContext admin = userAuthorizationContextAssembler.requiredAuthorizationContext();
        ensureExportPermission(admin);
        List<FinalScoreExportRow> rows = finalRecordQueryRepository.listAdminFinalScoreExportRows(
                toAccessContext(admin),
                query,
                MAX_SYNC_EXPORT_ROWS + 1
        );
        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("无匹配导出数据");
        }
        if (rows.size() > MAX_SYNC_EXPORT_ROWS) {
            log.warn("final-score-export.row-cap-exceeded academicYear={} status={} grade={} classes={} returnedRowCount={} maxSyncExportRows={}",
                    query.academicYear(),
                    query.status(),
                    query.grade(),
                    query.classes(),
                    rows.size(),
                    MAX_SYNC_EXPORT_ROWS);
            throw new FinalScoreExportGenerationException("Excel 生成失败");
        }
        try {
            return workbookWriter.write(query.academicYear(), rows);
        } catch (FinalScoreExportGenerationException exception) {
            Throwable logged = exception.getCause() == null ? exception : exception.getCause();
            logWriterFailure(query, rows.size(), logged);
            throw exception;
        } catch (RuntimeException exception) {
            logWriterFailure(query, rows.size(), exception);
            throw new FinalScoreExportGenerationException("Excel 生成失败", exception);
        }
    }

    private void ensureExportPermission(UserAuthorizationContext context) {
        if (!context.hasAuthority(AuthorizationPermissionCodes.SCORE_EXPORT_ASSIGNED)) {
            throw new AccessDeniedAppException("当前用户无最终成绩导出权限");
        }
    }

    private FinalRecordAccessContext toAccessContext(UserAuthorizationContext context) {
        return new FinalRecordAccessContext(
                context.getUserId(),
                context.getUserNo(),
                context.getUserName(),
                context.getIdentity(),
                context.getRoles(),
                context.getAuthorities(),
                context.getScopeRules(),
                AuthorizationPermissionCodes.SCORE_EXPORT_ASSIGNED
        );
    }

    private void logWriterFailure(FinalScoreExportQuery query, int rowCount, Throwable exception) {
        log.error("final-score-export.workbook-writer-failed academicYear={} rowCount={} exceptionType={} exceptionMessage={}",
                query.academicYear(),
                rowCount,
                exception.getClass().getSimpleName(),
                exception.getMessage(),
                exception);
    }
}

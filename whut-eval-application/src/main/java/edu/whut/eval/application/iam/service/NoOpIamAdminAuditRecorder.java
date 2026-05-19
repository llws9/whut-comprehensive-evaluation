package edu.whut.eval.application.iam.service;

import edu.whut.eval.domain.iam.model.IamRoleAssignmentDetail;
import org.springframework.stereotype.Component;

/**
 * 默认空实现，确保主链路先可运行。
 */
@Component
public class NoOpIamAdminAuditRecorder implements IamAdminAuditRecorder {

    @Override
    public void recordRoleAssignmentUpdated(Long operatorUserId,
                                            IamRoleAssignmentDetail before,
                                            IamRoleAssignmentDetail after) {
        // no-op
    }
}

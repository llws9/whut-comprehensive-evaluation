package edu.whut.eval.domain.iam.repository;

import edu.whut.eval.domain.iam.model.IamScopeRuleDetail;

import java.util.List;
import java.util.Map;

public interface ScopeRuleAdminRepository {

    List<IamScopeRuleDetail> findByAssignmentId(Long assignmentId);

    boolean existsSemanticDuplicate(Long assignmentId,
                                    String permissionCode,
                                    String scopeType,
                                    Long orgUnitId,
                                    String categoryCode,
                                    String itemCode,
                                    Map<String, Object> expressionJson);

    IamScopeRuleDetail create(Long assignmentId,
                              String permissionCode,
                              String scopeType,
                              Long orgUnitId,
                              String orgUnitName,
                              String categoryCode,
                              String itemCode,
                              Map<String, Object> expressionJson,
                              Integer priority,
                              String status);
}

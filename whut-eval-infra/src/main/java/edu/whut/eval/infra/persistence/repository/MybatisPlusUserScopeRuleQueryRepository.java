package edu.whut.eval.infra.persistence.repository;

import edu.whut.eval.domain.iam.model.IamScopeRule;
import edu.whut.eval.domain.iam.repository.UserScopeRuleQueryRepository;
import edu.whut.eval.infra.persistence.mapper.IamScopeRuleQueryMapper;
import edu.whut.eval.infra.persistence.query.IamScopeRuleRow;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MybatisPlusUserScopeRuleQueryRepository implements UserScopeRuleQueryRepository {

    private final IamScopeRuleQueryMapper iamScopeRuleQueryMapper;

    public MybatisPlusUserScopeRuleQueryRepository(IamScopeRuleQueryMapper iamScopeRuleQueryMapper) {
        this.iamScopeRuleQueryMapper = iamScopeRuleQueryMapper;
    }

    @Override
    public List<IamScopeRule> findActiveScopeRulesByUserId(Long userId) {
        return iamScopeRuleQueryMapper.selectActiveScopeRulesByUserId(userId).stream()
                .map(this::toDomain)
                .toList();
    }

    private IamScopeRule toDomain(IamScopeRuleRow row) {
        return new IamScopeRule(
                row.getAssignmentId(),
                row.getPermissionCode(),
                row.getScopeType(),
                row.getOrgUnitId(),
                row.getCategoryCode(),
                row.getItemCode(),
                row.getExpressionJson(),
                row.getPriority(),
                row.getStatus()
        );
    }
}

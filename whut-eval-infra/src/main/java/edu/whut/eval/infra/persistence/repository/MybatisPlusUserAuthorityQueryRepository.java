package edu.whut.eval.infra.persistence.repository;

import edu.whut.eval.domain.iam.repository.UserAuthorityQueryRepository;
import edu.whut.eval.infra.persistence.mapper.IamPermissionQueryMapper;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashSet;
import java.util.Set;

@Repository
public class MybatisPlusUserAuthorityQueryRepository implements UserAuthorityQueryRepository {

    private final IamPermissionQueryMapper iamPermissionQueryMapper;

    public MybatisPlusUserAuthorityQueryRepository(IamPermissionQueryMapper iamPermissionQueryMapper) {
        this.iamPermissionQueryMapper = iamPermissionQueryMapper;
    }

    @Override
    public Set<String> findActivePermissionCodesByUserId(Long userId) {
        return new LinkedHashSet<>(iamPermissionQueryMapper.selectActivePermissionCodesByUserId(userId));
    }
}

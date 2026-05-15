package edu.whut.eval.domain.iam.repository;

import java.util.Set;

public interface UserAuthorityQueryRepository {

    Set<String> findActivePermissionCodesByUserId(Long userId);
}

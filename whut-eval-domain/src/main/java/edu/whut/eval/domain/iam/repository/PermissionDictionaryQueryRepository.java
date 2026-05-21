package edu.whut.eval.domain.iam.repository;

import edu.whut.eval.domain.iam.model.PermissionDictionaryEntry;

import java.util.List;
import java.util.Set;

public interface PermissionDictionaryQueryRepository {

    List<PermissionDictionaryEntry> findPermissions(String keyword, String module, String status);

    List<PermissionDictionaryEntry> findByCodes(Set<String> permissionCodes, String status);
}

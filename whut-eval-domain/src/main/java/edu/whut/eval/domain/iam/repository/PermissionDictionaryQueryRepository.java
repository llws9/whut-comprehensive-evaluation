package edu.whut.eval.domain.iam.repository;

import edu.whut.eval.domain.iam.model.PermissionDictionaryEntry;

import java.util.List;

public interface PermissionDictionaryQueryRepository {

    List<PermissionDictionaryEntry> findPermissions(String keyword, String module, String status);
}

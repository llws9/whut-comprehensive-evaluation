package edu.whut.eval.infra.persistence.repository;

import edu.whut.eval.domain.iam.model.PermissionDictionaryEntry;
import edu.whut.eval.domain.iam.repository.PermissionDictionaryQueryRepository;
import edu.whut.eval.infra.persistence.mapper.AdminPermissionDictionaryMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public class MybatisPermissionDictionaryQueryRepository implements PermissionDictionaryQueryRepository {

    private final AdminPermissionDictionaryMapper adminPermissionDictionaryMapper;

    public MybatisPermissionDictionaryQueryRepository(AdminPermissionDictionaryMapper adminPermissionDictionaryMapper) {
        this.adminPermissionDictionaryMapper = adminPermissionDictionaryMapper;
    }

    @Override
    public List<PermissionDictionaryEntry> findPermissions(String keyword, String module, String status) {
        return adminPermissionDictionaryMapper.selectPermissions(keyword, module, status)
                .stream()
                .map(row -> new PermissionDictionaryEntry(
                        row.getPermissionCode(),
                        row.getPermissionName(),
                        row.getModule(),
                        row.getDescription(),
                        row.getStatus()
                ))
                .toList();
    }

    @Override
    public List<PermissionDictionaryEntry> findByCodes(Set<String> permissionCodes, String status) {
        if (permissionCodes == null || permissionCodes.isEmpty()) {
            return List.of();
        }
        return adminPermissionDictionaryMapper.selectPermissionsByCodes(permissionCodes, status)
                .stream()
                .map(row -> new PermissionDictionaryEntry(
                        row.getPermissionCode(),
                        row.getPermissionName(),
                        row.getModule(),
                        row.getDescription(),
                        row.getStatus()
                ))
                .toList();
    }
}

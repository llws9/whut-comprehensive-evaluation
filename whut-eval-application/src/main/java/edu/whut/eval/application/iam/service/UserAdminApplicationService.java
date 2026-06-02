package edu.whut.eval.application.iam.service;

import edu.whut.eval.application.auth.service.SessionRevocationService;
import edu.whut.eval.application.iam.command.CreateUserCommand;
import edu.whut.eval.application.iam.command.ImportUsersCommand;
import edu.whut.eval.application.iam.command.UpdateUserStatusCommand;
import edu.whut.eval.application.iam.query.UserAdminPageItemView;
import edu.whut.eval.application.iam.query.UserAdminPageQuery;
import edu.whut.eval.application.iam.query.UserCreatedView;
import edu.whut.eval.application.iam.query.UserImportFailedRowView;
import edu.whut.eval.application.iam.query.UserImportResultView;
import edu.whut.eval.application.iam.query.UserImportRowView;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.common.exception.ResourceNotFoundException;
import edu.whut.eval.domain.iam.model.IamUser;
import edu.whut.eval.domain.iam.repository.IamUserCommandRepository;
import edu.whut.eval.domain.iam.repository.IamUserQueryRepository;
import edu.whut.eval.domain.org.repository.OrgUnitLookupRepository;
import edu.whut.eval.domain.org.repository.UserMembershipAdminRepository;
import edu.whut.eval.domain.shared.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

@Service
public class UserAdminApplicationService {

    private static final Set<String> ALLOWED_IMPORT_MODE = Set.of("UPSERT", "INSERT_ONLY");

    private final IamUserQueryRepository userQueryRepository;
    private final IamUserCommandRepository userCommandRepository;
    private final SessionRevocationService sessionRevocationService;
    private final UserImportParser userImportParser;
    private final OrgUnitLookupRepository orgUnitLookupRepository;
    private final UserMembershipAdminRepository userMembershipAdminRepository;

    public UserAdminApplicationService(IamUserQueryRepository userQueryRepository,
                                       IamUserCommandRepository userCommandRepository,
                                       SessionRevocationService sessionRevocationService,
                                       UserImportParser userImportParser,
                                       OrgUnitLookupRepository orgUnitLookupRepository,
                                       UserMembershipAdminRepository userMembershipAdminRepository) {
        this.userQueryRepository = userQueryRepository;
        this.userCommandRepository = userCommandRepository;
        this.sessionRevocationService = sessionRevocationService;
        this.userImportParser = userImportParser;
        this.orgUnitLookupRepository = orgUnitLookupRepository;
        this.userMembershipAdminRepository = userMembershipAdminRepository;
    }

    @Transactional(readOnly = true)
    public PageResult<UserAdminPageItemView> pageUsers(UserAdminPageQuery query) {
        edu.whut.eval.domain.iam.query.UserPageQuery domainQuery = new edu.whut.eval.domain.iam.query.UserPageQuery();
        domainQuery.setPageNo(query.pageNo());
        domainQuery.setPageSize(query.pageSize());
        String keyword = query.keyword();
        if (keyword != null) {
            keyword = keyword.trim();
            if (keyword.isBlank()) {
                keyword = null;
            }
        }
        domainQuery.setKeyword(keyword);
        domainQuery.setStatus(query.status());
        domainQuery.setOrgUnitId(query.orgUnitId());

        PageResult<IamUser> page = userQueryRepository.pageUsers(domainQuery);
        List<UserAdminPageItemView> views = page.records().stream()
                .map(user -> new UserAdminPageItemView(
                        user.id(),
                        user.userNo(),
                        user.userName(),
                        user.status(),
                        List.of(),
                        List.of(),
                        null
                ))
                .toList();
        return new PageResult<>(page.total(), views);
    }

    @Transactional
    public UserCreatedView createUser(CreateUserCommand command) {
        userQueryRepository.findByUserNo(command.userNo()).ifPresent(u -> {
            throw new ConflictException("用户编号已存在: " + command.userNo());
        });

        if (command.primaryOrgUnitId() != null) {
            orgUnitLookupRepository.findById(command.primaryOrgUnitId())
                    .orElseThrow(() -> new ResourceNotFoundException("组织不存在: " + command.primaryOrgUnitId()));
        }

        String passwordHash = hashPassword(command.passwordHash());
        IamUser user = userCommandRepository.createUser(
                command.userNo(),
                command.userName(),
                passwordHash,
                command.email(),
                command.phone()
        );

        if (command.primaryOrgUnitId() != null) {
            userMembershipAdminRepository.createPrimaryMembership(
                    user.id(),
                    command.primaryOrgUnitId(),
                    LocalDateTime.now().toString()
            );
        }

        return new UserCreatedView(
                user.id(),
                user.userNo(),
                user.userName(),
                user.status()
        );
    }

    @Transactional
    public void updateStatus(Long userId, UpdateUserStatusCommand command) {
        boolean updated = userCommandRepository.updateStatus(userId, command.status());
        if (!updated) {
            throw new ResourceNotFoundException("用户不存在: " + userId);
        }
        if ("DISABLED".equals(command.status())) {
            sessionRevocationService.revokeAllActiveSessions(userId, "user_disabled");
        }
        if ("LOCKED".equals(command.status())) {
            sessionRevocationService.revokeAllActiveSessions(userId, "user_locked");
        }
    }

    @Transactional
    public UserImportResultView importUsers(ImportUsersCommand command) {
        if (command.importMode() == null || !ALLOWED_IMPORT_MODE.contains(command.importMode())) {
            throw new edu.whut.eval.common.exception.ValidationException("importMode 仅允许 UPSERT 或 INSERT_ONLY");
        }
        if (command.fileContent() == null || command.fileContent().length == 0) {
            throw new edu.whut.eval.common.exception.ValidationException("上传文件不能为空");
        }

        List<UserImportRowView> rows = userImportParser.parse(command.fileContent());
        if (rows.isEmpty()) {
            return new UserImportResultView(0, 0, 0, List.of());
        }

        if ("INSERT_ONLY".equals(command.importMode())) {
            for (UserImportRowView row : rows) {
                String userNo = row.userNo() == null ? null : row.userNo().trim();
                if (userNo != null && !userNo.isBlank() && userQueryRepository.findByUserNo(userNo).isPresent()) {
                    throw new ConflictException("INSERT_ONLY 模式存在重复 userNo: " + userNo);
                }
            }
        }

        long totalCount = rows.size();
        long successCount = 0;
        List<UserImportFailedRowView> failedRows = new ArrayList<>();

        for (UserImportRowView row : rows) {
            String userNo = row.userNo() == null ? null : row.userNo().trim();
            String userName = row.userName() == null ? null : row.userName().trim();
            String password = row.password() == null ? null : row.password().trim();

            if (userNo == null || userNo.isBlank()) {
                failedRows.add(new UserImportFailedRowView(row.rowNo(), "userNo 不能为空"));
                continue;
            }
            if (userName == null || userName.isBlank()) {
                failedRows.add(new UserImportFailedRowView(row.rowNo(), "userName 不能为空"));
                continue;
            }
            if (password == null || password.isBlank()) {
                failedRows.add(new UserImportFailedRowView(row.rowNo(), "password 不能为空"));
                continue;
            }

            String passwordHash = hashPassword(password);
            IamUser existing = userQueryRepository.findByUserNo(userNo).orElse(null);
            if (existing == null) {
                userCommandRepository.createUser(userNo, userName, passwordHash, row.email(), row.phone());
            } else {
                userCommandRepository.updateForImportByUserNo(userNo, userName, passwordHash, row.email(), row.phone());
            }
            successCount++;
        }

        long failedCount = failedRows.size();
        return new UserImportResultView(totalCount, successCount, failedCount, failedRows);
    }

    private String hashPassword(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawPassword.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available", exception);
        }
    }
}
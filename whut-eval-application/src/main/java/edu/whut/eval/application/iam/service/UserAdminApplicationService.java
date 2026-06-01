package edu.whut.eval.application.iam.service;

import edu.whut.eval.application.auth.service.SessionRevocationService;
import edu.whut.eval.application.iam.command.CreateUserCommand;
import edu.whut.eval.application.iam.command.UpdateUserStatusCommand;
import edu.whut.eval.application.iam.query.UserAdminPageItemView;
import edu.whut.eval.application.iam.query.UserAdminPageQuery;
import edu.whut.eval.application.iam.query.UserCreatedView;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.common.exception.ResourceNotFoundException;
import edu.whut.eval.domain.iam.model.IamUser;
import edu.whut.eval.domain.iam.repository.IamUserCommandRepository;
import edu.whut.eval.domain.iam.repository.IamUserQueryRepository;
import edu.whut.eval.domain.shared.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Service
public class UserAdminApplicationService {

    private final IamUserQueryRepository userQueryRepository;
    private final IamUserCommandRepository userCommandRepository;
    private final SessionRevocationService sessionRevocationService;

    public UserAdminApplicationService(IamUserQueryRepository userQueryRepository,
                                        IamUserCommandRepository userCommandRepository,
                                        SessionRevocationService sessionRevocationService) {
        this.userQueryRepository = userQueryRepository;
        this.userCommandRepository = userCommandRepository;
        this.sessionRevocationService = sessionRevocationService;
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

        String passwordHash = hashPassword(command.passwordHash());
        IamUser user = userCommandRepository.createUser(
                command.userNo(),
                command.userName(),
                passwordHash,
                command.email(),
                command.phone()
        );

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
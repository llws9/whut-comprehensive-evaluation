package edu.whut.eval.application.iam.service;

import edu.whut.eval.application.iam.command.ImportUsersCommand;
import edu.whut.eval.application.auth.service.PasswordHasher;
import edu.whut.eval.application.iam.command.CreateUserCommand;
import edu.whut.eval.application.iam.command.UpdateUserStatusCommand;
import edu.whut.eval.application.iam.query.UserImportFailedRowView;
import edu.whut.eval.application.iam.query.UserImportResultView;
import edu.whut.eval.application.iam.query.UserAdminView;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.common.exception.FileStorageException;
import edu.whut.eval.common.exception.ResourceNotFoundException;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.iam.model.IamUser;
import edu.whut.eval.domain.iam.repository.IamUserQueryRepository;
import edu.whut.eval.domain.iam.repository.UserAdminCommandRepository;
import edu.whut.eval.domain.org.model.OrgMembership;
import edu.whut.eval.domain.org.model.OrgUnit;
import edu.whut.eval.domain.org.repository.OrgUnitLookupRepository;
import edu.whut.eval.domain.org.repository.UserMembershipAdminRepository;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class DefaultUserAdminCommandApplicationService implements UserAdminCommandApplicationService {

    private static final Set<String> ALLOWED_STATUS = Set.of("ACTIVE", "DISABLED", "LOCKED");
    private static final Set<String> ALLOWED_IMPORT_MODES = Set.of("UPSERT", "INSERT_ONLY");
    private static final List<String> IMPORT_HEADERS = List.of(
            "userNo",
            "userName",
            "password",
            "email",
            "phone",
            "primaryOrgUnitCode"
    );
    private static final String IMPORT_TEMPLATE_ERROR_MESSAGE =
            "导入模板错误，表头必须严格匹配: userNo,userName,password,email,phone,primaryOrgUnitCode";

    private final IamUserQueryRepository iamUserQueryRepository;
    private final UserAdminCommandRepository userAdminCommandRepository;
    private final OrgUnitLookupRepository orgUnitLookupRepository;
    private final UserMembershipAdminRepository userMembershipAdminRepository;
    private final PasswordHasher passwordHasher;

    public DefaultUserAdminCommandApplicationService(IamUserQueryRepository iamUserQueryRepository,
                                                     UserAdminCommandRepository userAdminCommandRepository,
                                                     OrgUnitLookupRepository orgUnitLookupRepository,
                                                     UserMembershipAdminRepository userMembershipAdminRepository,
                                                     PasswordHasher passwordHasher) {
        this.iamUserQueryRepository = iamUserQueryRepository;
        this.userAdminCommandRepository = userAdminCommandRepository;
        this.orgUnitLookupRepository = orgUnitLookupRepository;
        this.userMembershipAdminRepository = userMembershipAdminRepository;
        this.passwordHasher = passwordHasher;
    }

    @Override
    @Transactional
    public UserAdminView createUser(CreateUserCommand command) {
        String userNo = requireText(command.userNo(), "userNo");
        String userName = requireText(command.userName(), "userName");
        String password = requireText(command.password(), "password");
        String email = normalize(command.email());
        String phone = normalize(command.phone());

        iamUserQueryRepository.findByUserNo(userNo)
                .ifPresent(item -> {
                    throw new ConflictException("userNo 已存在: " + userNo);
                });

        OrgUnit primaryOrgUnit = resolveOrgUnit(command.primaryOrgUnitId());
        IamUser created = userAdminCommandRepository.create(
                userNo,
                userName,
                email,
                phone,
                passwordHasher.hash(password),
                "ACTIVE"
        );
        if (primaryOrgUnit != null) {
            attachPrimaryMembership(created.id(), primaryOrgUnit.id());
        }
        return toView(created);
    }

    @Override
    @Transactional
    public UserImportResultView importUsers(ImportUsersCommand command) {
        validateImportCommand(command);
        String importMode = normalizeImportMode(command.importMode());
        List<ImportSheetRow> rows = parseImportRows(command.inputStream());
        List<UserImportFailedRowView> failedRows = new ArrayList<>();
        long successCount = 0;

        for (ImportSheetRow row : rows) {
            try {
                importSingleRow(row, importMode);
                successCount++;
            } catch (ConflictException exception) {
                throw exception;
            } catch (ValidationException exception) {
                failedRows.add(new UserImportFailedRowView(row.rowNo(), normalize(row.userNo()), exception.getMessage()));
            }
        }

        return new UserImportResultView(
                rows.size(),
                successCount,
                failedRows.size(),
                failedRows
        );
    }

    @Override
    @Transactional
    public void updateUserStatus(Long userId, UpdateUserStatusCommand command) {
        IamUser existing = iamUserQueryRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在: " + userId));
        String status = requireText(command.status(), "status");
        if (!ALLOWED_STATUS.contains(status)) {
            throw new ValidationException("status 仅允许 ACTIVE、DISABLED 或 LOCKED");
        }
        if (status.equals(existing.status())) {
            throw new ConflictException("用户状态未变化");
        }
        boolean updated = userAdminCommandRepository.updateStatus(userId, existing.status(), status);
        if (!updated) {
            throw new ConflictException("用户状态已变更，请刷新后重试");
        }
    }

    private OrgUnit resolveOrgUnit(Long orgUnitId) {
        if (orgUnitId == null) {
            return null;
        }
        return orgUnitLookupRepository.findById(orgUnitId)
                .orElseThrow(() -> new ResourceNotFoundException("组织不存在: " + orgUnitId));
    }

    private void importSingleRow(ImportSheetRow row, String importMode) {
        String userNo = requireText(row.userNo(), "userNo");
        String userName = requireText(row.userName(), "userName");
        String password = requireText(row.password(), "password");
        String email = normalize(row.email());
        String phone = normalize(row.phone());
        OrgUnit primaryOrgUnit = resolveOrgUnitByCode(row.primaryOrgUnitCode());
        IamUser existing = iamUserQueryRepository.findByUserNo(userNo).orElse(null);
        if (existing != null) {
            if ("INSERT_ONLY".equals(importMode)) {
                throw new ConflictException("INSERT_ONLY 模式下 userNo 已存在: " + userNo);
            }
            userAdminCommandRepository.updateProfile(
                    existing.id(),
                    userName,
                    email,
                    phone,
                    passwordHasher.hash(password)
            );
            if (primaryOrgUnit != null) {
                upsertPrimaryMembership(existing.id(), primaryOrgUnit.id());
            }
            return;
        }

        IamUser created = userAdminCommandRepository.create(
                userNo,
                userName,
                email,
                phone,
                passwordHasher.hash(password),
                "ACTIVE"
        );
        if (primaryOrgUnit != null) {
            attachPrimaryMembership(created.id(), primaryOrgUnit.id());
        }
    }

    private OrgUnit resolveOrgUnitByCode(String orgUnitCode) {
        String normalizedCode = normalize(orgUnitCode);
        if (normalizedCode == null) {
            return null;
        }
        return orgUnitLookupRepository.findByCode(normalizedCode)
                .orElseThrow(() -> new ValidationException("primaryOrgUnitCode 不存在: " + normalizedCode));
    }

    private void attachPrimaryMembership(Long userId, Long orgUnitId) {
        String now = LocalDateTime.now().toString();
        userMembershipAdminRepository.replaceMemberships(userId, List.of(
                new OrgMembership(
                        null,
                        userId,
                        orgUnitId,
                        "MANUAL",
                        true,
                        "ACTIVE",
                        now,
                        null
                )
        ), List.of());
    }

    private void upsertPrimaryMembership(Long userId, Long primaryOrgUnitId) {
        userMembershipAdminRepository.lockUserForMembershipReplace(userId);
        List<OrgMembership> existingMemberships = userMembershipAdminRepository.findActiveMembershipsByUserId(userId);
        String now = LocalDateTime.now().toString();
        List<OrgMembership> activeMemberships = new ArrayList<>();
        boolean found = false;
        for (OrgMembership membership : existingMemberships) {
            boolean isPrimary = membership.orgUnitId().equals(primaryOrgUnitId);
            activeMemberships.add(new OrgMembership(
                    membership.id(),
                    userId,
                    membership.orgUnitId(),
                    membership.membershipType(),
                    isPrimary,
                    "ACTIVE",
                    membership.joinedAt(),
                    null
            ));
            if (isPrimary) {
                found = true;
            }
        }
        if (!found) {
            activeMemberships.add(new OrgMembership(
                    null,
                    userId,
                    primaryOrgUnitId,
                    "MANUAL",
                    true,
                    "ACTIVE",
                    now,
                    null
            ));
        }
        userMembershipAdminRepository.replaceMemberships(userId, activeMemberships, List.of());
    }

    private void validateImportCommand(ImportUsersCommand command) {
        if (command == null || command.inputStream() == null || command.size() <= 0) {
            throw new ValidationException("导入文件不能为空");
        }
    }

    private String normalizeImportMode(String importMode) {
        String normalizedMode = requireText(importMode, "importMode");
        if (!ALLOWED_IMPORT_MODES.contains(normalizedMode)) {
            throw new ValidationException("importMode 仅允许 UPSERT 或 INSERT_ONLY");
        }
        return normalizedMode;
    }

    private List<ImportSheetRow> parseImportRows(InputStream inputStream) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
            if (workbook.getNumberOfSheets() <= 0) {
                throw new ValidationException(IMPORT_TEMPLATE_ERROR_MESSAGE);
            }
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            validateHeader(sheet.getRow(0), formatter);
            List<ImportSheetRow> rows = new ArrayList<>();
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (isBlankRow(row, formatter)) {
                    continue;
                }
                rows.add(new ImportSheetRow(
                        rowIndex + 1L,
                        readCell(row, 0, formatter),
                        readCell(row, 1, formatter),
                        readCell(row, 2, formatter),
                        readCell(row, 3, formatter),
                        readCell(row, 4, formatter),
                        readCell(row, 5, formatter)
                ));
            }
            return rows;
        } catch (ValidationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new FileStorageException("导入文件解析失败", exception);
        }
    }

    private void validateHeader(Row headerRow, DataFormatter formatter) {
        if (headerRow == null) {
            throw new ValidationException(IMPORT_TEMPLATE_ERROR_MESSAGE);
        }
        for (int index = 0; index < IMPORT_HEADERS.size(); index++) {
            if (!IMPORT_HEADERS.get(index).equals(readCell(headerRow, index, formatter))) {
                throw new ValidationException(IMPORT_TEMPLATE_ERROR_MESSAGE);
            }
        }
    }

    private boolean isBlankRow(Row row, DataFormatter formatter) {
        if (row == null) {
            return true;
        }
        for (int index = 0; index < IMPORT_HEADERS.size(); index++) {
            if (normalize(readCell(row, index, formatter)) != null) {
                return false;
            }
        }
        return true;
    }

    private String readCell(Row row, int cellIndex, DataFormatter formatter) {
        if (row == null || row.getCell(cellIndex) == null) {
            return null;
        }
        return normalize(formatter.formatCellValue(row.getCell(cellIndex)));
    }

    private String requireText(String value, String field) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new ValidationException(field + " 不能为空");
        }
        return normalized;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private UserAdminView toView(IamUser user) {
        return new UserAdminView(
                user.id(),
                user.userNo(),
                user.userName(),
                user.status()
        );
    }

    private record ImportSheetRow(
            long rowNo,
            String userNo,
            String userName,
            String password,
            String email,
            String phone,
            String primaryOrgUnitCode
    ) {
    }
}

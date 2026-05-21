package edu.whut.eval.app.iam;

import edu.whut.eval.application.auth.service.PasswordHasher;
import edu.whut.eval.application.iam.command.CreateUserCommand;
import edu.whut.eval.application.iam.command.ImportUsersCommand;
import edu.whut.eval.application.iam.command.UpdateUserStatusCommand;
import edu.whut.eval.common.exception.FileStorageException;
import edu.whut.eval.application.iam.query.UserImportResultView;
import edu.whut.eval.application.iam.query.UserAdminView;
import edu.whut.eval.application.iam.service.DefaultUserAdminCommandApplicationService;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.common.exception.ResourceNotFoundException;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.iam.model.IamUser;
import edu.whut.eval.domain.iam.repository.IamUserQueryRepository;
import edu.whut.eval.domain.iam.repository.UserAdminCommandRepository;
import edu.whut.eval.domain.org.model.OrgMembership;
import edu.whut.eval.domain.org.model.OrgUnit;
import edu.whut.eval.domain.org.repository.OrgUnitLookupRepository;
import edu.whut.eval.domain.org.repository.UserMembershipAdminRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DefaultUserAdminCommandApplicationServiceTest {

    @Mock
    private IamUserQueryRepository iamUserQueryRepository;

    @Mock
    private UserAdminCommandRepository userAdminCommandRepository;

    @Mock
    private OrgUnitLookupRepository orgUnitLookupRepository;

    @Mock
    private UserMembershipAdminRepository userMembershipAdminRepository;

    @Mock
    private PasswordHasher passwordHasher;

    @InjectMocks
    private DefaultUserAdminCommandApplicationService service;

    @Test
    void shouldCreateUserAndPrimaryMembershipWhenPrimaryOrgUnitProvided() {
        given(iamUserQueryRepository.findByUserNo("2024305001")).willReturn(Optional.empty());
        given(orgUnitLookupRepository.findById(2002L)).willReturn(Optional.of(
                new OrgUnit(2002L, 1L, "COLLEGE", "CS", "计算机与人工智能学院", "/1/2002/", "ACTIVE")
        ));
        given(passwordHasher.hash("ChangeMe123!")).willReturn("hashed-password");
        given(userAdminCommandRepository.create(
                "2024305001",
                "王老师",
                "wang@example.com",
                "13800000000",
                "hashed-password",
                "ACTIVE"
        )).willReturn(new IamUser(1010L, "2024305001", "王老师", "wang@example.com", "13800000000", "ACTIVE"));

        UserAdminView result = service.createUser(new CreateUserCommand(
                "2024305001",
                "王老师",
                "ChangeMe123!",
                "wang@example.com",
                "13800000000",
                2002L
        ));

        assertThat(result.userId()).isEqualTo(1010L);
        assertThat(result.userNo()).isEqualTo("2024305001");
        assertThat(result.status()).isEqualTo("ACTIVE");

        ArgumentCaptor<List<OrgMembership>> activeCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<OrgMembership>> inactiveCaptor = ArgumentCaptor.forClass(List.class);
        verify(userMembershipAdminRepository).replaceMemberships(eq(1010L), activeCaptor.capture(), inactiveCaptor.capture());
        assertThat(activeCaptor.getValue()).singleElement().satisfies(item -> {
            assertThat(item.id()).isNull();
            assertThat(item.userId()).isEqualTo(1010L);
            assertThat(item.orgUnitId()).isEqualTo(2002L);
            assertThat(item.membershipType()).isEqualTo("MANUAL");
            assertThat(item.isPrimary()).isTrue();
            assertThat(item.status()).isEqualTo("ACTIVE");
            assertThat(item.joinedAt()).isNotBlank();
            assertThat(item.leftAt()).isNull();
        });
        assertThat(inactiveCaptor.getValue()).isEmpty();
    }

    @Test
    void shouldRejectCreateWhenUserNoAlreadyExists() {
        given(iamUserQueryRepository.findByUserNo("2024305001")).willReturn(Optional.of(
                new IamUser(1010L, "2024305001", "王老师", "wang@example.com", "13800000000", "ACTIVE")
        ));

        assertThatThrownBy(() -> service.createUser(new CreateUserCommand(
                "2024305001",
                "王老师",
                "ChangeMe123!",
                "wang@example.com",
                "13800000000",
                2002L
        )))
                .isInstanceOf(ConflictException.class)
                .hasMessage("userNo 已存在: 2024305001");
    }

    @Test
    void shouldRejectCreateWhenPrimaryOrgUnitDoesNotExist() {
        given(iamUserQueryRepository.findByUserNo("2024305001")).willReturn(Optional.empty());
        given(orgUnitLookupRepository.findById(2002L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.createUser(new CreateUserCommand(
                "2024305001",
                "王老师",
                "ChangeMe123!",
                "wang@example.com",
                "13800000000",
                2002L
        )))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("组织不存在: 2002");
    }

    @Test
    void shouldSkipMembershipCreateWhenPrimaryOrgUnitMissing() {
        given(iamUserQueryRepository.findByUserNo("2024305001")).willReturn(Optional.empty());
        given(passwordHasher.hash("ChangeMe123!")).willReturn("hashed-password");
        given(userAdminCommandRepository.create(
                "2024305001",
                "王老师",
                null,
                null,
                "hashed-password",
                "ACTIVE"
        )).willReturn(new IamUser(1010L, "2024305001", "王老师", null, null, "ACTIVE"));

        service.createUser(new CreateUserCommand(
                "2024305001",
                "王老师",
                "ChangeMe123!",
                null,
                null,
                null
        ));

        verify(userMembershipAdminRepository, never()).replaceMemberships(eq(1010L), anyList(), anyList());
    }

    @Test
    void shouldRejectUpdateWhenUserDoesNotExist() {
        given(iamUserQueryRepository.findById(1010L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateUserStatus(1010L, new UpdateUserStatusCommand("DISABLED", "manual")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("用户不存在: 1010");
    }

    @Test
    void shouldRejectUpdateWhenStatusIsIllegal() {
        given(iamUserQueryRepository.findById(1010L)).willReturn(Optional.of(
                new IamUser(1010L, "2024305001", "王老师", "wang@example.com", "13800000000", "ACTIVE")
        ));

        assertThatThrownBy(() -> service.updateUserStatus(1010L, new UpdateUserStatusCommand("INACTIVE", "manual")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("status 仅允许 ACTIVE、DISABLED 或 LOCKED");
    }

    @Test
    void shouldRejectUpdateWhenStatusDoesNotChange() {
        given(iamUserQueryRepository.findById(1010L)).willReturn(Optional.of(
                new IamUser(1010L, "2024305001", "王老师", "wang@example.com", "13800000000", "DISABLED")
        ));

        assertThatThrownBy(() -> service.updateUserStatus(1010L, new UpdateUserStatusCommand("DISABLED", "manual")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("用户状态未变化");
    }

    @Test
    void shouldRejectUpdateWhenRepositoryReportsConflict() {
        given(iamUserQueryRepository.findById(1010L)).willReturn(Optional.of(
                new IamUser(1010L, "2024305001", "王老师", "wang@example.com", "13800000000", "ACTIVE")
        ));
        given(userAdminCommandRepository.updateStatus(1010L, "ACTIVE", "LOCKED")).willReturn(false);

        assertThatThrownBy(() -> service.updateUserStatus(1010L, new UpdateUserStatusCommand("LOCKED", "manual")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("用户状态已变更，请刷新后重试");
    }

    @Test
    void shouldUpdateUserStatusWhenTransitionIsValid() {
        given(iamUserQueryRepository.findById(1010L)).willReturn(Optional.of(
                new IamUser(1010L, "2024305001", "王老师", "wang@example.com", "13800000000", "ACTIVE")
        ));
        given(userAdminCommandRepository.updateStatus(1010L, "ACTIVE", "DISABLED")).willReturn(true);

        service.updateUserStatus(1010L, new UpdateUserStatusCommand("DISABLED", "manual"));

        verify(userAdminCommandRepository).updateStatus(1010L, "ACTIVE", "DISABLED");
    }

    @Test
    void shouldImportUsersInUpsertModeAndCollectFailedRows() throws Exception {
        byte[] workbook = createWorkbook(
                new String[]{"userNo", "userName", "password", "email", "phone", "primaryOrgUnitCode"},
                new String[][]{
                        {"2024305001", "王老师", "ChangeMe123!", "wang@example.com", "13800000000", "CS"},
                        {"2024305002", "李老师", "ResetMe123!", "li@example.com", "13900000000", "EE"},
                        {"2024305003", "", "ResetMe123!", "bad@example.com", "13700000000", "CS"}
                }
        );
        given(orgUnitLookupRepository.findByCode("CS")).willReturn(Optional.of(
                new OrgUnit(2002L, 1L, "COLLEGE", "CS", "计算机与人工智能学院", "/1/2002/", "ACTIVE")
        ));
        given(orgUnitLookupRepository.findByCode("EE")).willReturn(Optional.of(
                new OrgUnit(2003L, 1L, "COLLEGE", "EE", "电气工程学院", "/1/2003/", "ACTIVE")
        ));
        given(iamUserQueryRepository.findByUserNo("2024305001")).willReturn(Optional.empty());
        given(iamUserQueryRepository.findByUserNo("2024305002")).willReturn(Optional.of(
                new IamUser(2020L, "2024305002", "旧李老师", "old-li@example.com", "13600000000", "ACTIVE")
        ));
        given(passwordHasher.hash("ChangeMe123!")).willReturn("hash-1");
        given(passwordHasher.hash("ResetMe123!")).willReturn("hash-2");
        given(userAdminCommandRepository.create(
                "2024305001",
                "王老师",
                "wang@example.com",
                "13800000000",
                "hash-1",
                "ACTIVE"
        )).willReturn(new IamUser(1010L, "2024305001", "王老师", "wang@example.com", "13800000000", "ACTIVE"));
        given(userAdminCommandRepository.updateProfile(
                2020L,
                "李老师",
                "li@example.com",
                "13900000000",
                "hash-2"
        )).willReturn(new IamUser(2020L, "2024305002", "李老师", "li@example.com", "13900000000", "ACTIVE"));
        given(userMembershipAdminRepository.findActiveMembershipsByUserId(2020L)).willReturn(List.of(
                new OrgMembership(70021L, 2020L, 2009L, "SYNC", true, "ACTIVE", "2024-01-01T00:00:00", null)
        ));

        UserImportResultView result = service.importUsers(new ImportUsersCommand(
                new ByteArrayInputStream(workbook),
                "users.xlsx",
                workbook.length,
                "UPSERT"
        ));

        assertThat(result.totalCount()).isEqualTo(3);
        assertThat(result.successCount()).isEqualTo(2);
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.failedRows()).singleElement().satisfies(item -> {
            assertThat(item.rowNo()).isEqualTo(4);
            assertThat(item.userNo()).isEqualTo("2024305003");
            assertThat(item.reason()).isEqualTo("userName 不能为空");
        });
        verify(userMembershipAdminRepository).replaceMemberships(eq(1010L), anyList(), anyList());
        verify(userMembershipAdminRepository).lockUserForMembershipReplace(2020L);
        verify(userMembershipAdminRepository).replaceMemberships(eq(2020L), anyList(), anyList());
    }

    @Test
    void shouldRejectImportWhenImportModeIsIllegal() throws Exception {
        byte[] workbook = createWorkbook(
                new String[]{"userNo", "userName", "password", "email", "phone", "primaryOrgUnitCode"},
                new String[][]{{"2024305001", "王老师", "ChangeMe123!", "wang@example.com", "13800000000", "CS"}}
        );

        assertThatThrownBy(() -> service.importUsers(new ImportUsersCommand(
                new ByteArrayInputStream(workbook),
                "users.xlsx",
                workbook.length,
                "MERGE"
        )))
                .isInstanceOf(ValidationException.class)
                .hasMessage("importMode 仅允许 UPSERT 或 INSERT_ONLY");
    }

    @Test
    void shouldRejectImportWhenTemplateIsInvalid() throws Exception {
        byte[] workbook = createWorkbook(
                new String[]{"userNo", "userName", "password", "email", "phone"},
                new String[][]{{"2024305001", "王老师", "ChangeMe123!", "wang@example.com", "13800000000"}}
        );

        assertThatThrownBy(() -> service.importUsers(new ImportUsersCommand(
                new ByteArrayInputStream(workbook),
                "users.xlsx",
                workbook.length,
                "UPSERT"
        )))
                .isInstanceOf(ValidationException.class)
                .hasMessage("导入模板错误，表头必须严格匹配: userNo,userName,password,email,phone,primaryOrgUnitCode");
    }

    @Test
    void shouldReturn503WhenWorkbookCannotBeParsed() {
        byte[] invalidWorkbook = "not-an-xlsx".getBytes();

        assertThatThrownBy(() -> service.importUsers(new ImportUsersCommand(
                new ByteArrayInputStream(invalidWorkbook),
                "users.xlsx",
                invalidWorkbook.length,
                "UPSERT"
        )))
                .isInstanceOf(FileStorageException.class)
                .hasMessage("导入文件解析失败");
    }

    @Test
    void shouldRejectInsertOnlyWhenUserAlreadyExists() throws Exception {
        byte[] workbook = createWorkbook(
                new String[]{"userNo", "userName", "password", "email", "phone", "primaryOrgUnitCode"},
                new String[][]{{"2024305002", "李老师", "ResetMe123!", "li@example.com", "13900000000", "EE"}}
        );
        given(orgUnitLookupRepository.findByCode("EE")).willReturn(Optional.of(
                new OrgUnit(2003L, 1L, "COLLEGE", "EE", "电气工程学院", "/1/2003/", "ACTIVE")
        ));
        given(iamUserQueryRepository.findByUserNo("2024305002")).willReturn(Optional.of(
                new IamUser(2020L, "2024305002", "旧李老师", "old-li@example.com", "13600000000", "ACTIVE")
        ));

        assertThatThrownBy(() -> service.importUsers(new ImportUsersCommand(
                new ByteArrayInputStream(workbook),
                "users.xlsx",
                workbook.length,
                "INSERT_ONLY"
        )))
                .isInstanceOf(ConflictException.class)
                .hasMessage("INSERT_ONLY 模式下 userNo 已存在: 2024305002");
    }

    private byte[] createWorkbook(String[] headers, String[][] rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("users");
            var headerRow = sheet.createRow(0);
            for (int index = 0; index < headers.length; index++) {
                headerRow.createCell(index).setCellValue(headers[index]);
            }
            for (int rowIndex = 0; rowIndex < rows.length; rowIndex++) {
                var row = sheet.createRow(rowIndex + 1);
                for (int columnIndex = 0; columnIndex < rows[rowIndex].length; columnIndex++) {
                    row.createCell(columnIndex).setCellValue(rows[rowIndex][columnIndex]);
                }
            }
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }
}

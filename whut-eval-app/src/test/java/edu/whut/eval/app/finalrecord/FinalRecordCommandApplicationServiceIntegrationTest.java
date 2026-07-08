package edu.whut.eval.app.finalrecord;

import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import edu.whut.eval.application.finalrecord.command.SubmitFinalRecordCommand;
import edu.whut.eval.application.finalrecord.repository.FinalRecordQueryRepository;
import edu.whut.eval.application.finalrecord.service.FinalRecordAccessValidator;
import edu.whut.eval.application.finalrecord.service.FinalRecordCommandApplicationService;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.domain.finalrecord.model.FinalComponentScore;
import edu.whut.eval.domain.finalrecord.model.FinalRecord;
import edu.whut.eval.domain.finalrecord.repository.AggregatedFinalRecordSnapshot;
import edu.whut.eval.domain.finalrecord.repository.FinalRecordRepository;
import edu.whut.eval.domain.finalrecord.service.FinalSubmissionWindowPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = FinalRecordCommandApplicationServiceIntegrationTest.TestConfig.class)
class FinalRecordCommandApplicationServiceIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FinalRecordCommandApplicationService service;

    @Autowired
    private FailingAfterComponentInsertRepository failingRepository;

    @BeforeEach
    void setUpSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS final_component_score");
        jdbcTemplate.execute("DROP TABLE IF EXISTS final_record");
        jdbcTemplate.execute("""
                CREATE TABLE final_record (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  student_user_id BIGINT NOT NULL,
                  academic_year VARCHAR(32) NOT NULL,
                  status VARCHAR(32) NOT NULL,
                  moral_total DECIMAL(10,2) NOT NULL,
                  intellectual_total DECIMAL(10,2) NOT NULL,
                  physical_total DECIMAL(10,2) NOT NULL,
                  labor_total DECIMAL(10,2) NOT NULL,
                  grand_total DECIMAL(10,2) NOT NULL,
                  submitted_at DATETIME NULL,
                  confirmed_at DATETIME NULL,
                  confirm_comment VARCHAR(1000) NULL,
                  version BIGINT NOT NULL,
                  created_at DATETIME NOT NULL,
                  updated_at DATETIME NOT NULL,
                  UNIQUE KEY uk_final_record_student_year (student_user_id, academic_year))
                """);
        jdbcTemplate.execute("""
                CREATE TABLE final_component_score (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  final_record_id BIGINT NOT NULL,
                  category_code VARCHAR(64) NOT NULL,
                  item_code VARCHAR(64) NOT NULL,
                  score_value DECIMAL(10,2) NOT NULL,
                  display_text VARCHAR(1000) NULL,
                  source_type VARCHAR(32) NOT NULL,
                  source_ref_id VARCHAR(64) NULL,
                  created_at DATETIME NOT NULL)
                """);
        failingRepository.reset();
    }

    @Test
    void shouldRollbackDraftAndComponentsWhenSubmitTransitionFails() {
        failingRepository.failAfterComponents = true;

        assertThatThrownBy(() -> service.submit(new SubmitFinalRecordCommand("2025-2026", 0L)))
                .isInstanceOf(ConflictException.class);

        assertThat(countRows("final_record", "student_user_id = 1001")).isZero();
        assertThat(countRows("final_component_score", "source_ref_id = '21013'")).isZero();
    }

    private int countRows(String tableName, String condition) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName + " WHERE " + condition, Integer.class);
    }

    @Configuration
    @EnableTransactionManagement
    @Import(MybatisPlusFinalRecordRepositoryIntegrationTest.TestConfig.class)
    static class TestConfig {
        @Bean
        UserAuthorizationContextAssembler userAuthorizationContextAssembler() {
            UserAuthorizationContext student = new UserAuthorizationContext(
                    1001L,
                    "S1001",
                    "Student",
                    "student",
                    Set.of("student"),
                    Set.of("final.submit.self"),
                    List.of()
            );
            return new UserAuthorizationContextAssembler() {
                @Override
                public Optional<UserAuthorizationContext> currentAuthorizationContext() {
                    return Optional.of(student);
                }
            };
        }

        @Bean
        FinalSubmissionWindowPolicy finalSubmissionWindowPolicy() {
            return (studentUserId, academicYear, now) -> {
            };
        }

        @Bean
        FinalRecordQueryRepository finalRecordQueryRepository() {
            return mock(FinalRecordQueryRepository.class);
        }

        @Bean
        FinalRecordAccessValidator finalRecordAccessValidator() {
            return mock(FinalRecordAccessValidator.class);
        }

        @Bean
        FailingAfterComponentInsertRepository failingAfterComponentInsertRepository(FinalRecordRepository delegate) {
            return new FailingAfterComponentInsertRepository(delegate);
        }

        @Bean
        FinalRecordCommandApplicationService finalRecordCommandApplicationService(
                UserAuthorizationContextAssembler assembler,
                FailingAfterComponentInsertRepository repository,
                FinalRecordQueryRepository queryRepository,
                FinalSubmissionWindowPolicy windowPolicy,
                FinalRecordAccessValidator accessValidator) {
            return new FinalRecordCommandApplicationService(assembler, repository, queryRepository, windowPolicy, accessValidator);
        }

        @Bean
        DataSourceTransactionManager transactionManager(javax.sql.DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }

    static class FailingAfterComponentInsertRepository implements FinalRecordRepository {
        private final FinalRecordRepository delegate;
        private boolean failAfterComponents;

        FailingAfterComponentInsertRepository(FinalRecordRepository delegate) {
            this.delegate = delegate;
        }

        void reset() {
            failAfterComponents = false;
        }

        @Override
        public Optional<FinalRecord> findByStudentAndAcademicYear(long studentUserId, String academicYear) {
            return delegate.findByStudentAndAcademicYear(studentUserId, academicYear);
        }

        @Override
        public Optional<FinalRecord> findById(long finalRecordId) {
            return delegate.findById(finalRecordId);
        }

        @Override
        public AggregatedFinalRecordSnapshot aggregateApprovedFacts(long studentUserId, String academicYear) {
            return new AggregatedFinalRecordSnapshot(
                    BigDecimal.ZERO,
                    new BigDecimal("2.00"),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    new BigDecimal("2.00"),
                    List.of(new FinalComponentScore(null, null, "INTELLECTUAL", "INTELLECTUAL_PAPER",
                            new BigDecimal("2.00"), "component", "APPLICATION", "21013", java.time.Instant.now()))
            );
        }

        @Override
        public FinalRecord insertDraft(FinalRecord record) {
            return delegate.insertDraft(record);
        }

        @Override
        public void deleteDraft(long finalRecordId) {
            delegate.deleteDraft(finalRecordId);
        }

        @Override
        public void deleteComponents(long finalRecordId) {
            delegate.deleteComponents(finalRecordId);
        }

        @Override
        public void batchInsertComponents(long finalRecordId, List<FinalComponentScore> components) {
            delegate.batchInsertComponents(finalRecordId, components);
        }

        @Override
        public FinalRecord updateTransition(FinalRecord record) {
            if (failAfterComponents) {
                throw new ConflictException("forced transition failure");
            }
            return delegate.updateTransition(record);
        }

        @Override
        public List<FinalComponentScore> listComponents(long finalRecordId) {
            return delegate.listComponents(finalRecordId);
        }
    }
}

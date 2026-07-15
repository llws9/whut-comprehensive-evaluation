package edu.whut.eval.app.application;

import edu.whut.eval.application.application.query.StudentEvaluationItemView;
import edu.whut.eval.application.application.query.StudentEvaluationPointsView;
import edu.whut.eval.application.application.service.StudentEvaluationApplicationService;
import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import edu.whut.eval.application.config.EvaluationConfigApplicationService;
import edu.whut.eval.common.exception.ResourceNotFoundException;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.domain.config.StudentContext;
import edu.whut.eval.domain.config.model.EvaluationItemsConfig;
import edu.whut.eval.domain.config.model.IndexOptionsConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class StudentEvaluationApplicationServiceTest {

    private EvaluationConfigApplicationService evaluationConfigApplicationService;
    private UserAuthorizationContextAssembler userAuthorizationContextAssembler;
    private StudentEvaluationApplicationService service;

    @BeforeEach
    void setUp() {
        evaluationConfigApplicationService = mock(EvaluationConfigApplicationService.class);
        userAuthorizationContextAssembler = mock(UserAuthorizationContextAssembler.class);
        service = new StudentEvaluationApplicationService(evaluationConfigApplicationService, userAuthorizationContextAssembler);
        given(userAuthorizationContextAssembler.currentAuthorizationContext()).willReturn(Optional.of(studentContext()));
        given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(studentContext());
    }

    @Test
    void shouldListEnabledStudentEvaluationItemsWithOptions() {
        given(evaluationConfigApplicationService.getItemsByCategory("INTELLECTUAL"))
                .willReturn(List.of(item("INTELLECTUAL_PAPER", "论文发表", "paper-options")));
        given(evaluationConfigApplicationService.getOptionsByItemCode("INTELLECTUAL_PAPER"))
                .willReturn(List.of(option("PAPER_I_1", "I类第一档", "36.00")));

        List<StudentEvaluationItemView> result = service.listItems("INTELLECTUAL");

        assertThat(result).hasSize(1);
        StudentEvaluationItemView item = result.getFirst();
        assertThat(item.itemCode()).isEqualTo("INTELLECTUAL_PAPER");
        assertThat(item.categoryCode()).isEqualTo("INTELLECTUAL");
        assertThat(item.maxPoints()).isEqualByComparingTo("36.00");
        assertThat(item.options()).hasSize(1);
        assertThat(item.options().getFirst().optionCode()).isEqualTo("PAPER_I_1");
        assertThat(item.options().getFirst().points()).isEqualByComparingTo("36.00");
    }

    @Test
    void shouldCalculatePointsWithCurrentStudentContextAndReturnOptionName() {
        given(evaluationConfigApplicationService.calculatePoints(eq("INTELLECTUAL_PAPER"), eq("PAPER_I_1"), any(StudentContext.class)))
                .willReturn(new BigDecimal("36.00"));
        given(evaluationConfigApplicationService.getOptionsByItemCode("INTELLECTUAL_PAPER"))
                .willReturn(List.of(option("PAPER_I_1", "I类第一档", "36.00")));

        StudentEvaluationPointsView result = service.calculatePoints("INTELLECTUAL_PAPER", "PAPER_I_1");

        assertThat(result.itemCode()).isEqualTo("INTELLECTUAL_PAPER");
        assertThat(result.optionCode()).isEqualTo("PAPER_I_1");
        assertThat(result.points()).isEqualByComparingTo("36.00");
        assertThat(result.optionName()).isEqualTo("I类第一档");

        ArgumentCaptor<StudentContext> contextCaptor = ArgumentCaptor.forClass(StudentContext.class);
        verify(evaluationConfigApplicationService)
                .calculatePoints(eq("INTELLECTUAL_PAPER"), eq("PAPER_I_1"), contextCaptor.capture());
        assertThat(contextCaptor.getValue().getStudentId()).isEqualTo("2024305999");
        assertThat(contextCaptor.getValue().getStudentName()).isEqualTo("张三");
        assertThat(contextCaptor.getValue().isPartyMember()).isTrue();
    }

    @Test
    void shouldReturn404WhenOptionCodeDoesNotExistForCalculatedItem() {
        given(evaluationConfigApplicationService.calculatePoints(eq("INTELLECTUAL_PAPER"), eq("UNKNOWN"), any(StudentContext.class)))
                .willReturn(new BigDecimal("0.00"));
        given(evaluationConfigApplicationService.getOptionsByItemCode("INTELLECTUAL_PAPER"))
                .willReturn(List.of(option("PAPER_I_1", "I类第一档", "36.00")));

        assertThatThrownBy(() -> service.calculatePoints("INTELLECTUAL_PAPER", "UNKNOWN"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("选项不存在");
    }

    private static UserAuthorizationContext studentContext() {
        return new UserAuthorizationContext(
                1001L,
                "2024305999",
                "张三",
                "student",
                Set.of("PARTY_MEMBER"),
                Set.of("application.view.self"),
                List.of()
        );
    }

    private static EvaluationItemsConfig.EvaluationItem item(String itemCode, String itemName, String optionsKey) {
        EvaluationItemsConfig.EvaluationItem item = new EvaluationItemsConfig.EvaluationItem();
        item.setItemCode(itemCode);
        item.setItemName(itemName);
        item.setCategoryCode("INTELLECTUAL");
        item.setCategoryName("智育");
        item.setDescription("学术论文发表加分");
        item.setMaxPoints(new BigDecimal("36.00"));
        item.setApplyMode("STUDENT_APPLY");
        item.setEnabled(true);
        item.setSortOrder(10);
        item.setOptionsKey(optionsKey);
        return item;
    }

    private static IndexOptionsConfig.OptionItem option(String optionCode, String optionName, String points) {
        IndexOptionsConfig.OptionItem option = new IndexOptionsConfig.OptionItem();
        option.setOptionCode(optionCode);
        option.setOptionName(optionName);
        option.setPoints(new BigDecimal(points));
        option.setDescription("发表在高水平期刊或会议");
        option.setSortOrder(1);
        return option;
    }
}

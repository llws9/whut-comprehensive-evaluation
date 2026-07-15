package edu.whut.eval.application.application.service;

import edu.whut.eval.application.application.query.StudentEvaluationItemView;
import edu.whut.eval.application.application.query.StudentEvaluationOptionView;
import edu.whut.eval.application.application.query.StudentEvaluationPointsView;
import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import edu.whut.eval.application.config.EvaluationConfigApplicationService;
import edu.whut.eval.common.exception.ResourceNotFoundException;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.domain.config.StudentContext;
import edu.whut.eval.domain.config.model.EvaluationItemsConfig;
import edu.whut.eval.domain.config.model.IndexOptionsConfig;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class StudentEvaluationApplicationService {

    private final EvaluationConfigApplicationService evaluationConfigApplicationService;
    private final UserAuthorizationContextAssembler userAuthorizationContextAssembler;

    public StudentEvaluationApplicationService(EvaluationConfigApplicationService evaluationConfigApplicationService,
                                               UserAuthorizationContextAssembler userAuthorizationContextAssembler) {
        this.evaluationConfigApplicationService = evaluationConfigApplicationService;
        this.userAuthorizationContextAssembler = userAuthorizationContextAssembler;
    }

    public List<StudentEvaluationItemView> listItems(String categoryCode) {
        return evaluationConfigApplicationService.getItemsByCategory(categoryCode)
                .stream()
                .map(this::toItemView)
                .toList();
    }

    public StudentEvaluationPointsView calculatePoints(String itemCode, String optionCode) {
        BigDecimal points = evaluationConfigApplicationService.calculatePoints(
                itemCode,
                optionCode,
                currentStudentContext()
        );
        IndexOptionsConfig.OptionItem option = evaluationConfigApplicationService.getOptionsByItemCode(itemCode)
                .stream()
                .filter(candidate -> optionCode.equals(candidate.getOptionCode()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("选项不存在"));
        return new StudentEvaluationPointsView(itemCode, optionCode, points, option.getOptionName());
    }

    private StudentEvaluationItemView toItemView(EvaluationItemsConfig.EvaluationItem item) {
        List<StudentEvaluationOptionView> options = evaluationConfigApplicationService.getOptionsByItemCode(item.getItemCode())
                .stream()
                .map(this::toOptionView)
                .toList();
        return new StudentEvaluationItemView(
                item.getItemCode(),
                item.getItemName(),
                item.getCategoryCode(),
                item.getCategoryName(),
                item.getDescription(),
                item.getMaxPoints(),
                item.getApplyMode(),
                item.isEnabled(),
                options
        );
    }

    private StudentEvaluationOptionView toOptionView(IndexOptionsConfig.OptionItem option) {
        return new StudentEvaluationOptionView(
                option.getOptionCode(),
                option.getOptionName(),
                option.getPoints(),
                option.getDescription()
        );
    }

    private StudentContext currentStudentContext() {
        UserAuthorizationContext context = userAuthorizationContextAssembler.requiredAuthorizationContext();
        return StudentContext.builder()
                .studentId(context.getUserNo())
                .studentName(context.getUserName())
                .partyMember(isPartyMember(context))
                .build();
    }

    private boolean isPartyMember(UserAuthorizationContext context) {
        return "PARTY_MEMBER".equalsIgnoreCase(context.getIdentity())
                || context.hasRole("PARTY_MEMBER")
                || context.hasRole("ROLE_PARTY_MEMBER");
    }
}

package edu.whut.eval.interfaces.student;

import edu.whut.eval.application.preference.command.CreateUserPreferenceCommand;
import edu.whut.eval.application.preference.query.UserPreferenceView;
import edu.whut.eval.application.preference.service.UserPreferenceCommandApplicationService;
import edu.whut.eval.common.api.ApiResponse;
import edu.whut.eval.interfaces.student.request.CreateUserPreferenceRequest;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学生侧偏好设置写接口。
 * 该控制器用于承接 P0-2 的最小写入样例，避免直接在查询控制器中混入写逻辑。
 */
@RestController
@Validated
@RequestMapping("/api/student/preferences")
public class StudentPreferenceController {

    private final UserPreferenceCommandApplicationService userPreferenceCommandApplicationService;

    public StudentPreferenceController(UserPreferenceCommandApplicationService userPreferenceCommandApplicationService) {
        this.userPreferenceCommandApplicationService = userPreferenceCommandApplicationService;
    }

    /**
     * 为当前登录学生创建一条偏好设置。
     */
    @PostMapping
    public ApiResponse<UserPreferenceView> createPreference(@Valid @RequestBody CreateUserPreferenceRequest request) {
        UserPreferenceView view = userPreferenceCommandApplicationService.createCurrentUserPreference(
                new CreateUserPreferenceCommand(request.getPreferredTheme(), request.getNotificationsEnabled())
        );
        return ApiResponse.success(view);
    }
}

package edu.whut.eval.interfaces.iam;

import edu.whut.eval.application.iam.query.UserIdentityView;
import edu.whut.eval.application.iam.service.UserIdentityQueryApplicationService;
import edu.whut.eval.common.api.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/iam/users")
@Validated
public class UserIdentityQueryController {

    private final UserIdentityQueryApplicationService userIdentityQueryApplicationService;

    public UserIdentityQueryController(UserIdentityQueryApplicationService userIdentityQueryApplicationService) {
        this.userIdentityQueryApplicationService = userIdentityQueryApplicationService;
    }

    @GetMapping("/{userNo}/identity")
    public ApiResponse<UserIdentityView> getUserIdentity(@PathVariable String userNo) {
        return ApiResponse.success(userIdentityQueryApplicationService.getUserIdentityByUserNo(userNo));
    }
}

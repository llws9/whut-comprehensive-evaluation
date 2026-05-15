package edu.whut.eval.application.iam.service;

import edu.whut.eval.application.iam.query.UserIdentityView;
import edu.whut.eval.common.exception.ResourceNotFoundException;
import edu.whut.eval.domain.iam.model.IamUser;
import edu.whut.eval.domain.iam.repository.IamUserQueryRepository;
import edu.whut.eval.domain.iam.repository.RoleAssignmentQueryRepository;
import edu.whut.eval.domain.org.repository.OrgQueryRepository;
import org.springframework.stereotype.Service;

@Service
public class UserIdentityQueryApplicationService {

    private final IamUserQueryRepository iamUserQueryRepository;
    private final RoleAssignmentQueryRepository roleAssignmentQueryRepository;
    private final OrgQueryRepository orgQueryRepository;

    public UserIdentityQueryApplicationService(IamUserQueryRepository iamUserQueryRepository,
                                               RoleAssignmentQueryRepository roleAssignmentQueryRepository,
                                               OrgQueryRepository orgQueryRepository) {
        this.iamUserQueryRepository = iamUserQueryRepository;
        this.roleAssignmentQueryRepository = roleAssignmentQueryRepository;
        this.orgQueryRepository = orgQueryRepository;
    }

    public UserIdentityView getUserIdentityByUserNo(String userNo) {
        IamUser user = iamUserQueryRepository.findByUserNo(userNo)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在: " + userNo));
        return new UserIdentityView(
                user,
                roleAssignmentQueryRepository.findActiveAssignmentsByUserId(user.id()),
                orgQueryRepository.findMembershipsByUserId(user.id())
        );
    }
}

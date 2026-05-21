package edu.whut.eval.application.iam.service;

import edu.whut.eval.application.iam.query.UserAdminPageItemView;
import edu.whut.eval.application.iam.query.UserAdminPageQuery;
import edu.whut.eval.domain.shared.PageResult;

public interface UserAdminQueryApplicationService {

    PageResult<UserAdminPageItemView> pageUsers(UserAdminPageQuery query);
}

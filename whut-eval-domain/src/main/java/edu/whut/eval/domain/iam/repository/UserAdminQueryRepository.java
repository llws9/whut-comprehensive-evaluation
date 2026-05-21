package edu.whut.eval.domain.iam.repository;

import edu.whut.eval.domain.iam.model.IamUserAdminPageItem;
import edu.whut.eval.domain.iam.query.UserAdminPageQuery;
import edu.whut.eval.domain.shared.PageResult;

public interface UserAdminQueryRepository {

    PageResult<IamUserAdminPageItem> pageUsers(UserAdminPageQuery query);
}

package edu.whut.eval.domain.iam.repository;

import edu.whut.eval.domain.iam.model.IamRoleAdminPageItem;
import edu.whut.eval.domain.iam.query.RoleAdminPageQuery;
import edu.whut.eval.domain.shared.PageResult;

public interface RoleAdminQueryRepository {

    PageResult<IamRoleAdminPageItem> pageRoles(RoleAdminPageQuery query);
}

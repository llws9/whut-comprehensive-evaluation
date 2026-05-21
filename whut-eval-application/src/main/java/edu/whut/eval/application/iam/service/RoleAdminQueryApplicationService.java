package edu.whut.eval.application.iam.service;

import edu.whut.eval.application.iam.query.RoleAdminPageItemView;
import edu.whut.eval.application.iam.query.RoleAdminPageQuery;
import edu.whut.eval.domain.shared.PageResult;

public interface RoleAdminQueryApplicationService {

    PageResult<RoleAdminPageItemView> pageRoles(RoleAdminPageQuery query);
}

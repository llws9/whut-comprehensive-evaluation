package edu.whut.eval.application.iam.query;

import java.util.List;

public record UserAdminPageItemView(Long userId,
                                    String userNo,
                                    String userName,
                                    String status,
                                    List<String> orgUnits,
                                    List<String> roleCodes,
                                    String createdAt) {
}

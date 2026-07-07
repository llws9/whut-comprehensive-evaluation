package edu.whut.eval.application.application.query;

public record ReviewApplicantView(Long userId,
                                  String userNo,
                                  String userName,
                                  Long orgUnitId,
                                  String orgUnitName) {
}

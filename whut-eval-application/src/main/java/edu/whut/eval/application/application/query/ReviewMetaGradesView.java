package edu.whut.eval.application.application.query;

import java.util.List;

public record ReviewMetaGradesView(List<String> gradeList,
                                   List<ReviewOrgUnitOptionView> orgUnitList,
                                   Long defaultOrgUnitId) {
}

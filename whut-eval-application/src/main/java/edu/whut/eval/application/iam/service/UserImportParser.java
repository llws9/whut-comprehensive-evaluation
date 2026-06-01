package edu.whut.eval.application.iam.service;

import edu.whut.eval.application.iam.query.UserImportRowView;

import java.util.List;

public interface UserImportParser {

    List<UserImportRowView> parse(byte[] fileContent);
}

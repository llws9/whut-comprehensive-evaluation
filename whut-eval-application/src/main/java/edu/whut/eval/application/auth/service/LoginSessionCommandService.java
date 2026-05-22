package edu.whut.eval.application.auth.service;

import edu.whut.eval.application.auth.model.LoginSessionCreateCommand;

public interface LoginSessionCommandService {

    void create(LoginSessionCreateCommand command);
}

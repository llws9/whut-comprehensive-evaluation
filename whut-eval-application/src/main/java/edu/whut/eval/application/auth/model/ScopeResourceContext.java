package edu.whut.eval.application.auth.model;

public interface ScopeResourceContext {

    Long getOwnerUserId();

    Long getOrgUnitId();

    String getOrgPath();

    String getCategoryCode();

    String getItemCode();

    Object getFieldValue(String fieldName);
}

package edu.whut.eval.interfaces.admin.response;

import java.util.List;

public class OrgUnitTreeResponse {

    private final Long id;
    private final String unitCode;
    private final String unitName;
    private final String unitType;
    private final String status;
    private final List<OrgUnitTreeResponse> children;

    public OrgUnitTreeResponse(Long id,
                               String unitCode,
                               String unitName,
                               String unitType,
                               String status,
                               List<OrgUnitTreeResponse> children) {
        this.id = id;
        this.unitCode = unitCode;
        this.unitName = unitName;
        this.unitType = unitType;
        this.status = status;
        this.children = children;
    }

    public Long getId() {
        return id;
    }

    public String getUnitCode() {
        return unitCode;
    }

    public String getUnitName() {
        return unitName;
    }

    public String getUnitType() {
        return unitType;
    }

    public String getStatus() {
        return status;
    }

    public List<OrgUnitTreeResponse> getChildren() {
        return children;
    }
}

package edu.whut.eval.interfaces.iam.response;

public class UserMembershipResponse {

    private final Long membershipId;
    private final Long orgUnitId;
    private final String orgUnitName;
    private final String orgUnitType;
    private final Boolean isPrimary;
    private final String status;

    public UserMembershipResponse(Long membershipId,
                                  Long orgUnitId,
                                  String orgUnitName,
                                  String orgUnitType,
                                  Boolean isPrimary,
                                  String status) {
        this.membershipId = membershipId;
        this.orgUnitId = orgUnitId;
        this.orgUnitName = orgUnitName;
        this.orgUnitType = orgUnitType;
        this.isPrimary = isPrimary;
        this.status = status;
    }

    public Long getMembershipId() {
        return membershipId;
    }

    public Long getOrgUnitId() {
        return orgUnitId;
    }

    public String getOrgUnitName() {
        return orgUnitName;
    }

    public String getOrgUnitType() {
        return orgUnitType;
    }

    public Boolean getIsPrimary() {
        return isPrimary;
    }

    public String getStatus() {
        return status;
    }
}

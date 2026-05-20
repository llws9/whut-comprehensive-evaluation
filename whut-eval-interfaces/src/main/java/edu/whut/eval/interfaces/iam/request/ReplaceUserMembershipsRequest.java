package edu.whut.eval.interfaces.iam.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public class ReplaceUserMembershipsRequest {

    @NotNull
    @Valid
    private List<MembershipItem> memberships;

    public List<MembershipItem> getMemberships() {
        return memberships;
    }

    public void setMemberships(List<MembershipItem> memberships) {
        this.memberships = memberships;
    }

    public static class MembershipItem {

        @NotNull
        @Positive
        private Long orgUnitId;

        private Boolean isPrimary;

        public Long getOrgUnitId() {
            return orgUnitId;
        }

        public void setOrgUnitId(Long orgUnitId) {
            this.orgUnitId = orgUnitId;
        }

        public Boolean getIsPrimary() {
            return isPrimary;
        }

        public void setIsPrimary(Boolean isPrimary) {
            this.isPrimary = isPrimary;
        }
    }
}

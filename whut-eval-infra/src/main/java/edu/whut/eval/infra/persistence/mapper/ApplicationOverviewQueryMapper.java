package edu.whut.eval.infra.persistence.mapper;

import edu.whut.eval.domain.application.query.ApplicationOverviewSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ApplicationOverviewQueryMapper {

    @Select("""
            SELECT
              COALESCE(SUM(CASE WHEN status = 'DRAFT' THEN 1 ELSE 0 END), 0) AS draftCount,
              COALESCE(SUM(CASE WHEN status = 'SUBMITTED' THEN 1 ELSE 0 END), 0) AS submittedCount,
              COALESCE(SUM(CASE WHEN status = 'RETURNED' THEN 1 ELSE 0 END), 0) AS returnedCount,
              COALESCE(SUM(CASE WHEN status = 'APPROVED' THEN 1 ELSE 0 END), 0) AS approvedCount,
              COALESCE(SUM(CASE WHEN status = 'REJECTED' THEN 1 ELSE 0 END), 0) AS rejectedCount,
              MAX(CASE WHEN status <> 'DELETED' THEN academic_year ELSE NULL END) AS latestAcademicYear
            FROM application_submission
            WHERE applicant_user_id = #{applicantUserId}
              AND status <> 'DELETED'
            """)
    ApplicationOverviewSummary selectStudentOverview(@Param("applicantUserId") Long applicantUserId);
}

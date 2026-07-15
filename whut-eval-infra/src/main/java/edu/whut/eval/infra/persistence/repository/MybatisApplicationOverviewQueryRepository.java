package edu.whut.eval.infra.persistence.repository;

import edu.whut.eval.domain.application.query.ApplicationOverviewSummary;
import edu.whut.eval.domain.application.repository.ApplicationOverviewQueryRepository;
import edu.whut.eval.infra.persistence.mapper.ApplicationOverviewQueryMapper;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisApplicationOverviewQueryRepository implements ApplicationOverviewQueryRepository {

    private final ApplicationOverviewQueryMapper applicationOverviewQueryMapper;

    public MybatisApplicationOverviewQueryRepository(ApplicationOverviewQueryMapper applicationOverviewQueryMapper) {
        this.applicationOverviewQueryMapper = applicationOverviewQueryMapper;
    }

    @Override
    public ApplicationOverviewSummary getStudentOverview(Long applicantUserId) {
        ApplicationOverviewSummary summary = applicationOverviewQueryMapper.selectStudentOverview(applicantUserId);
        if (summary == null) {
            return new ApplicationOverviewSummary(0, 0, 0, 0, 0, null);
        }
        return summary;
    }
}

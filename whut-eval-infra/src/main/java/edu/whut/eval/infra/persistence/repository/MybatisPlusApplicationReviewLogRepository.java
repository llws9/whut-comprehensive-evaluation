package edu.whut.eval.infra.persistence.repository;

import edu.whut.eval.domain.application.model.ApplicationReviewAction;
import edu.whut.eval.domain.application.model.ApplicationReviewLog;
import edu.whut.eval.domain.application.repository.ApplicationReviewLogRepository;
import edu.whut.eval.infra.persistence.dataobject.ApplicationReviewLogDO;
import edu.whut.eval.infra.persistence.mapper.ApplicationReviewLogMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Repository
public class MybatisPlusApplicationReviewLogRepository implements ApplicationReviewLogRepository {

    private final ApplicationReviewLogMapper applicationReviewLogMapper;

    public MybatisPlusApplicationReviewLogRepository(ApplicationReviewLogMapper applicationReviewLogMapper) {
        this.applicationReviewLogMapper = applicationReviewLogMapper;
    }

    @Override
    public ApplicationReviewLog append(ApplicationReviewLog reviewLog) {
        ApplicationReviewLogDO dataObject = toDataObject(reviewLog);
        applicationReviewLogMapper.insert(dataObject);
        return toDomain(dataObject);
    }

    @Override
    public List<ApplicationReviewLog> listByApplicationId(Long applicationId) {
        return applicationReviewLogMapper.selectByApplicationId(applicationId)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    private ApplicationReviewLogDO toDataObject(ApplicationReviewLog reviewLog) {
        ApplicationReviewLogDO dataObject = new ApplicationReviewLogDO();
        dataObject.setId(reviewLog.getId());
        dataObject.setApplicationId(reviewLog.getApplicationId());
        dataObject.setAction(reviewLog.getAction().name());
        dataObject.setReviewerId(reviewLog.getReviewerId());
        dataObject.setReviewRole(reviewLog.getReviewRole());
        dataObject.setReason(reviewLog.getReason());
        dataObject.setReviewedAt(toLocalDateTime(reviewLog.getReviewedAt()));
        return dataObject;
    }

    private ApplicationReviewLog toDomain(ApplicationReviewLogDO dataObject) {
        return new ApplicationReviewLog(
                dataObject.getId(),
                dataObject.getApplicationId(),
                ApplicationReviewAction.valueOf(dataObject.getAction()),
                dataObject.getReviewerId(),
                dataObject.getReviewRole(),
                dataObject.getReason(),
                toInstant(dataObject.getReviewedAt())
        );
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private Instant toInstant(LocalDateTime localDateTime) {
        return localDateTime == null ? null : localDateTime.toInstant(ZoneOffset.UTC);
    }
}

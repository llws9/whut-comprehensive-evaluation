package edu.whut.eval.infra.support;

import edu.whut.eval.domain.application.service.ApplicationSubmissionWindowPolicy;
import org.springframework.stereotype.Component;

/**
 * 申请窗口策略的占位实现。
 * 在正式窗口规则落地前，默认允许提交，避免应用服务骨架无法装配。
 */
@Component
public class AllowAllApplicationSubmissionWindowPolicy implements ApplicationSubmissionWindowPolicy {

    @Override
    public boolean isWindowOpen(Long orgUnitId, String categoryCode, String itemCode, String academicYear, String term) {
        return true;
    }
}

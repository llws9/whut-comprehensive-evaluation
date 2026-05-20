package edu.whut.eval.application.iam.service;

import edu.whut.eval.application.iam.command.ReplaceUserMembershipItemCommand;
import edu.whut.eval.application.iam.command.ReplaceUserMembershipsCommand;
import edu.whut.eval.application.iam.query.UserMembershipAdminView;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.common.exception.ResourceNotFoundException;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.iam.repository.IamUserQueryRepository;
import edu.whut.eval.domain.org.model.OrgMembership;
import edu.whut.eval.domain.org.model.OrgUnit;
import edu.whut.eval.domain.org.repository.OrgUnitLookupRepository;
import edu.whut.eval.domain.org.repository.UserMembershipAdminRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class DefaultUserMembershipAdminApplicationService implements UserMembershipAdminApplicationService {

    private final ConcurrentHashMap<Long, UserLockHolder> replaceLocksByUserId = new ConcurrentHashMap<>();

    private final IamUserQueryRepository iamUserQueryRepository;
    private final OrgUnitLookupRepository orgUnitLookupRepository;
    private final UserMembershipAdminRepository userMembershipAdminRepository;

    public DefaultUserMembershipAdminApplicationService(IamUserQueryRepository iamUserQueryRepository,
                                                        OrgUnitLookupRepository orgUnitLookupRepository,
                                                        UserMembershipAdminRepository userMembershipAdminRepository) {
        this.iamUserQueryRepository = iamUserQueryRepository;
        this.orgUnitLookupRepository = orgUnitLookupRepository;
        this.userMembershipAdminRepository = userMembershipAdminRepository;
    }

    @Override
    public List<UserMembershipAdminView> listMemberships(Long userId) {
        ensureUserExists(userId);
        return userMembershipAdminRepository.findActiveMembershipsByUserId(userId).stream()
                .map(this::toView)
                .toList();
    }

    @Override
    @Transactional
    public void replaceMemberships(Long userId, ReplaceUserMembershipsCommand command) {
        runWithUserLock(userId, () -> doReplaceMemberships(userId, command));
    }

    private void doReplaceMemberships(Long userId, ReplaceUserMembershipsCommand command) {
        ensureUserExists(userId);
        List<ReplaceUserMembershipItemCommand> requestedMemberships = command.memberships() == null ? List.of() : command.memberships();
        validateRequestedMemberships(requestedMemberships);

        List<OrgMembership> existingMemberships = userMembershipAdminRepository.findActiveMembershipsByUserId(userId);
        Map<Long, OrgMembership> existingByOrgUnitId = new HashMap<>();
        for (OrgMembership membership : existingMemberships) {
            existingByOrgUnitId.put(membership.orgUnitId(), membership);
        }

        String now = LocalDateTime.now().toString();
        List<OrgMembership> activeMemberships = new ArrayList<>();
        Set<Long> requestedOrgUnitIds = new HashSet<>();
        for (ReplaceUserMembershipItemCommand requested : requestedMemberships) {
            requestedOrgUnitIds.add(requested.orgUnitId());
            OrgMembership existing = existingByOrgUnitId.get(requested.orgUnitId());
            if (existing != null) {
                activeMemberships.add(new OrgMembership(
                        existing.id(),
                        userId,
                        requested.orgUnitId(),
                        existing.membershipType(),
                        requested.isPrimary(),
                        "ACTIVE",
                        existing.joinedAt(),
                        null
                ));
                continue;
            }
            activeMemberships.add(new OrgMembership(
                    null,
                    userId,
                    requested.orgUnitId(),
                    "MANUAL",
                    requested.isPrimary(),
                    "ACTIVE",
                    now,
                    null
            ));
        }

        List<OrgMembership> inactiveMemberships = existingMemberships.stream()
                .filter(item -> !requestedOrgUnitIds.contains(item.orgUnitId()))
                .map(item -> new OrgMembership(
                        item.id(),
                        userId,
                        item.orgUnitId(),
                        item.membershipType(),
                        item.isPrimary(),
                        "INACTIVE",
                        item.joinedAt(),
                        now
                ))
                .toList();

        userMembershipAdminRepository.replaceMemberships(userId, activeMemberships, inactiveMemberships);
    }

    private void runWithUserLock(Long userId, Runnable action) {
        UserLockHolder holder = replaceLocksByUserId.compute(userId, (ignored, existing) -> {
            if (existing == null) {
                existing = new UserLockHolder();
            }
            existing.retain();
            return existing;
        });
        holder.lock();
        try {
            action.run();
        } finally {
            holder.unlock();
            if (holder.release() == 0) {
                replaceLocksByUserId.remove(userId, holder);
            }
        }
    }

    private void validateRequestedMemberships(List<ReplaceUserMembershipItemCommand> memberships) {
        Set<Long> orgUnitIds = new HashSet<>();
        int primaryCount = 0;
        for (ReplaceUserMembershipItemCommand membership : memberships) {
            if (membership.orgUnitId() == null) {
                throw new ValidationException("orgUnitId 不能为空");
            }
            if (!orgUnitIds.add(membership.orgUnitId())) {
                throw new ConflictException("orgUnitId 不允许重复");
            }
            if (membership.isPrimary()) {
                primaryCount++;
                if (primaryCount > 1) {
                    throw new ValidationException("最多只能有一个主组织");
                }
            }
        }
        orgUnitIds.forEach(this::resolveOrgUnit);
    }

    private void ensureUserExists(Long userId) {
        iamUserQueryRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在: " + userId));
    }

    private OrgUnit resolveOrgUnit(Long orgUnitId) {
        return orgUnitLookupRepository.findById(orgUnitId)
                .orElseThrow(() -> new ResourceNotFoundException("组织不存在: " + orgUnitId));
    }

    private UserMembershipAdminView toView(OrgMembership membership) {
        OrgUnit orgUnit = resolveOrgUnit(membership.orgUnitId());
        return new UserMembershipAdminView(
                membership.id(),
                membership.orgUnitId(),
                orgUnit.unitName(),
                orgUnit.unitType(),
                membership.isPrimary(),
                membership.status()
        );
    }

    private static final class UserLockHolder {

        private final ReentrantLock lock = new ReentrantLock();
        private final AtomicInteger refCount = new AtomicInteger();

        private void retain() {
            refCount.incrementAndGet();
        }

        private int release() {
            return refCount.decrementAndGet();
        }

        private void lock() {
            lock.lock();
        }

        private void unlock() {
            lock.unlock();
        }
    }
}

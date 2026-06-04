package edu.whut.eval.app.infra;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import edu.whut.eval.domain.iam.model.IamSession;
import edu.whut.eval.infra.persistence.entity.IamSessionDO;
import edu.whut.eval.infra.persistence.mapper.IamSessionMapper;
import edu.whut.eval.infra.persistence.repository.MybatisPlusIamSessionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.then;

class MybatisPlusIamSessionRepositoryTest {

    @Test
    void shouldInsertSessionEntityWhenCreateSession() {
        IamSessionMapper mapper = mock(IamSessionMapper.class);
        MybatisPlusIamSessionRepository repository = new MybatisPlusIamSessionRepository(mapper);
        LocalDateTime createdAt = LocalDateTime.now().minusSeconds(1);
        LocalDateTime expiredAt = LocalDateTime.now().plusDays(7);
        IamSession session = new IamSession(
                null,
                "session-no-123",
                1001L,
                "access-jti-123",
                "refresh-jti-456",
                "WEB",
                "127.0.0.1",
                expiredAt,
                null,
                IamSession.SessionStatus.ACTIVE,
                createdAt
        );

        repository.create(session);

        ArgumentCaptor<IamSessionDO> captor = ArgumentCaptor.forClass(IamSessionDO.class);
        then(mapper).should().insert(captor.capture());
        IamSessionDO entity = captor.getValue();
        assertThat(entity.getSessionNo()).isEqualTo("session-no-123");
        assertThat(entity.getUserId()).isEqualTo(1001L);
        assertThat(entity.getAccessTokenId()).isEqualTo("access-jti-123");
        assertThat(entity.getRefreshTokenId()).isEqualTo("refresh-jti-456");
        assertThat(entity.getDeviceType()).isEqualTo("WEB");
        assertThat(entity.getClientIp()).isEqualTo("127.0.0.1");
        assertThat(entity.getExpiredAt()).isEqualTo(expiredAt);
        assertThat(entity.getStatus()).isEqualTo("ACTIVE");
        assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
        assertThat(entity.getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldUpdateTokenIdsWhenContinueRefreshSession() {
        IamSessionMapper mapper = mock(IamSessionMapper.class);
        MybatisPlusIamSessionRepository repository = new MybatisPlusIamSessionRepository(mapper);
        LocalDateTime expiredAt = LocalDateTime.now().plusDays(7);
        given(mapper.update(any(IamSessionDO.class), any(Wrapper.class))).willReturn(1);

        boolean updated = repository.continueRefreshSession(
                "session-no-123",
                "old-refresh-jti",
                "new-access-jti",
                "new-refresh-jti",
                expiredAt
        );

        assertThat(updated).isTrue();
        ArgumentCaptor<IamSessionDO> entityCaptor = ArgumentCaptor.forClass(IamSessionDO.class);
        then(mapper).should().update(entityCaptor.capture(), any(Wrapper.class));
        IamSessionDO entity = entityCaptor.getValue();
        assertThat(entity.getAccessTokenId()).isEqualTo("new-access-jti");
        assertThat(entity.getRefreshTokenId()).isEqualTo("new-refresh-jti");
        assertThat(entity.getExpiredAt()).isEqualTo(expiredAt);
        assertThat(entity.getUpdatedAt()).isNotNull();
    }

}

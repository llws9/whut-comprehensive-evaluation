package edu.whut.eval.app.iam;

import edu.whut.eval.domain.iam.model.IamSession;
import edu.whut.eval.domain.iam.model.IamSessionStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class IamSessionTest {

    @Test
    void shouldBeActiveOnlyWhenSessionIsNotRevokedOrExpired() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 22, 12, 0, 0);
        IamSession activeSession = new IamSession(
                1L,
                1001L,
                "sid-active",
                "127.0.0.1",
                "JUnit",
                now.plusMinutes(30),
                null,
                IamSessionStatus.ACTIVE,
                now.minusMinutes(5)
        );
        IamSession expiredSession = new IamSession(
                2L,
                1001L,
                "sid-expired",
                "127.0.0.1",
                "JUnit",
                now.minusSeconds(1),
                null,
                IamSessionStatus.ACTIVE,
                now.minusMinutes(5)
        );
        IamSession revokedSession = new IamSession(
                3L,
                1001L,
                "sid-revoked",
                "127.0.0.1",
                "JUnit",
                now.plusMinutes(30),
                now.minusMinutes(1),
                IamSessionStatus.REVOKED,
                now.minusMinutes(5)
        );

        assertThat(activeSession.isActive(now)).isTrue();
        assertThat(expiredSession.isActive(now)).isFalse();
        assertThat(revokedSession.isActive(now)).isFalse();
    }

    @Test
    void shouldReturnRevokedCopyWithTimestamp() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 22, 12, 0, 0);
        IamSession session = new IamSession(
                1L,
                1001L,
                "sid-active",
                "127.0.0.1",
                "JUnit",
                now.plusMinutes(30),
                null,
                IamSessionStatus.ACTIVE,
                now.minusMinutes(5)
        );

        IamSession revoked = session.revoke(now);

        assertThat(revoked.status()).isEqualTo(IamSessionStatus.REVOKED);
        assertThat(revoked.revokedAt()).isEqualTo(now);
        assertThat(revoked.sessionId()).isEqualTo("sid-active");
    }

    @Test
    void shouldReturnExtendedCopyWithNewExpiration() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 22, 12, 0, 0);
        IamSession session = new IamSession(
                1L,
                1001L,
                "sid-active",
                "127.0.0.1",
                "JUnit",
                now.plusMinutes(30),
                null,
                IamSessionStatus.ACTIVE,
                now.minusMinutes(5)
        );

        IamSession extended = session.extendTo(now.plusHours(2));

        assertThat(extended.expiredAt()).isEqualTo(now.plusHours(2));
        assertThat(extended.status()).isEqualTo(IamSessionStatus.ACTIVE);
        assertThat(extended.revokedAt()).isNull();
    }
}

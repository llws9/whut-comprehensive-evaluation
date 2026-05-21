package edu.whut.eval.app.iam;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import edu.whut.eval.infra.persistence.entity.IamUserDO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class IamUserDOTest {

    @Test
    void shouldUseAssignIdStrategyForIamUserPrimaryKey() throws NoSuchFieldException {
        Field idField = IamUserDO.class.getDeclaredField("id");

        TableId tableId = idField.getAnnotation(TableId.class);

        assertThat(tableId).isNotNull();
        assertThat(tableId.type()).isEqualTo(IdType.ASSIGN_ID);
    }
}

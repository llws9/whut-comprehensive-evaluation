package edu.whut.eval.infra.persistence.mapper;

import edu.whut.eval.infra.persistence.dataobject.UserPreferenceDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserPreferenceMapper {

    /**
     * 判断某个用户是否已经存在偏好设置。
     */
    @Select("SELECT COUNT(1) > 0 FROM user_preference WHERE user_id = #{userId}")
    boolean existsByUserId(@Param("userId") Long userId);

    /**
     * 读取指定用户的偏好设置。
     */
    @Select("SELECT id, user_id, preferred_theme, notifications_enabled FROM user_preference WHERE user_id = #{userId}")
    UserPreferenceDO selectByUserId(@Param("userId") Long userId);

    /**
     * 插入一条偏好设置记录，并回填自增主键。
     */
    @Insert("INSERT INTO user_preference (user_id, preferred_theme, notifications_enabled) VALUES (#{userId}, #{preferredTheme}, #{notificationsEnabled})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserPreferenceDO userPreferenceDO);
}

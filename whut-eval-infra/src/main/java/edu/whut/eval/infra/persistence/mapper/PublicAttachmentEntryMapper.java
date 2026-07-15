package edu.whut.eval.infra.persistence.mapper;

import edu.whut.eval.infra.persistence.dataobject.PublicAttachmentQueryDO;
import edu.whut.eval.infra.persistence.dataobject.PublicAttachmentEntryDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface PublicAttachmentEntryMapper {

    /**
     * 按文件 ID 批量查询公共附件池发布记录。
     */
    @Select({
            "<script>",
            "SELECT id, file_id, display_name, description, category_code, scope_type, scope_value, status, ",
            "       published_by, published_at, sort_no, created_at, updated_at ",
            "FROM public_attachment_entry ",
            "WHERE file_id IN ",
            "<foreach collection='fileIds' item='fileId' open='(' separator=',' close=')'>",
            "  #{fileId}",
            "</foreach>",
            "</script>"
    })
    List<PublicAttachmentEntryDO> selectByFileIds(@Param("fileIds") List<String> fileIds);

    @Select("""
            SELECT COUNT(1)
            FROM public_attachment_entry pae
            JOIN file_asset fa ON fa.file_id = pae.file_id
            WHERE pae.file_id = #{fileId}
              AND pae.status = 'PUBLISHED'
              AND pae.scope_type = 'ALL'
              AND fa.status = 'ACTIVE'
            """)
    int countPublishedAllActiveByFileId(@Param("fileId") String fileId);

    @Select("""
            SELECT COUNT(1)
            FROM public_attachment_entry
            WHERE file_id = #{fileId}
              AND status = 'PUBLISHED'
            """)
    int countActivePublishedByFileId(@Param("fileId") String fileId);

    @Select("""
            SELECT id, file_id, display_name, description, category_code, scope_type, scope_value, status,
                   published_by, published_at, sort_no, created_at, updated_at
            FROM public_attachment_entry
            WHERE id = #{entryId}
            """)
    Optional<PublicAttachmentEntryDO> selectById(@Param("entryId") Long entryId);

    @Insert("""
            INSERT INTO public_attachment_entry (
                file_id, display_name, description, category_code, scope_type, scope_value, status,
                published_by, published_at, sort_no, created_at, updated_at
            )
            VALUES (
                #{fileId}, #{displayName}, #{description}, #{categoryCode}, #{scopeType}, #{scopeValue}, 'PUBLISHED',
                #{publishedBy}, #{publishedAt}, #{sortNo}, #{publishedAt}, #{publishedAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertPublished(PublishPublicAttachmentSqlRecord record);

    @Update("""
            UPDATE public_attachment_entry
            SET status = 'OFFLINE',
                updated_at = #{offlineAt}
            WHERE id = #{entryId}
              AND status = 'PUBLISHED'
            """)
    int offlineById(@Param("entryId") Long entryId,
                    @Param("reason") String reason,
                    @Param("offlineAt") LocalDateTime offlineAt);

    @Select("""
            <script>
            SELECT pae.id AS entry_id,
                   pae.file_id,
                   pae.display_name,
                   pae.description,
                   pae.category_code,
                   fa.original_filename,
                   fa.content_type,
                   fa.size,
                   pae.published_at,
                   pae.sort_no
            FROM public_attachment_entry pae
            JOIN file_asset fa ON fa.file_id = pae.file_id
            WHERE pae.status = 'PUBLISHED'
              AND pae.scope_type = 'ALL'
              AND fa.status = 'ACTIVE'
            <if test='categoryCode != null and categoryCode != ""'>
              AND pae.category_code = #{categoryCode}
            </if>
            ORDER BY pae.sort_no ASC, pae.published_at DESC, pae.id ASC
            </script>
            """)
    List<PublicAttachmentQueryDO> selectPublishedAllActive(@Param("categoryCode") String categoryCode);

    final class PublishPublicAttachmentSqlRecord {
        private Long id;
        private final String fileId;
        private final String displayName;
        private final String description;
        private final String categoryCode;
        private final String scopeType;
        private final String scopeValue;
        private final Long publishedBy;
        private final LocalDateTime publishedAt;
        private final Integer sortNo;

        public PublishPublicAttachmentSqlRecord(String fileId,
                                                String displayName,
                                                String description,
                                                String categoryCode,
                                                String scopeType,
                                                String scopeValue,
                                                Long publishedBy,
                                                LocalDateTime publishedAt,
                                                Integer sortNo) {
            this.fileId = fileId;
            this.displayName = displayName;
            this.description = description;
            this.categoryCode = categoryCode;
            this.scopeType = scopeType;
            this.scopeValue = scopeValue;
            this.publishedBy = publishedBy;
            this.publishedAt = publishedAt;
            this.sortNo = sortNo;
        }

        public Long id() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getFileId() {
            return fileId;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }

        public String getCategoryCode() {
            return categoryCode;
        }

        public String getScopeType() {
            return scopeType;
        }

        public String getScopeValue() {
            return scopeValue;
        }

        public Long getPublishedBy() {
            return publishedBy;
        }

        public LocalDateTime getPublishedAt() {
            return publishedAt;
        }

        public Integer getSortNo() {
            return sortNo;
        }
    }
}

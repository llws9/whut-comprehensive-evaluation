package edu.whut.eval.application.application.service;

import edu.whut.eval.domain.application.model.AttachmentRef;

import java.util.List;

/**
 * 根据业务文件 ID 解析可绑定到申请中的附件引用。
 * 解析过程同时负责校验当前用户是否有权使用对应附件。
 */
public interface ApplicationAttachmentResolver {

    /**
     * 按输入顺序解析附件，并返回申请聚合可直接持有的附件引用。
     */
    List<AttachmentRef> resolveForBinding(List<String> attachmentFileIds, Long currentUserId);
}

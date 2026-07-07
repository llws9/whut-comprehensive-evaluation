package edu.whut.eval.application.application.query;

public class ApplicationAttachmentView {

    private String fileId;
    private String originalFilename;
    private String contentType;
    private Long size;
    private Integer sortNo;

    public ApplicationAttachmentView() {
    }

    public ApplicationAttachmentView(String fileId,
                                     String originalFilename,
                                     String contentType,
                                     Long size,
                                     Integer sortNo) {
        this.fileId = fileId;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.size = size;
        this.sortNo = sortNo;
    }

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }
}

package com.campus.rag;

/** 知识库中的一个可检索片段 */
public class RagChunk {
    private final String id;
    private final String title;
    private final String tags;
    private final String content;

    public RagChunk(String id, String title, String tags, String content) {
        this.id = id;
        this.title = title == null ? "" : title;
        this.tags = tags == null ? "" : tags;
        this.content = content == null ? "" : content;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getTags() {
        return tags;
    }

    public String getContent() {
        return content;
    }

    public String searchableText() {
        return title + "\n" + tags + "\n" + content;
    }
}

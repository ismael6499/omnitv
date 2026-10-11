package com.nitsutech.omnitv.comments;

import java.util.ArrayList;
import java.util.List;

public class YouTubeCommentItem {
    public String commentId;
    public String authorName;
    public String authorThumbnailUrl;
    public String publishedTimeText;
    public String originalText;
    public String translatedText;
    public String targetLanguage; // "es", "en", or null
    public boolean isTranslated = false;
    public boolean isTranslating = false;
    public int replyCount = 0;
    public String replyContinuationToken;
    public List<YouTubeCommentItem> replies = new ArrayList<>();

    public YouTubeCommentItem() {}

    public YouTubeCommentItem(String commentId, String authorName, String originalText) {
        this.commentId = commentId;
        this.authorName = authorName;
        this.originalText = originalText;
    }

    public String getDisplayText() {
        if (isTranslated && translatedText != null && !translatedText.isEmpty()) {
            return translatedText;
        }
        return originalText != null ? originalText : "";
    }
}

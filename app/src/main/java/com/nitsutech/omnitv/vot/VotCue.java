package com.nitsutech.omnitv.vot;

public class VotCue {
    public final int id;
    public final long startMs;
    public final long durationMs;
    public final long endMs;
    public final String originalText;
    public String translatedText;
    public boolean isTranslated;
    public boolean isSpoken;

    public VotCue(int id, long startMs, long durationMs, String originalText) {
        this.id = id;
        this.startMs = startMs;
        this.durationMs = durationMs > 0 ? durationMs : 2500;
        this.endMs = startMs + this.durationMs;
        this.originalText = originalText != null ? originalText.trim() : "";
        this.translatedText = "";
        this.isTranslated = false;
        this.isSpoken = false;
    }

    @Override
    public String toString() {
        return "VotCue{" +
                "id=" + id +
                ", start=" + (startMs / 1000.0) + "s" +
                ", dur=" + (durationMs / 1000.0) + "s" +
                ", orig='" + originalText + '\'' +
                ", trans='" + translatedText + '\'' +
                '}';
    }
}

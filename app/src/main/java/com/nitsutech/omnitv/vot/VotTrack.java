package com.nitsutech.omnitv.vot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class VotTrack {
    public final String videoId;
    public final String languageCode;
    public final List<VotCue> cues;
    private int searchStartIndex = 0;

    public VotTrack(String videoId, String languageCode, List<VotCue> cues) {
        this.videoId = videoId;
        this.languageCode = languageCode != null ? languageCode : "en";
        this.cues = cues != null ? cues : new ArrayList<VotCue>();
    }

    public synchronized VotCue getNextCueToSpeak(long currentPositionMs) {
        if (cues.isEmpty()) return null;

        for (int i = searchStartIndex; i < cues.size(); i++) {
            VotCue cue = cues.get(i);
            // If the cue ended more than 1 second ago, skip it
            if (cue.endMs < currentPositionMs - 1000) {
                cue.isSpoken = true;
                searchStartIndex = i;
                continue;
            }

            // If cue is upcoming within 350ms window and hasn't ended yet
            if (currentPositionMs >= (cue.startMs - 350) && currentPositionMs <= cue.endMs) {
                if (!cue.isSpoken && cue.isTranslated && cue.translatedText != null && !cue.translatedText.isEmpty()) {
                    searchStartIndex = i;
                    return cue;
                }
            }

            // If this cue starts far into the future, stop searching
            if (cue.startMs > currentPositionMs + 5000) {
                break;
            }
        }
        return null;
    }

    public synchronized List<VotCue> getUpcomingUntranslatedCues(long currentPositionMs, int maxCount, long lookaheadMs) {
        List<VotCue> result = new ArrayList<>();
        if (cues.isEmpty()) return result;

        long maxTargetMs = currentPositionMs + lookaheadMs;
        for (VotCue cue : cues) {
            if (cue.endMs < currentPositionMs) continue;
            if (cue.startMs > maxTargetMs) break;

            if (!cue.isTranslated) {
                result.add(cue);
                if (result.size() >= maxCount) break;
            }
        }
        return result;
    }

    public synchronized void syncPosition(long currentPositionMs) {
        searchStartIndex = 0;
        for (int i = 0; i < cues.size(); i++) {
            VotCue cue = cues.get(i);
            if (cue.endMs < currentPositionMs - 1500) {
                cue.isSpoken = true;
            } else {
                cue.isSpoken = false;
                if (searchStartIndex == 0 && cue.startMs >= currentPositionMs - 1000) {
                    searchStartIndex = Math.max(0, i - 1);
                }
            }
        }
    }

    public synchronized void invalidateTranslations() {
        for (VotCue cue : cues) {
            cue.isTranslated = false;
            cue.translatedText = "";
        }
    }

    public synchronized int size() {
        return cues.size();
    }
}

package com.nitsutech.omnitv;

import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.service.notification.NotificationListenerService;
import android.util.Log;
import java.util.List;

public class MediaNotificationListener extends NotificationListenerService {
    private static final String TAG = "MediaNotifListener";
    public static MediaNotificationListener instance;
    public static volatile String activeTitle = "";
    public static volatile String activeMediaId = "";

    private MediaSessionManager mediaSessionManager;
    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsChangedListener = 
        new MediaSessionManager.OnActiveSessionsChangedListener() {
            @Override
            public void onActiveSessionsChanged(List<MediaController> controllers) {
                Log.d(TAG, "onActiveSessionsChanged: count=" + (controllers != null ? controllers.size() : 0));
                attachControllers(controllers);
            }
        };

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        instance = this;
        Log.d(TAG, "MediaNotificationListener connected successfully!");
        setupMediaSessionListener();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (instance == this) instance = null;
        if (mediaSessionManager != null) {
            try {
                mediaSessionManager.removeOnActiveSessionsChangedListener(sessionsChangedListener);
            } catch (Exception ignored) {}
        }
    }

    public void setupMediaSessionListener() {
        try {
            mediaSessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (mediaSessionManager != null) {
                ComponentName cn = new ComponentName(this, MediaNotificationListener.class);
                mediaSessionManager.addOnActiveSessionsChangedListener(sessionsChangedListener, cn);
                List<MediaController> controllers = mediaSessionManager.getActiveSessions(cn);
                attachControllers(controllers);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up MediaSessionManager listener", e);
        }
    }

    private void attachControllers(List<MediaController> controllers) {
        if (controllers == null) return;
        for (final MediaController controller : controllers) {
            if (controller == null) continue;
            final String pkg = controller.getPackageName();
            Log.d(TAG, "Active MediaController: pkg=" + pkg + ", tag=" + controller.getTag());

            // Check current metadata
            MediaMetadata curMeta = controller.getMetadata();
            if (curMeta != null) {
                String title = extractTitleFromMetadata(curMeta);
                if (title != null && !title.trim().isEmpty()) {
                    activeTitle = title.trim();
                }
                String artist = extractArtistFromMetadata(curMeta);
                long duration = curMeta.getLong(MediaMetadata.METADATA_KEY_DURATION);
                String mediaId = extractMediaIdFromMetadata(curMeta);
                if (mediaId != null && !mediaId.trim().isEmpty()) {
                    activeMediaId = mediaId.trim();
                }
                ButtonMappingService svc = ButtonMappingService.instance;
                if (svc != null) {
                    svc.onStreamingVideoChanged(pkg, activeTitle, artist, duration, activeMediaId);
                }
                try {
                    com.nitsutech.omnitv.ai.AiSummaryOverlay.getInstance().onVideoChanged(activeTitle, activeMediaId);
                } catch (Exception ignored) {}
            }
            
            controller.registerCallback(new MediaController.Callback() {
                @Override
                public void onMetadataChanged(MediaMetadata metadata) {
                    if (metadata != null) {
                        String title = extractTitleFromMetadata(metadata);
                        if (title != null && !title.trim().isEmpty()) {
                            activeTitle = title.trim();
                        }
                        String artist = extractArtistFromMetadata(metadata);
                        long duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
                        String mediaId = extractMediaIdFromMetadata(metadata);
                        if (mediaId != null && !mediaId.trim().isEmpty()) {
                            activeMediaId = mediaId.trim();
                        }
                        Log.d(TAG, "MediaController metadataChanged [" + pkg + "]: title='" + activeTitle + "', artist='" + artist + "', dur=" + duration + ", mediaId=" + activeMediaId);
                        
                        ButtonMappingService svc = ButtonMappingService.instance;
                        if (svc != null) {
                            svc.onStreamingVideoChanged(pkg, activeTitle, artist, duration, activeMediaId);
                        }
                        try {
                            com.nitsutech.omnitv.ai.AiSummaryOverlay.getInstance().onVideoChanged(activeTitle, activeMediaId);
                        } catch (Exception ignored) {}
                    }
                }

                @Override
                public void onPlaybackStateChanged(PlaybackState state) {
                    if (state != null) {
                        Log.d(TAG, "MediaController playbackStateChanged [" + pkg + "]: state=" + state.getState() + ", pos=" + state.getPosition());
                        if (state.getState() == PlaybackState.STATE_PLAYING) {
                            resetFrameStepState();
                        }
                        ButtonMappingService svc = ButtonMappingService.instance;
                        if (svc != null) {
                            svc.onStreamingPlaybackStateChanged(pkg, state.getState(), state.getPosition());
                        }
                    }
                }
            });
        }
    }

    private static long lastSteppedFrameIndex = -1;
    private static long lastStepTimestamp = 0;

    public static void resetFrameStepState() {
        lastSteppedFrameIndex = -1;
        lastStepTimestamp = 0;
    }

    public static boolean stepActiveMediaFrame(int direction, double frameDurationMs) {
        return stepActiveMediaFrameByCount(direction, frameDurationMs);
    }

    public static boolean stepActiveMediaFrameByCount(int frameDeltaCount, double frameDurationMs) {
        try {
            if (instance != null && instance.mediaSessionManager != null) {
                ComponentName cn = new ComponentName(instance, MediaNotificationListener.class);
                List<MediaController> controllers = instance.mediaSessionManager.getActiveSessions(cn);
                if (controllers != null) {
                    for (MediaController mc : controllers) {
                        if (mc != null && mc.getPlaybackState() != null) {
                            String pkg = mc.getPackageName();
                            if (pkg != null && (pkg.contains("smarttube") || pkg.contains("youtube"))) {
                                long curPos = mc.getPlaybackState().getPosition();
                                long now = android.os.SystemClock.elapsedRealtime();
                                long targetFrameIndex;

                                if (lastStepTimestamp > 0 && (now - lastStepTimestamp) < 1500 && lastSteppedFrameIndex >= 0) {
                                    long expectedPos = Math.round(lastSteppedFrameIndex * frameDurationMs);
                                    if (Math.abs(curPos - expectedPos) > (frameDurationMs * 6)) {
                                        long baseFrame = Math.round(curPos / frameDurationMs);
                                        targetFrameIndex = baseFrame + frameDeltaCount;
                                    } else {
                                        targetFrameIndex = lastSteppedFrameIndex + frameDeltaCount;
                                    }
                                } else {
                                    long baseFrame = Math.round(curPos / frameDurationMs);
                                    targetFrameIndex = baseFrame + frameDeltaCount;
                                }

                                if (targetFrameIndex < 0) targetFrameIndex = 0;
                                lastSteppedFrameIndex = targetFrameIndex;
                                lastStepTimestamp = now;

                                long newPos = Math.round(targetFrameIndex * frameDurationMs + (frameDurationMs / 2.0));
                                Log.d(TAG, "Stepping " + frameDeltaCount + " frames in " + pkg + " [targetFrame=" + targetFrameIndex + ", dur=" + frameDurationMs + "ms] from " + curPos + " to " + newPos);
                                mc.getTransportControls().seekTo(newPos);
                                return true;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stepping active media frame by count: " + frameDeltaCount, e);
        }
        return false;
    }

    public static void pauseAllActiveMedia(Context context) {
        try {
            if (instance != null && instance.mediaSessionManager != null) {
                ComponentName cn = new ComponentName(instance, MediaNotificationListener.class);
                List<MediaController> controllers = instance.mediaSessionManager.getActiveSessions(cn);
                if (controllers != null) {
                    for (MediaController mc : controllers) {
                        if (mc != null && mc.getPlaybackState() != null && mc.getPlaybackState().getState() == PlaybackState.STATE_PLAYING) {
                            Log.d(TAG, "Pausing media via MediaController: " + mc.getPackageName());
                            mc.getTransportControls().pause();
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error pausing media via MediaController", e);
        }
    }

    public static boolean seekActiveMediaBy(long deltaMs) {
        try {
            if (instance != null && instance.mediaSessionManager != null) {
                ComponentName cn = new ComponentName(instance, MediaNotificationListener.class);
                List<MediaController> controllers = instance.mediaSessionManager.getActiveSessions(cn);
                if (controllers != null) {
                    for (MediaController mc : controllers) {
                        if (mc != null && mc.getPlaybackState() != null) {
                            String pkg = mc.getPackageName();
                            if (pkg != null && (pkg.contains("smarttube") || pkg.contains("youtube"))) {
                                long curPos = mc.getPlaybackState().getPosition();
                                long newPos = Math.max(0, curPos + deltaMs);
                                Log.d(TAG, "Seeking " + pkg + " from " + curPos + " to " + newPos + " (delta: " + deltaMs + "ms)");
                                mc.getTransportControls().seekTo(newPos);
                                return true;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error seeking active media via MediaController", e);
        }
        return false;
    }

    public static boolean seekToPosition(long positionMs) {
        try {
            if (instance != null && instance.mediaSessionManager != null) {
                ComponentName cn = new ComponentName(instance, MediaNotificationListener.class);
                List<MediaController> controllers = instance.mediaSessionManager.getActiveSessions(cn);
                if (controllers != null) {
                    for (MediaController mc : controllers) {
                        if (mc != null && mc.getPlaybackState() != null) {
                            String pkg = mc.getPackageName();
                            if (pkg != null && (pkg.contains("smarttube") || pkg.contains("youtube") || pkg.contains("netflix") || pkg.contains("disney"))) {
                                Log.d(TAG, "Seeking " + pkg + " to absolute position: " + positionMs + "ms");
                                mc.getTransportControls().seekTo(positionMs);
                                return true;
                            }
                        }
                    }
                    for (MediaController mc : controllers) {
                        if (mc != null && mc.getTransportControls() != null) {
                            mc.getTransportControls().seekTo(positionMs);
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error seeking to position " + positionMs, e);
        }
        return false;
    }

    public static String extractCleanVideoId(String input) {
        if (input == null || input.trim().isEmpty()) return null;
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("([a-zA-Z0-9_-]{11})");
        java.util.regex.Matcher m = p.matcher(input.trim());
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    public static String extractTitleFromMetadata(MediaMetadata meta) {
        if (meta == null) return null;
        String t = meta.getString(MediaMetadata.METADATA_KEY_TITLE);
        if (t != null && !t.trim().isEmpty()) return t.trim();
        t = meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        if (t != null && !t.trim().isEmpty()) return t.trim();
        if (meta.getDescription() != null) {
            CharSequence cs = meta.getDescription().getTitle();
            if (cs != null && !cs.toString().trim().isEmpty()) {
                return cs.toString().trim();
            }
            cs = meta.getDescription().getDescription();
            if (cs != null && !cs.toString().trim().isEmpty()) {
                String s = cs.toString().trim();
                int commaIdx = s.indexOf(",");
                if (commaIdx > 2) {
                    return s.substring(0, commaIdx).trim();
                }
                return s;
            }
        }
        return null;
    }

    public static String extractArtistFromMetadata(MediaMetadata meta) {
        if (meta == null) return null;
        String a = meta.getString(MediaMetadata.METADATA_KEY_ARTIST);
        if (a != null && !a.trim().isEmpty()) return a.trim();
        a = meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
        if (a != null && !a.trim().isEmpty()) return a.trim();
        if (meta.getDescription() != null && meta.getDescription().getSubtitle() != null) {
            return meta.getDescription().getSubtitle().toString().trim();
        }
        return null;
    }

    public static String extractMediaIdFromMetadata(MediaMetadata meta) {
        if (meta == null) return null;
        String id = meta.getString(MediaMetadata.METADATA_KEY_MEDIA_ID);
        if (id != null && !id.trim().isEmpty()) return id.trim();
        if (meta.getDescription() != null && meta.getDescription().getMediaId() != null) {
            return meta.getDescription().getMediaId().trim();
        }
        return null;
    }

    public static class LiveVideoInfo {
        public String title = "";
        public String mediaId = "";
        public String artist = "";
        public String pkg = "";
    }

    public static LiveVideoInfo getLiveVideoInfo(Context context) {
        LiveVideoInfo info = new LiveVideoInfo();
        info.title = activeTitle != null ? activeTitle : "";
        info.mediaId = activeMediaId != null ? activeMediaId : "";
        try {
            if (instance != null && instance.mediaSessionManager != null) {
                ComponentName cn = new ComponentName(instance, MediaNotificationListener.class);
                List<MediaController> controllers = instance.mediaSessionManager.getActiveSessions(cn);
                if (controllers != null) {
                    for (MediaController mc : controllers) {
                        if (mc == null) continue;
                        String pkg = mc.getPackageName();
                        PlaybackState ps = mc.getPlaybackState();
                        boolean isPlaying = ps != null && ps.getState() == PlaybackState.STATE_PLAYING;
                        MediaMetadata mm = mc.getMetadata();
                        if (mm != null) {
                            String t = extractTitleFromMetadata(mm);
                            String id = extractMediaIdFromMetadata(mm);
                            String a = extractArtistFromMetadata(mm);
                            if (t != null && !t.isEmpty()) {
                                info.title = t;
                                info.mediaId = id != null ? id : "";
                                info.artist = a != null ? a : "";
                                info.pkg = pkg != null ? pkg : "";
                                activeTitle = t;
                                if (id != null && !id.isEmpty()) activeMediaId = id;
                                if (isPlaying) break; // Priority on playing session
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error fetching live video info", e);
        }
        return info;
    }
}

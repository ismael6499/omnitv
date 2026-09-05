package com.example.togglegrayscale;

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
                String title = curMeta.getString(MediaMetadata.METADATA_KEY_TITLE);
                String artist = curMeta.getString(MediaMetadata.METADATA_KEY_ARTIST);
                long duration = curMeta.getLong(MediaMetadata.METADATA_KEY_DURATION);
                String mediaId = curMeta.getString(MediaMetadata.METADATA_KEY_MEDIA_ID);
                if ((mediaId == null || mediaId.isEmpty()) && curMeta.getDescription() != null) {
                    mediaId = curMeta.getDescription().getMediaId();
                }
                ButtonMappingService svc = ButtonMappingService.instance;
                if (svc != null) {
                    svc.onStreamingVideoChanged(pkg, title, artist, duration, mediaId);
                }
            }
            
            controller.registerCallback(new MediaController.Callback() {
                @Override
                public void onMetadataChanged(MediaMetadata metadata) {
                    if (metadata != null) {
                        String title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
                        String artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
                        long duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
                        String mediaId = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID);
                        if ((mediaId == null || mediaId.isEmpty()) && metadata.getDescription() != null) {
                            mediaId = metadata.getDescription().getMediaId();
                        }
                        Log.d(TAG, "MediaController metadataChanged [" + pkg + "]: title='" + title + "', artist='" + artist + "', dur=" + duration + ", mediaId=" + mediaId);
                        
                        ButtonMappingService svc = ButtonMappingService.instance;
                        if (svc != null) {
                            svc.onStreamingVideoChanged(pkg, title, artist, duration, mediaId);
                        }
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
}

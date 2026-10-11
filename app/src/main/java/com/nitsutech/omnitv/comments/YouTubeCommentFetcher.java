package com.nitsutech.omnitv.comments;

import android.content.Context;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class YouTubeCommentFetcher {
    private static final String TAG = "YouTubeCommentFetcher";

    // Public InnerTube web client identifier decoded at runtime
    private static String getInnerTubeKey() {
        int[] d = {10, 2, 49, 42, 24, 50, 10, 4, 20, 13, 1, 121, 24, 39, 58, 30, 115, 26, 127, 24, 31, 14, 3, 7, 12, 8, 34, 39, 60, 20, 18, 114, 20, 122, 122, 58, 40, 28, 115};
        char[] c = new char[d.length];
        for (int i = 0; i < d.length; i++) {
            c[i] = (char) (d[i] ^ 0x4B);
        }
        return new String(c);
    }

    private static final String NEXT_URL = "https://www.youtube.com/youtubei/v1/next?key=" + getInnerTubeKey();

    public interface CommentsCallback {
        void onSuccess(List<YouTubeCommentItem> comments, String nextContinuationToken);
        void onError(String error);
    }

    public static void fetchVideoCommentsAsync(final String videoId, final String continuationToken, final CommentsCallback callback) {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                JSONObject context = new JSONObject();
                JSONObject client = new JSONObject();
                client.put("clientName", "WEB");
                client.put("clientVersion", "2.20240315.00.00");
                context.put("client", client);
                body.put("context", context);

                if (continuationToken != null && !continuationToken.isEmpty()) {
                    body.put("continuation", continuationToken);
                } else if (videoId != null && !videoId.isEmpty()) {
                    body.put("videoId", videoId);
                } else {
                    if (callback != null) callback.onError("No videoId or continuation token provided");
                    return;
                }

                String response = postHttp(NEXT_URL, body.toString());
                if (response == null || response.isEmpty()) {
                    if (callback != null) callback.onError("Empty response from InnerTube");
                    return;
                }

                JSONObject root = new JSONObject(response);
                List<YouTubeCommentItem> list = new ArrayList<>();
                String nextToken = extractCommentsAndContinuation(root, list);

                // If initial video query didn't return comments directly, look for initial comment section continuation token
                if (list.isEmpty() && nextToken != null && (continuationToken == null || continuationToken.isEmpty())) {
                    Log.d(TAG, "Resolving comment section continuation token: " + nextToken);
                    fetchVideoCommentsAsync(null, nextToken, callback);
                    return;
                }

                if (callback != null) callback.onSuccess(list, nextToken);
            } catch (Exception e) {
                Log.e(TAG, "Error in fetchVideoCommentsAsync", e);
                if (callback != null) callback.onError(e.getMessage());
            }
        }).start();
    }

    public static void fetchRepliesAsync(final String replyContinuationToken, final CommentsCallback callback) {
        fetchVideoCommentsAsync(null, replyContinuationToken, callback);
    }

    private static String extractCommentsAndContinuation(JSONObject root, List<YouTubeCommentItem> outList) {
        String nextContinuation = null;
        try {
            // Check direct continuationItems (typical for comment continuation or reply requests)
            JSONArray continuationItems = null;
            if (root.has("onResponseReceivedEndpoints")) {
                JSONArray endpoints = root.optJSONArray("onResponseReceivedEndpoints");
                if (endpoints != null) {
                    for (int i = 0; i < endpoints.length(); i++) {
                        JSONObject ep = endpoints.optJSONObject(i);
                        if (ep == null) continue;
                        JSONObject reloadContinuation = ep.optJSONObject("reloadContinuationItemsCommand");
                        if (reloadContinuation != null) {
                            continuationItems = reloadContinuation.optJSONArray("continuationItems");
                        }
                        JSONObject appendContinuation = ep.optJSONObject("appendContinuationItemsAction");
                        if (appendContinuation != null) {
                            continuationItems = appendContinuation.optJSONArray("continuationItems");
                        }
                    }
                }
            }

            // Check contents section for initial video request
            if (continuationItems == null && root.has("contents")) {
                JSONObject contents = root.optJSONObject("contents");
                if (contents != null) {
                    JSONObject twoCol = contents.optJSONObject("twoColumnWatchNextResults");
                    if (twoCol != null) {
                        JSONObject results = twoCol.optJSONObject("results");
                        if (results != null) {
                            JSONObject res = results.optJSONObject("results");
                            if (res != null) {
                                JSONArray items = res.optJSONArray("contents");
                                if (items != null) {
                                    for (int i = 0; i < items.length(); i++) {
                                        JSONObject item = items.optJSONObject(i);
                                        if (item != null && item.has("itemSectionRenderer")) {
                                            JSONObject isr = item.optJSONObject("itemSectionRenderer");
                                            if (isr != null && "comments-section".equals(isr.optString("sectionIdentifier"))) {
                                                continuationItems = isr.optJSONArray("contents");
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (continuationItems != null) {
                for (int i = 0; i < continuationItems.length(); i++) {
                    JSONObject cItem = continuationItems.optJSONObject(i);
                    if (cItem == null) continue;

                    // 1. Comment thread
                    if (cItem.has("commentThreadRenderer")) {
                        JSONObject thread = cItem.optJSONObject("commentThreadRenderer");
                        if (thread != null) {
                            JSONObject commentRenderer = thread.optJSONObject("comment");
                            if (commentRenderer != null) {
                                JSONObject cr = commentRenderer.optJSONObject("commentRenderer");
                                if (cr != null) {
                                    YouTubeCommentItem item = parseCommentRenderer(cr);
                                    if (item != null) {
                                        // Check for replies token inside thread
                                        JSONObject repliesObj = thread.optJSONObject("replies");
                                        if (repliesObj != null) {
                                            JSONObject crr = repliesObj.optJSONObject("commentRepliesRenderer");
                                            if (crr != null) {
                                                JSONArray replyContinuations = crr.optJSONArray("contents");
                                                if (replyContinuations != null && replyContinuations.length() > 0) {
                                                    for (int r = 0; r < replyContinuations.length(); r++) {
                                                        JSONObject rObj = replyContinuations.optJSONObject(r);
                                                        if (rObj != null && rObj.has("continuationItemRenderer")) {
                                                            JSONObject cir = rObj.optJSONObject("continuationItemRenderer");
                                                            JSONObject button = cir != null ? cir.optJSONObject("button") : null;
                                                            JSONObject buttonRenderer = button != null ? button.optJSONObject("buttonRenderer") : null;
                                                            JSONObject command = buttonRenderer != null ? buttonRenderer.optJSONObject("command") : null;
                                                            JSONObject ce = command != null ? command.optJSONObject("continuationEndpoint") : null;
                                                            JSONObject cc = ce != null ? ce.optJSONObject("continuationCommand") : null;
                                                            if (cc != null) {
                                                                item.replyContinuationToken = cc.optString("token", null);
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        outList.add(item);
                                    }
                                }
                            }
                        }
                    }
                    // 2. Direct comment (e.g. inside replies list)
                    else if (cItem.has("commentRenderer")) {
                        YouTubeCommentItem item = parseCommentRenderer(cItem.optJSONObject("commentRenderer"));
                        if (item != null) outList.add(item);
                    }
                    // 3. Next page continuation token
                    else if (cItem.has("continuationItemRenderer")) {
                        JSONObject cir = cItem.optJSONObject("continuationItemRenderer");
                        if (cir != null) {
                            JSONObject ce = cir.optJSONObject("continuationEndpoint");
                            if (ce != null) {
                                JSONObject cc = ce.optJSONObject("continuationCommand");
                                if (cc != null) {
                                    nextContinuation = cc.optString("token", null);
                                }
                            }
                            if (nextContinuation == null) {
                                JSONObject button = cir.optJSONObject("button");
                                JSONObject buttonRenderer = button != null ? button.optJSONObject("buttonRenderer") : null;
                                JSONObject command = buttonRenderer != null ? buttonRenderer.optJSONObject("command") : null;
                                JSONObject bce = command != null ? command.optJSONObject("continuationEndpoint") : null;
                                JSONObject bcc = bce != null ? bce.optJSONObject("continuationCommand") : null;
                                if (bcc != null) {
                                    nextContinuation = bcc.optString("token", null);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error extracting comments and continuation", e);
        }
        return nextContinuation;
    }

    private static YouTubeCommentItem parseCommentRenderer(JSONObject cr) {
        if (cr == null) return null;
        try {
            YouTubeCommentItem item = new YouTubeCommentItem();
            item.commentId = cr.optString("commentId", "");

            JSONObject authorText = cr.optJSONObject("authorText");
            if (authorText != null) {
                item.authorName = authorText.optString("simpleText", "");
            }

            JSONObject publishedTime = cr.optJSONObject("publishedTimeText");
            if (publishedTime != null) {
                JSONArray runs = publishedTime.optJSONArray("runs");
                if (runs != null && runs.length() > 0) {
                    item.publishedTimeText = runs.getJSONObject(0).optString("text", "");
                }
            }

            JSONObject contentText = cr.optJSONObject("contentText");
            if (contentText != null) {
                JSONArray runs = contentText.optJSONArray("runs");
                if (runs != null) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < runs.length(); i++) {
                        JSONObject run = runs.optJSONObject(i);
                        if (run != null) {
                            sb.append(run.optString("text", ""));
                        }
                    }
                    item.originalText = sb.toString().trim();
                } else {
                    item.originalText = contentText.optString("simpleText", "").trim();
                }
            }

            item.replyCount = cr.optInt("replyCount", 0);
            return item;
        } catch (Exception e) {
            Log.e(TAG, "Error parsing comment renderer", e);
            return null;
        }
    }

    private static String postHttp(String urlStr, String jsonBody) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(10000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

            byte[] input = jsonBody.getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(input, 0, input.length);
            }

            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                    }
                    return sb.toString();
                }
            } else {
                Log.w(TAG, "HTTP POST failed (" + code + ") for " + urlStr);
            }
        } catch (Exception e) {
            Log.e(TAG, "HTTP POST error for " + urlStr, e);
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }
}

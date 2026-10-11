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
        fetchCommentsAsync(videoId, continuationToken, false, callback);
    }

    private static void fetchCommentsAsync(final String videoId, final String continuationToken,
                                           final boolean repliesOnly, final CommentsCallback callback) {
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
                String nextToken = extractCommentsAndContinuation(root, list, repliesOnly);

                // If initial video query didn't return comments directly, look for initial comment section continuation token
                if (list.isEmpty() && nextToken != null && (continuationToken == null || continuationToken.isEmpty())) {
                    Log.d(TAG, "Resolving comment section continuation token: " + nextToken);
                    fetchCommentsAsync(null, nextToken, false, callback);
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
        fetchCommentsAsync(null, replyContinuationToken, true, callback);
    }

    private static String extractCommentsAndContinuation(JSONObject root, List<YouTubeCommentItem> outList, boolean repliesOnly) {
        String nextContinuation = null;
        try {
            // Build comment entities map from frameworkUpdates (Modern YouTube InnerTube web responses)
            java.util.Map<String, JSONObject> entityMap = new java.util.HashMap<>();
            JSONObject fw = root.optJSONObject("frameworkUpdates");
            if (fw != null) {
                JSONObject batch = fw.optJSONObject("entityBatchUpdate");
                if (batch != null) {
                    JSONArray mutations = batch.optJSONArray("mutations");
                    if (mutations != null) {
                        for (int m = 0; m < mutations.length(); m++) {
                            JSONObject mut = mutations.optJSONObject(m);
                            if (mut == null) continue;
                            JSONObject payload = mut.optJSONObject("payload");
                            if (payload != null && payload.has("commentEntityPayload")) {
                                JSONObject cep = payload.optJSONObject("commentEntityPayload");
                                if (cep != null) {
                                    JSONObject props = cep.optJSONObject("properties");
                                    String cid = props != null ? props.optString("commentId", "") : "";
                                    String key = cep.optString("key", "");
                                    if (!cid.isEmpty()) entityMap.put(cid, cep);
                                    if (!key.isEmpty()) entityMap.put(key, cep);
                                }
                            }
                        }
                    }
                }
            }

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
                            JSONArray items = reloadContinuation.optJSONArray("continuationItems");
                            if (items != null) continuationItems = items;
                        }
                        JSONObject appendContinuation = ep.optJSONObject("appendContinuationItemsAction");
                        if (appendContinuation != null) {
                            JSONArray items = appendContinuation.optJSONArray("continuationItems");
                            if (items != null) continuationItems = items;
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
                                            if (isr != null) {
                                                String secId = isr.optString("sectionIdentifier", "");
                                                String targetId = isr.optString("targetId", "");
                                                if ("comments-section".equals(secId) || "comments-section".equals(targetId) || secId.contains("comment")) {
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
            }

            // Check engagementPanels for initial comments section token if not found yet
            if (continuationItems == null && root.has("engagementPanels")) {
                JSONArray panels = root.optJSONArray("engagementPanels");
                if (panels != null) {
                    for (int p = 0; p < panels.length(); p++) {
                        JSONObject panel = panels.optJSONObject(p);
                        if (panel == null) continue;
                        JSONObject epsl = panel.optJSONObject("engagementPanelSectionListRenderer");
                        if (epsl != null) {
                            String pId = epsl.optString("panelIdentifier", "");
                            if (pId.contains("comments-section")) {
                                JSONObject content = epsl.optJSONObject("content");
                                if (content != null) {
                                    JSONObject slr = content.optJSONObject("sectionListRenderer");
                                    if (slr != null) {
                                        JSONArray slrContents = slr.optJSONArray("contents");
                                        if (slrContents != null && slrContents.length() > 0) {
                                            for (int s = 0; s < slrContents.length(); s++) {
                                                JSONObject sec = slrContents.optJSONObject(s);
                                                if (sec != null && sec.has("itemSectionRenderer")) {
                                                    continuationItems = sec.optJSONObject("itemSectionRenderer").optJSONArray("contents");
                                                    break;
                                                }
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
                            YouTubeCommentItem item = null;

                            // A. Check classic commentRenderer
                            JSONObject commentRenderer = thread.optJSONObject("comment");
                            if (commentRenderer != null) {
                                JSONObject cr = commentRenderer.optJSONObject("commentRenderer");
                                if (cr != null) {
                                    item = parseCommentRenderer(cr);
                                }
                            }

                            // B. Check modern commentViewModel + entityMap
                            if (item == null && thread.has("commentViewModel")) {
                                JSONObject cvmContainer = thread.optJSONObject("commentViewModel");
                                JSONObject cvm = cvmContainer != null ? cvmContainer.optJSONObject("commentViewModel") : null;
                                if (cvm != null) {
                                    String cid = cvm.optString("commentId", "");
                                    JSONObject cep = entityMap.get(cid);
                                    if (cep != null) {
                                        item = parseCommentEntityPayload(cep);
                                    }
                                }
                            }

                            if (item != null) {
                                // Check for replies token inside thread
                                JSONObject repliesObj = thread.optJSONObject("replies");
                                if (repliesObj != null) {
                                    JSONObject crr = repliesObj.optJSONObject("commentRepliesRenderer");
                                    if (crr != null) {
                                        // Extract reply count from viewReplies if not yet set
                                        if (item.replyCount == 0) {
                                            JSONObject viewReplies = crr.optJSONObject("viewReplies");
                                            if (viewReplies != null) {
                                                JSONObject btn = viewReplies.optJSONObject("buttonRenderer");
                                                JSONObject txt = btn != null ? btn.optJSONObject("text") : null;
                                                JSONArray runs = txt != null ? txt.optJSONArray("runs") : null;
                                                if (runs != null && runs.length() > 0) {
                                                    String rText = runs.optJSONObject(0).optString("text", "");
                                                    try {
                                                        String numStr = rText.replaceAll("[^0-9]", "");
                                                        if (!numStr.isEmpty()) item.replyCount = Integer.parseInt(numStr);
                                                    } catch (Exception ignored) {}
                                                }
                                            }
                                        }

                                        JSONArray replyContinuations = crr.optJSONArray("contents");
                                        if (replyContinuations != null && replyContinuations.length() > 0) {
                                            for (int r = 0; r < replyContinuations.length(); r++) {
                                                JSONObject rObj = replyContinuations.optJSONObject(r);
                                                if (rObj != null) {
                                                    List<YouTubeCommentItem> parsedReplies = new ArrayList<>();
                                                    collectReplyItems(rObj, entityMap, parsedReplies);
                                                    if (repliesOnly) {
                                                        outList.addAll(parsedReplies);
                                                    } else {
                                                        item.replies.addAll(parsedReplies);
                                                    }
                                                }
                                                if (rObj != null && rObj.has("continuationItemRenderer")) {
                                                    JSONObject cir = rObj.optJSONObject("continuationItemRenderer");
                                                    JSONObject button = cir != null ? cir.optJSONObject("button") : null;
                                                    JSONObject buttonRenderer = button != null ? button.optJSONObject("buttonRenderer") : null;
                                                    JSONObject command = buttonRenderer != null ? buttonRenderer.optJSONObject("command") : null;
                                                    JSONObject ce = command != null ? command.optJSONObject("continuationEndpoint") : null;
                                                    if (ce == null && cir != null) ce = cir.optJSONObject("continuationEndpoint");
                                                    JSONObject cc = ce != null ? ce.optJSONObject("continuationCommand") : null;
                                                    if (cc != null) {
                                                        item.replyContinuationToken = cc.optString("token", null);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                if (!repliesOnly) outList.add(item);
                            }
                        }
                    }
                    // 2. Direct comment (e.g. inside replies list)
                    else if (cItem.has("commentRenderer")) {
                        YouTubeCommentItem item = parseCommentRenderer(cItem.optJSONObject("commentRenderer"));
                        if (item != null) outList.add(item);
                    }
                    // 3. Direct commentViewModel in replies list
                    else if (cItem.has("commentViewModel")) {
                        YouTubeCommentItem item = parseCommentViewModel(cItem.optJSONObject("commentViewModel"), entityMap);
                        if (item != null) outList.add(item);
                    }
                    // 4. Next page continuation token
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

    /** Walks only the reply renderer subtree, so the thread's parent comment is never returned as a reply. */
    private static void collectReplyItems(JSONObject node, java.util.Map<String, JSONObject> entityMap,
                                          List<YouTubeCommentItem> out) {
        if (node == null) return;

        JSONObject classic = node.optJSONObject("commentRenderer");
        if (classic != null) {
            YouTubeCommentItem item = parseCommentRenderer(classic);
            if (item != null) out.add(item);
            return;
        }

        JSONObject viewModelContainer = node.optJSONObject("commentViewModel");
        if (viewModelContainer != null) {
            YouTubeCommentItem item = parseCommentViewModel(viewModelContainer, entityMap);
            if (item != null) {
                out.add(item);
                return;
            }
        }

        java.util.Iterator<String> keys = node.keys();
        while (keys.hasNext()) {
            Object child = node.opt(keys.next());
            if (child instanceof JSONObject) {
                collectReplyItems((JSONObject) child, entityMap, out);
            } else if (child instanceof JSONArray) {
                JSONArray array = (JSONArray) child;
                for (int i = 0; i < array.length(); i++) {
                    JSONObject item = array.optJSONObject(i);
                    if (item != null) collectReplyItems(item, entityMap, out);
                }
            }
        }
    }

    private static YouTubeCommentItem parseCommentViewModel(JSONObject container,
                                                            java.util.Map<String, JSONObject> entityMap) {
        if (container == null) return null;
        JSONObject viewModel = container.optJSONObject("commentViewModel");
        if (viewModel == null && container.has("commentId")) viewModel = container;
        if (viewModel == null) return null;
        JSONObject payload = entityMap.get(viewModel.optString("commentId", ""));
        if (payload != null) return parseCommentEntityPayload(payload);

        // Some continuation responses include the view model without a matching entity mutation.
        YouTubeCommentItem item = new YouTubeCommentItem();
        item.commentId = viewModel.optString("commentId", "");
        JSONObject content = viewModel.optJSONObject("contentText");
        if (content != null) {
            JSONArray runs = content.optJSONArray("runs");
            if (runs != null) {
                StringBuilder text = new StringBuilder();
                for (int i = 0; i < runs.length(); i++) {
                    JSONObject run = runs.optJSONObject(i);
                    if (run != null) text.append(run.optString("text", ""));
                }
                item.originalText = text.toString().trim();
            } else {
                item.originalText = content.optString("content", content.optString("simpleText", "")).trim();
            }
        }
        JSONObject author = viewModel.optJSONObject("authorText");
        if (author != null) item.authorName = author.optString("simpleText", "");
        JSONObject published = viewModel.optJSONObject("publishedTimeText");
        if (published != null) {
            JSONArray runs = published.optJSONArray("runs");
            if (runs != null && runs.length() > 0) {
                JSONObject run = runs.optJSONObject(0);
                if (run != null) item.publishedTimeText = run.optString("text", "");
            }
        }
        return item.originalText != null && !item.originalText.isEmpty() ? item : null;
    }

    private static YouTubeCommentItem parseCommentEntityPayload(JSONObject cep) {
        if (cep == null) return null;
        try {
            YouTubeCommentItem item = new YouTubeCommentItem();
            JSONObject props = cep.optJSONObject("properties");
            if (props != null) {
                item.commentId = props.optString("commentId", "");
                JSONObject contentObj = props.optJSONObject("content");
                if (contentObj != null) {
                    item.originalText = contentObj.optString("content", "").trim();
                }
                item.publishedTimeText = props.optString("publishedTime", "");
            }

            JSONObject author = cep.optJSONObject("author");
            if (author != null) {
                item.authorName = author.optString("displayName", "Viewer");
            }

            JSONObject toolbar = cep.optJSONObject("toolbar");
            if (toolbar != null) {
                String repStr = toolbar.optString("replyCount", "0");
                try {
                    String clean = repStr.replaceAll("[^0-9]", "");
                    if (!clean.isEmpty()) item.replyCount = Integer.parseInt(clean);
                } catch (Exception ignored) {}
            }

            return item;
        } catch (Exception e) {
            Log.e(TAG, "Error parsing commentEntityPayload", e);
            return null;
        }
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

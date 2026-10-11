package com.nitsutech.omnitv.translator;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Local multi-script OCR (Latin, Korean, Japanese, Chinese) using Google ML Kit, run in parallel. */
public class TextOcrEngine {
    private static final String TAG = "TextOcrEngine";

    public interface OcrCallback {
        void onResult(List<String> blocks);
        void onError(String error);
    }

    private static class Block {
        String text;
        Rect box;
        float score;
    }

    public static void recognize(final Bitmap bitmap, final OcrCallback callback) {
        new Thread(() -> {
            List<TextRecognizer> recognizers = new ArrayList<>();
            try {
                InputImage image = InputImage.fromBitmap(bitmap, 0);
                final TextRecognizer ko = TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());
                final TextRecognizer ja = TextRecognition.getClient(new JapaneseTextRecognizerOptions.Builder().build());
                final TextRecognizer zh = TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
                final TextRecognizer la = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
                recognizers.add(ko); recognizers.add(ja); recognizers.add(zh); recognizers.add(la);

                List<Task<Text>> tasks = new ArrayList<>();
                for (TextRecognizer r : recognizers) tasks.add(r.process(image));
                Tasks.await(Tasks.whenAllComplete(tasks));

                List<Block> candidates = new ArrayList<>();
                for (int i = 0; i < tasks.size(); i++) {
                    Task<Text> t = tasks.get(i);
                    if (!t.isSuccessful() || t.getResult() == null) continue;
                    for (Text.TextBlock tb : t.getResult().getTextBlocks()) {
                        // Keep ML Kit's line breaks: each block is rendered as a paragraph card.
                        String s = tb.getText() == null ? "" : tb.getText().trim();
                        Rect box = tb.getBoundingBox();
                        int scriptFit = scriptFit(i, s);
                        if (s.isEmpty() || box == null || scriptFit == 0) continue;
                        Block b = new Block();
                        b.text = s;
                        b.box = box;
                        b.score = confidenceScore(tb) + scriptFit * 0.01f;
                        candidates.add(b);
                    }
                }

                // Prefer the most confident reading for a region instead of always accepting
                // whichever script recognizer happened to run first.
                candidates.sort((a, b) -> Float.compare(b.score, a.score));
                List<Block> accepted = new ArrayList<>();
                for (Block candidate : candidates) {
                    boolean duplicate = false;
                    for (Block existing : accepted) {
                        if (overlapRatio(existing.box, candidate.box) > 0.5f) {
                            duplicate = true;
                            break;
                        }
                    }
                    if (!duplicate) accepted.add(candidate);
                }

                sortInReadingOrder(accepted);

                final List<String> out = new ArrayList<>();
                for (Block b : accepted) out.add(b.text);
                callback.onResult(out);
            } catch (Exception e) {
                Log.e(TAG, "OCR failed", e);
                callback.onError(e.getMessage());
            } finally {
                for (TextRecognizer r : recognizers) {
                    try { r.close(); } catch (Exception ignored) {}
                }
            }
        }).start();
    }

    /** Returns a small specificity bonus when the recognized text fits the model's script. */
    private static int scriptFit(int index, String s) {
        boolean hangul = false, kana = false, han = false, latin = false, alnum = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            Character.UnicodeBlock b = Character.UnicodeBlock.of(c);
            if (b == Character.UnicodeBlock.HANGUL_SYLLABLES || b == Character.UnicodeBlock.HANGUL_JAMO
                    || b == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO) hangul = true;
            else if (b == Character.UnicodeBlock.HIRAGANA || b == Character.UnicodeBlock.KATAKANA) kana = true;
            else if (b == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) han = true;
            else if (Character.isLetter(c) && Character.UnicodeScript.of(c) == Character.UnicodeScript.LATIN) latin = true;
            if (Character.isLetterOrDigit(c)) alnum = true;
        }
        switch (index) {
            case 0: return hangul ? 3 : 0;
            // Kana identifies Japanese strongly; Kanji-only text is still a valid Japanese candidate.
            case 1: return kana ? 3 : (han && !hangul ? 1 : 0);
            case 2: return han && !hangul && !kana ? 2 : 0;
            // The Latin model should not win merely because CJK characters count as letters.
            default: return latin && !hangul && !kana && !han && alnum ? 2 : 0;
        }
    }

    private static float confidenceScore(Text.TextBlock block) {
        float total = 0f;
        int count = 0;
        for (Text.Line line : block.getLines()) {
            float confidence = line.getConfidence();
            // Some Play Services versions return 0 when confidence isn't available.
            if (confidence > 0f && confidence <= 1f) {
                total += confidence;
                count++;
            }
        }
        return count == 0 ? 0f : total / count;
    }

    /** Sorts blocks by top-to-bottom rows, then left-to-right within each visual row. */
    private static void sortInReadingOrder(List<Block> blocks) {
        Collections.sort(blocks, (a, b) -> {
            int top = Integer.compare(a.box.top, b.box.top);
            return top != 0 ? top : Integer.compare(a.box.left, b.box.left);
        });

        List<Block> ordered = new ArrayList<>(blocks.size());
        int start = 0;
        while (start < blocks.size()) {
            Block anchor = blocks.get(start);
            List<Block> row = new ArrayList<>();
            row.add(anchor);
            int end = start + 1;
            while (end < blocks.size() && sharesVisualRow(anchor.box, blocks.get(end).box)) {
                row.add(blocks.get(end));
                end++;
            }
            Collections.sort(row, (a, b) -> Integer.compare(a.box.left, b.box.left));
            ordered.addAll(row);
            start = end;
        }
        blocks.clear();
        blocks.addAll(ordered);
    }

    private static boolean sharesVisualRow(Rect a, Rect b) {
        int overlap = Math.max(0, Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top));
        int minHeight = Math.max(1, Math.min(a.height(), b.height()));
        return overlap / (float) minHeight >= 0.5f;
    }

    private static float overlapRatio(Rect a, Rect b) {
        Rect inter = new Rect();
        if (!inter.setIntersect(a, b)) return 0f;
        float interArea = (float) inter.width() * inter.height();
        float minArea = Math.max(1f, Math.min((float) a.width() * a.height(), (float) b.width() * b.height()));
        return interArea / minArea;
    }
}

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
    }

    public static void recognize(final Bitmap bitmap, final OcrCallback callback) {
        new Thread(() -> {
            List<TextRecognizer> recognizers = new ArrayList<>();
            try {
                InputImage image = InputImage.fromBitmap(bitmap, 0);
                // Priority order: CJK first so Latin garbage over CJK glyphs gets discarded
                final TextRecognizer ko = TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());
                final TextRecognizer ja = TextRecognition.getClient(new JapaneseTextRecognizerOptions.Builder().build());
                final TextRecognizer zh = TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
                final TextRecognizer la = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
                recognizers.add(ko); recognizers.add(ja); recognizers.add(zh); recognizers.add(la);

                List<Task<Text>> tasks = new ArrayList<>();
                for (TextRecognizer r : recognizers) tasks.add(r.process(image));
                Tasks.await(Tasks.whenAllComplete(tasks));

                List<Block> accepted = new ArrayList<>();
                for (int i = 0; i < tasks.size(); i++) {
                    Task<Text> t = tasks.get(i);
                    if (!t.isSuccessful() || t.getResult() == null) continue;
                    for (Text.TextBlock tb : t.getResult().getTextBlocks()) {
                        String s = tb.getText() == null ? "" : tb.getText().replace('\n', ' ').trim();
                        Rect box = tb.getBoundingBox();
                        if (s.length() < 2 || box == null || !scriptMatches(i, s)) continue;
                        boolean overlaps = false;
                        for (Block a : accepted) {
                            if (overlapRatio(a.box, box) > 0.5f) { overlaps = true; break; }
                        }
                        if (overlaps) continue;
                        Block b = new Block();
                        b.text = s;
                        b.box = box;
                        accepted.add(b);
                    }
                }

                Collections.sort(accepted, (a, b) -> {
                    int rowTol = Math.max(a.box.height(), b.box.height()) / 2;
                    if (Math.abs(a.box.top - b.box.top) > rowTol) return Integer.compare(a.box.top, b.box.top);
                    return Integer.compare(a.box.left, b.box.left);
                });

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

    // index: 0 = Korean, 1 = Japanese, 2 = Chinese, 3 = Latin
    private static boolean scriptMatches(int index, String s) {
        boolean hangul = false, kana = false, han = false, alnum = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            Character.UnicodeBlock b = Character.UnicodeBlock.of(c);
            if (b == Character.UnicodeBlock.HANGUL_SYLLABLES || b == Character.UnicodeBlock.HANGUL_JAMO
                    || b == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO) hangul = true;
            else if (b == Character.UnicodeBlock.HIRAGANA || b == Character.UnicodeBlock.KATAKANA) kana = true;
            else if (b == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) han = true;
            if (Character.isLetterOrDigit(c)) alnum = true;
        }
        switch (index) {
            case 0: return hangul;
            case 1: return kana || (han && !hangul);
            case 2: return han && !hangul && !kana;
            default: return alnum;
        }
    }

    private static float overlapRatio(Rect a, Rect b) {
        Rect inter = new Rect();
        if (!inter.setIntersect(a, b)) return 0f;
        float interArea = (float) inter.width() * inter.height();
        float minArea = Math.max(1f, Math.min((float) a.width() * a.height(), (float) b.width() * b.height()));
        return interArea / minArea;
    }
}

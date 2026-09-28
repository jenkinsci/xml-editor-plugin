package io.jenkins.plugins.xmleditor.core.diff;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Minimal unified diff of two texts, line by line (common prefix/suffix trimmed, LCS on the rest). */
public final class LineDiff {

    /** Above this many LCS cells the changed region is shown as a block removal followed by a block addition. */
    private static final long MAX_LCS_CELLS = 4_000_000L;

    private LineDiff() {}

    private record Op(char type, String text, int oldIndex, int newIndex) {}

    /** @return the unified diff, or an empty string when the texts are equal */
    public static String unified(String before, String after, String name, int context, int maxLines) {
        if (before.equals(after)) {
            return "";
        }
        List<String> a = lines(before);
        List<String> b = lines(after);
        List<Op> ops = diff(a, b);
        List<String> out = new ArrayList<>();
        out.add("--- a/" + name);
        out.add("+++ b/" + name);
        int i = 0;
        while (i < ops.size()) {
            if (ops.get(i).type() == ' ') {
                i++;
                continue;
            }
            int start = Math.max(0, i - context);
            int end = i;
            int j = i;
            while (j < ops.size()) {
                if (ops.get(j).type() != ' ') {
                    end = j++;
                    continue;
                }
                int k = j;
                while (k < ops.size() && ops.get(k).type() == ' ') {
                    k++;
                }
                if (k < ops.size() && k - j <= 2 * context) {
                    j = k;
                } else {
                    break;
                }
            }
            int stop = Math.min(ops.size(), end + context + 1);
            out.add(header(ops.subList(start, stop)));
            for (Op op : ops.subList(start, stop)) {
                out.add(op.type() + op.text());
            }
            i = stop;
        }
        StringBuilder sb = new StringBuilder();
        for (int n = 0; n < out.size() && n < maxLines; n++) {
            sb.append(out.get(n)).append('\n');
        }
        if (out.size() > maxLines) {
            sb.append("... (diff truncated)\n");
        }
        return sb.toString();
    }

    private static String header(List<Op> hunk) {
        int oldCount = 0;
        int newCount = 0;
        for (Op op : hunk) {
            if (op.type() != '+') {
                oldCount++;
            }
            if (op.type() != '-') {
                newCount++;
            }
        }
        Op first = hunk.get(0);
        int oldStart = oldCount == 0 ? first.oldIndex() : first.oldIndex() + 1;
        int newStart = newCount == 0 ? first.newIndex() : first.newIndex() + 1;
        return "@@ -" + oldStart + "," + oldCount + " +" + newStart + "," + newCount + " @@";
    }

    private static List<String> lines(String text) {
        String s = text.replace("\r\n", "\n").replace('\r', '\n');
        if (s.endsWith("\n")) {
            s = s.substring(0, s.length() - 1);
        }
        return Arrays.asList(s.split("\n", -1));
    }

    private static List<Op> diff(List<String> a, List<String> b) {
        int prefix = 0;
        while (prefix < a.size() && prefix < b.size() && a.get(prefix).equals(b.get(prefix))) {
            prefix++;
        }
        int suffix = 0;
        while (suffix < a.size() - prefix
                && suffix < b.size() - prefix
                && a.get(a.size() - 1 - suffix).equals(b.get(b.size() - 1 - suffix))) {
            suffix++;
        }
        List<Op> ops = new ArrayList<>(Math.max(a.size(), b.size()) + 16);
        for (int i = 0; i < prefix; i++) {
            ops.add(new Op(' ', a.get(i), i, i));
        }
        middle(a, b, prefix, a.size() - suffix, prefix, b.size() - suffix, ops);
        for (int i = suffix; i > 0; i--) {
            ops.add(new Op(' ', a.get(a.size() - i), a.size() - i, b.size() - i));
        }
        return ops;
    }

    private static void middle(List<String> a, List<String> b, int a0, int a1, int b0, int b1, List<Op> ops) {
        int n = a1 - a0;
        int m = b1 - b0;
        if ((long) n * m > MAX_LCS_CELLS) {
            for (int i = a0; i < a1; i++) {
                ops.add(new Op('-', a.get(i), i, b0));
            }
            for (int j = b0; j < b1; j++) {
                ops.add(new Op('+', b.get(j), a1, j));
            }
            return;
        }
        int[][] lcs = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                lcs[i][j] = a.get(a0 + i).equals(b.get(b0 + j))
                        ? lcs[i + 1][j + 1] + 1
                        : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }
        int i = 0;
        int j = 0;
        while (i < n || j < m) {
            if (i < n && j < m && a.get(a0 + i).equals(b.get(b0 + j))) {
                ops.add(new Op(' ', a.get(a0 + i), a0 + i, b0 + j));
                i++;
                j++;
            } else if (i < n && (j == m || lcs[i + 1][j] >= lcs[i][j + 1])) {
                ops.add(new Op('-', a.get(a0 + i), a0 + i, b0 + j));
                i++;
            } else {
                ops.add(new Op('+', b.get(b0 + j), a0 + i, b0 + j));
                j++;
            }
        }
    }
}

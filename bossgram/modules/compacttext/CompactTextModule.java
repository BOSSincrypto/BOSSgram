package com.bossgram.modules.compacttext;

import com.bossgram.api.BossContext;
import com.bossgram.api.BossModule;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Native port of compact_text.py core (no host SDK).
 * Long outgoing texts are packed into a file (TXT or minimal DOCX) and sent
 * as a document instead of a huge message bubble. Threshold, format and the
 * ".cpt" force-command match the .py defaults. UI rename dialog from .py is
 * intentionally v1-simplified: auto filename "chatId_date_time.ext".
 */
public class CompactTextModule implements BossModule {

    public static final int FORMAT_TXT = 0;
    public static final int FORMAT_DOCX = 1;
    public static final int FORMAT_CUSTOM = 2;

    private static final int DEFAULT_THRESHOLD = 5000;
    private static final int TELEGRAM_LIMIT = 4096;

    private BossContext ctx;

    @Override public String id() { return "compacttext"; }

    @Override public void onInit(BossContext ctx) {
        this.ctx = ctx;
    }

    public boolean isEnabled() {
        return ctx != null && ctx.prefs("boss_compacttext").getBoolean("enabled", true);
    }

    public void setEnabled(boolean v) {
        if (ctx != null) ctx.prefs("boss_compacttext").edit().putBoolean("enabled", v).apply();
    }

    public int getThreshold() {
        int t = ctx != null ? ctx.prefs("boss_compacttext").getInt("threshold", DEFAULT_THRESHOLD) : DEFAULT_THRESHOLD;
        return Math.max(100, Math.min(500000, t));
    }

    public void setThreshold(int v) {
        if (ctx != null) ctx.prefs("boss_compacttext").edit()
                .putInt("threshold", Math.max(100, Math.min(500000, v))).apply();
    }

    public int getFormat() {
        return ctx != null ? ctx.prefs("boss_compacttext").getInt("format", FORMAT_TXT) : FORMAT_TXT;
    }

    public void setFormat(int v) {
        if (ctx != null) ctx.prefs("boss_compacttext").edit().putInt("format", v).apply();
    }

    public String getCustomExt() {
        String e = ctx != null ? ctx.prefs("boss_compacttext").getString("custom_ext", "txt") : "txt";
        if (e == null) return "txt";
        e = e.trim().replaceFirst("^\\.", "");
        return e.isEmpty() ? "txt" : e;
    }

    public void setCustomExt(String v) {
        if (ctx != null) ctx.prefs("boss_compacttext").edit()
                .putString("custom_ext", v != null ? v : "txt").apply();
    }

    public boolean getCptCommand() {
        return ctx == null || ctx.prefs("boss_compacttext").getBoolean("cpt_command", true);
    }

    public void setCptCommand(boolean v) {
        if (ctx != null) ctx.prefs("boss_compacttext").edit().putBoolean("cpt_command", v).apply();
    }

    /**
     * Called from BossHooks at the start of SendMessagesHelper.sendMessage.
     * Returns true if the message was consumed (packed to file), false to
     * let stock sending continue.
     */
    public boolean interceptSend(int account, long peer, String message) {
        if (!isEnabled() || message == null || message.isEmpty()) return false;
        try {
            String text = message;
            boolean force = false;
            if (getCptCommand()) {
                String stripped = text.replaceFirst("^[\\s\\uFEFF\\u200B]+", "");
                for (String sym : new String[]{"!", ".", "/"}) {
                    String cmd = sym + "cpt";
                    if (stripped.startsWith(cmd)) {
                        String rest = stripped.substring(cmd.length()).replaceFirst("^\\s+", "");
                        if (rest.isEmpty()) return true; // bare command: swallow
                        text = rest;
                        force = true;
                        break;
                    }
                }
            }
            if (!force && text.length() < getThreshold()) return false;
            // Very long plain sends: split by Telegram rules is handled by stock path
            // only when below threshold; above threshold we always pack.
            final String out = text;
            final long toPeer = peer;
            final int acc = account;
            new Thread(() -> packAndSend(acc, toPeer, out)).start();
            return true;
        } catch (Throwable t) {
            t.printStackTrace();
            return false;
        }
    }

    private void packAndSend(int account, long peer, String text) {
        try {
            String ext = resolveExt();
            String name = fileName(peer, ext);
            File dir = new File(ctx.appContext().getExternalCacheDir(), "CompactText");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, name);
            String mime = "text/plain";
            if ("docx".equalsIgnoreCase(ext)) {
                if (writeDocx(f, text)) {
                    mime = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                } else {
                    writeBytes(f, text.getBytes(StandardCharsets.UTF_8));
                }
            } else {
                writeBytes(f, text.getBytes(StandardCharsets.UTF_8));
            }
            String path = f.getAbsolutePath();
            final String finalMime = mime;
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    SendMessagesHelper.prepareSendingDocument(
                            account, path, path, null, null, finalMime,
                            peer, null, null, null, null, null, true, 0, null, null, 0, false);
                } catch (Throwable t) { t.printStackTrace(); }
            });
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private String resolveExt() {
        int fmt = getFormat();
        if (fmt == FORMAT_DOCX) return "docx";
        if (fmt == FORMAT_CUSTOM) return getCustomExt();
        return "txt";
    }

    private String fileName(long peer, String ext) {
        String cid;
        if (peer < 0) {
            cid = String.valueOf(Math.abs(peer));
            if (cid.startsWith("100")) cid = cid.substring(3);
        } else {
            cid = String.valueOf(peer);
        }
        String stamp = new SimpleDateFormat("dd.MM.yyyy_HH.mm.ss", Locale.US).format(new Date());
        return cid + "_" + stamp + "." + ext;
    }

    private void writeBytes(File f, byte[] data) throws Exception {
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(data);
        }
    }

    /** Minimal single-paragraph DOCX (zip with document.xml), no external deps. */
    private boolean writeDocx(File f, String text) {
        try {
            StringBuilder paras = new StringBuilder();
            for (String line : text.split("\n", -1)) {
                paras.append("<w:p><w:r><w:t xml:space=\"preserve\">")
                        .append(esc(line))
                        .append("</w:t></w:r></w:p>");
            }
            String documentXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\""
                    + " xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                    + "<w:body>" + paras
                    + "<w:sectPr><w:pgSz w:w=\"12240\" w:h=\"15840\"/>"
                    + "<w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\"/>"
                    + "</w:sectPr></w:body></w:document>";
            String contentTypes = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                    + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                    + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                    + "<Override PartName=\"/word/document.xml\""
                    + " ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
                    + "</Types>";
            String rels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\""
                    + " Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\""
                    + " Target=\"word/document.xml\"/></Relationships>";
            try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(f))) {
                z.setLevel(9);
                z.putNextEntry(new ZipEntry("[Content_Types].xml"));
                z.write(contentTypes.getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
                z.putNextEntry(new ZipEntry("_rels/.rels"));
                z.write(rels.getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
                z.putNextEntry(new ZipEntry("word/document.xml"));
                z.write(documentXml.getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
            }
            return true;
        } catch (Throwable t) {
            t.printStackTrace();
            return false;
        }
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @SuppressWarnings("unused")
    private byte[] zipBytes(String a, String b, String c) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bos)) {
            z.putNextEntry(new ZipEntry("a.xml"));
            z.write(a.getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
        }
        return bos.toByteArray();
    }

    public int telegramLimit() {
        return TELEGRAM_LIMIT;
    }

    public int defaultThreshold() {
        return DEFAULT_THRESHOLD;
    }

    public int currentAccount() {
        return UserConfig.selectedAccount;
    }
}

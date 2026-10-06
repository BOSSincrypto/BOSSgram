package com.bossgram.modules.antidelete;

import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import com.bossgram.core.BossHooks;
import com.bossgram.api.BossModule;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;

import java.util.HashSet;
import java.util.Set;

/**
 * BossGram settings: AntiDelete toggle / mode / per-chat list / limits / cleanup + Themes stub.
 * Opened via bossgram://settings deep-link (hook 5/5), no upstream UI edits.
 */
public class BossSettingsActivity extends BaseFragment {

    private LinearLayout container;

    @Override
    public boolean onFragmentCreate() {
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        actionBar.setTitle("BossGram");
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        fragmentView = new ScrollView(context);
        ((ScrollView) fragmentView).setFillViewport(true);
        container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        ((ScrollView) fragmentView).addView(container,
                new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        rebuild(context);
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (container != null && getParentActivity() != null) rebuild(getParentActivity());
    }

    private AntiDeleteModule mod() {
        for (BossModule m : BossHooks.modules()) {
            if (m instanceof AntiDeleteModule) return (AntiDeleteModule) m;
        }
        return null;
    }

    private void rebuild(Context context) {
        container.removeAllViews();
        AntiDeleteModule m = mod();
        if (m == null || m.filter() == null) {
            HeaderCell h = new HeaderCell(context);
            h.setText("Модуль анти-удаления не инициализирован");
            container.addView(h, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            return;
        }
        ChatFilter f = m.filter();

        HeaderCell h1 = new HeaderCell(context);
        h1.setText("Анти-удаление");
        container.addView(h1, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextCheckCell enable = new TextCheckCell(context);
        enable.setTextAndCheck("Сохранять удаленные", f.isEnabled(), true);
        enable.setOnClickListener(v -> {
            f.setEnabled(!f.isEnabled());
            enable.setChecked(f.isEnabled());
        });
        container.addView(enable, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextSettingsCell mode = new TextSettingsCell(context);
        mode.setTextAndValue("Режим списка", f.getMode() == ChatFilter.Mode.ALL_EXCEPT ? "Все, кроме…" : "Только…", true);
        mode.setOnClickListener(v -> {
            f.setMode(f.getMode() == ChatFilter.Mode.ALL_EXCEPT ? ChatFilter.Mode.ONLY_LIST : ChatFilter.Mode.ALL_EXCEPT);
            rebuild(context);
        });
        container.addView(mode, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextSettingsCell list = new TextSettingsCell(context);
        list.setTextAndValue("Чаты в списке", String.valueOf(f.getListIds().size()), true);
        list.setOnClickListener(v -> askIds(context, f));
        container.addView(list, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextSettingsCell perChat = new TextSettingsCell(context);
        perChat.setTextAndValue("Лимит на чат", String.valueOf(f.maxPerChat()), true);
        perChat.setOnClickListener(v -> askInt(context, "Лимит на чат", f.maxPerChat(), 10, 5000,
                val -> { f.setMaxPerChat(val); rebuild(context); }));
        container.addView(perChat, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextSettingsCell age = new TextSettingsCell(context);
        age.setTextAndValue("Хранить, дней", String.valueOf(f.maxAgeDays()), true);
        age.setOnClickListener(v -> askInt(context, "Хранить, дней", f.maxAgeDays(), 1, 365,
                val -> { f.setMaxAgeDays(val); rebuild(context); }));
        container.addView(age, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextSettingsCell saved = new TextSettingsCell(context);
        saved.setTextAndValue("Сохраненные", "просмотр и очистка", true);
        saved.setOnClickListener(v -> presentFragment(new BossSavedActivity(), false, true));
        container.addView(saved, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextSettingsCell clearAll = new TextSettingsCell(context);
        clearAll.setText("Очистить всё сохраненное", false);
        clearAll.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
        clearAll.setOnClickListener(v -> confirm(context, "Удалить все сохраненные сообщения?", () -> {
            m.store().clearAll();
            Toast.makeText(context, "Очищено", Toast.LENGTH_SHORT).show();
        }));
        container.addView(clearAll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        HeaderCell h2 = new HeaderCell(context);
        h2.setText("Плагины (аудит пройден, рантайм позже)");
        container.addView(h2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        for (PluginCatalog.Entry e : PluginCatalog.all()) {
            TextSettingsCell row = new TextSettingsCell(context);
            row.setTextAndValue(e.name + " " + e.version, e.status, true);
            container.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        HeaderCell h3 = new HeaderCell(context);
        h3.setText("Темы");
        container.addView(h3, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextSettingsCell palette = new TextSettingsCell(context);
        palette.setTextAndValue("Палитра", "по умолчанию", false);
        palette.setOnClickListener(v ->
                Toast.makeText(context, "Кастомные палитры — следующий шаг", Toast.LENGTH_SHORT).show());
        container.addView(palette, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        addPluginPorts(context);
        addAccountHider(context);
        addFolderSpoiler(context);
        addBossPorts(context);
    }

    private void addToggle(Context context, String title, boolean checked, java.util.function.Consumer<Boolean> onFlip) {
        TextCheckCell row = new TextCheckCell(context);
        row.setTextAndCheck(title, checked, true);
        row.setOnClickListener(v -> {
            boolean nv = !row.isChecked();
            row.setChecked(nv);
            onFlip.accept(nv);
        });
        container.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    private <T extends BossModule> T findModule(Class<T> cls) {
        for (BossModule m : BossHooks.modules()) {
            if (cls.isInstance(m)) return cls.cast(m);
        }
        return null;
    }

    private void addPluginPorts(Context context) {
        HeaderCell h = new HeaderCell(context);
        h.setText("Порты плагинов");
        container.addView(h, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        com.bossgram.modules.adblock.AdBlockModule ad = findModule(com.bossgram.modules.adblock.AdBlockModule.class);
        if (ad != null) addToggle(context, "Блокировка рекламы", ad.isEnabled(), ad::setEnabled);

        com.bossgram.modules.accountlimit.AccountLimitModule lim =
                findModule(com.bossgram.modules.accountlimit.AccountLimitModule.class);
        if (lim != null) addToggle(context, "До 16 аккаунтов", lim.isEnabled(), lim::setEnabled);

        com.bossgram.modules.forumtabs.ForumTabsModule tabs =
                findModule(com.bossgram.modules.forumtabs.ForumTabsModule.class);
        if (tabs != null) addToggle(context, "Вкладки во всех форумах", tabs.isEnabled(), tabs::setEnabled);

        com.bossgram.modules.globalsearch.GlobalSearchModule gs =
                findModule(com.bossgram.modules.globalsearch.GlobalSearchModule.class);
        if (gs != null) addToggle(context, "Глобальный поиск юзеров", gs.isEnabled(), gs::setEnabled);

        com.bossgram.modules.accountage.AccountAgeModule age =
                findModule(com.bossgram.modules.accountage.AccountAgeModule.class);
        if (age != null) {
            addToggle(context, "Проверка возраста аккаунта", age.isEnabled(), age::setEnabled);
            TextSettingsCell check = new TextSettingsCell(context);
            check.setTextAndValue("Проверить @username", "ID, фото, DC, оценка", false);
            check.setOnClickListener(v -> askAgeUsername(context, age));
            container.addView(check, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
    }

    private void askAgeUsername(Context context, com.bossgram.modules.accountage.AccountAgeModule age) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Возраст аккаунта");
        builder.setMessage("Введи @username юзера, группы или канала. Для юзеров — оценка возраста по старейшему фото.");
        EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        int pad = AndroidUtilities.dp(16);
        input.setPadding(pad, pad, pad, pad);
        builder.setView(input);
        builder.setPositiveButton("Проверить", (d, w) -> {
            String q = input.getText().toString().trim();
            if (q.isEmpty()) return;
            Toast.makeText(context, "Запрашиваю…", Toast.LENGTH_SHORT).show();
            age.checkUsername(q, new com.bossgram.modules.accountage.AccountAgeModule.Listener() {
                @Override public void onResult(com.bossgram.modules.accountage.AccountAgeModule.Report r) {
                    showAgeReport(context, r);
                }
                @Override public void onError(String message) {
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                }
            });
        });
        builder.setNegativeButton("Отмена", (d, w) -> {});
        showDialog(builder.create());
    }

    private void showAgeReport(Context context, com.bossgram.modules.accountage.AccountAgeModule.Report r) {
        StringBuilder sb = new StringBuilder();
        sb.append(r.name);
        if (r.isChat) {
            sb.append("\nID группы: ").append(r.chatId);
            if (r.extra != null && !r.extra.isEmpty()) sb.append(" (").append(r.extra).append(")");
        } else {
            sb.append("\nID пользователя: ").append(r.userId);
        }
        if (r.username != null && !r.username.isEmpty()) sb.append("\n@").append(r.username);
        if (!r.isChat) {
            if (r.dcId != 0) sb.append("\nDC: ").append(r.dcId);
            if (r.photosTotal >= 0) sb.append("\nФото: ").append(r.photosTotal);
            if (r.earliestPhotoDate > 0) {
                java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("MM.yyyy", java.util.Locale.US);
                sb.append("\nСтарейшее фото: ").append(f.format(new java.util.Date(r.earliestPhotoDate * 1000)));
                sb.append("\nАккаунту не меньше: ").append(ageSince(r.earliestPhotoDate));
            } else {
                sb.append("\nВозраст: неизвестен (нет фото)");
            }
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(r.isChat ? "Группа/канал" : "Возраст аккаунта");
        builder.setMessage(sb.toString());
        builder.setPositiveButton("OK", (d, w) -> {});
        showDialog(builder.create());
    }

    private String ageSince(long tsSec) {
        long days = Math.max(0, (System.currentTimeMillis() / 1000 - tsSec) / 86400);
        if (days < 30) return days + " дн.";
        if (days < 365) return (days / 30) + " мес.";
        return (days / 365) + " г. " + ((days % 365) / 30) + " мес.";
    }

    private void addAccountHider(Context context) {
        com.bossgram.modules.accounthider.AccountHiderModule m =
                findModule(com.bossgram.modules.accounthider.AccountHiderModule.class);
        if (m == null) return;

        HeaderCell h = new HeaderCell(context);
        h.setText("Скрытые аккаунты");
        container.addView(h, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextCheckCell enable = new TextCheckCell(context);
        enable.setTextAndCheck("Скрывать выбранные", m.isEnabled(), true);
        enable.setOnClickListener(v -> {
            m.setEnabled(!m.isEnabled());
            enable.setChecked(m.isEnabled());
        });
        container.addView(enable, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        java.util.Set<Integer> hidden = m.getHidden();
        for (int a = 0; a < org.telegram.messenger.UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (!org.telegram.messenger.UserConfig.getInstance(a).isClientActivated()) continue;
            org.telegram.tgnet.TLRPC.User u = org.telegram.messenger.UserConfig.getInstance(a).getCurrentUser();
            String name = u != null ? org.telegram.messenger.UserObject.getUserName(u) : ("Аккаунт " + (a + 1));
            if (a == getCurrentAccount()) name += " (текущий)";
            final int acc = a;
            TextCheckCell row = new TextCheckCell(context);
            row.setTextAndCheck(name, hidden.contains(a), true);
            row.setOnClickListener(v -> {
                m.setHidden(acc, !m.getHidden().contains(acc));
                row.setChecked(m.getHidden().contains(acc));
            });
            container.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
    }

    private void addFolderSpoiler(Context context) {
        HeaderCell h = new HeaderCell(context);
        h.setText("Папки и спойлеры");
        container.addView(h, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        for (BossModule m : BossHooks.modules()) {
            if (m instanceof com.bossgram.modules.addtofolder.AddToFolderModule) {
                com.bossgram.modules.addtofolder.AddToFolderModule mod =
                        (com.bossgram.modules.addtofolder.AddToFolderModule) m;
                TextCheckCell row = new TextCheckCell(context);
                row.setTextAndCheck("«В папку» внутри папок", mod.isEnabled(), true);
                row.setOnClickListener(v -> {
                    mod.setEnabled(!mod.isEnabled());
                    row.setChecked(mod.isEnabled());
                });
                container.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            }
            if (m instanceof com.bossgram.modules.antispoiler.AntiSpoilerModule) {
                com.bossgram.modules.antispoiler.AntiSpoilerModule mod =
                        (com.bossgram.modules.antispoiler.AntiSpoilerModule) m;
                TextCheckCell r2 = new TextCheckCell(context);
                r2.setTextAndCheck("Раскрывать спойлеры", mod.isEnabled(), false);
                r2.setOnClickListener(v -> {
                    mod.setEnabled(!mod.isEnabled());
                    r2.setChecked(mod.isEnabled());
                });
                container.addView(r2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            }
        }
    }

    private void addBossPorts(Context context) {
        HeaderCell h = new HeaderCell(context);
        h.setText("Саммари, экспорт, длинные тексты");
        container.addView(h, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        com.bossgram.modules.chatsummary.ChatSummaryModule sum =
                findModule(com.bossgram.modules.chatsummary.ChatSummaryModule.class);
        if (sum != null) {
            addToggle(context, "AI-саммари чата", sum.isEnabled(), sum::setEnabled);
            TextSettingsCell row = new TextSettingsCell(context);
            row.setTextAndValue("Сделать саммари", "50 / 100 / 200 сообщений", true);
            row.setOnClickListener(v -> askSummary(context, sum));
            container.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            String k = sum.getKey();
            String masked = k == null || k.isEmpty() ? "не задан" : "задан (" + k.length() + " симв.)";
            // NOTE: separate row below to avoid overwriting the action above.
            TextSettingsCell prov = new TextSettingsCell(context);
            prov.setTextAndValue("Настройки AI: " + providerName(sum.getProvider()), masked, true);
            prov.setOnClickListener(v -> askSummarySettings(context, sum));
            container.addView(prov, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        com.bossgram.modules.chatexport.ChatExportModule exp =
                findModule(com.bossgram.modules.chatexport.ChatExportModule.class);
        if (exp != null) {
            addToggle(context, "Экспорт чата (HTML/JSON/TXT)", exp.isEnabled(), exp::setEnabled);
            TextSettingsCell row = new TextSettingsCell(context);
            row.setTextAndValue("Экспортировать чат", formatName(exp.getFormat()), true);
            row.setOnClickListener(v -> askExport(context, exp));
            container.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        com.bossgram.modules.compacttext.CompactTextModule ct =
                findModule(com.bossgram.modules.compacttext.CompactTextModule.class);
        if (ct != null) {
            addToggle(context, "Длинные тексты в файл", ct.isEnabled(), ct::setEnabled);
            TextSettingsCell row = new TextSettingsCell(context);
            row.setTextAndValue("Порог символов", String.valueOf(ct.getThreshold()), true);
            row.setOnClickListener(v -> askInt(context, "Порог символов", ct.getThreshold(), 100, 500000,
                    val -> { ct.setThreshold(val); rebuild(context); }));
            container.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        HeaderCell hid = new HeaderCell(context);
        hid.setText("ID пользователей и групп");
        container.addView(hid, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        com.bossgram.modules.showid.ShowIdModule sid =
                findModule(com.bossgram.modules.showid.ShowIdModule.class);
        if (sid != null) {
            addToggle(context, "Показывать ID", sid.isEnabled(), sid::setEnabled);
            try {
                int acc = getCurrentAccount();
                org.telegram.messenger.UserConfig uc = org.telegram.messenger.UserConfig.getInstance(acc);
                if (uc != null && uc.getCurrentUser() != null) {
                    long myId = uc.getCurrentUser().id;
                    TextSettingsCell me = new TextSettingsCell(context);
                    me.setTextAndValue("Мой ID", String.valueOf(myId), false);
                    container.addView(me, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                }
            } catch (Throwable ignore) {}
            TextSettingsCell lookup = new TextSettingsCell(context);
            lookup.setTextAndValue("Узнать ID по @username", "юзер, группа, канал", false);
            lookup.setOnClickListener(v -> askShowId(context, sid));
            container.addView(lookup, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
    }

    private String providerName(int p) {
        if (p == 1) return "Anthropic";
        if (p == 2) return "Gemini";
        if (p == 3) return "Ollama";
        if (p == 4) return "Custom";
        return "OpenAI";
    }

    private String formatName(int f) {
        if (f == 1) return "JSON";
        if (f == 2) return "TXT";
        return "HTML";
    }

    private void askSummary(Context context, com.bossgram.modules.chatsummary.ChatSummaryModule sum) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Саммари чата");
        builder.setMessage("Введи ID диалога (узнать можно в разделе «ID» ниже) и число сообщений.");
        LinearLayout ll = new LinearLayout(context);
        ll.setOrientation(LinearLayout.VERTICAL);
        int pad = AndroidUtilities.dp(16);
        ll.setPadding(pad, pad, pad, pad);
        EditText idInput = new EditText(context);
        idInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        idInput.setHint("ID диалога");
        EditText countInput = new EditText(context);
        countInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        countInput.setText("100");
        countInput.setHint("Сообщений: 50-500");
        ll.addView(idInput);
        ll.addView(countInput);
        builder.setView(ll);
        builder.setPositiveButton("Сделать", (d, w) -> {
            try {
                long dialogId = Long.parseLong(idInput.getText().toString().trim());
                int count = Integer.parseInt(countInput.getText().toString().trim());
                android.widget.Toast.makeText(context, "Анализирую…", android.widget.Toast.LENGTH_SHORT).show();
                sum.summarize(dialogId, count, new com.bossgram.modules.chatsummary.ChatSummaryModule.Listener() {
                    @Override public void onResult(String summary, int used) {
                        AlertDialog.Builder b2 = new AlertDialog.Builder(context);
                        b2.setTitle("Саммари · " + used + " сообщений");
                        b2.setMessage(summary);
                        b2.setPositiveButton("OK", (dd, ww) -> {});
                        b2.setNegativeButton("Копировать", (dd, ww) -> {
                            try {
                                android.content.ClipboardManager cm =
                                        (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                                cm.setPrimaryClip(android.content.ClipData.newPlainText("summary", summary));
                                android.widget.Toast.makeText(context, "Скопировано", android.widget.Toast.LENGTH_SHORT).show();
                            } catch (Throwable ignore) {}
                        });
                        showDialog(b2.create());
                    }
                    @Override public void onError(String message) {
                        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (NumberFormatException ignore) {
                android.widget.Toast.makeText(context, "Проверь ID и число", android.widget.Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Отмена", (d, w) -> {});
        showDialog(builder.create());
    }

    private void askSummarySettings(Context context, com.bossgram.modules.chatsummary.ChatSummaryModule sum) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Настройки AI");
        builder.setMessage("Провайдер: 0 OpenAI, 1 Anthropic, 2 Gemini, 3 Ollama, 4 Custom. Ключ хранится только на устройстве.");
        LinearLayout ll = new LinearLayout(context);
        ll.setOrientation(LinearLayout.VERTICAL);
        int pad = AndroidUtilities.dp(16);
        ll.setPadding(pad, pad, pad, pad);
        EditText prov = new EditText(context);
        prov.setInputType(InputType.TYPE_CLASS_NUMBER);
        prov.setText(String.valueOf(sum.getProvider()));
        prov.setHint("Провайдер 0-4");
        EditText key = new EditText(context);
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setText(sum.getKey());
        key.setHint("Ключ");
        EditText model = new EditText(context);
        model.setInputType(InputType.TYPE_CLASS_TEXT);
        model.setText(sum.getModel());
        model.setHint("Модель (пусто = по умолчанию)");
        ll.addView(prov);
        ll.addView(key);
        ll.addView(model);
        builder.setView(ll);
        builder.setPositiveButton("OK", (d, w) -> {
            try {
                sum.setProvider(Math.max(0, Math.min(4, Integer.parseInt(prov.getText().toString().trim()))));
            } catch (NumberFormatException ignore) {}
            sum.setKey(key.getText().toString().trim());
            sum.setModel(model.getText().toString().trim());
            rebuild(context);
        });
        builder.setNegativeButton("Отмена", (d, w) -> {});
        showDialog(builder.create());
    }

    private void askExport(Context context, com.bossgram.modules.chatexport.ChatExportModule exp) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Экспорт чата");
        builder.setMessage("Введи ID диалога. Формат: 0 HTML, 1 JSON, 2 TXT. Файл сохранится в кэше и предложится отправка.");
        LinearLayout ll = new LinearLayout(context);
        ll.setOrientation(LinearLayout.VERTICAL);
        int pad = AndroidUtilities.dp(16);
        ll.setPadding(pad, pad, pad, pad);
        EditText idInput = new EditText(context);
        idInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        idInput.setHint("ID диалога");
        EditText fmtInput = new EditText(context);
        fmtInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        fmtInput.setText(String.valueOf(exp.getFormat()));
        fmtInput.setHint("Формат 0-2");
        ll.addView(idInput);
        ll.addView(fmtInput);
        builder.setView(ll);
        builder.setPositiveButton("Экспорт", (d, w) -> {
            try {
                long dialogId = Long.parseLong(idInput.getText().toString().trim());
                try {
                    exp.setFormat(Math.max(0, Math.min(2, Integer.parseInt(fmtInput.getText().toString().trim()))));
                } catch (NumberFormatException ignore) {}
                android.widget.Toast.makeText(context, "Экспортирую…", android.widget.Toast.LENGTH_SHORT).show();
                exp.exportChat(dialogId, new com.bossgram.modules.chatexport.ChatExportModule.Listener() {
                    @Override public void onDone(java.io.File file, int count) {
                        AlertDialog.Builder b2 = new AlertDialog.Builder(context);
                        b2.setTitle("Экспорт завершён");
                        b2.setMessage(count + " сообщений:\n" + file.getAbsolutePath());
                        b2.setPositiveButton("OK", (dd, ww) -> {});
                        b2.setNegativeButton("Отправить", (dd, ww) -> {
                            try {
                                android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_SEND);
                                intent.setType("*/*");
                                intent.putExtra(android.content.Intent.EXTRA_STREAM,
                                        androidx.core.content.FileProvider.getUriForFile(context,
                                                context.getPackageName() + ".provider", file));
                                intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                context.startActivity(android.content.Intent.createChooser(intent, "Отправить файл"));
                            } catch (Throwable t) {
                                android.widget.Toast.makeText(context, file.getAbsolutePath(),
                                        android.widget.Toast.LENGTH_LONG).show();
                            }
                        });
                        showDialog(b2.create());
                        rebuild(context);
                    }
                    @Override public void onError(String message) {
                        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (NumberFormatException ignore) {
                android.widget.Toast.makeText(context, "Проверь ID", android.widget.Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Отмена", (d, w) -> {});
        showDialog(builder.create());
    }

    private void askShowId(Context context, com.bossgram.modules.showid.ShowIdModule sid) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("ID по @username");
        builder.setMessage("Введи @username юзера, группы или канала.");
        EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        int pad = AndroidUtilities.dp(16);
        input.setPadding(pad, pad, pad, pad);
        builder.setView(input);
        builder.setPositiveButton("Узнать", (d, w) -> {
            String q = input.getText().toString().trim();
            if (q.isEmpty()) return;
            android.widget.Toast.makeText(context, "Запрашиваю…", android.widget.Toast.LENGTH_SHORT).show();
            sid.lookup(q, new com.bossgram.modules.showid.ShowIdModule.Listener() {
                @Override public void onResult(String title, String lines) {
                    AlertDialog.Builder b2 = new AlertDialog.Builder(context);
                    b2.setTitle(title);
                    b2.setMessage(lines);
                    b2.setPositiveButton("OK", (dd, ww) -> {});
                    b2.setNegativeButton("Копировать", (dd, ww) -> {
                        try {
                            android.content.ClipboardManager cm =
                                    (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("id", lines));
                        } catch (Throwable ignore) {}
                    });
                    showDialog(b2.create());
                }
                @Override public void onError(String message) {
                    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show();
                }
            });
        });
        builder.setNegativeButton("Отмена", (d, w) -> {});
        showDialog(builder.create());
    }

    // ---- dialogs ----

    private void askIds(Context context, ChatFilter f) {
        StringBuilder sb = new StringBuilder();
        for (Long id : f.getListIds()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(id);
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("ID чатов через запятую");
        builder.setMessage("Узнать ID: раздел «ID пользователей и групп» ниже (по @username) или ID текущего диалога. Пусто = очистить список.");
        EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(sb.toString());
        int pad = AndroidUtilities.dp(16);
        input.setPadding(pad, pad, pad, pad);
        builder.setView(input);
        builder.setPositiveButton("OK", (d, w) -> {
            Set<Long> ids = new HashSet<>();
            for (String part : input.getText().toString().split("[,\\s]+")) {
                part = part.trim();
                if (part.isEmpty()) continue;
                try { ids.add(Long.parseLong(part)); } catch (NumberFormatException ignore) {}
            }
            f.setList(ids);
            rebuild(context);
        });
        builder.setNegativeButton("Отмена", (d, w) -> {});
        showDialog(builder.create());
    }

    private interface IntConsumer { void accept(int v); }

    private void askInt(Context context, String title, int current, int min, int max, IntConsumer out) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);
        EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(current));
        int pad = AndroidUtilities.dp(16);
        input.setPadding(pad, pad, pad, pad);
        builder.setView(input);
        builder.setPositiveButton("OK", (d, w) -> {
            try {
                int v = Integer.parseInt(input.getText().toString().trim());
                out.accept(Math.max(min, Math.min(max, v)));
            } catch (NumberFormatException ignore) {}
        });
        builder.setNegativeButton("Отмена", (d, w) -> {});
        showDialog(builder.create());
    }

    private void confirm(Context context, String text, Runnable onYes) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("BossGram");
        builder.setMessage(text);
        builder.setPositiveButton("Да", (d, w) -> onYes.run());
        builder.setNegativeButton("Нет", (d, w) -> {});
        showDialog(builder.create());
    }
}

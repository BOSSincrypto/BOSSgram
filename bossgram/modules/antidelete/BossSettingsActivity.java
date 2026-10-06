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
            check.setTextAndValue("Проверить @username", "фото, DC, оценка", false);
            check.setOnClickListener(v -> askAgeUsername(context, age));
            container.addView(check, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
    }

    private void askAgeUsername(Context context, com.bossgram.modules.accountage.AccountAgeModule age) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Возраст аккаунта");
        builder.setMessage("Введи @username. Оценка — по дате самого старого фото профиля (нижняя граница).");
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
        sb.append(r.name).append("\nID: ").append(r.userId);
        if (r.username != null && !r.username.isEmpty()) sb.append("\n@").append(r.username);
        if (r.dcId != 0) sb.append("\nDC: ").append(r.dcId);
        if (r.photosTotal >= 0) sb.append("\nФото: ").append(r.photosTotal);
        if (r.earliestPhotoDate > 0) {
            java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("MM.yyyy", java.util.Locale.US);
            sb.append("\nСтарейшее фото: ").append(f.format(new java.util.Date(r.earliestPhotoDate * 1000)));
            sb.append("\nАккаунту не меньше: ").append(ageSince(r.earliestPhotoDate));
        } else {
            sb.append("\nВозраст: неизвестен (нет фото)");
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Возраст аккаунта");
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

    // ---- dialogs ----

    private void askIds(Context context, ChatFilter f) {
        StringBuilder sb = new StringBuilder();
        for (Long id : f.getListIds()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(id);
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("ID чатов через запятую");
        builder.setMessage("Узнать ID: перешли сообщение себе, ID виден в debug-меню. Пусто = очистить список.");
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

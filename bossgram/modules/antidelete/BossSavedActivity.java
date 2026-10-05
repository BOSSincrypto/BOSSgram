package com.bossgram.modules.antidelete;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
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
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;

import java.util.Date;
import java.util.List;

/**
 * Viewer + per-chat cleanup for saved deleted messages. v1: plain text list, 50 per chat.
 */
public class BossSavedActivity extends BaseFragment {

    private LinearLayout container;

    @Override
    public boolean onFragmentCreate() {
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        actionBar.setTitle("Сохраненные");
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
        if (m == null || m.store() == null) return;
        AntiDeleteStore store = m.store();

        List<Long> dialogs = store.listDialogs();
        if (dialogs.isEmpty()) {
            HeaderCell h = new HeaderCell(context);
            h.setText("Пока пусто — удаленные сообщения появятся здесь");
            container.addView(h, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            return;
        }

        TextSettingsCell clearAll = new TextSettingsCell(context);
        clearAll.setText("Очистить всё", false);
        clearAll.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
        clearAll.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle("BossGram");
            builder.setMessage("Удалить все сохраненные сообщения?");
            builder.setPositiveButton("Да", (d, w) -> { store.clearAll(); rebuild(context); });
            builder.setNegativeButton("Нет", (d, w) -> {});
            showDialog(builder.create());
        });
        container.addView(clearAll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        for (Long dialogId : dialogs) {
            List<AntiDeleteStore.Entry> entries = store.listEntries(dialogId, 50);
            HeaderCell h = new HeaderCell(context);
            h.setText("Чат " + dialogId + " (" + store.countFor(dialogId) + ")");
            container.addView(h, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            for (AntiDeleteStore.Entry e : entries) {
                TextView tv = new TextView(context);
                tv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                tv.setTextSize(15);
                tv.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(6), AndroidUtilities.dp(16), AndroidUtilities.dp(6));
                String body = e.text == null || e.text.isEmpty() ? "(без текста)" : e.text;
                tv.setText("#" + e.id + " · " + new Date(e.ts).toString() + "\n" + body);
                tv.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                container.addView(tv, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            }

            TextSettingsCell clear = new TextSettingsCell(context);
            clear.setText("Очистить чат " + dialogId, true);
            clear.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
            clear.setOnClickListener(v -> {
                store.clearChat(dialogId);
                Toast.makeText(context, "Чат очищен", Toast.LENGTH_SHORT).show();
                rebuild(context);
            });
            container.addView(clear, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
    }
}

package io.qzxy.lite.preview;

import android.os.Bundle;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import io.qzxy.lite.core.EduEvaluation;
import io.qzxy.lite.core.EduEvaluationForm;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 学生评价的答题页: the school's own questions, answered on native controls.
 *
 * <p>The school renders its form as a fragment of HTML. Instead of showing that fragment in a
 * WebView, {@link EduEvaluationForm} reads it back out of the markup — the same {@code data-*} ids
 * and {@code div.input-xspj} labels its own script reads — and this screen draws the result: a card
 * per 评价模板, one block per 评价项, a row of options per choice.</p>
 *
 * <p>Nothing is pre-filled and nothing is scored by the app. 暂存 and 提交 post exactly the answers
 * on screen; 提交 asks once more first, because the school accepts it only once. A question the
 * parser could not recognise is reported instead of being drawn, and blocks 提交 — a control that
 * silently posts the wrong parameter would be worse than an honest "这一项要在学校页面上填".</p>
 */
public final class EvaluationFormActivity extends Page {
    static final String EXTRA_CLASS = "eval.class";
    static final String EXTRA_COURSE = "eval.course";
    static final String EXTRA_TEACHER_ID = "eval.teacher_id";
    static final String EXTRA_TYPE = "eval.type";
    static final String EXTRA_MODEL = "eval.model";
    static final String EXTRA_GRADE_ENTRY = "eval.grade_entry";
    static final String EXTRA_STATUS = "eval.status";
    static final String EXTRA_TITLE = "eval.title";

    /** A 评语 is a sentence or two; the school's own box is smaller than this. */
    private static final int MAX_TEXT = 300;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private DeviceScreen screen;
    private TextView status;
    private LinearLayout body, actions;
    private int generation;
    private boolean busy;

    private EduEvaluationForm.Form form;
    private List<EduEvaluationForm.Answer> answers = new ArrayList<EduEvaluationForm.Answer>();
    private final List<Item> items = new ArrayList<Item>();
    private final List<Comment> comments = new ArrayList<Comment>();
    /** 已提交: the school no longer accepts changes, so the sheet is shown without its buttons. */
    private boolean readOnly;
    private final android.os.Handler watchdog = new android.os.Handler(android.os.Looper.getMainLooper());

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    /** One 评价项 on screen, with the controls that carry its answer. */
    private static final class Item {
        EduEvaluationForm.Question question;
        EduEvaluationForm.Answer answer;
        final List<TextView> options = new ArrayList<TextView>();
        EditText input;
        void paint() {
            for (int i = 0; i < options.size(); i++) {
                EduEvaluationForm.Option option = question.options.get(i);
                boolean on = question.kind == EduEvaluationForm.MULTI
                    ? answer.picks.contains(option.value) : option.value.equals(answer.pick);
                mark(options.get(i), on);
            }
        }
    }

    /** One 评语 box, kept next to the panel it belongs to. */
    private static final class Comment {
        EditText field;
        String text = "";
    }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        String title = value(EXTRA_TITLE);
        readOnly = "1".equals(value(EXTRA_STATUS));
        screen = new DeviceScreen(this, title.isEmpty() ? "学生评价" : title);
        screen.icon("↻", "重新读取", this::reload);

        status = new TextView(this);
        status.setTextSize(12);
        status.setTextColor(AppUi.MUTED);
        status.setPadding(dp(2), 0, dp(2), dp(10));
        status.setText("正在读取这份评价…");
        screen.root.addView(status);

        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        screen.root.addView(body, new LinearLayout.LayoutParams(-1, -2));

        actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(4), 0, 0);
        screen.root.addView(actions);
        load();
    }

    private String value(String key) {
        String text = getIntent() == null ? null : getIntent().getStringExtra(key);
        return text == null ? "" : text;
    }

    // ---- loading ---------------------------------------------------------------------------

    private void reload() {
        if (busy) return;
        body.removeAllViews();
        actions.removeAllViews();
        items.clear();
        comments.clear();
        load();
    }

    private void load() {
        if (busy) return;
        EduClient.capture();
        busy = true;
        screen.loading(true);
        status.setText("正在读取这份评价…");
        Diag.event(Diag.INFO, "EVAL", "sheet.start", "stage", "read");
        final int ticket = ++generation;
        watchdog.removeCallbacksAndMessages(null);
        watchdog.postDelayed(() -> {
            if (ticket != generation || !busy) return;
            busy = false;
            screen.loading(false);
            status.setText("读取超时了。教学系统可能没响应，请检查网络后点右上角重新读取。");
        }, 45000);
        worker.execute(() -> {
            try {
                EduVault.Credential credential = new EduVault(getApplicationContext()).load();
                EduClient.remember(credential == null ? "" : credential.user,
                    credential == null ? "" : credential.password,
                    credential != null && credential.refused);
                String html = EduClient.evaluationForm(value(EXTRA_CLASS), value(EXTRA_COURSE),
                    value(EXTRA_TEACHER_ID), value(EXTRA_TYPE), value(EXTRA_MODEL),
                    value(EXTRA_GRADE_ENTRY), value(EXTRA_STATUS));
                EduEvaluationForm.Form parsed = EduEvaluationForm.parse(html);
                post(ticket, () -> show(parsed));
            } catch (EduClient.NotSignedIn expired) {
                post(ticket, this::askLogin);
            } catch (EduClient.BadPassword wrong) {
                post(ticket, () -> fail(wrong.getMessage()));
            } catch (EduClient.CaptchaNeeded captcha) {
                post(ticket, () -> fail(captcha.getMessage()));
            } catch (Exception failure) {
                post(ticket, () -> fail(EduClient.describe(failure)));
            }
        });
    }

    private void askLogin() {
        busy = false;
        screen.loading(false);
        status.setText("登录过期了。回到上一页点「网页登录」重新登一次，再进来就能评。");
        body.removeAllViews();
        body.addView(hint("这份评价要先登录教务系统才能读。"));
    }

    private void fail(String message) {
        busy = false;
        screen.loading(false);
        Diag.event(Diag.WARN, "EVAL", "sheet.failed", "stage", "read");
        status.setText(message == null || message.isEmpty() ? "这份评价没读出来。" : message);
        body.removeAllViews();
        body.addView(hint("可以点右上角「重新读取」再试一次。如果提示还没开放，等评教窗口开了再来。"));
    }

    // ---- rendering -------------------------------------------------------------------------

    private void show(EduEvaluationForm.Form parsed) {
        busy = false;
        screen.loading(false);
        form = parsed;
        answers = parsed.answers();
        body.removeAllViews();
        items.clear();
        comments.clear();

        if (parsed.empty()) {
            Diag.event(Diag.WARN, "EVAL", "sheet.empty", "count", 0);
            status.setText("学校这次没有下发评价项。");
            body.addView(hint("这门课可能还没开放评教，或者学校把题目留到了评教窗口内。稍后再来。"));
            return;
        }
        Diag.event(Diag.INFO, "EVAL", "sheet.ready", "count", parsed.count());
        carryOver(parsed);
        status.setText(readOnly
            ? "共 " + parsed.count() + " 个评价项 · 已提交，这里只显示当时的选择"
            : "共 " + parsed.count() + " 个评价项");

        int index = 0;
        for (EduEvaluationForm.Panel panel : parsed.panels) {
            panelCard(panel, index);
            index += panel.count();
        }
        if (!readOnly) buttons();
    }

    /**
     * Takes the answers the school already recorded (a 暂存 sheet, or a submitted one) so the reader
     * sees their own previous choices instead of a blank form.
     */
    private void carryOver(EduEvaluationForm.Form parsed) {
        int index = 0;
        for (EduEvaluationForm.Panel panel : parsed.panels) {
            for (List<EduEvaluationForm.Question> group : panel.groups) {
                for (EduEvaluationForm.Question question : group) {
                    if (index >= answers.size()) return;
                    EduEvaluationForm.Answer answer = answers.get(index++);
                    for (EduEvaluationForm.Option option : question.options) {
                        if (!option.checked) continue;
                        if (question.kind == EduEvaluationForm.MULTI) answer.picks.add(option.value);
                        else answer.pick = option.value;
                    }
                }
            }
        }
    }

    /** One 评价模板: its name, its questions, and its 评语 box. */
    private void panelCard(EduEvaluationForm.Panel panel, int start) {
        LinearLayout card = card();
        TextView name = new TextView(this);
        name.setText(panel.name());
        name.setTextSize(15);
        name.setTypeface(null, Typeface.BOLD);
        name.setTextColor(AppUi.INK);
        card.addView(name);

        TextView count = new TextView(this);
        count.setText(panel.count() + " 个评价项");
        count.setTextSize(11);
        count.setTextColor(AppUi.MUTED);
        count.setPadding(0, dp(2), 0, dp(2));
        card.addView(count);

        int index = start;
        for (List<EduEvaluationForm.Question> group : panel.groups) {
            for (EduEvaluationForm.Question question : group) {
                EduEvaluationForm.Answer answer = index < answers.size()
                    ? answers.get(index) : new EduEvaluationForm.Answer();
                card.addView(questionBlock(question, answer));
                index++;
            }
        }
        if (panel.comment) card.addView(commentBlock());
        addCard(card);
    }

    /** One question: what is asked, then whichever control its shape calls for. */
    private LinearLayout questionBlock(EduEvaluationForm.Question question, EduEvaluationForm.Answer answer) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(0, dp(12), 0, 0);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.TOP);
        TextView title = new TextView(this);
        title.setText(question.title.isEmpty() ? "评价项" : question.title);
        title.setTextSize(14);
        title.setTextColor(AppUi.INK);
        title.setLineSpacing(dp(4), 1);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        if (question.required) {
            TextView star = new TextView(this);
            star.setText("*");
            star.setTextSize(13);
            star.setTextColor(AppUi.DANGER);
            star.setPadding(dp(4), 0, 0, 0);
            star.setContentDescription("必填");
            titleRow.addView(star);
        }
        block.addView(titleRow);

        Item item = new Item();
        item.question = question;
        item.answer = answer;

        if (question.kind == EduEvaluationForm.CHOICE || question.kind == EduEvaluationForm.MULTI) {
            for (EduEvaluationForm.Option option : question.options) {
                TextView row = optionRow(option.label);
                row.setContentDescription(option.label);
                row.setOnClickListener(v -> {
                    if (readOnly || busy) return;
                    if (question.kind == EduEvaluationForm.MULTI) answer.toggle(option.value);
                    else answer.pick = option.value;
                    item.paint();
                });
                item.options.add(row);
                block.addView(row);
            }
            item.paint();
        } else if (question.kind == EduEvaluationForm.TEXT) {
            EditText input = new EditText(this);
            boolean sentence = EduEvaluationForm.FIELD_TEXT.equals(question.field);
            input.setText(answer.text);
            input.setTextSize(14);
            input.setTextColor(AppUi.INK);
            input.setHint(sentence ? "填写你的评价" : "填写分数");
            input.setHintTextColor(AppUi.MUTED);
            input.setInputType(sentence
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                : InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            if (sentence) input.setMinLines(2);
            input.setEnabled(!readOnly && !question.readOnly);
            input.setBackground(AppUi.outlined(this, AppUi.SURFACE, 10, AppUi.LINE));
            input.setPadding(dp(10), dp(8), dp(10), dp(8));
            input.addTextChangedListener(new Watcher(answer, null));
            item.input = input;
            block.addView(input, new LinearLayout.LayoutParams(-1, -2));
        } else {
            TextView note = new TextView(this);
            note.setText("这一项是学校提供的特殊题型，本页还不能代替，请到教务网页里完成。");
            note.setTextSize(12);
            note.setTextColor(AppUi.DANGER);
            note.setPadding(0, dp(6), 0, 0);
            block.addView(note);
        }
        items.add(item);
        return block;
    }

    private TextView optionRow(String label) {
        TextView row = new TextView(this);
        row.setText(label);
        row.setTextSize(14);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinHeight(dp(42));
        row.setPadding(dp(12), dp(8), dp(12), dp(8));
        mark(row, false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(6);
        row.setLayoutParams(params);
        return row;
    }

    /** Selected = accent border, accent bold text; unselected = the plain card outline. */
    private static void mark(TextView view, boolean on) {
        view.setTextColor(on ? AppUi.ACCENT : AppUi.INK);
        view.setTypeface(null, on ? Typeface.BOLD : Typeface.NORMAL);
        view.setBackground(on
            ? AppUi.outlined(view.getContext(), AppUi.SOFT, 10, AppUi.ACCENT)
            : AppUi.outlined(view.getContext(), AppUi.SURFACE, 10, AppUi.LINE));
    }

    /** The 评语 box, only when the school's template ships one. */
    private LinearLayout commentBlock() {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(0, dp(12), 0, 0);

        TextView title = new TextView(this);
        title.setText("评语");
        title.setTextSize(14);
        title.setTextColor(AppUi.INK);
        block.addView(title);

        TextView note = new TextView(this);
        note.setText("学校没有在这里说明字数要求，留空也可以提交。");
        note.setTextSize(11);
        note.setTextColor(AppUi.MUTED);
        note.setPadding(0, dp(2), 0, dp(4));
        block.addView(note);

        EditText input = new EditText(this);
        input.setTextSize(14);
        input.setTextColor(AppUi.INK);
        input.setHint("想对这门课说的话");
        input.setHintTextColor(AppUi.MUTED);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(2);
        input.setEnabled(!readOnly);
        input.setBackground(AppUi.outlined(this, AppUi.SURFACE, 10, AppUi.LINE));
        input.setPadding(dp(10), dp(8), dp(10), dp(8));
        Comment comment = new Comment();
        comment.field = input;
        input.addTextChangedListener(new Watcher(null, comment));
        comments.add(comment);
        block.addView(input, new LinearLayout.LayoutParams(-1, -2));
        return block;
    }

    private void buttons() {
        actions.removeAllViews();
        TextView save = button("暂存", false);
        save.setContentDescription("暂存这份评价，之后可以继续评");
        save.setOnClickListener(v -> write(false));
        actions.addView(save, new LinearLayout.LayoutParams(0, -2, 1));

        TextView send = button("提交", true);
        send.setContentDescription("提交这份评价，提交后不能再改");
        send.setOnClickListener(v -> write(true));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        params.leftMargin = dp(10);
        actions.addView(send, params);
    }

    private TextView button(String label, boolean primary) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextSize(15);
        view.setTypeface(null, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(dp(48));
        if (primary) {
            view.setTextColor(AppUi.ON_ACCENT);
            view.setBackground(AppUi.shape(this, AppUi.ACCENT, 12));
            AppUi.press(view, AppUi.ACCENT, 12);
        } else {
            view.setTextColor(AppUi.ACCENT);
            view.setBackground(AppUi.outlined(this, AppUi.SURFACE, 12, AppUi.ACCENT));
            AppUi.press(view, 0x00ffffff, 12);
        }
        return view;
    }

    // ---- writing ---------------------------------------------------------------------------

    /**
     * 暂存 or 提交. 提交 first checks that every 必填项 is answered and that no question was left
     * unsupported, then asks once more — the school accepts a submission only once, so a mis-tap
     * here would cost the reader a course.
     */
    private void write(boolean submit) {
        if (busy || form == null) return;
        if (submit) {
            String blocked = blockers();
            if (!blocked.isEmpty()) {
                status.setText(blocked);
                return;
            }
            int missing = missing();
            if (missing > 0) {
                status.setText("还有 " + missing + " 个必填项没选，选完再提交。");
                return;
            }
        }
        collect();
        if (submit) confirm(); else send(false);
    }

    /** Why 提交 cannot go through at all: an unsupported question, or no answer anywhere. */
    private String blockers() {
        boolean any = false;
        for (Item item : items) {
            if (!item.question.supported()) {
                Diag.event(Diag.WARN, "EVAL", "sheet.unsupported", "count", items.size());
                return "这份表单里有本页还不支持的题型，请到教务网页里完成这一次评价。";
            }
            if (item.question.answered(item.answer)) any = true;
        }
        return any ? "" : "一个选项都还没选。";
    }

    private int missing() {
        int count = 0;
        for (Item item : items) {
            if (item.question.required && !item.question.answered(item.answer)) count++;
        }
        return count;
    }

    private void confirm() {
        int answered = 0;
        for (Item item : items) if (item.question.answered(item.answer)) answered++;
        Theme.dialog(this)
            .setTitle("确认提交这份评价？")
            .setMessage("共 " + items.size() + " 个评价项，已选 " + answered + " 个。\n\n"
                + "提交后学校不再接收修改，这门课会记为已评。")
            .setNegativeButton("再看看", null)
            .setPositiveButton("确认提交", (dialog, which) -> send(true))
            .show();
    }

    /** Copies whatever is typed in the free-text fields into the answers before building a body. */
    private void collect() {
        for (Item item : items) {
            if (item.input != null) item.answer.text = item.input.getText().toString();
        }
        for (Comment comment : comments) comment.text = comment.field.getText().toString();
    }

    private void send(boolean submit) {
        if (busy || form == null) return;
        collect();
        List<String> notes = new ArrayList<String>();
        for (Comment comment : comments) notes.add(comment.text);
        final String payload = EduEvaluationForm.body(form, answers, notes, submit);
        busy = true;
        screen.loading(true);
        status.setText(submit ? "正在提交…" : "正在暂存…");
        setButtonsEnabled(false);
        final int ticket = generation;
        final String classId = form.classId, teacherId = form.teacherId;
        worker.execute(() -> {
            int outcome = EduEvaluationForm.UNCLEAR;
            String words = "";
            String trouble = null;
            try {
                String reply = EduClient.evaluationWrite(payload, submit);
                outcome = EduEvaluationForm.outcome(reply);
                words = EduEvaluationForm.note(reply);
            } catch (EduClient.NotSignedIn expired) {
                post(ticket, () -> {
                    setButtonsEnabled(true);
                    askLogin();
                });
                return;
            } catch (IOException failure) {
                // 超时、断网、HTTP 非 200：这次到底写没写进去，只有学校知道。先去查状态，
                // 绝不重发——重新提交一次评价的代价比多等一步大得多。
                trouble = EduClient.describe(failure);
                outcome = EduEvaluationForm.UNCLEAR;
            }

            // 无论学校回了什么，都以它记录里的状态为准。查不到就是「还不能确认」，不猜。
            int state = EduEvaluation.UNKNOWN;
            try {
                state = EduClient.evaluationState(classId, teacherId);
            } catch (IOException unreadable) {
                if (trouble == null) trouble = EduClient.describe(unreadable);
            }
            if (submit) {
                if (state == EduEvaluation.SUBMITTED) outcome = EduEvaluationForm.OK;
                else if (outcome == EduEvaluationForm.OK) outcome = EduEvaluationForm.UNCLEAR;
            } else if (state == EduEvaluation.SAVED || state == EduEvaluation.SUBMITTED) {
                outcome = EduEvaluationForm.OK;
            }
            final int finalOutcome = outcome;
            final int finalState = state;
            final String finalWords = words;
            final String finalTrouble = trouble;
            post(ticket, () -> done(submit, finalOutcome, finalWords, finalState, finalTrouble));
        });
    }

    private void done(boolean submit, int outcome, String words, int state, String trouble) {
        busy = false;
        screen.loading(false);
        setButtonsEnabled(true);
        Diag.event(outcome == EduEvaluationForm.OK ? Diag.INFO : Diag.WARN, "EVAL",
            outcome == EduEvaluationForm.OK
                ? (submit ? "write.sent" : "write.saved")
                : (submit || state != EduEvaluation.UNKNOWN ? "write.unverified" : "write.failed"),
            "result", Integer.toString(outcome), "count", items.size());
        if (outcome == EduEvaluationForm.OK) {
            if (submit) {
                status.setText("已提交，学校已记为已评。");
                // The list behind this screen re-reads on resume; closing here is what makes the
                // 已提交 state visible right away instead of after a manual refresh.
                watchdog.postDelayed(this::finish, 900);
            } else {
                status.setText("已暂存，可以继续评或直接提交。");
            }
            return;
        }
        if (outcome == EduEvaluationForm.FAILED) {
            status.setText("学校没有接受：" + (words.isEmpty() ? "请检查必填项后再试。" : words));
            return;
        }
        if (state == EduEvaluation.SAVED && submit) {
            status.setText("学校收了，但这份评价仍是暂存状态、没有记成已评。"
                + (words.isEmpty() ? "" : "学校说：" + words));
            return;
        }
        if (!words.isEmpty()) {
            status.setText("学校回了这句话，但状态还没变更：" + words);
            return;
        }
        status.setText(trouble != null
            ? "这次没能完成：" + trouble + "。已去学校查过状态，没有重复提交，可以稍后再看。"
            : "学校没有回话，状态也还没变更。稍后再看一次，不要重复提交。");
    }

    private void setButtonsEnabled(boolean enabled) {
        for (int i = 0; i < actions.getChildCount(); i++) {
            View child = actions.getChildAt(i);
            child.setEnabled(enabled);
            child.setAlpha(enabled ? 1f : 0.5f);
        }
    }

    // ---- plumbing --------------------------------------------------------------------------

    /** Feeds an EditText's changes into the answer or the comment it belongs to. */
    private static final class Watcher implements android.text.TextWatcher {
        private final EduEvaluationForm.Answer answer;
        private final Comment comment;
        Watcher(EduEvaluationForm.Answer answer, Comment comment) {
            this.answer = answer;
            this.comment = comment;
        }
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
        @Override public void afterTextChanged(Editable text) {
            String value = text == null ? "" : text.toString();
            if (value.length() > MAX_TEXT) value = value.substring(0, MAX_TEXT);
            if (answer != null) answer.text = value;
            if (comment != null) comment.text = value;
        }
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(AppUi.outlined(this, AppUi.SURFACE, 12, AppUi.LINE));
        return card;
    }

    private void addCard(LinearLayout card) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(10);
        body.addView(card, params);
    }

    private TextView hint(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(13);
        view.setTextColor(AppUi.MUTED);
        view.setPadding(0, dp(16), 0, dp(16));
        return view;
    }

    private void post(int ticket, Runnable task) {
        runOnUiThread(() -> { if (!isDestroyed() && !isFinishing() && ticket == generation) task.run(); });
    }

    @Override protected void onStop() { super.onStop(); EduClient.release(); }

    @Override protected void onDestroy() { generation++; worker.shutdownNow(); super.onDestroy(); }
}

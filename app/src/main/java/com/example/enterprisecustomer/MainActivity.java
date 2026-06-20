package com.example.enterprisecustomer;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ClipboardManager;
import android.content.SharedPreferences;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.CallLog;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final int REQ_IMPORT_XLSX = 1001;
    private static final int REQ_EXPORT_XLSX = 1002;
    private static final int REQ_CONTACT_PERM = 2001;
    private static final int REQ_CALL_LOG_PERM = 2002;
    private static final int REQ_BACKUP_EXPORT = 3001;
    private static final int REQ_BACKUP_RESTORE = 3002;

    private CustomerDbHelper db;
    private LinearLayout root;
    private LinearLayout content;
    private LinearLayout nav;
    private int currentTab = 0;
    private String currentSearch = "";
    private String currentStatus = "";
    private long currentGroupId = 0;
    private static final int PAGE_SIZE = 100;
    private int customerVisibleLimit = PAGE_SIZE;
    private int contactVisibleLimit = PAGE_SIZE;
    private List<String> pendingExportHeaders;
    private List<List<String>> pendingExportRows;
    private List<CustomerDbHelper.ContactItem> pendingImportContacts;
    private List<Map<String, String>> pendingImportRows;
    private Uri pendingImportUri;
    private long pendingImportGroupId;
    private boolean pendingImportUseExcelGroup;
    private String pendingImportDefaultStatus;
    private String pendingImportFileName;

    private final String[] tabs = {"首页", "客户", "通讯录", "导入导出", "我的"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        autoBackupBeforeV13Open();
        db = new CustomerDbHelper(this);
        buildRoot();
        render();
        handleIncomingFile(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIncomingFile(intent);
    }

    private void buildRoot() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(245, 247, 251));
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(6), dp(6), dp(6), dp(6));
        nav.setBackgroundColor(Color.WHITE);
        root.addView(nav, new LinearLayout.LayoutParams(-1, dp(64)));
        setContentView(root);
    }

    private void render() {
        content.removeAllViews();
        nav.removeAllViews();
        for (int i = 0; i < tabs.length; i++) {
            final int idx = i;
            TextView t = new TextView(this);
            t.setText(tabs[i]);
            t.setGravity(Gravity.CENTER);
            t.setTextSize(14);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            t.setTextColor(i == currentTab ? Color.WHITE : Color.rgb(38, 64, 105));
            t.setBackground(round(i == currentTab ? Color.rgb(30, 94, 255) : Color.TRANSPARENT, dp(14), 0));
            t.setOnClickListener(v -> { currentTab = idx; render(); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1);
            lp.setMargins(dp(3), 0, dp(3), 0);
            nav.addView(t, lp);
        }
        if (currentTab == 0) renderHome();
        else if (currentTab == 1) renderCustomers();
        else if (currentTab == 2) renderContactSync();
        else if (currentTab == 3) renderImportExport();
        else renderSettings();
    }

    private void renderHome() {
        ScrollView scroll = scroll();
        LinearLayout box = vertical(dp(16));
        scroll.addView(box);
        box.addView(header("企业客户管家", "企业客户管理、Excel导入导出与手机通讯录同步"));
        CustomerDbHelper.Stats s = db.getStats();
        LinearLayout grid1 = horizontal(0);
        box.addView(grid1);
        grid1.addView(statCard("总企业", s.companyCount + "", "家公司"), weightLp());
        grid1.addView(statCard("总联系人", s.contactCount + "", "个电话"), weightLp());
        LinearLayout grid2 = horizontal(0);
        box.addView(grid2);
        grid2.addView(statCard("未设置", s.statusNone + "", "待判断"), weightLp());
        grid2.addView(statCard("已导入", s.importedCount + "", "通讯录"), weightLp());
        LinearLayout grid3 = horizontal(0);
        box.addView(grid3);
        grid3.addView(statCard("关注", s.focusCount + "", "客户"), weightLp());
        grid3.addView(statCard("跟进", s.followCount + "", "客户"), weightLp());
        grid3.addView(statCard("潜力", s.importantCount + "", "客户"), weightLp());
        LinearLayout grid4 = horizontal(0);
        box.addView(grid4);
        grid4.addView(statCard("今日待跟进", s.todayFollowCount + "", "客户"), weightLp());
        grid4.addView(statCard("逾期未跟进", s.overdueFollowCount + "", "客户"), weightLp());
        grid4.addView(statCard("未入库号码", s.callPendingCount + "", "条"), weightLp());

        box.addView(sectionTitle("快捷操作"));
        LinearLayout ops1 = horizontal(0);
        box.addView(ops1);
        ops1.addView(primaryButton("导入 Excel", v -> openImportFile()), weightLp());
        ops1.addView(primaryButton("新增企业", v -> showCompanyEditor(0)), weightLp());
        LinearLayout ops2 = horizontal(0);
        box.addView(ops2);
        ops2.addView(primaryButton("批量导入通讯录", v -> showBulkImportDialog()), weightLp());
        ops2.addView(primaryButton("导出 Excel", v -> showExportDialog()), weightLp());
        LinearLayout ops3 = horizontal(0);
        box.addView(ops3);
        ops3.addView(primaryButton("快速录入", v -> showQuickPasteDialog()), weightLp());
        ops3.addView(primaryButton("通话记录匹配", v -> showCallLogPage()), weightLp());
        LinearLayout ops4 = horizontal(0);
        box.addView(ops4);
        ops4.addView(primaryButton("潜力客户", v -> showPotentialCustomerPage()), weightLp());
        ops4.addView(primaryButton("今日待跟进", v -> showFollowTaskPage("today")), weightLp());
        LinearLayout ops5 = horizontal(0);
        box.addView(ops5);
        ops5.addView(primaryButton("数据备份", v -> showBackupDialog()), weightLp());
        ops5.addView(primaryButton("逾期跟进", v -> showFollowTaskPage("overdue")), weightLp());

        box.addView(sectionTitle("客户状态入口"));
        LinearLayout status = horizontal(0);
        box.addView(status);
        status.addView(statusButton("未设置", CustomerDbHelper.STATUS_NONE), weightLp());
        status.addView(statusButton("关注", CustomerDbHelper.STATUS_FOCUS), weightLp());
        status.addView(statusButton("跟进", CustomerDbHelper.STATUS_FOLLOW), weightLp());
        status.addView(statusButton("潜力", CustomerDbHelper.STATUS_IMPORTANT), weightLp());
        content.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
    }

    private Button statusButton(String label, String status) {
        Button b = smallButton(label, v -> { currentTab = 1; currentStatus = status; render(); });
        return b;
    }

    private void renderCustomers() {
        LinearLayout wrapper = vertical(0);
        wrapper.setPadding(dp(12), dp(12), dp(12), 0);
        wrapper.addView(titleRow("客户", "分页加载与缓存统计，适配大客户池"));
        EditText search = new EditText(this);
        search.setHint("输入公司、联系人、电话、区域、标签搜索");
        search.setSingleLine(true);
        search.setText(currentSearch);
        search.setTextSize(14);
        search.setBackground(round(Color.WHITE, dp(12), Color.rgb(220, 226, 236)));
        search.setPadding(dp(12), 0, dp(12), 0);
        wrapper.addView(search, new LinearLayout.LayoutParams(-1, dp(44)));
        LinearLayout searchOps = horizontal(0);
        wrapper.addView(searchOps);
        searchOps.addView(smallButton("搜索", v -> { currentSearch = search.getText().toString(); customerVisibleLimit = PAGE_SIZE; render(); }), weightLp());
        searchOps.addView(smallButton("清空", v -> { currentSearch = ""; currentStatus = ""; currentGroupId = 0; customerVisibleLimit = PAGE_SIZE; render(); }), weightLp());
        searchOps.addView(smallButton("新增企业", v -> showCompanyEditor(0)), weightLp());
        LinearLayout batchOps = horizontal(0);
        wrapper.addView(batchOps);
        batchOps.addView(smallButton("搜索结果入组", v -> showBatchGroupDialog()), weightLp());
        batchOps.addView(smallButton("搜索结果导入通讯录", v -> importCurrentSearchContacts()), weightLp());
        batchOps.addView(smallButton("快速录入", v -> showQuickPasteDialog()), weightLp());
        wrapper.addView(chipBar());
        ScrollView listScroll = scroll();
        LinearLayout list = vertical(dp(8));
        listScroll.addView(list);
        List<CustomerDbHelper.CompanyItem> companies = db.getCompaniesPage(currentSearch, currentStatus, currentGroupId, customerVisibleLimit, 0);
        if (companies.isEmpty()) list.addView(emptyBox("暂无客户数据", "可先导入 Excel 或手动新增企业"));
        for (CustomerDbHelper.CompanyItem c : companies) list.addView(companyCard(c));
        if (companies.size() >= customerVisibleLimit) {
            list.addView(primaryButton("加载更多（当前显示 " + companies.size() + " 条）", v -> { customerVisibleLimit += PAGE_SIZE; render(); }));
        }
        wrapper.addView(listScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        content.addView(wrapper, new LinearLayout.LayoutParams(-1, -1));
    }

    private void renderCustomersOnly() { render(); }

    private View chipBar() {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        LinearLayout bar = horizontal(0);
        hsv.addView(bar);
        bar.addView(chip("全部", currentStatus.equals(""), v -> { currentStatus = ""; customerVisibleLimit = PAGE_SIZE; render(); }));
        bar.addView(chip("未设置", CustomerDbHelper.STATUS_NONE.equals(currentStatus), v -> { currentStatus = CustomerDbHelper.STATUS_NONE; customerVisibleLimit = PAGE_SIZE; render(); }));
        bar.addView(chip("关注", CustomerDbHelper.STATUS_FOCUS.equals(currentStatus), v -> { currentStatus = CustomerDbHelper.STATUS_FOCUS; customerVisibleLimit = PAGE_SIZE; render(); }));
        bar.addView(chip("跟进", CustomerDbHelper.STATUS_FOLLOW.equals(currentStatus), v -> { currentStatus = CustomerDbHelper.STATUS_FOLLOW; customerVisibleLimit = PAGE_SIZE; render(); }));
        bar.addView(chip("潜力", CustomerDbHelper.STATUS_IMPORTANT.equals(currentStatus), v -> { currentStatus = CustomerDbHelper.STATUS_IMPORTANT; customerVisibleLimit = PAGE_SIZE; render(); }));
        bar.addView(chip("全部分组", currentGroupId == 0, v -> { currentGroupId = 0; customerVisibleLimit = PAGE_SIZE; render(); }));
        for (CustomerDbHelper.GroupItem g : db.getGroups()) {
            bar.addView(chip(g.name, currentGroupId == g.id, v -> { currentGroupId = g.id; customerVisibleLimit = PAGE_SIZE; render(); }));
        }
        hsv.setPadding(0, dp(8), 0, dp(8));
        return hsv;
    }

    private View companyCard(CustomerDbHelper.CompanyItem c) {
        LinearLayout card = card();
        LinearLayout top = horizontal(0);
        TextView title = text(String.format(Locale.CHINA, "%03d  %s", c.seq, c.name), 16, Color.rgb(22, 38, 64), true);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        top.addView(label(c.statusText()));
        card.addView(top);
        card.addView(text(c.groupName + "｜" + nvl(c.region, "未填区域") + "｜" + nvl(c.industry, "未填行业"), 13, Color.rgb(90, 103, 125), false));
        card.addView(text("联系人 " + c.contactCount + " ｜备注 " + c.noteCount + " ｜通讯录 " + c.importText() + " ｜最高号码 " + CustomerDbHelper.starText(db.maxStarForCompany(c.id)), 13, Color.rgb(90, 103, 125), false));
        if (!CustomerDbHelper.empty(c.tags)) card.addView(text("标签：" + c.tags, 13, Color.rgb(90, 103, 125), false));
        LinearLayout ops = horizontal(0);
        card.addView(ops);
        ops.addView(smallButton("详情", v -> showCompanyDetail(c.id)), weightLp());
        ops.addView(smallButton("导入", v -> importCompanyContacts(c.id)), weightLp());
        ops.addView(smallButton("编辑", v -> showCompanyEditor(c.id)), weightLp());
        return card;
    }

    private void showCompanyDetail(long companyId) {
        CustomerDbHelper.CompanyItem c = db.getCompany(companyId);
        ScrollView scroll = scroll();
        LinearLayout box = vertical(dp(10));
        box.setPadding(dp(8), dp(8), dp(8), dp(8));
        scroll.addView(box);
        box.addView(text(c.name, 20, Color.rgb(22, 38, 64), true));
        box.addView(text("分组：" + c.groupName + "    序号：" + c.seq, 14, Color.rgb(90, 103, 125), false));
        box.addView(text("状态：" + c.statusText() + "    通讯录：" + c.importText(), 14, Color.rgb(90, 103, 125), false));
        box.addView(text("行业：" + nvl(c.industry, "未填写"), 14, Color.rgb(90, 103, 125), false));
        box.addView(text("参保人数：" + nvl(c.employeeCount, "未填写"), 14, Color.rgb(90, 103, 125), false));
        box.addView(text("区域：" + nvl(c.region, "未填写"), 14, Color.rgb(90, 103, 125), false));
        box.addView(text("地址：" + nvl(c.address, "未填写"), 14, Color.rgb(90, 103, 125), false));
        if (!CustomerDbHelper.empty(c.tags)) box.addView(text("标签：" + c.tags, 14, Color.rgb(90, 103, 125), false));
        if (!CustomerDbHelper.empty(c.extraInfo)) box.addView(text("扩展信息：" + c.extraInfo, 14, Color.rgb(90, 103, 125), false));
        box.addView(sectionTitle("客户状态"));
        LinearLayout stat = horizontal(0);
        box.addView(stat);
        stat.addView(smallButton("未设置", v -> { db.setCompanyStatus(c.id, ""); showCompanyDetail(c.id); }), weightLp());
        stat.addView(smallButton("关注", v -> { db.setCompanyStatus(c.id, CustomerDbHelper.STATUS_FOCUS); showCompanyDetail(c.id); }), weightLp());
        stat.addView(smallButton("跟进", v -> { db.setCompanyStatus(c.id, CustomerDbHelper.STATUS_FOLLOW); showCompanyDetail(c.id); }), weightLp());
        stat.addView(smallButton("潜力", v -> { db.setCompanyStatus(c.id, CustomerDbHelper.STATUS_IMPORTANT); showCompanyDetail(c.id); }), weightLp());
        box.addView(sectionTitle("联系人"));
        List<CustomerDbHelper.ContactItem> contacts = db.getContacts(c.id, null);
        if (contacts.isEmpty()) box.addView(text("暂无联系人", 14, Color.GRAY, false));
        for (CustomerDbHelper.ContactItem ct : contacts) {
            LinearLayout item = vertical(dp(3));
            item.setPadding(dp(8), dp(6), dp(8), dp(6));
            item.setBackground(round(Color.rgb(248, 250, 253), dp(10), Color.rgb(225, 230, 238)));
            item.addView(text((CustomerDbHelper.empty(ct.contactName) ? "联系人" + ct.order : ct.contactName) + "  " + ct.phone + "  " + CustomerDbHelper.starText(ct.starLevel) + "  " + (ct.imported ? "已导入" : "未导入"), 14, Color.rgb(42, 58, 86), true));
            LinearLayout co = horizontal(0); item.addView(co);
            co.addView(smallButton("拨号", v -> dial(ct.phone)), weightLp());
            co.addView(smallButton("导入", v -> importContactsWithPreview(Arrays.asList(ct))), weightLp());
            co.addView(smallButton("星级", v -> showStarDialog(ct, c.id)), weightLp());
            co.addView(smallButton("删除", v -> confirm("删除联系人", "只删除App内联系人，不会删除手机通讯录。", () -> { db.deleteContact(ct.id); showCompanyDetail(c.id); })), weightLp());
            box.addView(item);
        }
        box.addView(smallButton("新增联系人", v -> showAddContactDialog(c.id)));
        box.addView(sectionTitle("备注记录"));
        List<CustomerDbHelper.NoteItem> notes = db.getNotes(c.id);
        if (notes.isEmpty()) box.addView(text("暂无备注", 14, Color.GRAY, false));
        for (CustomerDbHelper.NoteItem n : notes) {
            TextView nt = text(n.createdAt + "\n" + n.content, 14, Color.rgb(42, 58, 86), false);
            nt.setPadding(dp(8), dp(6), dp(8), dp(6));
            nt.setBackground(round(Color.rgb(248, 250, 253), dp(10), Color.rgb(225, 230, 238)));
            nt.setOnLongClickListener(v -> { confirm("删除备注", "确认删除这条备注？", () -> { db.deleteNote(n.id); showCompanyDetail(c.id); }); return true; });
            box.addView(nt);
        }
        box.addView(smallButton("新增备注", v -> showAddNoteDialog(c.id)));
        box.addView(sectionTitle("跟进记录"));
        List<CustomerDbHelper.FollowRecordItem> follows = db.getFollowRecords(c.id);
        if (follows.isEmpty()) box.addView(text("暂无跟进记录", 14, Color.GRAY, false));
        for (CustomerDbHelper.FollowRecordItem fr : follows) {
            LinearLayout fi = vertical(dp(3));
            fi.setPadding(dp(8), dp(6), dp(8), dp(6));
            fi.setBackground(round(Color.rgb(248, 250, 253), dp(10), Color.rgb(225, 230, 238)));
            fi.addView(text(fr.followTime + "  " + nvl(fr.followMethod,"其他") + (fr.done ? "  已完成" : ""), 14, Color.rgb(42,58,86), true));
            fi.addView(text(nvl(fr.content,""), 14, Color.rgb(42,58,86), false));
            if (!CustomerDbHelper.empty(fr.nextFollowDate)) fi.addView(text("下次跟进：" + fr.nextFollowDate, 13, Color.rgb(170, 94, 20), true));
            LinearLayout fo = horizontal(0); fi.addView(fo);
            fo.addView(smallButton(fr.done ? "设为未完成" : "完成", v -> { db.markFollowDone(fr.id, !fr.done); showCompanyDetail(c.id); }), weightLp());
            fo.addView(smallButton("删除", v -> confirm("删除跟进记录", "确认删除这条跟进记录？", () -> { db.deleteFollowRecord(fr.id); showCompanyDetail(c.id); })), weightLp());
            box.addView(fi);
        }
        box.addView(smallButton("新增跟进记录", v -> showAddFollowDialog(c.id, "电话", "")));
        box.addView(sectionTitle("快捷操作"));
        LinearLayout ops1 = horizontal(0); box.addView(ops1);
        ops1.addView(primaryButton("导入该企业全部联系人", v -> importCompanyContacts(c.id)), weightLp());
        ops1.addView(primaryButton("编辑企业", v -> showCompanyEditor(c.id)), weightLp());
        box.addView(smallButton("编辑标签", v -> showEditTags(c.id)));
        box.addView(smallButton("删除企业", v -> confirm("删除企业", "默认只删除App内企业资料，不会删除手机通讯录。", () -> { db.deleteCompany(c.id); Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show(); render(); })));
        AlertDialog dialog = new AlertDialog.Builder(this).setView(scroll).setPositiveButton("关闭", null).create();
        dialog.show();
    }

    private void showCompanyEditor(long companyId) {
        CustomerDbHelper.CompanyItem old = companyId > 0 ? db.getCompany(companyId) : null;
        LinearLayout box = vertical(dp(8));
        box.setPadding(dp(18), dp(8), dp(18), dp(8));
        EditText group = edit("分组", old == null ? "默认分组" : old.groupName);
        EditText name = edit("公司名称（必填）", old == null ? "" : old.name);
        EditText industry = edit("所属行业", old == null ? "" : old.industry);
        EditText employee = edit("参保人数", old == null ? "" : old.employeeCount);
        EditText region = edit("区域", old == null ? "" : old.region);
        EditText address = edit("地址", old == null ? "" : old.address);
        box.addView(group); box.addView(name); box.addView(industry); box.addView(employee); box.addView(region); box.addView(address);
        new AlertDialog.Builder(this)
                .setTitle(companyId > 0 ? "编辑企业" : "新增企业")
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (d,w) -> {
                    if (CustomerDbHelper.empty(name.getText().toString())) { toast("公司名称不能为空"); return; }
                    if (companyId > 0) db.updateCompanyBasic(companyId, group.getText().toString(), name.getText().toString(), industry.getText().toString(), employee.getText().toString(), region.getText().toString(), address.getText().toString());
                    else db.addCompany(group.getText().toString(), name.getText().toString(), industry.getText().toString(), employee.getText().toString(), region.getText().toString(), address.getText().toString());
                    render();
                }).show();
    }

    private void showAddContactDialog(long companyId) {
        LinearLayout box = vertical(dp(8)); box.setPadding(dp(18), dp(8), dp(18), dp(8));
        EditText name = edit("联系人姓名，可为空", "");
        EditText phone = edit("电话号码", ""); phone.setInputType(InputType.TYPE_CLASS_PHONE);
        Spinner star = new Spinner(this);
        ArrayAdapter<String> starAd = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, Arrays.asList("未标记", "★", "★★", "★★★"));
        starAd.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); star.setAdapter(starAd);
        box.addView(name); box.addView(phone); box.addView(text("号码星级", 13, Color.rgb(42,58,86), true)); box.addView(star);
        new AlertDialog.Builder(this).setTitle("新增联系人").setView(box).setNegativeButton("取消", null).setPositiveButton("保存", (d,w)->{
            if (CustomerDbHelper.empty(phone.getText().toString())) { toast("电话不能为空"); return; }
            db.addContact(companyId, name.getText().toString(), phone.getText().toString(), star.getSelectedItemPosition());
            showCompanyDetail(companyId);
        }).show();
    }

    private void showAddNoteDialog(long companyId) {
        EditText note = edit("请输入备注内容", ""); note.setMinLines(3);
        new AlertDialog.Builder(this).setTitle("新增备注").setView(note).setNegativeButton("取消", null).setPositiveButton("保存", (d,w)->{
            db.addNote(companyId, note.getText().toString()); showCompanyDetail(companyId);
        }).show();
    }

    private void showEditTags(long companyId) {
        CustomerDbHelper.CompanyItem c = db.getCompany(companyId);
        EditText tags = edit("多个标签用；分隔", c.tags);
        new AlertDialog.Builder(this).setTitle("编辑标签").setView(tags).setNegativeButton("取消", null).setPositiveButton("保存", (d,w)->{
            db.updateTags(companyId, tags.getText().toString()); showCompanyDetail(companyId);
        }).show();
    }

    private void renderContactSync() {
        ScrollView scroll = scroll();
        LinearLayout box = vertical(dp(12));
        box.setPadding(dp(12), dp(12), dp(12), dp(12));
        scroll.addView(box);
        box.addView(titleRow("通讯录", "本App导入联系人统一加入手机通讯录分组“企业客户管家”，支持分页查看和批量同步删除"));
        LinearLayout ops = horizontal(0); box.addView(ops);
        ops.addView(primaryButton("批量导入", v -> showBulkImportDialog()), weightLp());
        ops.addView(primaryButton("批量删除已导入", v -> showBulkDeleteImportedDialog()), weightLp());
        ops.addView(primaryButton("通话记录", v -> showCallLogPage()), weightLp());
        ops.addView(primaryButton("刷新", v -> { contactVisibleLimit = PAGE_SIZE; render(); }), weightLp());
        int importedCount = db.countContacts(true);
        int unimportedCount = db.countContacts(false);
        box.addView(sectionTitle("统计"));
        LinearLayout stats = horizontal(0); box.addView(stats);
        stats.addView(statCard("已导入", importedCount+"", "联系人"), weightLp());
        stats.addView(statCard("未导入", unimportedCount+"", "联系人"), weightLp());
        box.addView(sectionTitle("已导入通讯录（分页显示）"));
        List<CustomerDbHelper.ContactItem> imported = db.getContactsPage(0, true, contactVisibleLimit, 0);
        if (imported.isEmpty()) box.addView(emptyBox("暂无已导入联系人", "从客户详情或批量导入功能写入手机通讯录"));
        for (CustomerDbHelper.ContactItem ct : imported) box.addView(importedContactCard(ct));
        if (imported.size() >= contactVisibleLimit && importedCount > imported.size()) {
            box.addView(primaryButton("加载更多（当前显示 " + imported.size() + " / " + importedCount + "）", v -> { contactVisibleLimit += PAGE_SIZE; render(); }));
        }
        content.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
    }

    private View importedContactCard(CustomerDbHelper.ContactItem ct) {
        LinearLayout card = card();
        card.addView(text(ct.displayName(), 16, Color.rgb(22, 38, 64), true));
        card.addView(text("电话：" + ct.phone + "｜星级：" + CustomerDbHelper.starText(ct.starLevel) + "｜分组：" + ct.groupName + "｜状态：" + (CustomerDbHelper.empty(ct.customerStatus) ? "未设置" : ct.customerStatus), 13, Color.rgb(90, 103, 125), false));
        card.addView(text("导入时间：" + nvl(ct.importedAt, "未知"), 13, Color.rgb(90, 103, 125), false));
        LinearLayout ops = horizontal(0); card.addView(ops);
        ops.addView(smallButton("拨号", v -> dial(ct.phone)), weightLp());
        ops.addView(smallButton("查看企业", v -> showCompanyDetail(ct.companyId)), weightLp());
        ops.addView(smallButton("同步删除", v -> confirm("同步删除通讯录", "该操作会删除手机通讯录中由本App导入的联系人，但保留App内企业资料。", () -> deleteImportedContact(ct))), weightLp());
        return card;
    }

    private void renderImportExport() {
        ScrollView scroll = scroll();
        LinearLayout box = vertical(dp(12));
        box.setPadding(dp(12), dp(12), dp(12), dp(12));
        scroll.addView(box);
        box.addView(titleRow("导入导出", "宽松导入、宽表导出，支持导入时设置客户状态"));
        box.addView(sectionTitle("Excel 导入"));
        box.addView(infoCard("导入规则", "只要有“公司名称”列即可导入；联系人、电话、备注、标签可动态识别；可通过“客户状态”列或本次导入默认状态设置关注/跟进/潜力。高等级状态可覆盖低等级状态。"));
        box.addView(primaryButton("选择 Excel 导入", v -> openImportFile()));
        box.addView(primaryButton("粘贴识别快速录入", v -> showQuickPasteDialog()));
        box.addView(sectionTitle("Excel 导出"));
        box.addView(primaryButton("导出全部企业", v -> prepareExportByFilter("全部企业", "", 0)));
        box.addView(primaryButton("按条件导出", v -> showExportDialog()));
        box.addView(primaryButton("下载导入模板", v -> exportTemplate()));
        content.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
    }

    private void renderSettings() {
        ScrollView scroll = scroll();
        LinearLayout box = vertical(dp(12));
        box.setPadding(dp(12), dp(12), dp(12), dp(12));
        scroll.addView(box);
        box.addView(titleRow("我的", "分组、标签、规则与数据维护"));
        box.addView(sectionTitle("当前规则"));
        box.addView(infoCard("核心规则", "公司名称唯一识别；客户状态为关注/跟进/潜力，重要程度依次增加；导入时高等级状态可覆盖低等级状态；通讯录名称为“公司名称-联系人姓名”；删除通讯录仅删除本App导入的联系人。"));
        box.addView(sectionTitle("分组管理"));
        for (CustomerDbHelper.GroupItem g : db.getGroups()) {
            LinearLayout groupCard = card();
            TextView groupName = text(g.name, 17, Color.rgb(22, 38, 64), true);
            groupName.setSingleLine(false);
            groupCard.addView(groupName);
            CustomerDbHelper.GroupStats gs = db.getGroupStats(g.id);
            groupCard.addView(text("企业 " + gs.companyCount + "｜联系人 " + gs.contactCount + "｜已导入 " + gs.importedCount + "｜潜力 " + gs.importantCount + "｜3星 " + gs.star3Count, 13, Color.rgb(90,103,125), false));
            LinearLayout row = horizontal(0);
            row.addView(smallButton("详情", v -> showGroupDetail(g)), weightLp());
            row.addView(smallButton("导入", v -> showGroupImportDialog(g)), weightLp());
            row.addView(smallButton("改名", v -> showRenameGroup(g)), weightLp());
            row.addView(smallButton("删除", v -> confirm("删除分组", "删除分组后，企业会移入默认分组。", () -> { db.deleteGroup(g.id); render(); })), weightLp());
            groupCard.addView(row);
            box.addView(groupCard);
        }
        box.addView(primaryButton("新增分组", v -> showAddGroupDialog()));
        box.addView(sectionTitle("数据维护"));
        LinearLayout m1 = horizontal(0); box.addView(m1);
        m1.addView(primaryButton("重建缓存", v -> runMaintenance("重建缓存", () -> db.rebuildAllCaches())), weightLp());
        m1.addView(primaryButton("重建索引", v -> runMaintenance("重建索引", () -> db.rebuildIndexes())), weightLp());
        LinearLayout m2 = horizontal(0); box.addView(m2);
        m2.addView(primaryButton("压缩数据库", v -> runMaintenance("压缩数据库", () -> db.vacuumDatabase())), weightLp());
        m2.addView(primaryButton("查看导入日志", v -> showImportLogs()), weightLp());
        box.addView(primaryButton("数据备份与恢复", v -> showBackupDialog()));
        box.addView(primaryButton("查看异常数据", v -> showExceptions()));
        box.addView(primaryButton("导出异常数据Excel", v -> exportExceptions()));
        box.addView(primaryButton("查看操作日志", v -> showOperationLogs()));
        box.addView(primaryButton("通话记录权限/匹配", v -> showCallLogPage()));
        box.addView(primaryButton("疑似重复企业检测", v -> showDuplicateSuspects()));
        box.addView(primaryButton("号码质量清洗", v -> showPhoneCleanPage()));
        box.addView(sectionTitle("使用说明"));
        box.addView(infoCard("流程", "1. 导入Excel建立企业库\n2. 在客户页搜索、筛选、标记关注/跟进/潜力\n3. 按分组或序号区间批量导入通讯录\n4. 在通讯录页查看已导入记录并可同步删除\n5. 定期导出Excel备份"));
        box.addView(primaryButton("清空全部App数据", v -> confirm("危险操作", "清空后无法恢复，且不会删除手机通讯录。确认清空？", () -> { deleteDatabase(CustomerDbHelper.DB_NAME); db = new CustomerDbHelper(this); render(); })));
        content.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
    }

    private void runMaintenance(String title, Runnable task) {
        ProgressDialog pd = new ProgressDialog(this);
        pd.setTitle(title);
        pd.setMessage("正在处理，请稍候……");
        pd.setIndeterminate(true);
        pd.setCancelable(false);
        pd.show();
        new Thread(() -> {
            try { task.run(); runOnUiThread(() -> { pd.dismiss(); toast(title + "完成"); render(); }); }
            catch (Exception e) { runOnUiThread(() -> { pd.dismiss(); new AlertDialog.Builder(this).setTitle(title + "失败").setMessage(e.getMessage()).setPositiveButton("确定", null).show(); }); }
        }).start();
    }

    private void showImportLogs() {
        StringBuilder sb = new StringBuilder();
        for (CustomerDbHelper.LogItem it : db.getImportLogs(30)) {
            sb.append(it.startedAt).append("  ").append(it.fileName).append("\n")
                    .append("行数:").append(it.totalRows).append(" 新增企业:").append(it.newCompanies).append(" 合并:").append(it.mergedCompanies).append(" 联系人:").append(it.newContacts).append("\n\n");
        }
        if (sb.length() == 0) sb.append("暂无导入日志");
        new AlertDialog.Builder(this).setTitle("导入日志").setMessage(sb.toString()).setPositiveButton("确定", null).show();
    }

    private void showExceptions() {
        StringBuilder sb = new StringBuilder();
        for (CustomerDbHelper.ExceptionItem it : db.getExceptions(50)) {
            sb.append(it.createdAt).append(" 第").append(it.rowNo).append("行 ").append(it.reason).append("\n").append(it.companyName).append("\n\n");
        }
        if (sb.length() == 0) sb.append("暂无异常数据");
        new AlertDialog.Builder(this).setTitle("异常数据").setMessage(sb.toString()).setPositiveButton("确定", null).show();
    }

    private void showAddGroupDialog() {
        EditText e = edit("分组名称", "");
        new AlertDialog.Builder(this).setTitle("新增分组").setView(e).setNegativeButton("取消", null).setPositiveButton("保存", (d,w)->{ db.ensureGroup(e.getText().toString()); render(); }).show();
    }

    private void showRenameGroup(CustomerDbHelper.GroupItem g) {
        EditText e = edit("分组名称", g.name);
        new AlertDialog.Builder(this).setTitle("重命名分组").setView(e).setNegativeButton("取消", null).setPositiveButton("保存", (d,w)->{ db.renameGroup(g.id, e.getText().toString()); render(); }).show();
    }

    private void openImportFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        try { startActivityForResult(i, REQ_IMPORT_XLSX); }
        catch (Exception e) {
            Intent any = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            any.addCategory(Intent.CATEGORY_OPENABLE);
            any.setType("*/*");
            startActivityForResult(any, REQ_IMPORT_XLSX);
        }
    }

    private void handleIncomingFile(Intent intent) {
        if (intent == null) return;
        Uri uri = null;
        if (Intent.ACTION_SEND.equals(intent.getAction())) uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        else if (Intent.ACTION_VIEW.equals(intent.getAction())) uri = intent.getData();
        if (uri != null) showImportOptions(uri);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        if (requestCode == REQ_IMPORT_XLSX) showImportOptions(uri);
        else if (requestCode == REQ_EXPORT_XLSX) writePendingExport(uri);
        else if (requestCode == REQ_BACKUP_EXPORT) writeBackupToUri(uri);
        else if (requestCode == REQ_BACKUP_RESTORE) confirmRestoreFromUri(uri);
    }

    private void showImportOptions(Uri uri) {
        LinearLayout box = vertical(dp(10)); box.setPadding(dp(18), dp(8), dp(18), dp(8));
        List<CustomerDbHelper.GroupItem> groups = db.getGroups();
        Spinner spinner = new Spinner(this);
        ArrayAdapter<CustomerDbHelper.GroupItem> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, groups);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        CheckBox useExcelGroup = new CheckBox(this);
        useExcelGroup.setText("如果Excel中有“分组”列，优先使用Excel分组");
        useExcelGroup.setChecked(true);
        Spinner statusSpinner = new Spinner(this);
        List<String> statusOptions = Arrays.asList("未设置", "关注", "跟进", "潜力");
        ArrayAdapter<String> statusAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, statusOptions);
        statusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        statusSpinner.setAdapter(statusAdapter);
        box.addView(text("选择默认分组", 14, Color.rgb(42, 58, 86), true));
        box.addView(spinner);
        box.addView(useExcelGroup);
        box.addView(text("本次导入默认客户状态", 14, Color.rgb(42, 58, 86), true));
        box.addView(statusSpinner);
        box.addView(text("说明：Excel中如有“客户状态”列则优先按每行状态导入；否则使用这里选择的默认状态。状态等级为 未设置 < 关注 < 跟进 < 潜力，高等级可覆盖低等级，低等级不会覆盖高等级。旧表中“重点”会按“潜力”处理。", 13, Color.rgb(125, 93, 20), false));
        new AlertDialog.Builder(this)
                .setTitle("导入Excel")
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("解析预览", (d,w)->{
                    CustomerDbHelper.GroupItem g = (CustomerDbHelper.GroupItem) spinner.getSelectedItem();
                    previewExcel(uri, g == null ? db.ensureGroup("默认分组") : g.id, useExcelGroup.isChecked(), (String) statusSpinner.getSelectedItem());
                }).show();
    }

    private void previewExcel(Uri uri, long groupId, boolean useExcelGroup, String defaultStatus) {
        ProgressDialog pd = new ProgressDialog(this);
        pd.setTitle("正在解析 Excel");
        pd.setMessage("正在读取表格并预估导入结果……");
        pd.setIndeterminate(true);
        pd.setCancelable(false);
        pd.show();
        new Thread(() -> {
            try {
                List<Map<String, String>> rows = XlsxSimpleReader.readFirstSheet(getContentResolver(), uri);
                String fileName = uri.getLastPathSegment() == null ? "Excel导入" : uri.getLastPathSegment();
                CustomerDbHelper.ImportPreviewResult r = db.previewImportRows(rows, groupId, useExcelGroup, fileName, defaultStatus);
                pendingImportRows = rows;
                pendingImportUri = uri;
                pendingImportGroupId = groupId;
                pendingImportUseExcelGroup = useExcelGroup;
                pendingImportDefaultStatus = defaultStatus;
                pendingImportFileName = fileName;
                runOnUiThread(() -> { pd.dismiss(); showImportPreviewDialog(r); });
            } catch (Exception e) {
                runOnUiThread(() -> { pd.dismiss(); new AlertDialog.Builder(this).setTitle("预览失败").setMessage(e.getMessage()).setPositiveButton("确定", null).show(); });
            }
        }).start();
    }

    private void showImportPreviewDialog(CustomerDbHelper.ImportPreviewResult r) {
        String msg = "本次 Excel 预览结果：\n\n" +
                "识别企业：" + (r.newCompanies + r.mergedCompanies) + " 家\n" +
                "新企业：" + r.newCompanies + " 家\n" +
                "已存在企业：" + r.mergedCompanies + " 家\n" +
                "预计新增电话：" + r.newPhoneCount + " 个\n" +
                "预计重复电话：" + r.duplicatePhoneCount + " 个\n" +
                "预计新增备注：" + r.newNotes + " 条\n" +
                "客户状态将提升：" + r.statusUpgradeCount + " 家\n" +
                "客户状态保持不变：" + r.statusKeepCount + " 家\n" +
                "异常行/提示：" + r.abnormalRows + " 行\n" +
                "空公司名称行：" + r.skippedNoCompany + " 行\n" +
                "电话格式异常：" + r.phoneAbnormalCount + " 个\n\n" +
                "确认后才会正式写入数据库。";
        new AlertDialog.Builder(this)
                .setTitle("导入前预览")
                .setMessage(msg)
                .setNegativeButton("取消", null)
                .setPositiveButton("确认导入", (d,w)->importParsedExcel())
                .show();
    }

    private void importParsedExcel() {
        if (pendingImportRows == null) { toast("没有待导入数据"); return; }
        ProgressDialog pd = new ProgressDialog(this);
        pd.setTitle("正在后台导入 Excel");
        pd.setMessage("正在查重、合并、批量写入，请勿关闭App……");
        pd.setIndeterminate(true);
        pd.setCancelable(false);
        pd.show();
        new Thread(() -> {
            try {
                CustomerDbHelper.ImportResult r = db.importRows(pendingImportRows, pendingImportGroupId, pendingImportUseExcelGroup, pendingImportFileName, pendingImportDefaultStatus);
                pendingImportRows = null;
                runOnUiThread(() -> {
                    pd.dismiss();
                    new AlertDialog.Builder(this)
                            .setTitle("导入完成")
                            .setMessage("新增企业：" + r.newCompanies + "\n" +
                                    "合并企业：" + r.mergedCompanies + "\n" +
                                    "新增联系人：" + r.newContacts + "\n" +
                                    "新增备注：" + r.newNotes + "\n" +
                                    "新增标签：" + r.newTags + "\n" +
                                    "状态提升：" + r.statusUpgradeCount + "\n" +
                                    "状态保持：" + r.statusKeepCount + "\n" +
                                    "重复行提示：" + r.duplicateRows + "\n" +
                                    "跳过无公司名称行：" + r.skippedNoCompany)
                            .setPositiveButton("确定", (d,w)->{ customerVisibleLimit = PAGE_SIZE; render(); })
                            .show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> { pd.dismiss(); new AlertDialog.Builder(this).setTitle("导入失败").setMessage(e.getMessage()).setPositiveButton("确定", null).show(); });
            }
        }).start();
    }

    private void showExportDialog() {
        LinearLayout box = vertical(dp(8)); box.setPadding(dp(18), dp(8), dp(18), dp(8));
        List<String> options = new ArrayList<>();
        options.add("全部企业"); options.add("未设置状态"); options.add("关注客户"); options.add("跟进客户"); options.add("潜力客户");
        for (CustomerDbHelper.GroupItem g : db.getGroups()) options.add("分组：" + g.name);
        Spinner sp = new Spinner(this);
        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, options);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(ad); box.addView(sp);
        new AlertDialog.Builder(this).setTitle("选择导出范围").setView(box).setNegativeButton("取消", null).setPositiveButton("导出", (d,w)->{
            String opt = (String) sp.getSelectedItem();
            if ("未设置状态".equals(opt)) { prepareExportByFilter(opt, CustomerDbHelper.STATUS_NONE, 0); }
            else if ("关注客户".equals(opt)) { prepareExportByFilter(opt, CustomerDbHelper.STATUS_FOCUS, 0); }
            else if ("跟进客户".equals(opt)) { prepareExportByFilter(opt, CustomerDbHelper.STATUS_FOLLOW, 0); }
            else if ("潜力客户".equals(opt)) { prepareExportByFilter(opt, CustomerDbHelper.STATUS_IMPORTANT, 0); }
            else if (opt != null && opt.startsWith("分组：")) {
                String name = opt.substring(3);
                long gid = 0; for (CustomerDbHelper.GroupItem g : db.getGroups()) if (g.name.equals(name)) gid = g.id;
                prepareExportByFilter(opt, "", gid);
            } else { prepareExportByFilter(opt, "", 0); }
        }).show();
    }

    private void prepareExportByFilter(String title, String status, long groupId) {
        ProgressDialog pd = new ProgressDialog(this);
        pd.setTitle("正在读取导出范围");
        pd.setMessage("正在分页读取客户数据，请稍候……");
        pd.setIndeterminate(true);
        pd.setCancelable(false);
        pd.show();
        new Thread(() -> {
            try {
                List<CustomerDbHelper.CompanyItem> companies = db.getCompanies("", status, groupId);
                runOnUiThread(() -> { pd.dismiss(); prepareExport(title == null ? "导出" : title, companies); });
            } catch (Exception e) {
                runOnUiThread(() -> { pd.dismiss(); new AlertDialog.Builder(this).setTitle("读取导出数据失败").setMessage(e.getMessage()).setPositiveButton("确定", null).show(); });
            }
        }).start();
    }

    private void prepareExport(String title, List<CustomerDbHelper.CompanyItem> companies) {
        ProgressDialog pd = new ProgressDialog(this);
        pd.setTitle("正在准备导出数据");
        pd.setMessage("正在后台整理宽表，请稍候……");
        pd.setIndeterminate(true);
        pd.setCancelable(false);
        pd.show();
        new Thread(() -> {
            try {
                buildExportData(companies);
                String fileName = "企业客户管家_" + title.replace("：", "_") + "_" + new SimpleDateFormat("yyyyMMdd_HHmm", Locale.CHINA).format(new Date()) + ".xlsx";
                runOnUiThread(() -> {
                    pd.dismiss();
                    Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                    i.putExtra(Intent.EXTRA_TITLE, fileName);
                    startActivityForResult(i, REQ_EXPORT_XLSX);
                });
            } catch (Exception e) {
                runOnUiThread(() -> { pd.dismiss(); new AlertDialog.Builder(this).setTitle("导出准备失败").setMessage(e.getMessage()).setPositiveButton("确定", null).show(); });
            }
        }).start();
    }

    private void exportTemplate() {
        pendingExportHeaders = Arrays.asList("序号", "分组", "客户状态", "公司名称", "所属行业", "参保人数", "区域", "地址", "联系人1", "电话号码1", "星级1", "联系人2", "电话号码2", "星级2", "联系人3", "电话号码3", "星级3", "联系人4", "电话号码4", "星级4", "备注1", "备注2", "备注3", "备注4", "备注5", "标签1", "标签2", "标签3", "标签4", "标签5");
        pendingExportRows = new ArrayList<>();
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        i.putExtra(Intent.EXTRA_TITLE, "企业客户管家_导入模板.xlsx");
        startActivityForResult(i, REQ_EXPORT_XLSX);
    }

    private void buildExportData(List<CustomerDbHelper.CompanyItem> companies) {
        int maxContacts = 1, maxNotes = 1, maxTags = 1;
        Map<Long, List<CustomerDbHelper.ContactItem>> contacts = db.contactsByCompany(companies);
        Map<Long, List<CustomerDbHelper.NoteItem>> notes = db.notesByCompany(companies);
        for (CustomerDbHelper.CompanyItem c : companies) {
            maxContacts = Math.max(maxContacts, contacts.get(c.id).size());
            maxNotes = Math.max(maxNotes, notes.get(c.id).size());
            maxTags = Math.max(maxTags, splitSemi(c.tags).size());
        }
        ArrayList<String> headers = new ArrayList<>();
        headers.addAll(Arrays.asList("序号", "分组", "客户状态", "公司名称", "所属行业", "参保人数", "区域", "地址"));
        for (int i=1;i<=maxContacts;i++) { headers.add("联系人" + i); headers.add("电话号码" + i); headers.add("星级" + i); }
        for (int i=1;i<=maxNotes;i++) headers.add("备注" + i);
        for (int i=1;i<=maxTags;i++) headers.add("标签" + i);
        headers.add("扩展信息"); headers.add("创建时间"); headers.add("更新时间");
        ArrayList<List<String>> rows = new ArrayList<>();
        for (CustomerDbHelper.CompanyItem c : companies) {
            ArrayList<String> row = new ArrayList<>();
            row.add(String.valueOf(c.seq)); row.add(nvl(c.groupName,"")); row.add(c.statusText()); row.add(nvl(c.name,""));
            row.add(nvl(c.industry,"")); row.add(nvl(c.employeeCount,"")); row.add(nvl(c.region,"")); row.add(nvl(c.address,""));
            List<CustomerDbHelper.ContactItem> cl = contacts.get(c.id);
            for (int i=0;i<maxContacts;i++) {
                if (i < cl.size()) { row.add(nvl(cl.get(i).contactName,"")); row.add(nvl(cl.get(i).phone,"")); row.add(String.valueOf(cl.get(i).starLevel)); }
                else { row.add(""); row.add(""); row.add(""); }
            }
            List<CustomerDbHelper.NoteItem> nl = notes.get(c.id);
            for (int i=0;i<maxNotes;i++) row.add(i < nl.size() ? nl.get(i).content : "");
            List<String> tags = splitSemi(c.tags);
            for (int i=0;i<maxTags;i++) row.add(i < tags.size() ? tags.get(i) : "");
            row.add(nvl(c.extraInfo,"")); row.add(nvl(c.createdAt,"")); row.add(nvl(c.updatedAt,""));
            rows.add(row);
        }
        pendingExportHeaders = headers;
        pendingExportRows = rows;
    }

    private void writePendingExport(Uri uri) {
        ProgressDialog pd = new ProgressDialog(this);
        pd.setTitle("正在导出 Excel");
        pd.setMessage("大数据宽表导出可能需要一些时间，请勿关闭App……");
        pd.setIndeterminate(true);
        pd.setCancelable(false);
        pd.show();
        new Thread(() -> {
            try {
                XlsxSimpleWriter.write(getContentResolver(), uri, pendingExportHeaders, pendingExportRows);
                runOnUiThread(() -> { pd.dismiss(); Toast.makeText(this, "导出成功", Toast.LENGTH_LONG).show(); });
            } catch (Exception e) {
                runOnUiThread(() -> { pd.dismiss(); new AlertDialog.Builder(this).setTitle("导出失败").setMessage(e.getMessage()).setPositiveButton("确定", null).show(); });
            }
        }).start();
    }

    private void importCompanyContacts(long companyId) {
        List<CustomerDbHelper.ContactItem> list = db.getContacts(companyId, false);
        if (list.isEmpty()) { toast("没有未导入联系人"); return; }
        importContactsWithPreview(list);
    }

    private void showBulkImportDialog() {
        LinearLayout box = vertical(dp(8)); box.setPadding(dp(18), dp(8), dp(18), dp(8));
        List<CustomerDbHelper.GroupItem> groups = db.getGroups();
        Spinner groupSp = new Spinner(this);
        ArrayAdapter<CustomerDbHelper.GroupItem> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, groups);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); groupSp.setAdapter(ad);
        EditText start = edit("起始企业序号", "1"); start.setInputType(InputType.TYPE_CLASS_NUMBER);
        EditText end = edit("结束企业序号", "20"); end.setInputType(InputType.TYPE_CLASS_NUMBER);
        box.addView(text("按分组企业序号区间导入", 14, Color.rgb(42,58,86), true));
        box.addView(groupSp); box.addView(start); box.addView(end);
        new AlertDialog.Builder(this).setTitle("批量导入通讯录").setView(box).setNegativeButton("取消", null).setPositiveButton("预览", (d,w)->{
            CustomerDbHelper.GroupItem g = (CustomerDbHelper.GroupItem) groupSp.getSelectedItem();
            int s = parseInt(start.getText().toString(), 1); int e = parseInt(end.getText().toString(), s);
            List<CustomerDbHelper.ContactItem> list = db.getUnimportedContactsByRange(g.id, s, e);
            importContactsWithPreview(list);
        }).show();
    }

    private void importContactsWithPreview(List<CustomerDbHelper.ContactItem> list) {
        if (list == null || list.isEmpty()) { toast("没有可导入的联系人"); return; }
        StringBuilder sb = new StringBuilder();
        int preview = Math.min(8, list.size());
        for (int i=0;i<preview;i++) sb.append(list.get(i).displayName()).append("  ").append(list.get(i).phone).append("\n");
        if (list.size() > preview) sb.append("……共 ").append(list.size()).append(" 个联系人");
        new AlertDialog.Builder(this)
                .setTitle("确认导入通讯录")
                .setMessage("本次将导入 " + list.size() + " 个联系人。\n\n" + sb + "\n\n已导入联系人会自动跳过。")
                .setNegativeButton("取消", null)
                .setPositiveButton("开始导入", (d,w)->{
                    pendingImportContacts = list;
                    ensureContactsPermissionThenImport();
                }).show();
    }

    private void ensureContactsPermissionThenImport() {
        if (checkSelfPermission(Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_CONTACTS, Manifest.permission.READ_CONTACTS}, REQ_CONTACT_PERM);
        } else importPendingContacts();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CONTACT_PERM) {
            boolean ok = true;
            for (int r : grantResults) if (r != PackageManager.PERMISSION_GRANTED) ok = false;
            if (ok) importPendingContacts(); else toast("需要通讯录权限才能导入和同步删除");
        } else if (requestCode == REQ_CALL_LOG_PERM) {
            boolean ok = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (ok) readRecentCalls(7); else toast("未授权读取通话记录，通话记录匹配功能不可用");
        }
    }

    private void importPendingContacts() {
        if (pendingImportContacts == null) return;
        List<CustomerDbHelper.ContactItem> working = new ArrayList<>(pendingImportContacts);
        pendingImportContacts = null;
        ProgressDialog pd = new ProgressDialog(this);
        pd.setTitle("正在分批导入通讯录");
        pd.setMessage("将统一加入手机通讯录分组：企业客户管家");
        pd.setIndeterminate(true);
        pd.setCancelable(false);
        pd.show();
        new Thread(() -> {
            int success = 0, skipped = 0, failed = 0;
            long groupId = -1;
            try { groupId = ensureDeviceContactGroup(); } catch (Exception ignored) {}
            for (CustomerDbHelper.ContactItem ct : working) {
                if (ct.imported) { skipped++; continue; }
                if (phoneExistsInDevice(ct.phoneNorm)) { skipped++; continue; }
                try {
                    long rawId = insertContactToDevice(ct.displayName(), ct.phone, ct.companyName, groupId);
                    if (rawId > 0) { db.markContactImported(ct.id, rawId); success++; }
                    else failed++;
                } catch (Exception e) { failed++; }
                try { Thread.sleep(8); } catch (Exception ignored) {}
            }
            final int fs = success, fk = skipped, ff = failed;
            runOnUiThread(() -> {
                pd.dismiss();
                new AlertDialog.Builder(this)
                        .setTitle("导入完成")
                        .setMessage("成功：" + fs + "\n跳过：" + fk + "\n失败：" + ff + "\n已统一加入手机通讯录分组：企业客户管家")
                        .setPositiveButton("确定", (d,w)->render())
                        .show();
            });
        }).start();
    }

    private long ensureDeviceContactGroup() {
        String groupTitle = "企业客户管家";
        Uri uri = ContactsContract.Groups.CONTENT_URI;
        android.database.Cursor c = getContentResolver().query(uri, new String[]{ContactsContract.Groups._ID}, ContactsContract.Groups.TITLE + "=? AND " + ContactsContract.Groups.DELETED + "=0", new String[]{groupTitle}, null);
        try { if (c != null && c.moveToFirst()) return c.getLong(0); } finally { if (c != null) c.close(); }
        android.content.ContentValues cv = new android.content.ContentValues();
        cv.put(ContactsContract.Groups.TITLE, groupTitle);
        cv.put(ContactsContract.Groups.GROUP_VISIBLE, 1);
        Uri inserted = getContentResolver().insert(uri, cv);
        if (inserted == null) return -1;
        return ContentUris.parseId(inserted);
    }

    private long insertContactToDevice(String displayName, String phone, String company, long groupId) throws Exception {
        ArrayList<ContentProviderOperation> ops = new ArrayList<>();
        ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null).build());
        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, displayName).build());
        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE).build());
        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, company)
                .withValue(ContactsContract.CommonDataKinds.Organization.TYPE, ContactsContract.CommonDataKinds.Organization.TYPE_WORK).build());
        if (groupId > 0) {
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID, groupId).build());
        }
        ContentProviderResult[] res = getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
        if (res != null && res.length > 0 && res[0].uri != null) return ContentUris.parseId(res[0].uri);
        return -1;
    }

    private boolean phoneExistsInDevice(String phoneNorm) {
        if (CustomerDbHelper.empty(phoneNorm) || checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return false;
        Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
        String[] projection = {ContactsContract.CommonDataKinds.Phone.NUMBER};
        android.database.Cursor c = getContentResolver().query(uri, projection, null, null, null);
        if (c == null) return false;
        try {
            while (c.moveToNext()) {
                String n = CustomerDbHelper.normalizePhone(c.getString(0));
                if (phoneNorm.equals(n)) return true;
            }
        } finally { c.close(); }
        return false;
    }

    private void showBulkDeleteImportedDialog() {
        int count = db.countContacts(true);
        if (count <= 0) { toast("当前没有已导入联系人"); return; }
        new AlertDialog.Builder(this)
                .setTitle("批量删除已导入联系人")
                .setMessage("本次将从手机通讯录中删除由本App导入的联系人，共 " + count + " 个。\n\n该操作不会删除App内企业资料，删除后联系人状态会恢复为未导入。\n\n是否继续？")
                .setNegativeButton("取消", null)
                .setPositiveButton("继续", (d,w)->confirm("二次确认", "请再次确认：只删除本App导入的手机通讯录联系人，不删除App内企业资料。", () -> bulkDeleteImportedContacts()))
                .show();
    }

    private void bulkDeleteImportedContacts() {
        if (checkSelfPermission(Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_CONTACTS, Manifest.permission.READ_CONTACTS}, REQ_CONTACT_PERM);
            return;
        }
        List<CustomerDbHelper.ContactItem> list = db.getAllImportedContactsForDelete();
        if (list.isEmpty()) { toast("没有可删除的联系人"); return; }
        ProgressDialog pd = new ProgressDialog(this);
        pd.setTitle("正在批量删除通讯录联系人");
        pd.setMessage("分批删除中，请勿关闭App……");
        pd.setIndeterminate(true);
        pd.setCancelable(false);
        pd.show();
        new Thread(() -> {
            int success = 0, failed = 0;
            for (CustomerDbHelper.ContactItem ct : list) {
                try {
                    if (ct.rawContactId > 0) {
                        Uri uri = ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI, ct.rawContactId);
                        getContentResolver().delete(uri, null, null);
                    }
                    success++;
                } catch (Exception e) { failed++; }
                try { Thread.sleep(5); } catch (Exception ignored) {}
            }
            db.markContactsUnimported(list);
            final int fs=success, ff=failed;
            runOnUiThread(() -> {
                pd.dismiss();
                new AlertDialog.Builder(this)
                        .setTitle("批量删除完成")
                        .setMessage("已处理：" + list.size() + "\n成功：" + fs + "\n失败：" + ff + "\nApp内企业资料已保留，联系人状态已恢复为未导入。")
                        .setPositiveButton("确定", (d,w)->{ contactVisibleLimit = PAGE_SIZE; render(); })
                        .show();
            });
        }).start();
    }

    private void deleteImportedContact(CustomerDbHelper.ContactItem ct) {
        if (checkSelfPermission(Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            pendingImportContacts = null;
            requestPermissions(new String[]{Manifest.permission.WRITE_CONTACTS, Manifest.permission.READ_CONTACTS}, REQ_CONTACT_PERM);
            return;
        }
        if (ct.rawContactId > 0) {
            try {
                Uri uri = ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI, ct.rawContactId);
                getContentResolver().delete(uri, null, null);
            } catch (Exception ignored) {}
        }
        db.markContactUnimported(ct.id);
        render();
    }



    private void showStarDialog(CustomerDbHelper.ContactItem ct, long companyId) {
        String[] opts = {"未标记", "★ 1星", "★★ 2星", "★★★ 3星"};
        new AlertDialog.Builder(this)
                .setTitle("设置号码星级")
                .setSingleChoiceItems(opts, Math.max(0, Math.min(3, ct.starLevel)), (d, which) -> {
                    db.updateContactStar(ct.id, which);
                    d.dismiss();
                    showCompanyDetail(companyId);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showQuickPasteDialog() {
        LinearLayout box = vertical(dp(8)); box.setPadding(dp(16), dp(8), dp(16), dp(8));
        EditText input = edit("粘贴企业信息，例如：公司名称、联系人、电话、地址、备注", "");
        input.setMinLines(7);
        List<CustomerDbHelper.GroupItem> groups = db.getGroups();
        Spinner groupSp = new Spinner(this);
        ArrayAdapter<CustomerDbHelper.GroupItem> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, groups);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); groupSp.setAdapter(ad);
        Button clip = smallButton("读取剪贴板", v -> {
            try {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                ClipData cd = cm == null ? null : cm.getPrimaryClip();
                if (cd != null && cd.getItemCount() > 0) input.setText(cd.getItemAt(0).coerceToText(this));
                else toast("剪贴板为空");
            } catch (Exception e) { toast("读取剪贴板失败"); }
        });
        box.addView(input); box.addView(clip); box.addView(text("默认分组", 13, Color.rgb(42,58,86), true)); box.addView(groupSp);
        new AlertDialog.Builder(this)
                .setTitle("粘贴识别快速录入")
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("智能识别", (d,w)->{
                    Map<String,String> row = parseQuickText(input.getText().toString());
                    showQuickPreview(row, (CustomerDbHelper.GroupItem) groupSp.getSelectedItem());
                }).show();
    }

    private Map<String,String> parseQuickText(String raw) {
        LinkedHashMap<String,String> row = new LinkedHashMap<>();
        if (raw == null) raw = "";
        String text = raw.replace('\r','\n');
        ArrayList<String> phones = new ArrayList<>();
        Matcher pm = Pattern.compile("(\\+?86[-\\s]?)?((1[3-9]\\d{9})|(0\\d{2,3}[-\\s]?\\d{7,8})|(400[-\\s]?\\d{3}[-\\s]?\\d{4}))").matcher(text);
        while (pm.find()) {
            String ph = pm.group().trim();
            if (!phones.contains(ph)) phones.add(ph);
        }
        int idx = 1;
        for (String ph : phones) row.put("电话号码" + (idx++), ph);
        String company = "", contact = "", address = "", note = "";
        for (String line : text.split("\\n")) {
            String l = line.trim(); if (CustomerDbHelper.empty(l)) continue;
            if (CustomerDbHelper.empty(company) && (l.contains("有限公司") || l.contains("有限责任公司") || l.contains("股份有限公司") || l.contains("集团") || l.endsWith("公司") || l.contains("经营部") || l.contains("商行") || l.contains("工厂") || l.endsWith("厂"))) company = l.replaceAll("^(公司名称|企业名称|单位名称)[:：]", "").trim();
            else if (CustomerDbHelper.empty(address) && (l.contains("地址") || l.contains("路") || l.contains("街") || l.contains("号") || l.contains("区"))) address = l.replaceAll("^(地址|企业地址|注册地址)[:：]", "").trim();
            else if (CustomerDbHelper.empty(contact) && (l.contains("联系人") || l.contains("张总") || l.contains("李总") || l.contains("王总") || l.contains("老板") || l.contains("经理"))) contact = l.replaceAll("^(联系人|姓名)[:：]", "").replaceAll("(电话|手机)[:：]?.*", "").trim();
            else note += (note.length()==0?"":"；") + l;
        }
        if (CustomerDbHelper.empty(company)) {
            String[] lines = text.split("\\n");
            if (lines.length > 0) company = lines[0].trim();
        }
        if (!CustomerDbHelper.empty(company)) row.put("公司名称", company);
        if (!CustomerDbHelper.empty(contact)) row.put("联系人1", contact);
        if (!CustomerDbHelper.empty(address)) row.put("地址", address);
        if (!CustomerDbHelper.empty(note)) row.put("备注1", note);
        return row;
    }

    private void showQuickPreview(Map<String,String> row, CustomerDbHelper.GroupItem group) {
        LinearLayout box = vertical(dp(8)); box.setPadding(dp(16), dp(8), dp(16), dp(8));
        EditText company = edit("公司名称（必填）", row.get("公司名称"));
        EditText contact = edit("联系人1", row.get("联系人1"));
        EditText phone = edit("电话号码1", row.get("电话号码1")); phone.setInputType(InputType.TYPE_CLASS_PHONE);
        EditText address = edit("地址", row.get("地址"));
        EditText note = edit("备注1", row.get("备注1")); note.setMinLines(3);
        Spinner star = new Spinner(this);
        ArrayAdapter<String> starAd = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, Arrays.asList("未标记", "★", "★★", "★★★"));
        starAd.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); star.setAdapter(starAd);
        box.addView(company); box.addView(contact); box.addView(phone); box.addView(text("号码星级", 13, Color.rgb(42,58,86), true)); box.addView(star); box.addView(address); box.addView(note);
        new AlertDialog.Builder(this).setTitle("识别结果预览").setView(box).setNegativeButton("取消", null).setPositiveButton("确认入库", (d,w)->{
            if (CustomerDbHelper.empty(company.getText().toString())) { toast("公司名称不能为空"); return; }
            LinkedHashMap<String,String> r = new LinkedHashMap<>();
            r.put("公司名称", company.getText().toString());
            r.put("地址", address.getText().toString());
            r.put("联系人1", contact.getText().toString());
            r.put("电话号码1", phone.getText().toString());
            r.put("星级1", String.valueOf(star.getSelectedItemPosition()));
            r.put("备注1", note.getText().toString());
            db.importRows(Arrays.asList(r), group == null ? db.ensureGroup("默认分组") : group.id, false, "快速录入");
            db.logOperation("快速录入", "粘贴识别入库", company.getText().toString());
            toast("已入库/合并补充"); render();
        }).show();
    }

    private void showAddFollowDialog(long companyId, String defaultMethod, String defaultContent) {
        LinearLayout box = vertical(dp(8)); box.setPadding(dp(16), dp(8), dp(16), dp(8));
        Spinner method = new Spinner(this);
        List<String> methods = Arrays.asList("电话", "微信", "面访", "短信", "其他");
        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, methods);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); method.setAdapter(ad);
        int idx = methods.indexOf(defaultMethod); if (idx >= 0) method.setSelection(idx);
        EditText content = edit("跟进内容", defaultContent); content.setMinLines(4);
        EditText next = edit("下次跟进日期，例如 2026-06-30，可为空", "");
        box.addView(text("跟进方式", 13, Color.rgb(42,58,86), true)); box.addView(method); box.addView(content); box.addView(next);
        new AlertDialog.Builder(this).setTitle("新增跟进记录").setView(box).setNegativeButton("取消", null).setPositiveButton("保存", (d,w)->{
            db.addFollowRecord(companyId, (String) method.getSelectedItem(), content.getText().toString(), next.getText().toString());
            toast("已保存跟进记录"); showCompanyDetail(companyId);
        }).show();
    }

    private void showCallToFollowDialog(CustomerDbHelper.CallRecordItem cr) {
        String content = cr.callTimeText + " " + cr.callType + "电话，通话" + cr.duration + "秒。";
        showAddFollowDialog(cr.appCompanyId, "电话", content);
    }

    private void showFollowTaskPage(String mode) {
        String title = "today".equals(mode) ? "今日待跟进" : ("overdue".equals(mode) ? "逾期未跟进" : "即将跟进");
        ScrollView scroll = scroll(); LinearLayout box = vertical(dp(8)); box.setPadding(dp(12), dp(8), dp(12), dp(8)); scroll.addView(box);
        box.addView(titleRow(title, "根据企业跟进记录中的下次跟进日期生成"));
        List<CustomerDbHelper.FollowTaskItem> tasks = db.getFollowTasks(mode, 150);
        if (tasks.isEmpty()) box.addView(emptyBox("暂无" + title, "可在企业详情页新增跟进记录并设置下次跟进日期"));
        for (CustomerDbHelper.FollowTaskItem t : tasks) {
            LinearLayout card = card();
            card.addView(text(t.companyName + "｜" + (CustomerDbHelper.empty(t.customerStatus)?"未设置":t.customerStatus), 16, Color.rgb(22,38,64), true));
            card.addView(text("下次跟进：" + nvl(t.nextFollowDate,"未设置") + "｜方式：" + nvl(t.followMethod,"其他"), 13, Color.rgb(90,103,125), false));
            card.addView(text(nvl(t.content,""), 13, Color.rgb(90,103,125), false));
            LinearLayout ops = horizontal(0); card.addView(ops);
            ops.addView(smallButton("查看企业", v -> showCompanyDetail(t.companyId)), weightLp());
            ops.addView(smallButton("完成", v -> { db.markFollowDone(t.followId, true); showFollowTaskPage(mode); }), weightLp());
            box.addView(card);
        }
        new AlertDialog.Builder(this).setView(scroll).setPositiveButton("关闭", null).show();
    }

    private void showPotentialCustomerPage() {
        ScrollView scroll = scroll(); LinearLayout box = vertical(dp(8)); box.setPadding(dp(12), dp(8), dp(12), dp(8)); scroll.addView(box);
        box.addView(titleRow("潜力客户", "按潜力状态、3星号码、下次跟进、更新时间优先显示"));
        List<CustomerDbHelper.CompanyItem> list = db.getPotentialCompanies(150);
        if (list.isEmpty()) box.addView(emptyBox("暂无潜力客户", "可通过导入客户状态或详情页手动设置为潜力"));
        for (CustomerDbHelper.CompanyItem c : list) box.addView(companyCard(c));
        new AlertDialog.Builder(this).setView(scroll).setPositiveButton("关闭", null).show();
    }

    private void showCallLogPage() {
        ScrollView scroll = scroll();
        LinearLayout box = vertical(dp(8)); box.setPadding(dp(12), dp(8), dp(12), dp(8)); scroll.addView(box);
        box.addView(titleRow("通话记录匹配", "读取手机号、通话类型、时间、时长、系统联系人名，并手动匹配入库"));
        LinearLayout ops = horizontal(0); box.addView(ops);
        ops.addView(primaryButton("读取7天", v -> ensureCallLogPermissionThenRead(7)), weightLp());
        ops.addView(primaryButton("读取30天", v -> ensureCallLogPermissionThenRead(30)), weightLp());
        box.addView(sectionTitle("待处理 / 已匹配通话"));
        List<CustomerDbHelper.CallRecordItem> calls = db.getCallRecords("", 120);
        if (calls.isEmpty()) box.addView(emptyBox("暂无通话记录", "点击上方按钮读取最近通话记录"));
        for (CustomerDbHelper.CallRecordItem cr : calls) box.addView(callRecordCard(cr));
        new AlertDialog.Builder(this).setView(scroll).setPositiveButton("关闭", null).show();
    }

    private View callRecordCard(CustomerDbHelper.CallRecordItem cr) {
        LinearLayout card = card();
        card.addView(text(cr.phone + "  " + cr.callType + "  " + cr.callTimeText, 15, Color.rgb(22,38,64), true));
        String nameInfo = "系统名称：" + nvl(cr.cachedName, nvl(cr.deviceName, "无"));
        card.addView(text(nameInfo + "｜时长：" + cr.duration + "秒｜状态：" + cr.status, 13, Color.rgb(90,103,125), false));
        if (cr.appCompanyId > 0) card.addView(text("App匹配：" + cr.appCompanyName + " " + nvl(cr.appContactName, "") + "｜备注：" + nvl(cr.appNote, "无"), 13, Color.rgb(90,103,125), false));
        LinearLayout ops = horizontal(0); card.addView(ops);
        if (cr.appCompanyId > 0) {
            ops.addView(smallButton("查看企业", v -> showCompanyDetail(cr.appCompanyId)), weightLp());
            ops.addView(smallButton("生成跟进", v -> showCallToFollowDialog(cr)), weightLp());
        }
        ops.addView(smallButton("加入已有企业", v -> showAttachCallToCompany(cr)), weightLp());
        ops.addView(smallButton("新建企业", v -> showNewCompanyFromCall(cr)), weightLp());
        ops.addView(smallButton("忽略", v -> { db.updateCallRecordStatus(cr.id, "已忽略"); showCallLogPage(); }), weightLp());
        ops.addView(smallButton("无效", v -> { db.updateCallRecordStatus(cr.id, "无效"); showCallLogPage(); }), weightLp());
        return card;
    }

    private void ensureCallLogPermissionThenRead(int days) {
        if (checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.READ_CALL_LOG}, REQ_CALL_LOG_PERM);
        else readRecentCalls(days);
    }

    private void readRecentCalls(int days) {
        ProgressDialog pd = new ProgressDialog(this); pd.setTitle("读取通话记录"); pd.setMessage("正在读取最近" + days + "天通话记录……"); pd.setIndeterminate(true); pd.setCancelable(false); pd.show();
        new Thread(() -> {
            int added = 0;
            try {
                long since = System.currentTimeMillis() - days * 24L * 3600L * 1000L;
                String[] proj = {CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION, CallLog.Calls.CACHED_NAME};
                android.database.Cursor c = getContentResolver().query(CallLog.Calls.CONTENT_URI, proj, CallLog.Calls.DATE + ">?", new String[]{String.valueOf(since)}, CallLog.Calls.DATE + " DESC");
                if (c != null) {
                    try {
                        while (c.moveToNext()) {
                            String phone = c.getString(0); int type = c.getInt(1); long time = c.getLong(2); int dur = c.getInt(3); String cached = c.getString(4);
                            String callType = type == CallLog.Calls.OUTGOING_TYPE ? "呼出" : (type == CallLog.Calls.INCOMING_TYPE ? "呼入" : (type == CallLog.Calls.MISSED_TYPE ? "未接" : "其他"));
                            String t = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(new Date(time));
                            String deviceName = lookupDeviceContactName(phone);
                            if (db.insertCallRecordIfNew(phone, callType, time, t, dur, cached, deviceName)) added++;
                        }
                    } finally { c.close(); }
                }
                db.logOperation("通话记录", "读取通话记录", "最近" + days + "天，新增" + added + "条");
            } catch (Exception e) { final String msg=e.getMessage(); runOnUiThread(() -> toast("读取失败：" + msg)); }
            final int a = added;
            runOnUiThread(() -> { pd.dismiss(); toast("新增通话记录 " + a + " 条"); showCallLogPage(); });
        }).start();
    }

    private String lookupDeviceContactName(String phone) {
        try {
            Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phone));
            android.database.Cursor c = getContentResolver().query(uri, new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null);
            try { if (c != null && c.moveToFirst()) return c.getString(0); } finally { if (c != null) c.close(); }
        } catch (Exception ignored) {}
        return "";
    }

    private void showAttachCallToCompany(CustomerDbHelper.CallRecordItem cr) {
        EditText kw = edit("搜索企业名称 / 关键词", nvl(cr.cachedName, cr.deviceName));
        new AlertDialog.Builder(this).setTitle("加入已有企业：先搜索企业").setView(kw).setNegativeButton("取消", null).setPositiveButton("搜索", (d,w)->showCompanyChoiceForCall(cr, kw.getText().toString())).show();
    }

    private void showCompanyChoiceForCall(CustomerDbHelper.CallRecordItem cr, String keyword) {
        List<CustomerDbHelper.CompanyItem> list = db.getCompaniesPage(keyword, "", 0, 80, 0);
        if (list.isEmpty()) { toast("没有搜索到企业"); return; }
        String[] names = new String[list.size()];
        for (int i=0;i<list.size();i++) names[i] = list.get(i).name + "｜" + list.get(i).groupName + "｜联系人" + list.get(i).contactCount;
        new AlertDialog.Builder(this).setTitle("选择目标企业").setItems(names, (d, which)->showAttachConfirm(cr, list.get(which))).setNegativeButton("取消", null).show();
    }

    private void showAttachConfirm(CustomerDbHelper.CallRecordItem cr, CustomerDbHelper.CompanyItem company) {
        LinearLayout box = vertical(dp(8)); box.setPadding(dp(16), dp(8), dp(16), dp(8));
        EditText name = edit("联系人姓名，可为空", nvl(cr.cachedName, cr.deviceName));
        EditText phone = edit("手机号", cr.phone); phone.setInputType(InputType.TYPE_CLASS_PHONE);
        EditText note = edit("备注", "来自通话记录：" + cr.callType + "，" + cr.callTimeText + "，通话" + cr.duration + "秒"); note.setMinLines(3);
        box.addView(text("目标企业：" + company.name, 14, Color.rgb(42,58,86), true)); box.addView(name); box.addView(phone); box.addView(note);
        new AlertDialog.Builder(this).setTitle("确认添加到已有企业").setView(box).setNegativeButton("取消", null).setPositiveButton("确认添加", (d,w)->{
            db.attachCallToCompany(cr.id, company.id, name.getText().toString(), phone.getText().toString(), note.getText().toString());
            toast("已添加到企业"); showCallLogPage();
        }).show();
    }

    private void showNewCompanyFromCall(CustomerDbHelper.CallRecordItem cr) {
        LinearLayout box = vertical(dp(8)); box.setPadding(dp(16), dp(8), dp(16), dp(8));
        EditText group = edit("分组", "默认分组");
        EditText company = edit("公司名称（必填）", "");
        EditText contact = edit("联系人姓名", nvl(cr.cachedName, cr.deviceName));
        EditText phone = edit("手机号", cr.phone); phone.setInputType(InputType.TYPE_CLASS_PHONE);
        EditText note = edit("备注", "来自通话记录：" + cr.callType + "，" + cr.callTimeText + "，通话" + cr.duration + "秒"); note.setMinLines(3);
        box.addView(group); box.addView(company); box.addView(contact); box.addView(phone); box.addView(note);
        new AlertDialog.Builder(this).setTitle("新建企业并加入号码").setView(box).setNegativeButton("取消", null).setPositiveButton("确认新建", (d,w)->{
            if (CustomerDbHelper.empty(company.getText().toString())) { toast("公司名称不能为空"); return; }
            long cid = db.addCompany(group.getText().toString(), company.getText().toString(), "", "", "", "");
            db.attachCallToCompany(cr.id, cid, contact.getText().toString(), phone.getText().toString(), note.getText().toString());
            toast("已新建并入库"); showCallLogPage();
        }).show();
    }

    private void showBatchGroupDialog() {
        List<Long> ids = db.getCompanyIdsByFilter(currentSearch, currentStatus, currentGroupId, 50000);
        if (ids.isEmpty()) { toast("当前没有可操作的搜索/筛选结果"); return; }
        LinearLayout box = vertical(dp(8)); box.setPadding(dp(16), dp(8), dp(16), dp(8));
        List<CustomerDbHelper.GroupItem> groups = db.getGroups();
        Spinner sp = new Spinner(this);
        ArrayAdapter<CustomerDbHelper.GroupItem> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, groups); ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); sp.setAdapter(ad);
        EditText newGroup = edit("也可输入新分组名称，填写后优先生效", "");
        box.addView(text("当前将操作 " + ids.size() + " 家企业", 14, Color.rgb(42,58,86), true)); box.addView(sp); box.addView(newGroup);
        new AlertDialog.Builder(this).setTitle("搜索结果批量加入分组").setView(box).setNegativeButton("取消", null).setPositiveButton("确认", (d,w)->{
            long gid;
            if (!CustomerDbHelper.empty(newGroup.getText().toString())) gid = db.ensureGroup(newGroup.getText().toString());
            else { CustomerDbHelper.GroupItem g = (CustomerDbHelper.GroupItem) sp.getSelectedItem(); gid = g == null ? db.ensureGroup("默认分组") : g.id; }
            confirm("确认修改分组", "本次将把 " + ids.size() + " 家企业移动到该分组。Excel导入不会自动覆盖分组，但这里是你手动批量操作，会修改主分组。是否继续？", () -> { int n = db.moveCompaniesToGroup(ids, gid); toast("已移动 " + n + " 家企业"); render(); });
        }).show();
    }

    private void importCurrentSearchContacts() {
        List<CustomerDbHelper.CompanyItem> companies = db.getCompaniesPage(currentSearch, currentStatus, currentGroupId, 5000, 0);
        ArrayList<CustomerDbHelper.ContactItem> contacts = new ArrayList<>();
        for (CustomerDbHelper.CompanyItem c : companies) contacts.addAll(db.getContacts(c.id, false));
        importContactsWithPreview(contacts);
    }

    private void autoBackupBeforeV13Open() {
        try {
            SharedPreferences sp = getSharedPreferences("upgrade", MODE_PRIVATE);
            if (sp.getBoolean("v13_auto_backup_done", false)) return;
            File dbFile = getDatabasePath(CustomerDbHelper.DB_NAME);
            if (dbFile.exists()) {
                File dir = new File(getExternalFilesDir(null), "backups");
                if (!dir.exists()) dir.mkdirs();
                File out = new File(dir, "企业客户管家_V13升级前自动备份_" + new SimpleDateFormat("yyyyMMdd_HHmm", Locale.CHINA).format(new Date()) + ".db");
                copyFile(dbFile, out);
            }
            sp.edit().putBoolean("v13_auto_backup_done", true).apply();
        } catch (Exception ignored) {}
    }

    private void showBackupDialog() {
        LinearLayout box = vertical(dp(8)); box.setPadding(dp(16), dp(8), dp(16), dp(8));
        box.addView(infoCard("说明", "备份会保存完整数据库。恢复会覆盖当前App内数据，恢复前系统会先自动备份当前数据。"));
        box.addView(primaryButton("一键备份到App备份目录", v -> runMaintenance("一键备份", () -> createInternalBackup())));
        box.addView(primaryButton("导出完整备份文件", v -> exportBackupFile())) ;
        box.addView(primaryButton("从备份文件恢复", v -> openRestoreBackupFile()));
        new AlertDialog.Builder(this).setTitle("数据备份与恢复").setView(box).setPositiveButton("关闭", null).show();
    }

    private void createInternalBackup() {
        try {
            File dbFile = getDatabasePath(CustomerDbHelper.DB_NAME);
            File dir = new File(getExternalFilesDir(null), "backups");
            if (!dir.exists()) dir.mkdirs();
            File out = new File(dir, "企业客户管家备份_" + new SimpleDateFormat("yyyyMMdd_HHmm", Locale.CHINA).format(new Date()) + ".db");
            copyFile(dbFile, out);
            db.logOperation("数据备份", "一键备份", out.getAbsolutePath());
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private void exportBackupFile() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/octet-stream");
        i.putExtra(Intent.EXTRA_TITLE, "企业客户管家备份_" + new SimpleDateFormat("yyyyMMdd_HHmm", Locale.CHINA).format(new Date()) + ".db");
        startActivityForResult(i, REQ_BACKUP_EXPORT);
    }

    private void writeBackupToUri(Uri uri) {
        ProgressDialog pd = new ProgressDialog(this); pd.setTitle("正在导出备份"); pd.setMessage("正在复制数据库文件……"); pd.setIndeterminate(true); pd.setCancelable(false); pd.show();
        new Thread(() -> { try { copyFileToUri(getDatabasePath(CustomerDbHelper.DB_NAME), uri); db.logOperation("数据备份", "导出备份文件", String.valueOf(uri)); runOnUiThread(() -> { pd.dismiss(); toast("备份导出成功"); }); } catch(Exception e) { runOnUiThread(() -> { pd.dismiss(); new AlertDialog.Builder(this).setTitle("备份失败").setMessage(e.getMessage()).setPositiveButton("确定", null).show(); }); } }).start();
    }

    private void openRestoreBackupFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, REQ_BACKUP_RESTORE);
    }

    private void confirmRestoreFromUri(Uri uri) {
        confirm("恢复备份", "恢复备份会覆盖当前App内数据。系统将先自动备份当前数据，再执行恢复。是否继续？", () -> restoreBackupFromUri(uri));
    }

    private void restoreBackupFromUri(Uri uri) {
        ProgressDialog pd = new ProgressDialog(this); pd.setTitle("正在恢复备份"); pd.setMessage("请勿关闭App……"); pd.setIndeterminate(true); pd.setCancelable(false); pd.show();
        new Thread(() -> {
            try {
                createInternalBackup();
                db.close();
                File dbFile = getDatabasePath(CustomerDbHelper.DB_NAME);
                copyUriToFile(uri, dbFile);
                db = new CustomerDbHelper(this);
                db.logOperation("数据恢复", "从备份文件恢复", String.valueOf(uri));
                runOnUiThread(() -> { pd.dismiss(); new AlertDialog.Builder(this).setTitle("恢复完成").setMessage("备份已恢复。建议重新打开App确认数据。 ").setPositiveButton("确定", (d,w)->render()).show(); });
            } catch(Exception e) { runOnUiThread(() -> { pd.dismiss(); new AlertDialog.Builder(this).setTitle("恢复失败").setMessage(e.getMessage()).setPositiveButton("确定", null).show(); }); }
        }).start();
    }

    private void copyFile(File src, File dst) throws Exception { try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst)) { byte[] buf = new byte[8192]; int n; while ((n=in.read(buf))>0) out.write(buf,0,n); } }
    private void copyFileToUri(File src, Uri uri) throws Exception { try (InputStream in = new FileInputStream(src); OutputStream out = getContentResolver().openOutputStream(uri)) { byte[] buf = new byte[8192]; int n; while ((n=in.read(buf))>0) out.write(buf,0,n); } }
    private void copyUriToFile(Uri uri, File dst) throws Exception { try (InputStream in = getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(dst)) { byte[] buf = new byte[8192]; int n; while ((n=in.read(buf))>0) out.write(buf,0,n); } }

    private void exportExceptions() {
        List<CustomerDbHelper.ExceptionItem> list = db.getExceptions(5000);
        pendingExportHeaders = Arrays.asList("时间", "来源", "行号", "公司名称", "异常原因", "原始数据");
        pendingExportRows = new ArrayList<>();
        for (CustomerDbHelper.ExceptionItem it : list) pendingExportRows.add(Arrays.asList(nvl(it.createdAt,""), nvl(it.source,""), String.valueOf(it.rowNo), nvl(it.companyName,""), nvl(it.reason,""), nvl(it.rawText,"")));
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"); i.putExtra(Intent.EXTRA_TITLE, "企业客户管家_异常数据_" + new SimpleDateFormat("yyyyMMdd_HHmm", Locale.CHINA).format(new Date()) + ".xlsx"); startActivityForResult(i, REQ_EXPORT_XLSX);
    }

    private void showDuplicateSuspects() {
        ScrollView scroll = scroll(); LinearLayout box = vertical(dp(8)); box.setPadding(dp(12), dp(8), dp(12), dp(8)); scroll.addView(box);
        box.addView(titleRow("疑似重复企业", "只提示，不自动合并；合并必须手动确认"));
        List<CustomerDbHelper.DuplicateItem> list = db.findDuplicateSuspects(80);
        if (list.isEmpty()) box.addView(emptyBox("暂无疑似重复企业", "系统未发现明显重复"));
        for (CustomerDbHelper.DuplicateItem it : list) {
            LinearLayout card = card(); card.addView(text(it.reason, 14, Color.rgb(170,94,20), true));
            card.addView(text("A：" + it.companyAName, 15, Color.rgb(42,58,86), true)); card.addView(text("B：" + it.companyBName, 15, Color.rgb(42,58,86), true));
            LinearLayout ops = horizontal(0); card.addView(ops);
            ops.addView(smallButton("看A", v -> showCompanyDetail(it.companyAId)), weightLp());
            ops.addView(smallButton("看B", v -> showCompanyDetail(it.companyBId)), weightLp());
            ops.addView(smallButton("合并到A", v -> confirm("合并企业", "将把B的联系人、备注、跟进记录合并到A，并删除B。是否继续？", () -> { db.mergeCompanies(it.companyAId, it.companyBId); showDuplicateSuspects(); })), weightLp());
            ops.addView(smallButton("忽略", v -> { db.ignoreDuplicate(it.key); showDuplicateSuspects(); }), weightLp());
            box.addView(card);
        }
        new AlertDialog.Builder(this).setView(scroll).setPositiveButton("关闭", null).show();
    }

    private void showPhoneCleanPage() {
        List<CustomerDbHelper.PhoneIssueItem> issues = db.scanPhoneIssues(200);
        StringBuilder sb = new StringBuilder();
        int preview = Math.min(60, issues.size());
        for (int i=0;i<preview;i++) {
            CustomerDbHelper.PhoneIssueItem it = issues.get(i);
            sb.append(i+1).append(". ").append(it.reason).append("\n").append(nvl(it.companyName,"")).append(" ").append(nvl(it.contactName,"")).append(" ").append(nvl(it.phone,it.phoneNorm)).append(" → ").append(nvl(it.suggested,"")).append("\n\n");
        }
        if (issues.size() > preview) sb.append("……共发现 ").append(issues.size()).append(" 条提示");
        if (issues.isEmpty()) sb.append("暂无明显号码问题");
        new AlertDialog.Builder(this)
                .setTitle("号码质量清洗")
                .setMessage(sb.toString())
                .setNegativeButton("关闭", null)
                .setPositiveButton("确认清洗格式", (d,w)->confirm("确认清洗", "仅清洗空格、横线、+86等格式问题，不自动合并企业。是否继续？", () -> { int n=db.cleanPhoneFormats(); toast("已清洗 " + n + " 个号码"); render(); }))
                .show();
    }

    private void showGroupDetail(CustomerDbHelper.GroupItem g) {
        List<CustomerDbHelper.CompanyItem> cs = db.getCompaniesPage("", "", g.id, 100000, 0);
        int contact=0, imported=0, star1=0, star2=0, star3=0;
        for (CustomerDbHelper.CompanyItem c : cs) {
            contact += c.contactCount; imported += c.importedContactCount;
            for (CustomerDbHelper.ContactItem ct : db.getContacts(c.id, null)) { if (ct.starLevel==1) star1++; else if (ct.starLevel==2) star2++; else if (ct.starLevel>=3) star3++; }
        }
        new AlertDialog.Builder(this).setTitle("分组详情：" + g.name).setMessage("企业数量：" + cs.size() + "\n联系人数量：" + contact + "\n已导入：" + imported + "\n未导入：" + (contact-imported) + "\n三星号码：" + star3 + "\n二星号码：" + star2 + "\n一星号码：" + star1).setNegativeButton("关闭", null).setPositiveButton("导入通讯录", (d,w)->showGroupImportDialog(g)).show();
    }

    private void showGroupImportDialog(CustomerDbHelper.GroupItem g) {
        LinearLayout box = vertical(dp(8)); box.setPadding(dp(16), dp(8), dp(16), dp(8));
        EditText start = edit("起始企业序号", "1"); start.setInputType(InputType.TYPE_CLASS_NUMBER);
        EditText end = edit("结束企业序号", "100"); end.setInputType(InputType.TYPE_CLASS_NUMBER);
        box.addView(text("分组：" + g.name + "，将按星级优先导入未导入联系人", 14, Color.rgb(42,58,86), true)); box.addView(start); box.addView(end);
        new AlertDialog.Builder(this).setTitle("导入该分组到手机通讯录").setView(box).setNegativeButton("取消", null).setPositiveButton("预览", (d,w)->{
            int s = parseInt(start.getText().toString(), 1); int e = parseInt(end.getText().toString(), s);
            importContactsWithPreview(db.getUnimportedContactsByRange(g.id, s, e));
        }).show();
    }

    private void showOperationLogs() {
        StringBuilder sb = new StringBuilder();
        for (CustomerDbHelper.OperationLogItem it : db.getOperationLogs(80)) sb.append(it.createdAt).append("  ").append(it.type).append("｜").append(it.title).append("\n").append(it.detail).append("\n\n");
        if (sb.length()==0) sb.append("暂无操作日志");
        new AlertDialog.Builder(this).setTitle("操作日志").setMessage(sb.toString()).setPositiveButton("确定", null).show();
    }

    private void dial(String phone) {
        try { startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phone)))); }
        catch (Exception e) { toast("无法打开拨号器"); }
    }

    private List<String> splitSemi(String s) {
        ArrayList<String> list = new ArrayList<>();
        if (s == null) return list;
        for (String p : s.split("；|;")) if (!CustomerDbHelper.empty(p)) list.add(p.trim());
        return list;
    }

    private int parseInt(String s, int def) { try { return Integer.parseInt(s.trim()); } catch(Exception e){ return def; } }
    private String nvl(String s, String def) { return CustomerDbHelper.empty(s) ? def : s; }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private void confirm(String title, String msg, Runnable ok) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(msg).setNegativeButton("取消", null).setPositiveButton("确认", (d,w)->ok.run()).show();
    }

    private LinearLayout vertical(int gap) { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setShowDividers(LinearLayout.SHOW_DIVIDER_NONE); return l; }
    private LinearLayout horizontal(int gap) { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private ScrollView scroll() { ScrollView s = new ScrollView(this); s.setFillViewport(false); return s; }
    private LinearLayout.LayoutParams weightLp() { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1); lp.setMargins(dp(4), dp(4), dp(4), dp(4)); return lp; }

    private View header(String title, String subtitle) {
        LinearLayout h = vertical(dp(4));
        h.setPadding(dp(18), dp(18), dp(18), dp(18));
        h.setBackground(round(Color.rgb(30, 94, 255), dp(18), 0));
        h.addView(text(title, 24, Color.WHITE, true));
        h.addView(text(subtitle, 13, Color.rgb(230, 237, 255), false));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, 0, 0, dp(12)); h.setLayoutParams(lp);
        return h;
    }

    private View titleRow(String title, String subtitle) {
        LinearLayout row = vertical(0);
        row.addView(text(title, 22, Color.rgb(22, 38, 64), true));
        row.addView(text(subtitle, 13, Color.rgb(90, 103, 125), false));
        row.setPadding(0, 0, 0, dp(10));
        return row;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(s == null ? "" : s); v.setTextSize(sp); v.setTextColor(color); v.setLineSpacing(dp(2), 1.0f); if (bold) v.setTypeface(Typeface.DEFAULT_BOLD); return v;
    }

    private TextView sectionTitle(String s) {
        TextView v = text(s, 16, Color.rgb(22, 38, 64), true); v.setPadding(0, dp(14), 0, dp(8)); return v;
    }

    private LinearLayout statCard(String title, String value, String suffix) {
        LinearLayout c = card();
        c.addView(text(title, 13, Color.rgb(90, 103, 125), false));
        c.addView(text(value, 24, Color.rgb(30, 94, 255), true));
        c.addView(text(suffix, 12, Color.rgb(130, 142, 160), false));
        return c;
    }

    private View infoCard(String title, String msg) {
        LinearLayout c = card(); c.addView(text(title, 15, Color.rgb(22, 38, 64), true)); c.addView(text(msg, 13, Color.rgb(90, 103, 125), false)); return c;
    }

    private LinearLayout card() {
        LinearLayout c = vertical(dp(6));
        c.setPadding(dp(12), dp(10), dp(12), dp(10));
        c.setBackground(round(Color.WHITE, dp(14), Color.rgb(226, 232, 240)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, dp(5), 0, dp(5)); c.setLayoutParams(lp);
        return c;
    }

    private TextView emptyBox(String title, String msg) {
        TextView v = text(title + "\n" + msg, 15, Color.rgb(120, 132, 150), false); v.setGravity(Gravity.CENTER); v.setPadding(dp(16), dp(32), dp(16), dp(32)); v.setBackground(round(Color.WHITE, dp(14), Color.rgb(226, 232, 240))); return v;
    }

    private TextView label(String s) {
        String t = CustomerDbHelper.empty(s) ? "未设置" : s;
        int color = Color.rgb(104, 116, 140);
        if (CustomerDbHelper.STATUS_FOCUS.equals(t)) color = Color.rgb(30, 94, 255);
        else if (CustomerDbHelper.STATUS_FOLLOW.equals(t)) color = Color.rgb(224, 122, 0);
        else if (CustomerDbHelper.STATUS_IMPORTANT.equals(t)) color = Color.rgb(220, 38, 38);
        TextView v = text(t, 12, Color.WHITE, true); v.setGravity(Gravity.CENTER); v.setPadding(dp(8), dp(3), dp(8), dp(3)); v.setBackground(round(color, dp(999), 0)); return v;
    }

    private TextView chip(String s, boolean active, View.OnClickListener l) {
        TextView v = text(s, 13, active ? Color.WHITE : Color.rgb(42, 58, 86), true);
        v.setGravity(Gravity.CENTER); v.setPadding(dp(12), dp(7), dp(12), dp(7));
        v.setBackground(round(active ? Color.rgb(30,94,255) : Color.WHITE, dp(999), Color.rgb(220, 226, 236)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2); lp.setMargins(dp(4), 0, dp(4), 0); v.setLayoutParams(lp); v.setOnClickListener(l); return v;
    }

    private Button primaryButton(String s, View.OnClickListener l) { Button b = new Button(this); b.setText(s); b.setTextColor(Color.WHITE); b.setTextSize(14); b.setAllCaps(false); b.setBackground(round(Color.rgb(30,94,255), dp(12), 0)); b.setOnClickListener(l); return b; }
    private Button primaryButton(String s) { return primaryButton(s, v -> {}); }
    private Button smallButton(String s, View.OnClickListener l) { Button b = new Button(this); b.setText(s); b.setTextSize(12); b.setAllCaps(false); b.setTextColor(Color.rgb(30,94,255)); b.setBackground(round(Color.rgb(239, 244, 255), dp(10), Color.rgb(200, 215, 255))); b.setOnClickListener(l); return b; }

    private EditText edit(String hint, String val) {
        EditText e = new EditText(this); e.setHint(hint); e.setText(val == null ? "" : val); e.setTextSize(14); e.setSingleLine(false); e.setMinLines(1); e.setPadding(dp(10), 0, dp(10), 0); e.setBackground(round(Color.WHITE, dp(10), Color.rgb(220, 226, 236))); return e;
    }

    private GradientDrawable round(int color, int radius, int strokeColor) {
        GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(radius); if (strokeColor != 0) g.setStroke(dp(1), strokeColor); return g;
    }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
}

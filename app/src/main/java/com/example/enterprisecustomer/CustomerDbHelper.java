package com.example.enterprisecustomer;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

public class CustomerDbHelper extends SQLiteOpenHelper {
    public static final String DB_NAME = "enterprise_customer_manager.db";
    public static final int DB_VERSION = 7;

    public static final String STATUS_NONE = "未设置";
    public static final String STATUS_FOCUS = "关注";
    public static final String STATUS_FOLLOW = "跟进";
    public static final String STATUS_IMPORTANT = "潜力";
    public static final String STATUS_OLD_IMPORTANT = "重点";

    public CustomerDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE groups_tbl (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL UNIQUE," +
                "created_at TEXT NOT NULL" +
                ")");
        db.execSQL("CREATE TABLE companies (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "group_id INTEGER NOT NULL," +
                "seq INTEGER NOT NULL," +
                "name TEXT NOT NULL," +
                "normalized_name TEXT NOT NULL UNIQUE," +
                "industry TEXT," +
                "employee_count TEXT," +
                "region TEXT," +
                "address TEXT," +
                "customer_status TEXT," +
                "tags TEXT," +
                "extra_info TEXT," +
                "contact_count INTEGER NOT NULL DEFAULT 0," +
                "note_count INTEGER NOT NULL DEFAULT 0," +
                "tag_count INTEGER NOT NULL DEFAULT 0," +
                "imported_contact_count INTEGER NOT NULL DEFAULT 0," +
                "search_text TEXT," +
                "created_at TEXT NOT NULL," +
                "updated_at TEXT NOT NULL" +
                ")");
        db.execSQL("CREATE TABLE contacts (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "company_id INTEGER NOT NULL," +
                "contact_name TEXT," +
                "phone TEXT NOT NULL," +
                "phone_norm TEXT NOT NULL," +
                "contact_order INTEGER NOT NULL," +
                "star_level INTEGER NOT NULL DEFAULT 0," +
                "imported INTEGER NOT NULL DEFAULT 0," +
                "raw_contact_id INTEGER," +
                "imported_at TEXT," +
                "created_at TEXT NOT NULL" +
                ")");
        db.execSQL("CREATE TABLE notes (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "company_id INTEGER NOT NULL," +
                "content TEXT NOT NULL," +
                "created_at TEXT NOT NULL" +
                ")");
        createIndexesAndMaintenanceTables(db);
        seedDefaultTags(db);
        ContentValues cv = new ContentValues();
        cv.put("name", "默认分组");
        cv.put("created_at", now());
        db.insert("groups_tbl", null, cv);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            tryExec(db, "ALTER TABLE companies ADD COLUMN contact_count INTEGER NOT NULL DEFAULT 0");
            tryExec(db, "ALTER TABLE companies ADD COLUMN note_count INTEGER NOT NULL DEFAULT 0");
            tryExec(db, "ALTER TABLE companies ADD COLUMN tag_count INTEGER NOT NULL DEFAULT 0");
            tryExec(db, "ALTER TABLE companies ADD COLUMN imported_contact_count INTEGER NOT NULL DEFAULT 0");
            tryExec(db, "ALTER TABLE companies ADD COLUMN search_text TEXT");
        }
        if (oldVersion < 3) {
            tryExec(db, "ALTER TABLE contacts ADD COLUMN star_level INTEGER NOT NULL DEFAULT 0");
        }
        if (oldVersion < 4) {
            tryExec(db, "UPDATE companies SET customer_status='潜力' WHERE customer_status='重点'");
        }
        if (oldVersion < 5) {
            // V1.3：只新增表和索引，不删除原有企业、联系人、备注、分组数据。
            tryExec(db, "UPDATE companies SET customer_status='潜力' WHERE customer_status='重点'");
        }
        if (oldVersion < 6) {
            // V1.3.1：备份互传与体验增强版。只补充索引/维护表，不破坏旧数据。
            tryExec(db, "UPDATE companies SET customer_status='潜力' WHERE customer_status='重点'");
        }
        if (oldVersion < 7) {
            // V1.3.2：客户标签体系增强与多号码导入修正版。
            tryExec(db, "UPDATE companies SET customer_status='潜力' WHERE customer_status='重点'");
        }
        createIndexesAndMaintenanceTables(db);
        migrateLegacyTags(db);
        rebuildAllCaches(db);
    }

    private void tryExec(SQLiteDatabase db, String sql) {
        try { db.execSQL(sql); } catch (Exception ignored) {}
    }

    private void createIndexesAndMaintenanceTables(SQLiteDatabase db) {
        tryExec(db, "CREATE INDEX IF NOT EXISTS idx_companies_group_seq ON companies(group_id, seq, id)");
        tryExec(db, "CREATE INDEX IF NOT EXISTS idx_companies_status ON companies(customer_status)");
        tryExec(db, "CREATE INDEX IF NOT EXISTS idx_companies_name ON companies(normalized_name)");
        tryExec(db, "CREATE INDEX IF NOT EXISTS idx_companies_region ON companies(region)");
        tryExec(db, "CREATE INDEX IF NOT EXISTS idx_companies_industry ON companies(industry)");
        tryExec(db, "CREATE INDEX IF NOT EXISTS idx_companies_search ON companies(search_text)");
        tryExec(db, "CREATE INDEX IF NOT EXISTS idx_contacts_company ON contacts(company_id, contact_order)");
        tryExec(db, "CREATE INDEX IF NOT EXISTS idx_contacts_phone ON contacts(phone_norm)");
        tryExec(db, "CREATE INDEX IF NOT EXISTS idx_contacts_imported ON contacts(imported)");
        tryExec(db, "CREATE INDEX IF NOT EXISTS idx_contacts_star ON contacts(star_level)");
        tryExec(db, "CREATE INDEX IF NOT EXISTS idx_contacts_raw ON contacts(raw_contact_id)");
        tryExec(db, "CREATE INDEX IF NOT EXISTS idx_notes_company ON notes(company_id)");
        tryExec(db, "CREATE TABLE IF NOT EXISTS stats_cache (key TEXT PRIMARY KEY, value INTEGER NOT NULL DEFAULT 0, updated_at TEXT)");
        tryExec(db, "CREATE TABLE IF NOT EXISTS import_logs (id INTEGER PRIMARY KEY AUTOINCREMENT, file_name TEXT, started_at TEXT, finished_at TEXT, total_rows INTEGER DEFAULT 0, new_companies INTEGER DEFAULT 0, merged_companies INTEGER DEFAULT 0, new_contacts INTEGER DEFAULT 0, new_notes INTEGER DEFAULT 0, new_tags INTEGER DEFAULT 0, skipped_no_company INTEGER DEFAULT 0, duplicate_rows INTEGER DEFAULT 0, abnormal_rows INTEGER DEFAULT 0, message TEXT)");
        tryExec(db, "CREATE TABLE IF NOT EXISTS exception_records (id INTEGER PRIMARY KEY AUTOINCREMENT, source TEXT, row_no INTEGER, company_name TEXT, reason TEXT, raw_text TEXT, created_at TEXT)");
        tryExec(db, "CREATE TABLE IF NOT EXISTS sync_batches (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, scope_text TEXT, contact_count INTEGER DEFAULT 0, success_count INTEGER DEFAULT 0, failed_count INTEGER DEFAULT 0, created_at TEXT)");
        tryExec(db, "CREATE TABLE IF NOT EXISTS search_index (company_id INTEGER PRIMARY KEY, search_text TEXT, updated_at TEXT)");
        tryExec(db, "CREATE INDEX IF NOT EXISTS idx_search_index_text ON search_index(search_text)");
        tryExec(db, "CREATE TABLE IF NOT EXISTS call_records (id INTEGER PRIMARY KEY AUTOINCREMENT, phone TEXT, phone_norm TEXT, call_type TEXT, call_time INTEGER, call_time_text TEXT, duration INTEGER DEFAULT 0, cached_name TEXT, device_name TEXT, app_company_id INTEGER DEFAULT 0, app_company_name TEXT, app_contact_name TEXT, app_note TEXT, status TEXT NOT NULL DEFAULT '未处理', raw_key TEXT UNIQUE, created_at TEXT)");
        tryExec(db, "CREATE INDEX IF NOT EXISTS idx_call_records_phone ON call_records(phone_norm)");
        tryExec(db, "CREATE INDEX IF NOT EXISTS idx_call_records_status ON call_records(status)");
        tryExec(db, "CREATE TABLE IF NOT EXISTS operation_logs (id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT, title TEXT, detail TEXT, created_at TEXT)");
        tryExec(db, "CREATE INDEX IF NOT EXISTS idx_operation_logs_type ON operation_logs(type)");
        tryExec(db, "CREATE TABLE IF NOT EXISTS follow_records (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, follow_time TEXT, follow_method TEXT, content TEXT, next_follow_date TEXT, done INTEGER NOT NULL DEFAULT 0, created_at TEXT NOT NULL)");
        tryExec(db, "CREATE INDEX IF NOT EXISTS idx_follow_company ON follow_records(company_id, id)");
        tryExec(db, "CREATE INDEX IF NOT EXISTS idx_follow_next ON follow_records(next_follow_date, done)");
        tryExec(db, "CREATE TABLE IF NOT EXISTS duplicate_ignores (key TEXT PRIMARY KEY, created_at TEXT)");
        tryExec(db, "CREATE TABLE IF NOT EXISTS backup_logs (id INTEGER PRIMARY KEY AUTOINCREMENT, file_name TEXT, type TEXT, result TEXT, created_at TEXT)");
        tryExec(db, "ALTER TABLE backup_logs ADD COLUMN company_count INTEGER DEFAULT 0");
        tryExec(db, "ALTER TABLE backup_logs ADD COLUMN contact_count INTEGER DEFAULT 0");
        tryExec(db, "ALTER TABLE backup_logs ADD COLUMN note_count INTEGER DEFAULT 0");

        tryExec(db, "CREATE TABLE IF NOT EXISTS tags (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, normalized_name TEXT NOT NULL UNIQUE, created_at TEXT NOT NULL, updated_at TEXT NOT NULL)");
        tryExec(db, "CREATE TABLE IF NOT EXISTS company_tags (company_id INTEGER NOT NULL, tag_id INTEGER NOT NULL, created_at TEXT NOT NULL, PRIMARY KEY(company_id, tag_id))");
        tryExec(db, "CREATE INDEX IF NOT EXISTS idx_tags_norm ON tags(normalized_name)");
        tryExec(db, "CREATE INDEX IF NOT EXISTS idx_company_tags_tag ON company_tags(tag_id, company_id)");
        tryExec(db, "CREATE INDEX IF NOT EXISTS idx_company_tags_company ON company_tags(company_id)");
        tryExec(db, "UPDATE companies SET customer_status='潜力' WHERE customer_status='重点'");
    }

    public static String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date());
    }

    private static final String[] DEFAULT_TAGS = new String[]{
            "有意向","强意向","待报价","已报价","待回访","暂不需要",
            "老板电话","法人电话","采购电话","号码无效","空号","资料待完善",
            "老客户","新客户","转介绍","已加微信","已成交",
            "设备采购","设备租赁","售后需求","价格敏感","急需","长期跟进",
            "无效客户","拒绝联系","暂不合作","信息不准"
    };

    public static String normalizeTagName(String s) {
        if (s == null) return "";
        String t = s.trim().replace('；', ';').replace('，', ',').replace('、', ',');
        t = t.replaceAll("\\s+", "");
        return t;
    }

    private void seedDefaultTags(SQLiteDatabase db) {
        for (String tag : DEFAULT_TAGS) ensureTag(db, tag);
    }

    private void migrateLegacyTags(SQLiteDatabase db) {
        try {
            seedDefaultTags(db);
            Cursor c = db.rawQuery("SELECT id,tags FROM companies WHERE tags IS NOT NULL AND tags<>''", null);
            int linked = 0;
            try {
                while (c.moveToNext()) {
                    long companyId = c.getLong(0);
                    String tags = c.getString(1);
                    linked += addTagsToCompany(db, companyId, tags);
                    syncCompanyTagsText(db, companyId);
                }
            } finally { c.close(); }
            logOperationRaw(db, "标签迁移", "旧标签迁移完成", "建立/确认企业标签关联 " + linked + " 条");
        } catch (Exception ignored) {}
    }

    private void logOperationRaw(SQLiteDatabase db, String type, String title, String detail) {
        try {
            ContentValues cv = new ContentValues();
            cv.put("type", nonNull(type)); cv.put("title", nonNull(title)); cv.put("detail", nonNull(detail)); cv.put("created_at", now());
            db.insert("operation_logs", null, cv);
        } catch (Exception ignored) {}
    }

    private long ensureTag(SQLiteDatabase db, String name) {
        String n = normalizeTagName(name);
        if (empty(n)) return -1;
        Cursor c = db.rawQuery("SELECT id FROM tags WHERE normalized_name=?", new String[]{n});
        try { if (c.moveToFirst()) return c.getLong(0); } finally { c.close(); }
        ContentValues cv = new ContentValues();
        cv.put("name", n); cv.put("normalized_name", n); cv.put("created_at", now()); cv.put("updated_at", now());
        return db.insert("tags", null, cv);
    }

    private int addTagsToCompany(SQLiteDatabase db, long companyId, String tagText) {
        int count = 0;
        for (String tag : splitTagText(tagText)) {
            long tagId = ensureTag(db, tag);
            if (tagId <= 0) continue;
            ContentValues cv = new ContentValues();
            cv.put("company_id", companyId); cv.put("tag_id", tagId); cv.put("created_at", now());
            long r = db.insertWithOnConflict("company_tags", null, cv, SQLiteDatabase.CONFLICT_IGNORE);
            if (r > 0) count++;
        }
        if (count > 0) syncCompanyTagsText(db, companyId);
        return count;
    }

    private List<String> splitTagText(String tagText) {
        ArrayList<String> list = new ArrayList<>();
        LinkedHashSet<String> set = new LinkedHashSet<>();
        if (tagText == null) return list;
        String t = tagText.replace('\n', '；').replace('\r', '；').replace(',', '；').replace('，', '；').replace('、', '；').replace(';', '；');
        for (String p : t.split("；")) {
            String n = normalizeTagName(p);
            if (!empty(n)) set.add(n);
        }
        list.addAll(set);
        return list;
    }

    private String tagsStringFromTables(SQLiteDatabase db, long companyId) {
        StringBuilder sb = new StringBuilder();
        Cursor c = db.rawQuery("SELECT t.name FROM tags t JOIN company_tags ct ON t.id=ct.tag_id WHERE ct.company_id=? ORDER BY t.name", new String[]{String.valueOf(companyId)});
        try {
            while (c.moveToNext()) { if (sb.length() > 0) sb.append("；"); sb.append(c.getString(0)); }
        } finally { c.close(); }
        return sb.toString();
    }

    private void syncCompanyTagsText(SQLiteDatabase db, long companyId) {
        String tags = tagsStringFromTables(db, companyId);
        int tagCount = 0;
        if (!empty(tags)) for (String ignored : tags.split("；")) tagCount++;
        ContentValues cv = new ContentValues();
        cv.put("tags", tags); cv.put("tag_count", tagCount); cv.put("updated_at", now());
        db.update("companies", cv, "id=?", new String[]{String.valueOf(companyId)});
    }

    public List<TagItem> getAllTagsWithCounts() {
        ArrayList<TagItem> list = new ArrayList<>();
        SQLiteDatabase db = getWritableDatabase();
        createIndexesAndMaintenanceTables(db);
        migrateLegacyTags(db);
        Cursor c = db.rawQuery("SELECT t.id,t.name,COUNT(ct.company_id) cnt FROM tags t LEFT JOIN company_tags ct ON t.id=ct.tag_id GROUP BY t.id,t.name ORDER BY cnt DESC,t.name ASC", null);
        try { while (c.moveToNext()) { TagItem it = new TagItem(); it.id=c.getLong(0); it.name=c.getString(1); it.companyCount=c.getInt(2); list.add(it); } } finally { c.close(); }
        return list;
    }

    public List<String> getCompanyTagNames(long companyId) {
        ArrayList<String> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT t.name FROM tags t JOIN company_tags ct ON t.id=ct.tag_id WHERE ct.company_id=? ORDER BY t.name", new String[]{String.valueOf(companyId)});
        try { while (c.moveToNext()) list.add(c.getString(0)); } finally { c.close(); }
        if (list.isEmpty()) {
            CompanyItem cpy = getCompany(companyId);
            list.addAll(splitTagText(cpy.tags));
        }
        return list;
    }

    public int addTagToCompany(long companyId, String tagName) {
        SQLiteDatabase db = getWritableDatabase();
        int n = addTagsToCompany(db, companyId, tagName);
        updateCompanyCache(db, companyId); rebuildStatsCache(db);
        if (n > 0) logOperation("标签", "添加标签", "企业ID=" + companyId + " 标签=" + tagName);
        return n;
    }

    public void removeTagFromCompany(long companyId, String tagName) {
        String norm = normalizeTagName(tagName);
        if (empty(norm)) return;
        SQLiteDatabase db = getWritableDatabase();
        long tagId = -1;
        Cursor c = db.rawQuery("SELECT id FROM tags WHERE normalized_name=?", new String[]{norm});
        try { if (c.moveToFirst()) tagId = c.getLong(0); } finally { c.close(); }
        if (tagId > 0) db.delete("company_tags", "company_id=? AND tag_id=?", new String[]{String.valueOf(companyId), String.valueOf(tagId)});
        syncCompanyTagsText(db, companyId); updateCompanyCache(db, companyId); rebuildStatsCache(db);
        logOperation("标签", "移除企业标签", "企业ID=" + companyId + " 标签=" + tagName);
    }

    public int batchAddTag(List<Long> companyIds, String tagName) {
        if (companyIds == null || companyIds.isEmpty()) return 0;
        SQLiteDatabase db = getWritableDatabase(); int n = 0;
        db.beginTransaction();
        try { for (Long id : companyIds) { n += addTagsToCompany(db, id, tagName); updateCompanyCache(db, id); } rebuildStatsCache(db); db.setTransactionSuccessful(); }
        finally { db.endTransaction(); }
        logOperation("标签", "批量添加标签", "数量=" + companyIds.size() + " 标签=" + tagName);
        return n;
    }

    public int batchRemoveTag(List<Long> companyIds, String tagName) {
        if (companyIds == null || companyIds.isEmpty()) return 0;
        SQLiteDatabase db = getWritableDatabase(); String norm = normalizeTagName(tagName); int n = 0; long tagId = -1;
        Cursor c = db.rawQuery("SELECT id FROM tags WHERE normalized_name=?", new String[]{norm});
        try { if (c.moveToFirst()) tagId = c.getLong(0); } finally { c.close(); }
        if (tagId <= 0) return 0;
        db.beginTransaction();
        try { for (Long id : companyIds) { n += db.delete("company_tags", "company_id=? AND tag_id=?", new String[]{String.valueOf(id), String.valueOf(tagId)}); syncCompanyTagsText(db, id); updateCompanyCache(db, id); } rebuildStatsCache(db); db.setTransactionSuccessful(); }
        finally { db.endTransaction(); }
        logOperation("标签", "批量移除标签", "数量=" + companyIds.size() + " 标签=" + tagName);
        return n;
    }

    public void renameTag(String oldName, String newName) {
        String oldNorm = normalizeTagName(oldName), newNorm = normalizeTagName(newName);
        if (empty(oldNorm) || empty(newNorm)) return;
        SQLiteDatabase db = getWritableDatabase();
        long oldId = ensureTag(db, oldName); long newId = ensureTag(db, newName);
        if (oldId == newId) return;
        db.beginTransaction();
        try {
            db.execSQL("INSERT OR IGNORE INTO company_tags(company_id,tag_id,created_at) SELECT company_id," + newId + ",created_at FROM company_tags WHERE tag_id=" + oldId);
            db.delete("company_tags", "tag_id=?", new String[]{String.valueOf(oldId)});
            db.delete("tags", "id=?", new String[]{String.valueOf(oldId)});
            Cursor c = db.rawQuery("SELECT DISTINCT company_id FROM company_tags WHERE tag_id=?", new String[]{String.valueOf(newId)});
            try { while (c.moveToNext()) syncCompanyTagsText(db, c.getLong(0)); } finally { c.close(); }
            rebuildAllCaches(db); db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        logOperation("标签", "重命名标签", oldName + " → " + newName);
    }

    public void mergeTags(String fromName, String toName) { renameTag(fromName, toName); }

    public void deleteTag(String tagName) {
        String norm = normalizeTagName(tagName); if (empty(norm)) return;
        SQLiteDatabase db = getWritableDatabase(); long tagId = -1;
        Cursor c = db.rawQuery("SELECT id FROM tags WHERE normalized_name=?", new String[]{norm});
        try { if (c.moveToFirst()) tagId = c.getLong(0); } finally { c.close(); }
        if (tagId <= 0) return;
        ArrayList<Long> affected = new ArrayList<>();
        Cursor a = db.rawQuery("SELECT company_id FROM company_tags WHERE tag_id=?", new String[]{String.valueOf(tagId)});
        try { while (a.moveToNext()) affected.add(a.getLong(0)); } finally { a.close(); }
        db.beginTransaction();
        try { db.delete("company_tags", "tag_id=?", new String[]{String.valueOf(tagId)}); db.delete("tags", "id=?", new String[]{String.valueOf(tagId)}); for (Long id : affected) syncCompanyTagsText(db, id); rebuildAllCaches(db); db.setTransactionSuccessful(); }
        finally { db.endTransaction(); }
        logOperation("标签", "删除标签", "标签=" + tagName + " 影响企业=" + affected.size());
    }

    public int countCompaniesByTag(String tagName) {
        String norm = normalizeTagName(tagName); if (empty(norm)) return 0;
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(DISTINCT ct.company_id) FROM company_tags ct JOIN tags t ON ct.tag_id=t.id WHERE t.normalized_name=?", new String[]{norm});
        try { return c.moveToFirst() ? c.getInt(0) : 0; } finally { c.close(); }
    }

    private boolean companyHasTag(SQLiteDatabase db, long companyId, String tagName) {
        String norm = normalizeTagName(tagName); if (empty(norm)) return true;
        Cursor c = db.rawQuery("SELECT 1 FROM company_tags ct JOIN tags t ON ct.tag_id=t.id WHERE ct.company_id=? AND t.normalized_name=? LIMIT 1", new String[]{String.valueOf(companyId), norm});
        try { return c.moveToFirst(); } finally { c.close(); }
    }


    public static String normalizeCompany(String s) {
        if (s == null) return "";
        String t = s.trim();
        t = t.replace('\u3000', ' ');
        t = t.replaceAll("\\s+", "");
        t = t.replace('（', '(').replace('）', ')');
        return t;
    }

    public static String normalizePhone(String s) {
        if (s == null) return "";
        String t = s.trim();
        t = t.replaceAll("[^0-9+]", "");
        if (t.startsWith("+86")) t = t.substring(3);
        if (t.startsWith("86") && t.length() == 13) t = t.substring(2);
        return t;
    }

    public static List<String> splitPhones(String raw) {
        ArrayList<String> out = new ArrayList<>();
        LinkedHashSet<String> set = new LinkedHashSet<>();
        if (raw == null) return out;
        String text = raw.trim();
        if (text.isEmpty()) return out;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\+?86[-\\s]?)?(1[3-9]\\d{9}|0\\d{2,3}[-\\s]?\\d{7,8}|400[-\\s]?\\d{3}[-\\s]?\\d{4})").matcher(text);
        while (m.find()) {
            String n = normalizePhone(m.group());
            if (!empty(n) && n.length() >= 5) set.add(n);
        }
        if (set.isEmpty()) {
            String t = text.replace('，', ',').replace('、', ',').replace('；', ',').replace(';', ',').replace('/', ',').replace('\\', ',').replace('\n', ',').replace('\r', ',').replace('\t', ',');
            for (String p : t.split(",|\\s+")) {
                String n = normalizePhone(p);
                if (!empty(n) && n.length() >= 5) set.add(n);
            }
        }
        out.addAll(set);
        return out;
    }

    public static String nonNull(String s) { return s == null ? "" : s; }
    public static boolean empty(String s) { return s == null || s.trim().isEmpty(); }

    public static String normalizeStatus(String s) {
        if (empty(s)) return "";
        String t = s.trim();
        if (STATUS_NONE.equals(t)) return "";
        if (STATUS_FOCUS.equals(t)) return STATUS_FOCUS;
        if (STATUS_FOLLOW.equals(t)) return STATUS_FOLLOW;
        if (STATUS_IMPORTANT.equals(t) || STATUS_OLD_IMPORTANT.equals(t) || "重要".equals(t) || "高潜".equals(t)) return STATUS_IMPORTANT;
        return "";
    }

    private static int statusRank(String status) {
        String s = normalizeStatus(status);
        if (STATUS_IMPORTANT.equals(s)) return 3;
        if (STATUS_FOLLOW.equals(s)) return 2;
        if (STATUS_FOCUS.equals(s)) return 1;
        return 0;
    }

    private boolean applyStatusIfHigher(SQLiteDatabase db, long companyId, String incomingStatus) {
        String status = normalizeStatus(incomingStatus);
        if (empty(status)) return false;
        String old = "";
        Cursor c = db.rawQuery("SELECT customer_status FROM companies WHERE id=?", new String[]{String.valueOf(companyId)});
        try { if (c.moveToFirst()) old = c.getString(0); } finally { c.close(); }
        if (statusRank(status) > statusRank(old)) {
            ContentValues cv = new ContentValues();
            cv.put("customer_status", status);
            cv.put("updated_at", now());
            db.update("companies", cv, "id=?", new String[]{String.valueOf(companyId)});
            return true;
        }
        return false;
    }

    public long ensureGroup(String name) {
        if (empty(name)) name = "默认分组";
        name = name.trim();
        SQLiteDatabase db = getWritableDatabase();
        Cursor c = db.rawQuery("SELECT id FROM groups_tbl WHERE name=?", new String[]{name});
        try {
            if (c.moveToFirst()) return c.getLong(0);
        } finally { c.close(); }
        ContentValues cv = new ContentValues();
        cv.put("name", name);
        cv.put("created_at", now());
        return db.insert("groups_tbl", null, cv);
    }

    public List<GroupItem> getGroups() {
        ArrayList<GroupItem> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT id,name FROM groups_tbl ORDER BY id", null);
        try {
            while (c.moveToNext()) list.add(new GroupItem(c.getLong(0), c.getString(1)));
        } finally { c.close(); }
        return list;
    }

    public String getGroupName(long id) {
        Cursor c = getReadableDatabase().rawQuery("SELECT name FROM groups_tbl WHERE id=?", new String[]{String.valueOf(id)});
        try { if (c.moveToFirst()) return c.getString(0); } finally { c.close(); }
        return "默认分组";
    }

    public void renameGroup(long id, String name) {
        if (empty(name)) return;
        ContentValues cv = new ContentValues();
        cv.put("name", name.trim());
        getWritableDatabase().update("groups_tbl", cv, "id=?", new String[]{String.valueOf(id)});
    }

    public void deleteGroup(long id) {
        long defaultId = ensureGroup("默认分组");
        if (id == defaultId) return;
        ContentValues cv = new ContentValues();
        cv.put("group_id", defaultId);
        getWritableDatabase().update("companies", cv, "group_id=?", new String[]{String.valueOf(id)});
        getWritableDatabase().delete("groups_tbl", "id=?", new String[]{String.valueOf(id)});
        resequenceGroup(defaultId);
    }

    private int nextSeq(SQLiteDatabase db, long groupId) {
        Cursor c = db.rawQuery("SELECT COALESCE(MAX(seq),0)+1 FROM companies WHERE group_id=?", new String[]{String.valueOf(groupId)});
        try { return c.moveToFirst() ? c.getInt(0) : 1; } finally { c.close(); }
    }


    private void rebuildAllCaches(SQLiteDatabase db) {
        Cursor c = db.rawQuery("SELECT id FROM companies", null);
        try {
            while (c.moveToNext()) updateCompanyCache(db, c.getLong(0));
        } finally { c.close(); }
        rebuildStatsCache(db);
    }

    public void rebuildAllCaches() {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            rebuildAllCaches(db);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public void rebuildSearchIndex() {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("search_index", null, null);
            Cursor c = db.rawQuery("SELECT id FROM companies", null);
            try { while (c.moveToNext()) updateCompanyCache(db, c.getLong(0)); } finally { c.close(); }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public void vacuumDatabase() { getWritableDatabase().execSQL("VACUUM"); }

    public void rebuildIndexes() {
        SQLiteDatabase db = getWritableDatabase();
        createIndexesAndMaintenanceTables(db);
    }

    private void updateCompanyCache(SQLiteDatabase db, long companyId) {
        int contactCount = intQuery(db, "SELECT COUNT(*) FROM contacts WHERE company_id=" + companyId);
        int noteCount = intQuery(db, "SELECT COUNT(*) FROM notes WHERE company_id=" + companyId);
        int importedCount = intQuery(db, "SELECT COUNT(*) FROM contacts WHERE company_id=" + companyId + " AND imported=1");
        String tagsFromTable = tagsStringFromTables(db, companyId);
        if (!empty(tagsFromTable)) {
            ContentValues tv = new ContentValues(); tv.put("tags", tagsFromTable);
            db.update("companies", tv, "id=?", new String[]{String.valueOf(companyId)});
        }
        CompanyItem c = getCompanyLight(db, companyId);
        String searchText = buildSearchText(db, companyId, c);
        int tagCount = intQuery(db, "SELECT COUNT(*) FROM company_tags WHERE company_id=" + companyId);
        ContentValues cv = new ContentValues();
        cv.put("contact_count", contactCount);
        cv.put("note_count", noteCount);
        cv.put("tag_count", tagCount);
        cv.put("imported_contact_count", importedCount);
        cv.put("search_text", searchText);
        cv.put("updated_at", now());
        db.update("companies", cv, "id=?", new String[]{String.valueOf(companyId)});
        ContentValues si = new ContentValues();
        si.put("company_id", companyId);
        si.put("search_text", searchText);
        si.put("updated_at", now());
        db.insertWithOnConflict("search_index", null, si, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private CompanyItem getCompanyLight(SQLiteDatabase db, long id) {
        CompanyItem it = new CompanyItem();
        Cursor c = db.rawQuery("SELECT c.id,c.group_id,g.name,c.seq,c.name,c.industry,c.employee_count,c.region,c.address,c.customer_status,c.tags,c.extra_info,c.created_at,c.updated_at FROM companies c LEFT JOIN groups_tbl g ON c.group_id=g.id WHERE c.id=?", new String[]{String.valueOf(id)});
        try {
            if (c.moveToFirst()) {
                int i=0;
                it.id=c.getLong(i++); it.groupId=c.getLong(i++); it.groupName=c.getString(i++); it.seq=c.getInt(i++); it.name=c.getString(i++);
                it.industry=c.getString(i++); it.employeeCount=c.getString(i++); it.region=c.getString(i++); it.address=c.getString(i++);
                it.customerStatus=normalizeStatus(c.getString(i++)); it.tags=c.getString(i++); it.extraInfo=c.getString(i++); it.createdAt=c.getString(i++); it.updatedAt=c.getString(i++);
            }
        } finally { c.close(); }
        return it;
    }

    private String buildSearchText(SQLiteDatabase db, long companyId, CompanyItem c) {
        StringBuilder sb = new StringBuilder();
        sb.append(nonNull(c.name)).append(' ').append(nonNull(c.groupName)).append(' ').append(nonNull(c.industry)).append(' ').append(nonNull(c.region)).append(' ').append(nonNull(c.address)).append(' ').append(nonNull(c.tags)).append(' ').append(nonNull(c.extraInfo));
        Cursor ct = db.rawQuery("SELECT contact_name,phone FROM contacts WHERE company_id=?", new String[]{String.valueOf(companyId)});
        try { while (ct.moveToNext()) sb.append(' ').append(nonNull(ct.getString(0))).append(' ').append(nonNull(ct.getString(1))); } finally { ct.close(); }
        Cursor nt = db.rawQuery("SELECT content FROM notes WHERE company_id=?", new String[]{String.valueOf(companyId)});
        try { while (nt.moveToNext()) sb.append(' ').append(nonNull(nt.getString(0))); } finally { nt.close(); }
        Cursor fr = db.rawQuery("SELECT follow_method,content,next_follow_date FROM follow_records WHERE company_id=?", new String[]{String.valueOf(companyId)});
        try { while (fr.moveToNext()) sb.append(' ').append(nonNull(fr.getString(0))).append(' ').append(nonNull(fr.getString(1))).append(' ').append(nonNull(fr.getString(2))); } finally { fr.close(); }
        return sb.toString().toLowerCase(Locale.CHINA);
    }

    private void rebuildStatsCache(SQLiteDatabase db) {
        putStat(db, "company_count", intQuery(db, "SELECT COUNT(*) FROM companies"));
        putStat(db, "contact_count", intQuery(db, "SELECT COUNT(*) FROM contacts"));
        putStat(db, "imported_count", intQuery(db, "SELECT COUNT(*) FROM contacts WHERE imported=1"));
        putStat(db, "status_none", intQuery(db, "SELECT COUNT(*) FROM companies WHERE customer_status IS NULL OR customer_status='' "));
        putStat(db, "focus_count", intQuery(db, "SELECT COUNT(*) FROM companies WHERE customer_status='关注'"));
        putStat(db, "follow_count", intQuery(db, "SELECT COUNT(*) FROM companies WHERE customer_status='跟进'"));
        putStat(db, "important_count", intQuery(db, "SELECT COUNT(*) FROM companies WHERE customer_status='潜力' OR customer_status='重点'"));
        putStat(db, "exception_count", intQuery(db, "SELECT COUNT(*) FROM exception_records"));
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date());
        putStat(db, "today_follow_count", intQuery(db, "SELECT COUNT(DISTINCT company_id) FROM follow_records WHERE done=0 AND next_follow_date='" + today + "'"));
        putStat(db, "overdue_follow_count", intQuery(db, "SELECT COUNT(DISTINCT company_id) FROM follow_records WHERE done=0 AND next_follow_date<>'' AND next_follow_date<'" + today + "'"));
        putStat(db, "soon_follow_count", intQuery(db, "SELECT COUNT(DISTINCT company_id) FROM follow_records WHERE done=0 AND next_follow_date>'" + today + "' AND next_follow_date<=date('" + today + "','+7 day')"));
        putStat(db, "call_pending_count", intQuery(db, "SELECT COUNT(*) FROM call_records WHERE status='未处理' OR status='已匹配'"));
    }

    private void putStat(SQLiteDatabase db, String key, int value) {
        ContentValues cv = new ContentValues();
        cv.put("key", key); cv.put("value", value); cv.put("updated_at", now());
        db.insertWithOnConflict("stats_cache", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private int getCachedStat(SQLiteDatabase db, String key) {
        Cursor c = db.rawQuery("SELECT value FROM stats_cache WHERE key=?", new String[]{key});
        try { return c.moveToFirst() ? c.getInt(0) : -1; } finally { c.close(); }
    }

    private long startImportLog(SQLiteDatabase db, String fileName, int totalRows) {
        ContentValues cv = new ContentValues();
        cv.put("file_name", nonNull(fileName)); cv.put("started_at", now()); cv.put("total_rows", totalRows);
        return db.insert("import_logs", null, cv);
    }

    private void finishImportLog(SQLiteDatabase db, long logId, ImportResult r, String message) {
        if (logId <= 0) return;
        ContentValues cv = new ContentValues();
        cv.put("finished_at", now()); cv.put("new_companies", r.newCompanies); cv.put("merged_companies", r.mergedCompanies);
        cv.put("new_contacts", r.newContacts); cv.put("new_notes", r.newNotes); cv.put("new_tags", r.newTags);
        cv.put("skipped_no_company", r.skippedNoCompany); cv.put("duplicate_rows", r.duplicateRows); cv.put("abnormal_rows", r.abnormalRows); cv.put("message", nonNull(message));
        db.update("import_logs", cv, "id=?", new String[]{String.valueOf(logId)});
    }

    private void addException(SQLiteDatabase db, String source, int rowNo, String company, String reason, String raw) {
        ContentValues cv = new ContentValues();
        cv.put("source", nonNull(source)); cv.put("row_no", rowNo); cv.put("company_name", nonNull(company)); cv.put("reason", reason); cv.put("raw_text", nonNull(raw)); cv.put("created_at", now());
        db.insert("exception_records", null, cv);
    }

    public void resequenceGroup(long groupId) {
        SQLiteDatabase db = getWritableDatabase();
        Cursor c = db.rawQuery("SELECT id FROM companies WHERE group_id=? ORDER BY seq ASC,id ASC", new String[]{String.valueOf(groupId)});
        int seq = 1;
        db.beginTransaction();
        try {
            while (c.moveToNext()) {
                ContentValues cv = new ContentValues();
                cv.put("seq", seq++);
                db.update("companies", cv, "id=?", new String[]{String.valueOf(c.getLong(0))});
            }
            db.setTransactionSuccessful();
        } finally { c.close(); db.endTransaction(); }
    }

    public ImportPreviewResult previewImportRows(List<Map<String, String>> rows, long fallbackGroupId, boolean preferExcelGroup, String fileName, String defaultStatus) {
        ImportPreviewResult r = new ImportPreviewResult();
        SQLiteDatabase db = getReadableDatabase();
        Map<String, Long> existingCompanies = new HashMap<>();
        Cursor ec = db.rawQuery("SELECT normalized_name,id FROM companies", null);
        try { while (ec.moveToNext()) existingCompanies.put(ec.getString(0), ec.getLong(1)); } finally { ec.close(); }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        if (rows == null) return r;
        int rowNo = 1;
        for (Map<String, String> row : rows) {
            rowNo++;
            String companyName = first(row, "公司名称", "企业名称", "单位名称", "客户名称", "公司", "企业", "名称");
            if (empty(companyName)) { r.skippedNoCompany++; r.abnormalRows++; continue; }
            String norm = normalizeCompany(companyName);
            if (empty(norm)) { r.skippedNoCompany++; r.abnormalRows++; continue; }
            if (!seen.add(norm)) r.duplicateRows++;
            String rowStatus = first(row, "客户状态", "状态", "客户等级", "跟进状态");
            String importStatus = empty(rowStatus) ? normalizeStatus(defaultStatus) : normalizeStatus(rowStatus);
            Long existed = existingCompanies.get(norm);
            long companyId = existed == null ? -1 : existed;
            if (companyId <= 0) r.newCompanies++; else r.mergedCompanies++;
            if (!empty(importStatus)) {
                if (companyId <= 0) r.statusUpgradeCount++;
                else {
                    CompanyItem old = getCompanyLight(db, companyId);
                    if (statusRank(importStatus) > statusRank(old.customerStatus)) r.statusUpgradeCount++;
                    else r.statusKeepCount++;
                }
            }
            for (int i=1;i<=80;i++) {
                String phone = first(row, "电话号码"+i, "手机号"+i, "联系电话"+i, "电话"+i, "手机"+i);
                if (!empty(phone)) previewPhoneCell(db, r, companyId, phone);
            }
            String phone = first(row, "电话号码", "手机号", "联系电话", "电话", "手机", "移动电话", "联系方式");
            if (!empty(phone)) previewPhoneCell(db, r, companyId, phone);
            for (int i=1;i<=100;i++) {
                String note = first(row, "备注"+i, "备注信息"+i, "跟进记录"+i, "记录"+i);
                if (!empty(note)) r.newNotes++;
            }
            String note = first(row, "备注", "备注信息", "说明");
            if (!empty(note)) r.newNotes++;
            for (int i=1;i<=20;i++) {
                String follow = first(row, "跟进内容"+i, "跟进记录内容"+i, "跟进"+i);
                if (!empty(follow)) r.newFollows++;
            }
            String follow = first(row, "跟进内容", "跟进记录内容", "跟进");
            if (!empty(follow)) r.newFollows++;
        }
        return r;
    }

    private void previewPhoneCell(SQLiteDatabase db, ImportPreviewResult r, long companyId, String phoneCell) {
        List<String> phones = splitPhones(phoneCell);
        if (phones.size() > 1) { r.multiPhoneCellCount++; r.splitPhoneCount += phones.size(); }
        if (phones.isEmpty()) { r.phoneAbnormalCount++; r.abnormalRows++; return; }
        for (String pn : phones) {
            if (empty(pn) || pn.length() < 5) { r.phoneAbnormalCount++; r.abnormalRows++; }
            else if (companyId > 0 && contactExists(db, companyId, pn)) r.duplicatePhoneCount++;
            else r.newPhoneCount++;
        }
    }

    private boolean contactExists(SQLiteDatabase db, long companyId, String phoneNorm) {
        Cursor c = db.rawQuery("SELECT id FROM contacts WHERE company_id=? AND phone_norm=?", new String[]{String.valueOf(companyId), phoneNorm});
        try { return c.moveToFirst(); } finally { c.close(); }
    }

    public ImportResult importRows(List<Map<String, String>> rows, long fallbackGroupId, boolean preferExcelGroup) {
        return importRows(rows, fallbackGroupId, preferExcelGroup, "Excel导入", "");
    }

    public ImportResult importRows(List<Map<String, String>> rows, long fallbackGroupId, boolean preferExcelGroup, String fileName) {
        return importRows(rows, fallbackGroupId, preferExcelGroup, fileName, "");
    }

    public ImportResult importRows(List<Map<String, String>> rows, long fallbackGroupId, boolean preferExcelGroup, String fileName, String defaultStatus) {
        ImportResult result = new ImportResult();
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        long logId = 0;
        try {
            createIndexesAndMaintenanceTables(db);
            logId = startImportLog(db, fileName, rows == null ? 0 : rows.size());
            Map<String, Long> existingCompanies = new HashMap<>();
            Cursor ec = db.rawQuery("SELECT normalized_name,id FROM companies", null);
            try { while (ec.moveToNext()) existingCompanies.put(ec.getString(0), ec.getLong(1)); } finally { ec.close(); }
            Map<String, Long> groupCache = new HashMap<>();
            for (GroupItem g : getGroups()) groupCache.put(g.name, g.id);
            LinkedHashSet<String> rowSeen = new LinkedHashSet<>();
            LinkedHashSet<Long> affectedCompanies = new LinkedHashSet<>();
            int rowNo = 1;
            if (rows != null) {
                for (Map<String, String> row : rows) {
                    rowNo++;
                    String companyName = first(row, "公司名称", "企业名称", "单位名称", "客户名称", "公司", "企业", "名称");
                    if (empty(companyName)) { result.skippedNoCompany++; addException(db, fileName, rowNo, "", "公司名称为空", String.valueOf(row)); continue; }
                    String norm = normalizeCompany(companyName);
                    if (empty(norm)) { result.skippedNoCompany++; addException(db, fileName, rowNo, companyName, "公司名称清洗后为空", String.valueOf(row)); continue; }
                    if (!rowSeen.add(norm)) result.duplicateRows++;
                    String groupName = preferExcelGroup ? first(row, "分组", "客户分组", "组别") : "";
                    long groupId;
                    if (empty(groupName)) groupId = fallbackGroupId;
                    else if (groupCache.containsKey(groupName.trim())) groupId = groupCache.get(groupName.trim());
                    else { groupId = ensureGroup(groupName); groupCache.put(groupName.trim(), groupId); }
                    String rowStatus = first(row, "客户状态", "状态", "客户等级", "跟进状态");
                    String importStatus = empty(rowStatus) ? normalizeStatus(defaultStatus) : normalizeStatus(rowStatus);
                    Long cid = existingCompanies.get(norm);
                    long companyId;
                    if (cid == null || cid <= 0) {
                        companyId = insertCompany(db, row, companyName, groupId, importStatus);
                        existingCompanies.put(norm, companyId);
                        result.newCompanies++;
                        if (!empty(importStatus)) result.statusUpgradeCount++;
                    } else {
                        companyId = cid;
                        supplementCompany(db, companyId, row);
                        if (applyStatusIfHigher(db, companyId, importStatus)) result.statusUpgradeCount++;
                        else if (!empty(importStatus)) result.statusKeepCount++;
                        result.mergedCompanies++;
                    }
                    countMultiPhoneCells(row, result);
                    int contacts = importContactsForCompany(db, companyId, row);
                    int notes = importNotesForCompany(db, companyId, row);
                    int follows = importFollowRecordsForCompany(db, companyId, row);
                    int tags = importTagsAndExtraForCompany(db, companyId, row);
                    result.newContacts += contacts;
                    result.newNotes += notes;
                    result.newFollows += follows;
                    result.newTags += tags;
                    if (contacts == 0 && empty(first(row, "电话号码", "手机号", "联系电话", "电话", "手机", "移动电话", "联系方式"))) {
                        // 企业客户允许无电话，不作为错误，只记录为可查看异常提醒
                    }
                    affectedCompanies.add(companyId);
                }
            }
            for (Long id : affectedCompanies) updateCompanyCache(db, id);
            rebuildStatsCache(db);
            finishImportLog(db, logId, result, "导入完成");
            db.setTransactionSuccessful();
        } catch (Exception ex) {
            try { finishImportLog(db, logId, result, "导入异常：" + ex.getMessage()); } catch (Exception ignored) {}
            throw new RuntimeException(ex);
        } finally { db.endTransaction(); }
        return result;
    }

    private long findCompanyId(SQLiteDatabase db, String companyName) {
        String norm = normalizeCompany(companyName);
        Cursor c = db.rawQuery("SELECT id FROM companies WHERE normalized_name=?", new String[]{norm});
        try { return c.moveToFirst() ? c.getLong(0) : -1; } finally { c.close(); }
    }

    private long insertCompany(SQLiteDatabase db, Map<String, String> row, String name, long groupId, String importStatus) {
        ContentValues cv = new ContentValues();
        cv.put("group_id", groupId);
        cv.put("seq", nextSeq(db, groupId));
        cv.put("name", name.trim());
        cv.put("normalized_name", normalizeCompany(name));
        cv.put("industry", first(row, "所属行业", "行业", "经营行业"));
        cv.put("employee_count", first(row, "参保人数", "人数", "员工人数"));
        cv.put("region", first(row, "区域", "地区", "所在区域"));
        cv.put("address", first(row, "地址", "企业地址", "注册地址", "经营地址"));
        cv.put("customer_status", normalizeStatus(importStatus));
        cv.put("tags", "");
        cv.put("extra_info", collectExtraValues(row));
        cv.put("created_at", now());
        cv.put("updated_at", now());
        long id = db.insert("companies", null, cv);
        addTagsToCompany(db, id, collectTagValues(row));
        syncCompanyTagsText(db, id);
        return id;
    }

    private void supplementCompany(SQLiteDatabase db, long companyId, Map<String, String> row) {
        CompanyItem old = getCompany(companyId);
        ContentValues cv = new ContentValues();
        putIfOldEmpty(cv, "industry", old.industry, first(row, "所属行业", "行业", "经营行业"));
        putIfOldEmpty(cv, "employee_count", old.employeeCount, first(row, "参保人数", "人数", "员工人数"));
        putIfOldEmpty(cv, "region", old.region, first(row, "区域", "地区", "所在区域"));
        putIfOldEmpty(cv, "address", old.address, first(row, "地址", "企业地址", "注册地址", "经营地址"));
        addTagsToCompany(db, companyId, collectTagValues(row));
        String newExtra = mergeSemi(old.extraInfo, collectExtraValues(row));
        cv.put("extra_info", newExtra);
        cv.put("updated_at", now());
        db.update("companies", cv, "id=?", new String[]{String.valueOf(companyId)});
    }

    private void putIfOldEmpty(ContentValues cv, String col, String oldVal, String newVal) {
        if (empty(oldVal) && !empty(newVal)) cv.put(col, newVal.trim());
    }

    private void countMultiPhoneCells(Map<String, String> row, ImportResult r) {
        for (int i=1;i<=80;i++) {
            String phone = first(row, "电话号码"+i, "手机号"+i, "联系电话"+i, "电话"+i, "手机"+i);
            List<String> phones = splitPhones(phone);
            if (phones.size() > 1) { r.multiPhoneCellCount++; r.splitPhoneCount += phones.size(); }
        }
        String phone = first(row, "电话号码", "手机号", "联系电话", "电话", "手机", "移动电话", "联系方式");
        List<String> phones = splitPhones(phone);
        if (phones.size() > 1) { r.multiPhoneCellCount++; r.splitPhoneCount += phones.size(); }
    }

    private int importContactsForCompany(SQLiteDatabase db, long companyId, Map<String, String> row) {
        int count = 0;
        for (int i = 1; i <= 80; i++) {
            String name = first(row, "联系人" + i, "联系人姓名" + i, "联系人" + i + "姓名", "姓名" + i);
            String phone = first(row, "电话号码" + i, "手机号" + i, "联系电话" + i, "电话" + i, "手机" + i);
            String star = first(row, "星级" + i, "号码星级" + i, "重要程度" + i);
            if (!empty(phone) || !empty(name)) {
                if (empty(phone)) continue;
                for (String one : splitPhones(phone)) if (addContactIfNew(db, companyId, name, one, parseStar(star))) count++;
            }
        }
        String name = first(row, "联系人", "联系人姓名", "姓名");
        String phone = first(row, "电话号码", "手机号", "联系电话", "电话", "手机", "移动电话", "联系方式");
        String star = first(row, "星级", "号码星级", "重要程度");
        if (!empty(phone)) {
            for (String one : splitPhones(phone)) if (addContactIfNew(db, companyId, name, one, parseStar(star))) count++;
        }
        return count;
    }

    public static int parseStar(String s) {
        if (s == null) return 0;
        String t = s.trim();
        if (t.contains("★★★") || t.contains("三星") || t.equals("3")) return 3;
        if (t.contains("★★") || t.contains("二星") || t.equals("2")) return 2;
        if (t.contains("★") || t.contains("一星") || t.equals("1")) return 1;
        return 0;
    }

    public static String starText(int star) {
        if (star >= 3) return "★★★";
        if (star == 2) return "★★";
        if (star == 1) return "★";
        return "未标记";
    }

    private boolean addContactIfNew(SQLiteDatabase db, long companyId, String name, String phone) { return addContactIfNew(db, companyId, name, phone, 0); }

    private boolean addContactIfNew(SQLiteDatabase db, long companyId, String name, String phone, int starLevel) {
        String norm = normalizePhone(phone);
        if (empty(norm)) return false;
        Cursor c = db.rawQuery("SELECT id FROM contacts WHERE company_id=? AND phone_norm=?", new String[]{String.valueOf(companyId), norm});
        try { if (c.moveToFirst()) return false; } finally { c.close(); }
        int order = nextContactOrder(db, companyId);
        ContentValues cv = new ContentValues();
        cv.put("company_id", companyId);
        cv.put("contact_name", nonNull(name).trim());
        cv.put("phone", phone.trim());
        cv.put("phone_norm", norm);
        cv.put("contact_order", order);
        cv.put("star_level", Math.max(0, Math.min(3, starLevel)));
        cv.put("imported", 0);
        cv.put("created_at", now());
        db.insert("contacts", null, cv);
        return true;
    }

    private int nextContactOrder(SQLiteDatabase db, long companyId) {
        Cursor c = db.rawQuery("SELECT COALESCE(MAX(contact_order),0)+1 FROM contacts WHERE company_id=?", new String[]{String.valueOf(companyId)});
        try { return c.moveToFirst() ? c.getInt(0) : 1; } finally { c.close(); }
    }

    private int importNotesForCompany(SQLiteDatabase db, long companyId, Map<String, String> row) {
        int count = 0;
        for (int i = 1; i <= 100; i++) {
            String note = first(row, "备注" + i, "备注信息" + i, "跟进记录" + i, "记录" + i);
            if (!empty(note) && addNoteIfNew(db, companyId, note)) count++;
        }
        String note = first(row, "备注", "备注信息", "跟进记录", "记录", "说明");
        if (!empty(note) && addNoteIfNew(db, companyId, note)) count++;
        return count;
    }

    public boolean addNoteIfNew(SQLiteDatabase db, long companyId, String content) {
        if (empty(content)) return false;
        String val = content.trim();
        Cursor c = db.rawQuery("SELECT id FROM notes WHERE company_id=? AND content=?", new String[]{String.valueOf(companyId), val});
        try { if (c.moveToFirst()) return false; } finally { c.close(); }
        ContentValues cv = new ContentValues();
        cv.put("company_id", companyId);
        cv.put("content", val);
        cv.put("created_at", now());
        db.insert("notes", null, cv);
        return true;
    }

    private int importFollowRecordsForCompany(SQLiteDatabase db, long companyId, Map<String, String> row) {
        int count = 0;
        for (int i = 1; i <= 20; i++) {
            String content = first(row, "跟进内容" + i, "跟进记录内容" + i, "跟进" + i);
            String method = first(row, "跟进方式" + i, "跟进方法" + i, "方式" + i);
            String next = first(row, "下次跟进日期" + i, "下次联系日期" + i, "回访日期" + i);
            if (!empty(content) || !empty(next)) {
                if (empty(content)) content = "导入新增跟进";
                addFollowRecordRaw(db, companyId, method, content, next);
                count++;
            }
        }
        String content = first(row, "跟进内容", "跟进记录内容", "跟进");
        String method = first(row, "跟进方式", "跟进方法", "方式");
        String next = first(row, "下次跟进日期", "下次联系日期", "回访日期");
        if (!empty(content) || !empty(next)) {
            if (empty(content)) content = "导入新增跟进";
            addFollowRecordRaw(db, companyId, method, content, next);
            count++;
        }
        return count;
    }

    private void addFollowRecordRaw(SQLiteDatabase db, long companyId, String method, String content, String nextDate) {
        ContentValues cv = new ContentValues();
        cv.put("company_id", companyId); cv.put("follow_time", now()); cv.put("follow_method", empty(method) ? "其他" : method.trim());
        cv.put("content", nonNull(content).trim()); cv.put("next_follow_date", nonNull(nextDate).trim()); cv.put("done", 0); cv.put("created_at", now());
        db.insert("follow_records", null, cv);
    }

    private int importTagsAndExtraForCompany(SQLiteDatabase db, long companyId, Map<String, String> row) {
        CompanyItem old = getCompany(companyId);
        int addedTags = addTagsToCompany(db, companyId, collectTagValues(row));
        String newExtra = mergeSemi(old.extraInfo, collectExtraValues(row));
        if (!newExtra.equals(nonNull(old.extraInfo))) {
            ContentValues cv = new ContentValues();
            cv.put("extra_info", newExtra);
            cv.put("updated_at", now());
            db.update("companies", cv, "id=?", new String[]{String.valueOf(companyId)});
        }
        if (addedTags > 0) syncCompanyTagsText(db, companyId);
        return addedTags;
    }

    private static String mergeSemi(String oldVal, String addVal) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String part : nonNull(oldVal).split("；|;")) if (!empty(part)) set.add(part.trim());
        for (String part : nonNull(addVal).split("；|;")) if (!empty(part)) set.add(part.trim());
        StringBuilder sb = new StringBuilder();
        for (String s : set) { if (sb.length() > 0) sb.append("；"); sb.append(s); }
        return sb.toString();
    }

    private String collectTagValues(Map<String, String> row) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        for (String key : row.keySet()) {
            String k = cleanHeader(key);
            String v = row.get(key);
            if (empty(v)) continue;
            if (k.startsWith("标签") || k.equals("客户标签")) tags.add(v.trim());
        }
        return join(tags);
    }

    private String collectExtraValues(Map<String, String> row) {
        LinkedHashSet<String> extra = new LinkedHashSet<>();
        for (String key : row.keySet()) {
            String k = cleanHeader(key);
            String v = row.get(key);
            if (empty(v)) continue;
            if (isStandardHeader(k)) continue;
            extra.add(k + ":" + v.trim());
        }
        return join(extra);
    }

    private static String join(Set<String> values) {
        StringBuilder sb = new StringBuilder();
        for (String s : values) { if (sb.length() > 0) sb.append("；"); sb.append(s); }
        return sb.toString();
    }

    public static boolean isStandardHeader(String k) {
        String h = cleanHeader(k);
        if (h.matches("联系人\\d*|联系人姓名\\d*|联系人\\d+姓名|姓名\\d*")) return true;
        if (h.matches("电话号码\\d*|手机号\\d*|联系电话\\d*|电话\\d*|手机\\d*|移动电话|联系方式")) return true;
        if (h.matches("备注\\d*|备注信息\\d*|跟进记录\\d*|记录\\d*|说明")) return true;
        if (h.matches("标签\\d*|客户标签")) return true;
        if (h.matches("星级\\d*|号码星级\\d*|重要程度\\d*")) return true;
        if (h.equals("客户状态") || h.equals("状态") || h.equals("客户等级") || h.equals("跟进状态")) return true;
        if (h.matches("跟进方式\\d*|跟进方法\\d*|方式\\d*|跟进内容\\d*|跟进记录内容\\d*|跟进\\d*|下次跟进日期\\d*|下次联系日期\\d*|回访日期\\d*|客户来源")) return true;
        return h.equals("序号") || h.equals("分组") || h.equals("客户分组") || h.equals("组别") ||
                h.equals("公司名称") || h.equals("企业名称") || h.equals("单位名称") || h.equals("客户名称") || h.equals("公司") || h.equals("企业") || h.equals("名称") ||
                h.equals("所属行业") || h.equals("行业") || h.equals("经营行业") ||
                h.equals("参保人数") || h.equals("人数") || h.equals("员工人数") ||
                h.equals("区域") || h.equals("地区") || h.equals("所在区域") ||
                h.equals("地址") || h.equals("企业地址") || h.equals("注册地址") || h.equals("经营地址");
    }

    public static String cleanHeader(String s) {
        if (s == null) return "";
        return s.trim().replace(" ", "").replace("　", "");
    }

    public static String first(Map<String, String> row, String... aliases) {
        for (String alias : aliases) {
            String target = cleanHeader(alias);
            for (String key : row.keySet()) {
                if (cleanHeader(key).equals(target)) {
                    String v = row.get(key);
                    if (!empty(v)) return v.trim();
                }
            }
        }
        return "";
    }

    public Stats getStats() {
        Stats s = new Stats();
        SQLiteDatabase db = getWritableDatabase();
        if (getCachedStat(db, "company_count") < 0) rebuildStatsCache(db);
        s.companyCount = Math.max(0, getCachedStat(db, "company_count"));
        s.contactCount = Math.max(0, getCachedStat(db, "contact_count"));
        s.importedCount = Math.max(0, getCachedStat(db, "imported_count"));
        s.statusNone = Math.max(0, getCachedStat(db, "status_none"));
        s.focusCount = Math.max(0, getCachedStat(db, "focus_count"));
        s.followCount = Math.max(0, getCachedStat(db, "follow_count"));
        s.importantCount = Math.max(0, getCachedStat(db, "important_count"));
        s.exceptionCount = Math.max(0, getCachedStat(db, "exception_count"));
        s.todayFollowCount = Math.max(0, getCachedStat(db, "today_follow_count"));
        s.overdueFollowCount = Math.max(0, getCachedStat(db, "overdue_follow_count"));
        s.soonFollowCount = Math.max(0, getCachedStat(db, "soon_follow_count"));
        s.callPendingCount = Math.max(0, getCachedStat(db, "call_pending_count"));
        return s;
    }

    private int intQuery(SQLiteDatabase db, String sql) {
        Cursor c = db.rawQuery(sql, null);
        try { return c.moveToFirst() ? c.getInt(0) : 0; } finally { c.close(); }
    }

    public List<CompanyItem> getCompaniesPage(String query, String status, long groupId, int limit, int offset) {
        ArrayList<CompanyItem> list = new ArrayList<>();
        if (limit <= 0) limit = 100;
        if (offset < 0) offset = 0;
        StringBuilder sql = new StringBuilder();
        ArrayList<String> args = new ArrayList<>();
        sql.append("SELECT c.id,c.group_id,g.name,c.seq,c.name,c.industry,c.employee_count,c.region,c.address,c.customer_status,c.tags,c.extra_info,c.created_at,c.updated_at,");
        sql.append("c.contact_count,c.note_count,c.imported_contact_count ");
        sql.append("FROM companies c LEFT JOIN groups_tbl g ON c.group_id=g.id ");
        if (!empty(query)) sql.append("LEFT JOIN search_index si ON si.company_id=c.id ");
        sql.append("WHERE 1=1 ");
        if (groupId > 0) { sql.append("AND c.group_id=? "); args.add(String.valueOf(groupId)); }
        if (!empty(status)) {
            if (STATUS_NONE.equals(status)) sql.append("AND (c.customer_status IS NULL OR c.customer_status='') ");
            else if (STATUS_IMPORTANT.equals(status)) { sql.append("AND (c.customer_status=? OR c.customer_status=?) "); args.add(STATUS_IMPORTANT); args.add(STATUS_OLD_IMPORTANT); }
            else { sql.append("AND c.customer_status=? "); args.add(status); }
        }
        if (!empty(query)) {
            String like = "%" + query.trim().toLowerCase(Locale.CHINA) + "%";
            sql.append("AND (LOWER(c.name) LIKE ? OR c.search_text LIKE ? OR si.search_text LIKE ?) ");
            args.add(like); args.add(like); args.add(like);
        }
        sql.append("ORDER BY g.id ASC,c.seq ASC,c.id ASC LIMIT ? OFFSET ?");
        args.add(String.valueOf(limit)); args.add(String.valueOf(offset));
        Cursor c = getReadableDatabase().rawQuery(sql.toString(), args.toArray(new String[0]));
        try { while (c.moveToNext()) list.add(companyFromCursor(c)); } finally { c.close(); }
        return list;
    }

    public List<CompanyItem> getCompanies(String query, String status, long groupId) {
        // 保留给导出和维护使用：大数据场景下页面展示请使用 getCompaniesPage。
        return getCompaniesPage(query, status, groupId, 1000000, 0);
    }

    private CompanyItem companyFromCursor(Cursor c) {
        CompanyItem it = new CompanyItem();
        int i=0;
        it.id = c.getLong(i++);
        it.groupId = c.getLong(i++);
        it.groupName = c.getString(i++);
        it.seq = c.getInt(i++);
        it.name = c.getString(i++);
        it.industry = c.getString(i++);
        it.employeeCount = c.getString(i++);
        it.region = c.getString(i++);
        it.address = c.getString(i++);
        it.customerStatus = normalizeStatus(c.getString(i++));
        it.tags = c.getString(i++);
        it.extraInfo = c.getString(i++);
        it.createdAt = c.getString(i++);
        it.updatedAt = c.getString(i++);
        it.contactCount = c.getInt(i++);
        it.noteCount = c.getInt(i++);
        it.importedContactCount = c.getInt(i++);
        return it;
    }

    public CompanyItem getCompany(long id) {
        String sql = "SELECT c.id,c.group_id,g.name,c.seq,c.name,c.industry,c.employee_count,c.region,c.address,c.customer_status,c.tags,c.extra_info,c.created_at,c.updated_at," +
                "c.contact_count,c.note_count,c.imported_contact_count " +
                "FROM companies c LEFT JOIN groups_tbl g ON c.group_id=g.id WHERE c.id=?";
        Cursor c = getReadableDatabase().rawQuery(sql, new String[]{String.valueOf(id)});
        try { return c.moveToFirst() ? companyFromCursor(c) : new CompanyItem(); } finally { c.close(); }
    }

    public long addCompany(String groupName, String name, String industry, String employeeCount, String region, String address) {
        if (empty(name)) return -1;
        SQLiteDatabase db = getWritableDatabase();
        long existed = findCompanyId(db, name);
        if (existed > 0) return existed;
        long groupId = ensureGroup(groupName);
        ContentValues cv = new ContentValues();
        cv.put("group_id", groupId); cv.put("seq", nextSeq(db, groupId));
        cv.put("name", name.trim()); cv.put("normalized_name", normalizeCompany(name));
        cv.put("industry", nonNull(industry)); cv.put("employee_count", nonNull(employeeCount));
        cv.put("region", nonNull(region)); cv.put("address", nonNull(address));
        cv.put("customer_status", ""); cv.put("tags", ""); cv.put("extra_info", "");
        cv.put("created_at", now()); cv.put("updated_at", now());
        long id = db.insert("companies", null, cv);
        updateCompanyCache(db, id);
        rebuildStatsCache(db);
        return id;
    }

    public void updateCompanyBasic(long id, String groupName, String name, String industry, String employeeCount, String region, String address) {
        long groupId = ensureGroup(groupName);
        CompanyItem old = getCompany(id);
        ContentValues cv = new ContentValues();
        cv.put("group_id", groupId); cv.put("name", name.trim()); cv.put("normalized_name", normalizeCompany(name));
        cv.put("industry", nonNull(industry)); cv.put("employee_count", nonNull(employeeCount));
        cv.put("region", nonNull(region)); cv.put("address", nonNull(address));
        cv.put("updated_at", now());
        SQLiteDatabase db = getWritableDatabase();
        db.update("companies", cv, "id=?", new String[]{String.valueOf(id)});
        updateCompanyCache(db, id);
        rebuildStatsCache(db);
        if (old.groupId != groupId) { resequenceGroup(old.groupId); resequenceGroup(groupId); }
    }

    public void setCompanyStatus(long id, String status) {
        status = normalizeStatus(status);
        ContentValues cv = new ContentValues();
        cv.put("customer_status", nonNull(status));
        cv.put("updated_at", now());
        SQLiteDatabase db = getWritableDatabase();
        db.update("companies", cv, "id=?", new String[]{String.valueOf(id)});
        updateCompanyCache(db, id);
        rebuildStatsCache(db);
    }

    public void deleteCompany(long id) {
        SQLiteDatabase db = getWritableDatabase();
        CompanyItem old = getCompany(id);
        db.delete("notes", "company_id=?", new String[]{String.valueOf(id)});
        db.delete("contacts", "company_id=?", new String[]{String.valueOf(id)});
        db.delete("companies", "id=?", new String[]{String.valueOf(id)});
        resequenceGroup(old.groupId);
        rebuildStatsCache(db);
    }

    public long addContact(long companyId, String name, String phone) { return addContact(companyId, name, phone, 0); }

    public long addContact(long companyId, String name, String phone, int starLevel) {
        SQLiteDatabase db = getWritableDatabase();
        if (addContactIfNew(db, companyId, name, phone, starLevel)) {
            updateCompanyCache(db, companyId);
            rebuildStatsCache(db);
            logOperation("新增联系人", "添加电话", "企业ID=" + companyId + " 电话=" + phone);
            Cursor c = db.rawQuery("SELECT last_insert_rowid()", null);
            try { return c.moveToFirst() ? c.getLong(0) : -1; } finally { c.close(); }
        }
        return -1;
    }

    public void updateContactStar(long contactId, int starLevel) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("star_level", Math.max(0, Math.min(3, starLevel)));
        db.update("contacts", cv, "id=?", new String[]{String.valueOf(contactId)});
        long cid = contactCompanyId(db, contactId);
        if (cid > 0) updateCompanyCache(db, cid);
        logOperation("号码星级", "修改号码星级", "联系人ID=" + contactId + " 星级=" + starLevel);
    }

    public void deleteContact(long id) {
        SQLiteDatabase db = getWritableDatabase();
        long cid = -1;
        Cursor c = db.rawQuery("SELECT company_id FROM contacts WHERE id=?", new String[]{String.valueOf(id)});
        try { if (c.moveToFirst()) cid = c.getLong(0); } finally { c.close(); }
        db.delete("contacts", "id=?", new String[]{String.valueOf(id)});
        if (cid > 0) updateCompanyCache(db, cid);
        rebuildStatsCache(db);
    }

    public void addNote(long companyId, String content) {
        SQLiteDatabase db = getWritableDatabase();
        if (addNoteIfNew(db, companyId, content)) { updateCompanyCache(db, companyId); rebuildStatsCache(db); }
    }

    public void deleteNote(long id) {
        SQLiteDatabase db = getWritableDatabase();
        long cid = -1;
        Cursor c = db.rawQuery("SELECT company_id FROM notes WHERE id=?", new String[]{String.valueOf(id)});
        try { if (c.moveToFirst()) cid = c.getLong(0); } finally { c.close(); }
        db.delete("notes", "id=?", new String[]{String.valueOf(id)});
        if (cid > 0) updateCompanyCache(db, cid);
        rebuildStatsCache(db);
    }

    public void updateTags(long companyId, String tags) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("company_tags", "company_id=?", new String[]{String.valueOf(companyId)});
            addTagsToCompany(db, companyId, tags);
            syncCompanyTagsText(db, companyId);
            updateCompanyCache(db, companyId);
            rebuildStatsCache(db);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        logOperation("标签", "编辑企业标签", "企业ID=" + companyId + " 标签=" + tags);
    }

    public List<ContactItem> getContacts(long companyId, Boolean importedOnly) {
        ArrayList<ContactItem> list = new ArrayList<>();
        String sql = "SELECT ct.id,ct.company_id,c.name,ct.contact_name,ct.phone,ct.phone_norm,ct.contact_order,ct.star_level,ct.imported,ct.raw_contact_id,ct.imported_at,g.name,c.customer_status,c.seq,c.group_id " +
                "FROM contacts ct JOIN companies c ON ct.company_id=c.id LEFT JOIN groups_tbl g ON c.group_id=g.id WHERE 1=1 ";
        ArrayList<String> args = new ArrayList<>();
        if (companyId > 0) { sql += "AND ct.company_id=? "; args.add(String.valueOf(companyId)); }
        if (importedOnly != null) sql += importedOnly ? "AND ct.imported=1 " : "AND ct.imported=0 ";
        sql += "ORDER BY g.id,c.seq,ct.star_level DESC,ct.contact_order";
        Cursor c = getReadableDatabase().rawQuery(sql, args.toArray(new String[0]));
        try {
            while (c.moveToNext()) list.add(contactFromCursor(c));
        } finally { c.close(); }
        return list;
    }

    public List<ContactItem> getContactsPage(long companyId, Boolean importedOnly, int limit, int offset) {
        if (limit <= 0) limit = 100;
        if (offset < 0) offset = 0;
        ArrayList<ContactItem> list = new ArrayList<>();
        String sql = "SELECT ct.id,ct.company_id,c.name,ct.contact_name,ct.phone,ct.phone_norm,ct.contact_order,ct.star_level,ct.imported,ct.raw_contact_id,ct.imported_at,g.name,c.customer_status,c.seq,c.group_id " +
                "FROM contacts ct JOIN companies c ON ct.company_id=c.id LEFT JOIN groups_tbl g ON c.group_id=g.id WHERE 1=1 ";
        ArrayList<String> args = new ArrayList<>();
        if (companyId > 0) { sql += "AND ct.company_id=? "; args.add(String.valueOf(companyId)); }
        if (importedOnly != null) sql += importedOnly ? "AND ct.imported=1 " : "AND ct.imported=0 ";
        sql += "ORDER BY g.id,c.seq,ct.star_level DESC,ct.contact_order LIMIT ? OFFSET ?";
        args.add(String.valueOf(limit)); args.add(String.valueOf(offset));
        Cursor c = getReadableDatabase().rawQuery(sql, args.toArray(new String[0]));
        try { while (c.moveToNext()) list.add(contactFromCursor(c)); } finally { c.close(); }
        return list;
    }

    public int countContacts(Boolean importedOnly) {
        SQLiteDatabase db = getReadableDatabase();
        if (importedOnly == null) return intQuery(db, "SELECT COUNT(*) FROM contacts");
        return intQuery(db, importedOnly ? "SELECT COUNT(*) FROM contacts WHERE imported=1" : "SELECT COUNT(*) FROM contacts WHERE imported=0");
    }

    public List<ContactItem> getAllImportedContactsForDelete() { return getContacts(0, true); }

    public void markContactsUnimported(List<ContactItem> contacts) {
        if (contacts == null || contacts.isEmpty()) return;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            LinkedHashSet<Long> affected = new LinkedHashSet<>();
            for (ContactItem ct : contacts) {
                ContentValues cv = new ContentValues();
                cv.put("imported", 0); cv.putNull("raw_contact_id"); cv.putNull("imported_at");
                db.update("contacts", cv, "id=?", new String[]{String.valueOf(ct.id)});
                affected.add(ct.companyId);
            }
            for (Long id : affected) updateCompanyCache(db, id);
            rebuildStatsCache(db);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public List<ContactItem> getContactsForExport(long companyId) { return getContacts(companyId, null); }

    private ContactItem contactFromCursor(Cursor c) {
        ContactItem it = new ContactItem();
        int i=0;
        it.id = c.getLong(i++); it.companyId = c.getLong(i++); it.companyName = c.getString(i++);
        it.contactName = c.getString(i++); it.phone = c.getString(i++); it.phoneNorm = c.getString(i++);
        it.order = c.getInt(i++); it.starLevel = c.getInt(i++); it.imported = c.getInt(i++) == 1;
        it.rawContactId = c.isNull(i) ? 0 : c.getLong(i); i++;
        it.importedAt = c.getString(i++); it.groupName = c.getString(i++); it.customerStatus = c.getString(i++);
        it.companySeq = c.getInt(i++); it.groupId = c.getLong(i++);
        return it;
    }

    public List<NoteItem> getNotes(long companyId) {
        ArrayList<NoteItem> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT id,company_id,content,created_at FROM notes WHERE company_id=? ORDER BY id", new String[]{String.valueOf(companyId)});
        try {
            while (c.moveToNext()) {
                NoteItem n = new NoteItem();
                n.id=c.getLong(0); n.companyId=c.getLong(1); n.content=c.getString(2); n.createdAt=c.getString(3);
                list.add(n);
            }
        } finally { c.close(); }
        return list;
    }

    public void markContactImported(long contactId, long rawContactId) {
        ContentValues cv = new ContentValues();
        cv.put("imported", 1);
        cv.put("raw_contact_id", rawContactId);
        cv.put("imported_at", now());
        SQLiteDatabase db = getWritableDatabase();
        db.update("contacts", cv, "id=?", new String[]{String.valueOf(contactId)});
        long cid = contactCompanyId(db, contactId); if (cid > 0) updateCompanyCache(db, cid);
        rebuildStatsCache(db);
    }

    public void markContactUnimported(long contactId) {
        ContentValues cv = new ContentValues();
        cv.put("imported", 0);
        cv.putNull("raw_contact_id");
        cv.putNull("imported_at");
        SQLiteDatabase db = getWritableDatabase();
        db.update("contacts", cv, "id=?", new String[]{String.valueOf(contactId)});
        long cid = contactCompanyId(db, contactId); if (cid > 0) updateCompanyCache(db, cid);
        rebuildStatsCache(db);
    }

    private long contactCompanyId(SQLiteDatabase db, long contactId) {
        Cursor c = db.rawQuery("SELECT company_id FROM contacts WHERE id=?", new String[]{String.valueOf(contactId)});
        try { return c.moveToFirst() ? c.getLong(0) : -1; } finally { c.close(); }
    }

    public List<ContactItem> getUnimportedContactsByRange(long groupId, int startSeq, int endSeq) {
        ArrayList<ContactItem> list = new ArrayList<>();
        String sql = "SELECT ct.id,ct.company_id,c.name,ct.contact_name,ct.phone,ct.phone_norm,ct.contact_order,ct.star_level,ct.imported,ct.raw_contact_id,ct.imported_at,g.name,c.customer_status,c.seq,c.group_id " +
                "FROM contacts ct JOIN companies c ON ct.company_id=c.id LEFT JOIN groups_tbl g ON c.group_id=g.id " +
                "WHERE ct.imported=0 AND c.group_id=? AND c.seq>=? AND c.seq<=? ORDER BY c.seq,ct.star_level DESC,ct.contact_order";
        Cursor c = getReadableDatabase().rawQuery(sql, new String[]{String.valueOf(groupId), String.valueOf(startSeq), String.valueOf(endSeq)});
        try { while (c.moveToNext()) list.add(contactFromCursor(c)); } finally { c.close(); }
        return list;
    }

    public List<ContactItem> getUnimportedContactsByStatus(String status) {
        ArrayList<ContactItem> list = new ArrayList<>();
        String sql = "SELECT ct.id,ct.company_id,c.name,ct.contact_name,ct.phone,ct.phone_norm,ct.contact_order,ct.star_level,ct.imported,ct.raw_contact_id,ct.imported_at,g.name,c.customer_status,c.seq,c.group_id " +
                "FROM contacts ct JOIN companies c ON ct.company_id=c.id LEFT JOIN groups_tbl g ON c.group_id=g.id WHERE ct.imported=0 ";
        ArrayList<String> args = new ArrayList<>();
        if (!empty(status)) {
            if (STATUS_IMPORTANT.equals(status)) { sql += "AND (c.customer_status=? OR c.customer_status=?) "; args.add(STATUS_IMPORTANT); args.add(STATUS_OLD_IMPORTANT); }
            else { sql += "AND c.customer_status=? "; args.add(status); }
        }
        sql += "ORDER BY g.id,c.seq,ct.star_level DESC,ct.contact_order";
        Cursor c = getReadableDatabase().rawQuery(sql, args.toArray(new String[0]));
        try { while (c.moveToNext()) list.add(contactFromCursor(c)); } finally { c.close(); }
        return list;
    }

    public List<CompanyItem> getCompaniesForExport(String mode, long groupId, int startSeq, int endSeq) {
        String status = null;
        if (STATUS_NONE.equals(mode) || STATUS_FOCUS.equals(mode) || STATUS_FOLLOW.equals(mode) || STATUS_IMPORTANT.equals(mode)) status = mode;
        return getCompanies("", status, groupId);
    }

    public Map<Long, List<ContactItem>> contactsByCompany(List<CompanyItem> companies) {
        LinkedHashMap<Long, List<ContactItem>> map = new LinkedHashMap<>();
        for (CompanyItem c : companies) map.put(c.id, getContactsForExport(c.id));
        return map;
    }

    public Map<Long, List<NoteItem>> notesByCompany(List<CompanyItem> companies) {
        LinkedHashMap<Long, List<NoteItem>> map = new LinkedHashMap<>();
        for (CompanyItem c : companies) map.put(c.id, getNotes(c.id));
        return map;
    }


    public List<LogItem> getImportLogs(int limit) {
        ArrayList<LogItem> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT id,file_name,started_at,finished_at,total_rows,new_companies,merged_companies,new_contacts,new_notes,new_tags,skipped_no_company,duplicate_rows,abnormal_rows,message FROM import_logs ORDER BY id DESC LIMIT ?", new String[]{String.valueOf(limit <= 0 ? 50 : limit)});
        try {
            while (c.moveToNext()) {
                LogItem it = new LogItem(); int i=0;
                it.id=c.getLong(i++); it.fileName=c.getString(i++); it.startedAt=c.getString(i++); it.finishedAt=c.getString(i++); it.totalRows=c.getInt(i++);
                it.newCompanies=c.getInt(i++); it.mergedCompanies=c.getInt(i++); it.newContacts=c.getInt(i++); it.newNotes=c.getInt(i++); it.newTags=c.getInt(i++); it.skippedNoCompany=c.getInt(i++); it.duplicateRows=c.getInt(i++); it.abnormalRows=c.getInt(i++); it.message=c.getString(i++);
                list.add(it);
            }
        } finally { c.close(); }
        return list;
    }

    public int moveCompaniesToGroup(List<Long> companyIds, long targetGroupId) {
        if (companyIds == null || companyIds.isEmpty() || targetGroupId <= 0) return 0;
        SQLiteDatabase db = getWritableDatabase();
        int n = 0;
        db.beginTransaction();
        try {
            LinkedHashSet<Long> oldGroups = new LinkedHashSet<>();
            for (Long id : companyIds) {
                CompanyItem old = getCompany(id);
                oldGroups.add(old.groupId);
                ContentValues cv = new ContentValues();
                cv.put("group_id", targetGroupId);
                cv.put("updated_at", now());
                n += db.update("companies", cv, "id=?", new String[]{String.valueOf(id)});
                updateCompanyCache(db, id);
            }
            for (Long g : oldGroups) resequenceGroup(g);
            resequenceGroup(targetGroupId);
            rebuildStatsCache(db);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        logOperation("批量分组", "移动企业到分组", "数量=" + n + " 目标分组=" + getGroupName(targetGroupId));
        return n;
    }

    public List<Long> getCompanyIdsByFilter(String query, String status, long groupId, int max) {
        ArrayList<Long> ids = new ArrayList<>();
        List<CompanyItem> list = getCompaniesPage(query, status, groupId, max <= 0 ? 5000 : max, 0);
        for (CompanyItem c : list) ids.add(c.id);
        return ids;
    }

    public int maxStarForCompany(long companyId) {
        Cursor c = getReadableDatabase().rawQuery("SELECT COALESCE(MAX(star_level),0) FROM contacts WHERE company_id=?", new String[]{String.valueOf(companyId)});
        try { return c.moveToFirst() ? c.getInt(0) : 0; } finally { c.close(); }
    }

    public void logOperation(String type, String title, String detail) {
        try {
            ContentValues cv = new ContentValues();
            cv.put("type", nonNull(type)); cv.put("title", nonNull(title)); cv.put("detail", nonNull(detail)); cv.put("created_at", now());
            getWritableDatabase().insert("operation_logs", null, cv);
        } catch (Exception ignored) {}
    }

    public List<OperationLogItem> getOperationLogs(int limit) {
        ArrayList<OperationLogItem> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT id,type,title,detail,created_at FROM operation_logs ORDER BY id DESC LIMIT ?", new String[]{String.valueOf(limit <= 0 ? 80 : limit)});
        try { while (c.moveToNext()) { OperationLogItem it = new OperationLogItem(); int i=0; it.id=c.getLong(i++); it.type=c.getString(i++); it.title=c.getString(i++); it.detail=c.getString(i++); it.createdAt=c.getString(i++); list.add(it); } } finally { c.close(); }
        return list;
    }

    public boolean insertCallRecordIfNew(String phone, String callType, long callTime, String callTimeText, int duration, String cachedName, String deviceName) {
        String norm = normalizePhone(phone);
        if (empty(norm)) return false;
        String rawKey = norm + "_" + callTime + "_" + callType + "_" + duration;
        SQLiteDatabase db = getWritableDatabase();
        long matchedCompanyId = 0; String companyName = "", contactName = "", appNote = "";
        Cursor m = db.rawQuery("SELECT ct.company_id,c.name,ct.contact_name FROM contacts ct JOIN companies c ON ct.company_id=c.id WHERE ct.phone_norm=? LIMIT 1", new String[]{norm});
        try { if (m.moveToFirst()) { matchedCompanyId=m.getLong(0); companyName=m.getString(1); contactName=m.getString(2); } } finally { m.close(); }
        if (matchedCompanyId > 0) {
            Cursor n = db.rawQuery("SELECT content FROM notes WHERE company_id=? ORDER BY id DESC LIMIT 1", new String[]{String.valueOf(matchedCompanyId)});
            try { if (n.moveToFirst()) appNote = n.getString(0); } finally { n.close(); }
        }
        ContentValues cv = new ContentValues();
        cv.put("phone", phone); cv.put("phone_norm", norm); cv.put("call_type", callType); cv.put("call_time", callTime); cv.put("call_time_text", callTimeText); cv.put("duration", duration);
        cv.put("cached_name", nonNull(cachedName)); cv.put("device_name", nonNull(deviceName)); cv.put("app_company_id", matchedCompanyId); cv.put("app_company_name", companyName); cv.put("app_contact_name", contactName); cv.put("app_note", appNote);
        cv.put("status", matchedCompanyId > 0 ? "已匹配" : "未处理"); cv.put("raw_key", rawKey); cv.put("created_at", now());
        long id = db.insertWithOnConflict("call_records", null, cv, SQLiteDatabase.CONFLICT_IGNORE);
        return id > 0;
    }

    public List<CallRecordItem> getCallRecords(String status, int limit) {
        ArrayList<CallRecordItem> list = new ArrayList<>();
        String sql = "SELECT id,phone,phone_norm,call_type,call_time_text,duration,cached_name,device_name,app_company_id,app_company_name,app_contact_name,app_note,status FROM call_records WHERE 1=1 ";
        ArrayList<String> args = new ArrayList<>();
        if (!empty(status)) { sql += "AND status=? "; args.add(status); }
        sql += "ORDER BY call_time DESC,id DESC LIMIT ?"; args.add(String.valueOf(limit <= 0 ? 200 : limit));
        Cursor c = getReadableDatabase().rawQuery(sql, args.toArray(new String[0]));
        try { while (c.moveToNext()) { CallRecordItem it = new CallRecordItem(); int i=0; it.id=c.getLong(i++); it.phone=c.getString(i++); it.phoneNorm=c.getString(i++); it.callType=c.getString(i++); it.callTimeText=c.getString(i++); it.duration=c.getInt(i++); it.cachedName=c.getString(i++); it.deviceName=c.getString(i++); it.appCompanyId=c.getLong(i++); it.appCompanyName=c.getString(i++); it.appContactName=c.getString(i++); it.appNote=c.getString(i++); it.status=c.getString(i++); list.add(it); } } finally { c.close(); }
        return list;
    }

    public void updateCallRecordStatus(long id, String status) {
        ContentValues cv = new ContentValues(); cv.put("status", status);
        getWritableDatabase().update("call_records", cv, "id=?", new String[]{String.valueOf(id)});
    }

    public void attachCallToCompany(long callId, long companyId, String contactName, String phone, String note) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            long contactId = addContact(companyId, contactName, phone);
            if (!empty(note)) addNote(companyId, note);
            CompanyItem c = getCompany(companyId);
            ContentValues cv = new ContentValues();
            cv.put("app_company_id", companyId); cv.put("app_company_name", c.name); cv.put("app_contact_name", nonNull(contactName)); cv.put("app_note", nonNull(note)); cv.put("status", "已入库");
            db.update("call_records", cv, "id=?", new String[]{String.valueOf(callId)});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        logOperation("通话记录", "未入库号码加入企业", "企业ID=" + companyId + " 电话=" + phone);
    }


    public List<FollowRecordItem> getFollowRecords(long companyId) {
        ArrayList<FollowRecordItem> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT id,company_id,follow_time,follow_method,content,next_follow_date,done,created_at FROM follow_records WHERE company_id=? ORDER BY COALESCE(follow_time,created_at) DESC,id DESC", new String[]{String.valueOf(companyId)});
        try { while (c.moveToNext()) list.add(followFromCursor(c)); } finally { c.close(); }
        return list;
    }

    private FollowRecordItem followFromCursor(Cursor c) {
        FollowRecordItem it = new FollowRecordItem(); int i=0;
        it.id=c.getLong(i++); it.companyId=c.getLong(i++); it.followTime=c.getString(i++); it.followMethod=c.getString(i++); it.content=c.getString(i++); it.nextFollowDate=c.getString(i++); it.done=c.getInt(i++)==1; it.createdAt=c.getString(i++);
        return it;
    }

    public long addFollowRecord(long companyId, String method, String content, String nextDate) {
        ContentValues cv = new ContentValues();
        cv.put("company_id", companyId); cv.put("follow_time", now()); cv.put("follow_method", nonNull(method)); cv.put("content", nonNull(content)); cv.put("next_follow_date", nonNull(nextDate).trim()); cv.put("done", 0); cv.put("created_at", now());
        long id = getWritableDatabase().insert("follow_records", null, cv);
        logOperation("跟进记录", "新增跟进记录", "企业ID=" + companyId + " 下次跟进=" + nonNull(nextDate));
        rebuildStatsCache(getWritableDatabase());
        return id;
    }

    public void deleteFollowRecord(long id) {
        getWritableDatabase().delete("follow_records", "id=?", new String[]{String.valueOf(id)});
        logOperation("跟进记录", "删除跟进记录", "记录ID=" + id);
        rebuildStatsCache(getWritableDatabase());
    }

    public void markFollowDone(long id, boolean done) {
        ContentValues cv = new ContentValues(); cv.put("done", done ? 1 : 0);
        getWritableDatabase().update("follow_records", cv, "id=?", new String[]{String.valueOf(id)});
        rebuildStatsCache(getWritableDatabase());
    }

    public List<FollowTaskItem> getFollowTasks(String mode, int limit) {
        ArrayList<FollowTaskItem> list = new ArrayList<>();
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date());
        String where;
        if ("today".equals(mode)) where = "fr.done=0 AND fr.next_follow_date='" + today + "'";
        else if ("overdue".equals(mode)) where = "fr.done=0 AND fr.next_follow_date<>'' AND fr.next_follow_date<'" + today + "'";
        else where = "fr.done=0 AND fr.next_follow_date>'" + today + "' AND fr.next_follow_date<=date('" + today + "','+7 day')";
        String sql = "SELECT fr.id,fr.company_id,c.name,c.customer_status,fr.follow_method,fr.content,fr.next_follow_date,fr.created_at FROM follow_records fr JOIN companies c ON fr.company_id=c.id WHERE " + where + " ORDER BY fr.next_follow_date ASC,fr.id DESC LIMIT ?";
        Cursor c = getReadableDatabase().rawQuery(sql, new String[]{String.valueOf(limit <= 0 ? 100 : limit)});
        try { while (c.moveToNext()) { FollowTaskItem it = new FollowTaskItem(); int i=0; it.followId=c.getLong(i++); it.companyId=c.getLong(i++); it.companyName=c.getString(i++); it.customerStatus=c.getString(i++); it.followMethod=c.getString(i++); it.content=c.getString(i++); it.nextFollowDate=c.getString(i++); it.createdAt=c.getString(i++); list.add(it); } } finally { c.close(); }
        return list;
    }

    public List<CompanyItem> getPotentialCompanies(int limit) {
        ArrayList<CompanyItem> list = new ArrayList<>();
        String sql = "SELECT c.id,c.group_id,g.name,c.seq,c.name,c.industry,c.employee_count,c.region,c.address,c.customer_status,c.tags,c.extra_info,c.created_at,c.updated_at,c.contact_count,c.note_count,c.imported_contact_count " +
                "FROM companies c LEFT JOIN groups_tbl g ON c.group_id=g.id LEFT JOIN (SELECT company_id,MAX(star_level) ms FROM contacts GROUP BY company_id) st ON st.company_id=c.id LEFT JOIN (SELECT company_id,MIN(next_follow_date) nf FROM follow_records WHERE done=0 AND next_follow_date<>'' GROUP BY company_id) fr ON fr.company_id=c.id " +
                "WHERE c.customer_status='潜力' OR c.customer_status='重点' ORDER BY COALESCE(st.ms,0) DESC, CASE WHEN fr.nf IS NULL THEN 1 ELSE 0 END, fr.nf ASC, c.updated_at DESC LIMIT ?";
        Cursor c = getReadableDatabase().rawQuery(sql, new String[]{String.valueOf(limit <= 0 ? 150 : limit)});
        try { while (c.moveToNext()) list.add(companyFromCursor(c)); } finally { c.close(); }
        return list;
    }

    public List<DuplicateItem> findDuplicateSuspects(int limit) {
        ArrayList<DuplicateItem> list = new ArrayList<>();
        HashSet<String> added = new HashSet<>();
        Cursor p = getReadableDatabase().rawQuery("SELECT a.company_id,b.company_id,a.phone_norm FROM contacts a JOIN contacts b ON a.phone_norm=b.phone_norm AND a.company_id<b.company_id WHERE a.phone_norm<>'' GROUP BY a.company_id,b.company_id LIMIT ?", new String[]{String.valueOf(limit <= 0 ? 80 : limit)});
        try { while (p.moveToNext()) addDuplicate(list, added, p.getLong(0), p.getLong(1), "相同电话：" + p.getString(2)); } finally { p.close(); }
        ArrayList<CompanyItem> cs = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT id,group_id,0,seq,name,industry,employee_count,region,address,customer_status,tags,extra_info,created_at,updated_at,contact_count,note_count,tag_count,imported_contact_count FROM companies ORDER BY id DESC LIMIT 800", null);
        try { while (c.moveToNext()) { CompanyItem it = new CompanyItem(); int i=0; it.id=c.getLong(i++); it.groupId=c.getLong(i++); i++; it.seq=c.getInt(i++); it.name=c.getString(i++); it.industry=c.getString(i++); it.employeeCount=c.getString(i++); it.region=c.getString(i++); it.address=c.getString(i++); it.customerStatus=c.getString(i++); it.tags=c.getString(i++); it.extraInfo=c.getString(i++); it.createdAt=c.getString(i++); it.updatedAt=c.getString(i++); it.contactCount=c.getInt(i++); it.noteCount=c.getInt(i++); i++; it.importedContactCount=c.getInt(i++); cs.add(it); } } finally { c.close(); }
        for (int i=0;i<cs.size() && list.size() < (limit<=0?80:limit);i++) {
            String ai = simpleCompanyName(cs.get(i).name);
            if (ai.length() < 4) continue;
            for (int j=i+1;j<cs.size() && list.size() < (limit<=0?80:limit);j++) {
                String bj = simpleCompanyName(cs.get(j).name);
                if (bj.length() < 4) continue;
                if (ai.equals(bj) || ai.contains(bj) || bj.contains(ai)) addDuplicate(list, added, cs.get(i).id, cs.get(j).id, "企业名称高度相似");
            }
        }
        return list;
    }

    private void addDuplicate(ArrayList<DuplicateItem> list, HashSet<String> added, long a, long b, String reason) {
        if (a == b) return;
        long x = Math.min(a,b), y = Math.max(a,b);
        String key = x + "_" + y;
        if (added.contains(key)) return;
        Cursor ig = getReadableDatabase().rawQuery("SELECT key FROM duplicate_ignores WHERE key=?", new String[]{key});
        try { if (ig.moveToFirst()) return; } finally { ig.close(); }
        CompanyItem ca = getCompany(x), cb = getCompany(y);
        DuplicateItem it = new DuplicateItem(); it.companyAId=x; it.companyBId=y; it.companyAName=ca.name; it.companyBName=cb.name; it.reason=reason; it.key=key; list.add(it); added.add(key);
    }

    private static String simpleCompanyName(String s) {
        String t = normalizeCompany(s);
        String[] words = {"有限责任公司","股份有限公司","有限公司","集团","公司","企业","厂","商行","经营部","上海市","上海","浙江省","浙江","江苏省","江苏"};
        for (String w : words) t = t.replace(w, "");
        return t;
    }

    public void ignoreDuplicate(String key) {
        ContentValues cv = new ContentValues(); cv.put("key", key); cv.put("created_at", now());
        getWritableDatabase().insertWithOnConflict("duplicate_ignores", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void mergeCompanies(long keepId, long removeId) {
        if (keepId <= 0 || removeId <= 0 || keepId == removeId) return;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            CompanyItem keep = getCompany(keepId); CompanyItem rem = getCompany(removeId);
            String higherStatus = statusRank(rem.customerStatus) > statusRank(keep.customerStatus) ? normalizeStatus(rem.customerStatus) : normalizeStatus(keep.customerStatus);
            ContentValues ccv = new ContentValues();
            ccv.put("industry", empty(keep.industry) ? rem.industry : keep.industry);
            ccv.put("employee_count", empty(keep.employeeCount) ? rem.employeeCount : keep.employeeCount);
            ccv.put("region", empty(keep.region) ? rem.region : keep.region);
            ccv.put("address", empty(keep.address) ? rem.address : keep.address);
            ccv.put("customer_status", higherStatus);
            ccv.put("tags", mergeSemi(keep.tags, rem.tags));
            ccv.put("extra_info", mergeSemi(keep.extraInfo, rem.extraInfo));
            ccv.put("updated_at", now());
            db.update("companies", ccv, "id=?", new String[]{String.valueOf(keepId)});
            Cursor ct = db.rawQuery("SELECT contact_name,phone,phone_norm,star_level FROM contacts WHERE company_id=?", new String[]{String.valueOf(removeId)});
            try { while (ct.moveToNext()) { String name=ct.getString(0), phone=ct.getString(1), norm=ct.getString(2); int star=ct.getInt(3); Cursor ex = db.rawQuery("SELECT id,star_level FROM contacts WHERE company_id=? AND phone_norm=?", new String[]{String.valueOf(keepId), norm}); try { if (ex.moveToFirst()) { if (star > ex.getInt(1)) { ContentValues scv = new ContentValues(); scv.put("star_level", star); db.update("contacts", scv, "id=?", new String[]{String.valueOf(ex.getLong(0))}); } } else addContactIfNew(db, keepId, name, phone, star); } finally { ex.close(); } } } finally { ct.close(); }
            db.execSQL("UPDATE notes SET company_id=" + keepId + " WHERE company_id=" + removeId);
            db.execSQL("UPDATE follow_records SET company_id=" + keepId + " WHERE company_id=" + removeId);
            db.execSQL("UPDATE call_records SET app_company_id=" + keepId + ", app_company_name=(SELECT name FROM companies WHERE id=" + keepId + ") WHERE app_company_id=" + removeId);
            db.delete("contacts", "company_id=?", new String[]{String.valueOf(removeId)});
            db.delete("companies", "id=?", new String[]{String.valueOf(removeId)});
            updateCompanyCache(db, keepId);
            resequenceGroup(keep.groupId); resequenceGroup(rem.groupId);
            rebuildStatsCache(db);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        logOperation("疑似重复", "手动合并企业", "保留ID=" + keepId + " 合并ID=" + removeId);
    }

    public List<PhoneIssueItem> scanPhoneIssues(int limit) {
        ArrayList<PhoneIssueItem> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT ct.id,ct.company_id,c.name,ct.contact_name,ct.phone,ct.phone_norm FROM contacts ct JOIN companies c ON ct.company_id=c.id ORDER BY ct.id DESC LIMIT 5000", null);
        try { while (c.moveToNext() && list.size() < (limit<=0?200:limit)) { PhoneIssueItem it = new PhoneIssueItem(); it.contactId=c.getLong(0); it.companyId=c.getLong(1); it.companyName=c.getString(2); it.contactName=c.getString(3); it.phone=c.getString(4); it.phoneNorm=c.getString(5); String norm=normalizePhone(it.phone); if (!norm.equals(it.phone) || !norm.equals(it.phoneNorm)) { it.reason="格式可清洗"; it.suggested=norm; list.add(it); } else if (norm.length() < 5 || norm.length() > 13) { it.reason="号码长度异常"; it.suggested=norm; list.add(it); } } } finally { c.close(); }
        Cursor d = getReadableDatabase().rawQuery("SELECT phone_norm,COUNT(DISTINCT company_id) FROM contacts WHERE phone_norm<>'' GROUP BY phone_norm HAVING COUNT(DISTINCT company_id)>1 LIMIT 80", null);
        try { while (d.moveToNext() && list.size() < (limit<=0?200:limit)) { PhoneIssueItem it = new PhoneIssueItem(); it.reason="同一号码出现在多个企业"; it.phoneNorm=d.getString(0); it.suggested="涉及企业数：" + d.getInt(1); list.add(it); } } finally { d.close(); }
        return list;
    }

    public int cleanPhoneFormats() {
        int n=0; SQLiteDatabase db = getWritableDatabase();
        Cursor c = db.rawQuery("SELECT id,phone FROM contacts", null);
        db.beginTransaction();
        try { while (c.moveToNext()) { long id=c.getLong(0); String old=c.getString(1); String norm=normalizePhone(old); if (!empty(norm) && !norm.equals(old)) { ContentValues cv=new ContentValues(); cv.put("phone", norm); cv.put("phone_norm", norm); n += db.update("contacts", cv, "id=?", new String[]{String.valueOf(id)}); } } db.setTransactionSuccessful(); } finally { c.close(); db.endTransaction(); }
        rebuildAllCaches(); logOperation("号码清洗", "统一号码格式", "处理数量=" + n); return n;
    }

    public GroupStats getGroupStats(long groupId) {
        GroupStats gs = new GroupStats();
        SQLiteDatabase db = getReadableDatabase();
        gs.companyCount = intQuery(db, "SELECT COUNT(*) FROM companies WHERE group_id=" + groupId);
        gs.contactCount = intQuery(db, "SELECT COUNT(*) FROM contacts ct JOIN companies c ON ct.company_id=c.id WHERE c.group_id=" + groupId);
        gs.importedCount = intQuery(db, "SELECT COUNT(*) FROM contacts ct JOIN companies c ON ct.company_id=c.id WHERE c.group_id=" + groupId + " AND ct.imported=1");
        gs.focusCount = intQuery(db, "SELECT COUNT(*) FROM companies WHERE group_id=" + groupId + " AND customer_status='关注'");
        gs.followCount = intQuery(db, "SELECT COUNT(*) FROM companies WHERE group_id=" + groupId + " AND customer_status='跟进'");
        gs.importantCount = intQuery(db, "SELECT COUNT(*) FROM companies WHERE group_id=" + groupId + " AND (customer_status='潜力' OR customer_status='重点')");
        gs.star3Count = intQuery(db, "SELECT COUNT(*) FROM contacts ct JOIN companies c ON ct.company_id=c.id WHERE c.group_id=" + groupId + " AND ct.star_level>=3");
        gs.star2Count = intQuery(db, "SELECT COUNT(*) FROM contacts ct JOIN companies c ON ct.company_id=c.id WHERE c.group_id=" + groupId + " AND ct.star_level=2");
        gs.star1Count = intQuery(db, "SELECT COUNT(*) FROM contacts ct JOIN companies c ON ct.company_id=c.id WHERE c.group_id=" + groupId + " AND ct.star_level=1");
        return gs;
    }


    public List<ExceptionItem> getExceptions(int limit) {
        ArrayList<ExceptionItem> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT id,source,row_no,company_name,reason,raw_text,created_at FROM exception_records ORDER BY id DESC LIMIT ?", new String[]{String.valueOf(limit <= 0 ? 100 : limit)});
        try {
            while (c.moveToNext()) {
                ExceptionItem it = new ExceptionItem(); int i=0;
                it.id=c.getLong(i++); it.source=c.getString(i++); it.rowNo=c.getInt(i++); it.companyName=c.getString(i++); it.reason=c.getString(i++); it.rawText=c.getString(i++); it.createdAt=c.getString(i++);
                list.add(it);
            }
        } finally { c.close(); }
        return list;
    }


    public int countNotes(long companyId) {
        return intQuery(getReadableDatabase(), "SELECT COUNT(*) FROM notes WHERE company_id=" + companyId);
    }

    public int countFollowRecords(long companyId) {
        return intQuery(getReadableDatabase(), "SELECT COUNT(*) FROM follow_records WHERE company_id=" + companyId);
    }

    public int countImportedContacts(long companyId) {
        return intQuery(getReadableDatabase(), "SELECT COUNT(*) FROM contacts WHERE company_id=" + companyId + " AND imported=1");
    }

    public int countCompaniesBySpecialFilter(String filter) {
        return getCompanyIdsByFilter("", "", 0, filter, 1000000).size();
    }

    public ContactItem getBestContact(long companyId) {
        Cursor c = getReadableDatabase().rawQuery("SELECT ct.id,ct.company_id,c.name,ct.contact_name,ct.phone,ct.phone_norm,ct.contact_order,ct.star_level,ct.imported,ct.raw_contact_id,ct.imported_at,g.name,c.customer_status,c.seq,c.group_id FROM contacts ct JOIN companies c ON ct.company_id=c.id LEFT JOIN groups_tbl g ON c.group_id=g.id WHERE ct.company_id=? ORDER BY ct.star_level DESC,ct.contact_order LIMIT 1", new String[]{String.valueOf(companyId)});
        try { return c.moveToFirst() ? contactFromCursor(c) : null; } finally { c.close(); }
    }

    public FollowSummary getFollowSummary(long companyId) {
        FollowSummary fs = new FollowSummary();
        Cursor c = getReadableDatabase().rawQuery("SELECT follow_time,next_follow_date,content FROM follow_records WHERE company_id=? ORDER BY COALESCE(follow_time,created_at) DESC,id DESC LIMIT 1", new String[]{String.valueOf(companyId)});
        try { if (c.moveToFirst()) { fs.lastFollowTime = c.getString(0); fs.nextFollowDate = c.getString(1); fs.lastContent = c.getString(2); } } finally { c.close(); }
        Cursor n = getReadableDatabase().rawQuery("SELECT next_follow_date FROM follow_records WHERE company_id=? AND done=0 AND next_follow_date<>'' ORDER BY next_follow_date ASC LIMIT 1", new String[]{String.valueOf(companyId)});
        try { if (n.moveToFirst()) fs.nextFollowDate = n.getString(0); } finally { n.close(); }
        fs.followCount = countFollowRecords(companyId);
        return fs;
    }

    public CompanyImpact getCompanyImpact(long companyId) {
        CompanyImpact it = new CompanyImpact();
        it.contactCount = intQuery(getReadableDatabase(), "SELECT COUNT(*) FROM contacts WHERE company_id=" + companyId);
        it.noteCount = intQuery(getReadableDatabase(), "SELECT COUNT(*) FROM notes WHERE company_id=" + companyId);
        it.followCount = intQuery(getReadableDatabase(), "SELECT COUNT(*) FROM follow_records WHERE company_id=" + companyId);
        it.importedCount = intQuery(getReadableDatabase(), "SELECT COUNT(*) FROM contacts WHERE company_id=" + companyId + " AND imported=1");
        return it;
    }

    public void postponeFollow(long followId, int days) {
        if (days <= 0) days = 1;
        ContentValues cv = new ContentValues();
        cv.put("next_follow_date", new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date(System.currentTimeMillis() + days * 24L * 3600L * 1000L)));
        cv.put("done", 0);
        getWritableDatabase().update("follow_records", cv, "id=?", new String[]{String.valueOf(followId)});
        logOperation("跟进延期", "延期" + days + "天", "跟进记录ID=" + followId);
        rebuildStatsCache(getWritableDatabase());
    }

    public List<FollowTaskItem> getAllPendingFollowTasks(int limit) {
        ArrayList<FollowTaskItem> list = new ArrayList<>();
        String sql = "SELECT fr.id,fr.company_id,c.name,c.customer_status,fr.follow_method,fr.content,fr.next_follow_date,fr.created_at FROM follow_records fr JOIN companies c ON fr.company_id=c.id WHERE fr.done=0 ORDER BY CASE WHEN fr.next_follow_date='' OR fr.next_follow_date IS NULL THEN 1 ELSE 0 END, fr.next_follow_date ASC, fr.id DESC LIMIT ?";
        Cursor c = getReadableDatabase().rawQuery(sql, new String[]{String.valueOf(limit <= 0 ? 200 : limit)});
        try { while (c.moveToNext()) { FollowTaskItem it = new FollowTaskItem(); int i=0; it.followId=c.getLong(i++); it.companyId=c.getLong(i++); it.companyName=c.getString(i++); it.customerStatus=c.getString(i++); it.followMethod=c.getString(i++); it.content=c.getString(i++); it.nextFollowDate=c.getString(i++); it.createdAt=c.getString(i++); list.add(it); } } finally { c.close(); }
        return list;
    }

    public List<CompanyItem> getCompaniesPage(String query, String status, long groupId, String specialFilter, int limit, int offset) {
        return getCompaniesPage(query, status, groupId, specialFilter, "", limit, offset);
    }

    public List<CompanyItem> getCompaniesPage(String query, String status, long groupId, String specialFilter, String tagFilter, int limit, int offset) {
        ArrayList<CompanyItem> base = new ArrayList<>(getCompaniesPage(query, status, groupId, Math.max(limit * 6, limit + 200), offset));
        if (empty(specialFilter) && empty(tagFilter)) return base.size() > limit ? new ArrayList<>(base.subList(0, limit)) : base;
        SQLiteDatabase db = getReadableDatabase();
        ArrayList<CompanyItem> out = new ArrayList<>();
        for (CompanyItem c : base) {
            boolean ok = true;
            if ("unimported".equals(specialFilter)) ok = c.contactCount > c.importedContactCount;
            else if ("no_phone".equals(specialFilter)) ok = c.contactCount == 0;
            else if ("has_phone".equals(specialFilter)) ok = c.contactCount > 0;
            else if ("has_note".equals(specialFilter)) ok = c.noteCount > 0;
            else if ("recent".equals(specialFilter)) ok = !empty(c.updatedAt);
            if (ok && !empty(tagFilter)) ok = companyHasTag(db, c.id, tagFilter);
            if (ok) out.add(c);
            if (out.size() >= limit) break;
        }
        return out;
    }

    public List<Long> getCompanyIdsByFilter(String query, String status, long groupId, String specialFilter, int max) {
        return getCompanyIdsByFilter(query, status, groupId, specialFilter, "", max);
    }

    public List<Long> getCompanyIdsByFilter(String query, String status, long groupId, String specialFilter, String tagFilter, int max) {
        ArrayList<Long> ids = new ArrayList<>();
        List<CompanyItem> list = getCompaniesPage(query, status, groupId, specialFilter, tagFilter, max <= 0 ? 5000 : max, 0);
        for (CompanyItem c : list) ids.add(c.id);
        return ids;
    }

    public List<CompanyItem> getIncrementalCompanies(String since) {
        ArrayList<CompanyItem> list = new ArrayList<>();
        String sql = "SELECT c.id,c.group_id,g.name,c.seq,c.name,c.industry,c.employee_count,c.region,c.address,c.customer_status,c.tags,c.extra_info,c.created_at,c.updated_at,c.contact_count,c.note_count,c.imported_contact_count FROM companies c LEFT JOIN groups_tbl g ON c.group_id=g.id WHERE c.created_at>? OR c.updated_at>? ORDER BY c.updated_at ASC,c.id ASC";
        Cursor c = getReadableDatabase().rawQuery(sql, new String[]{nonNull(since), nonNull(since)});
        try { while (c.moveToNext()) list.add(companyFromCursor(c)); } finally { c.close(); }
        return list;
    }

    public List<ContactItem> getContactsSince(String since) {
        ArrayList<ContactItem> list = new ArrayList<>();
        String sql = "SELECT ct.id,ct.company_id,c.name,ct.contact_name,ct.phone,ct.phone_norm,ct.contact_order,ct.star_level,ct.imported,ct.raw_contact_id,ct.imported_at,g.name,c.customer_status,c.seq,c.group_id FROM contacts ct JOIN companies c ON ct.company_id=c.id LEFT JOIN groups_tbl g ON c.group_id=g.id WHERE ct.created_at>? ORDER BY ct.created_at ASC,ct.id ASC";
        Cursor c = getReadableDatabase().rawQuery(sql, new String[]{nonNull(since)});
        try { while (c.moveToNext()) list.add(contactFromCursor(c)); } finally { c.close(); }
        return list;
    }

    public List<NoteItem> getNotesSince(String since) {
        ArrayList<NoteItem> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT id,company_id,content,created_at FROM notes WHERE created_at>? ORDER BY created_at ASC,id ASC", new String[]{nonNull(since)});
        try { while (c.moveToNext()) { NoteItem n = new NoteItem(); n.id=c.getLong(0); n.companyId=c.getLong(1); n.content=c.getString(2); n.createdAt=c.getString(3); list.add(n); } } finally { c.close(); }
        return list;
    }

    public List<FollowRecordItem> getFollowRecordsSince(String since) {
        ArrayList<FollowRecordItem> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT id,company_id,follow_time,follow_method,content,next_follow_date,done,created_at FROM follow_records WHERE created_at>? ORDER BY created_at ASC,id ASC", new String[]{nonNull(since)});
        try { while (c.moveToNext()) list.add(followFromCursor(c)); } finally { c.close(); }
        return list;
    }

    public void logBackup(String fileName, String type, String result) {
        try {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put("file_name", nonNull(fileName)); cv.put("type", nonNull(type)); cv.put("result", nonNull(result)); cv.put("created_at", now());
            cv.put("company_count", intQuery(db, "SELECT COUNT(*) FROM companies"));
            cv.put("contact_count", intQuery(db, "SELECT COUNT(*) FROM contacts"));
            cv.put("note_count", intQuery(db, "SELECT COUNT(*) FROM notes"));
            db.insert("backup_logs", null, cv);
        } catch (Exception ignored) {}
    }

    public List<BackupLogItem> getBackupLogs(int limit) {
        ArrayList<BackupLogItem> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT id,file_name,type,result,created_at,COALESCE(company_count,0),COALESCE(contact_count,0),COALESCE(note_count,0) FROM backup_logs ORDER BY id DESC LIMIT ?", new String[]{String.valueOf(limit <= 0 ? 50 : limit)});
        try { while (c.moveToNext()) { BackupLogItem it = new BackupLogItem(); int i=0; it.id=c.getLong(i++); it.fileName=c.getString(i++); it.type=c.getString(i++); it.result=c.getString(i++); it.createdAt=c.getString(i++); it.companyCount=c.getInt(i++); it.contactCount=c.getInt(i++); it.noteCount=c.getInt(i++); list.add(it); } } finally { c.close(); }
        return list;
    }

    public long findCompanyByName(String name) { return findCompanyId(getReadableDatabase(), name); }

    public long findCompanyByPhone(String phone) {
        String norm = normalizePhone(phone);
        if (empty(norm)) return -1;
        Cursor c = getReadableDatabase().rawQuery("SELECT company_id FROM contacts WHERE phone_norm=? LIMIT 1", new String[]{norm});
        try { return c.moveToFirst() ? c.getLong(0) : -1; } finally { c.close(); }
    }


    public DataHealth getDataHealth() {
        SQLiteDatabase db = getWritableDatabase();
        createIndexesAndMaintenanceTables(db);
        migrateLegacyTags(db);
        DataHealth h = new DataHealth();
        h.companyCount = intQuery(db, "SELECT COUNT(*) FROM companies");
        h.contactCount = intQuery(db, "SELECT COUNT(*) FROM contacts");
        h.noPhoneCompanyCount = intQuery(db, "SELECT COUNT(*) FROM companies c LEFT JOIN contacts ct ON c.id=ct.company_id WHERE ct.id IS NULL");
        h.duplicatePhoneCount = intQuery(db, "SELECT COUNT(*) FROM (SELECT phone_norm FROM contacts WHERE phone_norm<>'' GROUP BY phone_norm HAVING COUNT(DISTINCT company_id)>1)");
        h.abnormalPhoneCount = intQuery(db, "SELECT COUNT(*) FROM contacts WHERE LENGTH(phone_norm)<5 OR LENGTH(phone_norm)>13");
        h.noTagCompanyCount = intQuery(db, "SELECT COUNT(*) FROM companies c LEFT JOIN company_tags ct ON c.id=ct.company_id WHERE ct.company_id IS NULL");
        h.tagCompanyCount = intQuery(db, "SELECT COUNT(DISTINCT company_id) FROM company_tags");
        h.overdueFollowCount = getStats().overdueFollowCount;
        h.pendingFollowCount = intQuery(db, "SELECT COUNT(*) FROM follow_records WHERE done=0");
        h.tagCount = intQuery(db, "SELECT COUNT(*) FROM tags");
        return h;
    }

    public int cleanOldLogs(int keepRecent) {
        SQLiteDatabase db = getWritableDatabase();
        int keep = keepRecent <= 0 ? 500 : keepRecent;
        int n = 0;
        n += db.delete("operation_logs", "id NOT IN (SELECT id FROM operation_logs ORDER BY id DESC LIMIT " + keep + ")", null);
        n += db.delete("import_logs", "id NOT IN (SELECT id FROM import_logs ORDER BY id DESC LIMIT " + keep + ")", null);
        logOperation("日志清理", "清理历史日志", "处理数量=" + n + "，保留最近" + keep + "条");
        return n;
    }

    public int cleanOldCallRecords(int days) {
        SQLiteDatabase db = getWritableDatabase();
        long before = System.currentTimeMillis() - (days <= 0 ? 90L : (long)days) * 24L * 3600L * 1000L;
        int n = db.delete("call_records", "call_time<? AND (status='已忽略' OR status='无效' OR status='未处理')", new String[]{String.valueOf(before)});
        rebuildStatsCache(db);
        logOperation("通话记录", "清理历史通话记录", "清理" + days + "天前记录=" + n);
        return n;
    }

    public static class FollowSummary { public int followCount; public String lastFollowTime, nextFollowDate, lastContent; }
    public static class CompanyImpact { public int contactCount, noteCount, followCount, importedCount; }
    public static class BackupLogItem { public long id; public int companyCount, contactCount, noteCount; public String fileName, type, result, createdAt; }
    public static class TagItem { public long id; public int companyCount; public String name; }
    public static class DataHealth { public int companyCount, contactCount, noPhoneCompanyCount, duplicatePhoneCount, abnormalPhoneCount, noTagCompanyCount, tagCompanyCount, overdueFollowCount, pendingFollowCount, tagCount; }


    public static class GroupItem { public long id; public String name; public GroupItem(long i,String n){id=i;name=n;} public String toString(){return name;} }
    public static class Stats { public int companyCount, contactCount, importedCount, statusNone, focusCount, followCount, importantCount, exceptionCount, todayFollowCount, overdueFollowCount, soonFollowCount, callPendingCount; }
    public static class ImportResult { public int newCompanies, mergedCompanies, newContacts, newNotes, newFollows, newTags, skippedNoCompany, duplicateRows, abnormalRows, statusUpgradeCount, statusKeepCount, newPhoneCount, duplicatePhoneCount, phoneAbnormalCount, multiPhoneCellCount, splitPhoneCount; }
    public static class ImportPreviewResult extends ImportResult { }

    public static class CompanyItem {
        public long id, groupId; public int seq, contactCount, noteCount, importedContactCount;
        public String groupName, name, industry, employeeCount, region, address, customerStatus, tags, extraInfo, createdAt, updatedAt;
        public String statusText(){ String s = normalizeStatus(customerStatus); return empty(s) ? STATUS_NONE : s; }
        public String importText(){ if (contactCount == 0) return "无联系人"; if (importedContactCount == 0) return "未导入"; if (importedContactCount >= contactCount) return "已导入"; return "部分导入"; }
    }

    public static class ContactItem {
        public long id, companyId, rawContactId, groupId; public int order, companySeq, starLevel; public boolean imported;
        public String companyName, contactName, phone, phoneNorm, importedAt, groupName, customerStatus;
        public String displayName(){
            String cn = nonNull(contactName).trim();
            if (!empty(cn)) return nonNull(companyName) + "-" + cn;
            if (order > 1) return nonNull(companyName) + "-联系人" + order;
            return nonNull(companyName);
        }
    }

    public static class NoteItem { public long id, companyId; public String content, createdAt; }
    public static class FollowRecordItem { public long id, companyId; public boolean done; public String followTime, followMethod, content, nextFollowDate, createdAt; }
    public static class FollowTaskItem { public long followId, companyId; public String companyName, customerStatus, followMethod, content, nextFollowDate, createdAt; }
    public static class DuplicateItem { public long companyAId, companyBId; public String companyAName, companyBName, reason, key; }
    public static class PhoneIssueItem { public long contactId, companyId; public String companyName, contactName, phone, phoneNorm, reason, suggested; }
    public static class GroupStats { public int companyCount, contactCount, importedCount, focusCount, followCount, importantCount, star1Count, star2Count, star3Count; }
    public static class LogItem { public long id; public int totalRows,newCompanies,mergedCompanies,newContacts,newNotes,newTags,skippedNoCompany,duplicateRows,abnormalRows; public String fileName,startedAt,finishedAt,message; }
    public static class ExceptionItem { public long id; public int rowNo; public String source,companyName,reason,rawText,createdAt; }
    public static class OperationLogItem { public long id; public String type,title,detail,createdAt; }
    public static class CallRecordItem { public long id,appCompanyId; public int duration; public String phone,phoneNorm,callType,callTimeText,cachedName,deviceName,appCompanyName,appContactName,appNote,status; }
}

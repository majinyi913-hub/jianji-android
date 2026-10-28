package cn.jianji.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public final class LedgerDb extends SQLiteOpenHelper {
    private static final String NET_SQL = "SELECT COALESCE(SUM(CASE WHEN type='income' THEN cents ELSE -cents END),0) FROM entries";
    public LedgerDb(Context context) { super(context, "jianji.db", null, 3); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE entries(id TEXT PRIMARY KEY,type TEXT NOT NULL CHECK(type IN ('income','expense')),cents INTEGER NOT NULL CHECK(cents>0 AND cents<=9999999999),category TEXT NOT NULL,date TEXT NOT NULL,note TEXT NOT NULL,created_at INTEGER NOT NULL,subcategory TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE INDEX entry_date ON entries(date DESC,created_at DESC)");
        db.execSQL("CREATE TABLE budgets(month TEXT PRIMARY KEY,cents INTEGER NOT NULL CHECK(cents>=0 AND cents<=9999999999))");
        db.execSQL("CREATE TABLE balance_state(id INTEGER PRIMARY KEY CHECK(id=1),base_cents INTEGER NOT NULL CHECK(base_cents BETWEEN -9000000000000000 AND 9000000000000000),updated_at INTEGER NOT NULL CHECK(updated_at>=0))");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) db.execSQL("ALTER TABLE entries ADD COLUMN subcategory TEXT NOT NULL DEFAULT ''");
        if (oldVersion < 3) db.execSQL("CREATE TABLE balance_state(id INTEGER PRIMARY KEY CHECK(id=1),base_cents INTEGER NOT NULL CHECK(base_cents BETWEEN -9000000000000000 AND 9000000000000000),updated_at INTEGER NOT NULL CHECK(updated_at>=0))");
    }

    private ContentValues values(Ledger.Entry e) {
        Ledger.validate(e);
        ContentValues v = new ContentValues();
        v.put("id", e.id); v.put("type", e.type); v.put("cents", e.cents); v.put("category", e.category);
        v.put("date", e.date); v.put("note", e.note); v.put("created_at", e.createdAt);
        v.put("subcategory", e.subcategory);
        return v;
    }
    public void save(Ledger.Entry e) { getWritableDatabase().insertWithOnConflict("entries", null, values(e), SQLiteDatabase.CONFLICT_REPLACE); }
    public void delete(String id) { getWritableDatabase().delete("entries", "id=?", new String[]{id}); }
    private Ledger.Entry read(Cursor c) {
        Ledger.Entry e = new Ledger.Entry();
        e.id = c.getString(0); e.type = c.getString(1); e.cents = c.getLong(2); e.category = c.getString(3);
        e.date = c.getString(4); e.note = c.getString(5); e.createdAt = c.getLong(6); e.subcategory = c.getString(7); return e;
    }
    public Ledger.Entry find(String id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,type,cents,category,date,note,created_at,subcategory FROM entries WHERE id=?", new String[]{id})) { return c.moveToFirst() ? read(c) : null; }
    }
    public List<Ledger.Entry> list(String month) {
        List<Ledger.Entry> result = new ArrayList<>();
        String where = month == null ? "" : " WHERE date>=? AND date<?";
        String[] args = month == null ? null : new String[]{month + "-01", java.time.YearMonth.parse(month).plusMonths(1) + "-01"};
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,type,cents,category,date,note,created_at,subcategory FROM entries" + where + " ORDER BY date DESC,created_at DESC,id ASC", args)) {
            while (c.moveToNext()) result.add(read(c));
        }
        return result;
    }
    public long count() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM entries", null)) { c.moveToFirst(); return c.getLong(0); }
    }
    public boolean hasBalance() { return hasBalance(getReadableDatabase()); }
    private boolean hasBalance(SQLiteDatabase db) {
        try(Cursor c=db.rawQuery("SELECT 1 FROM balance_state WHERE id=1",null)){return c.moveToFirst();}
    }
    private long net(SQLiteDatabase db) {
        try(Cursor c=db.rawQuery(NET_SQL,null)){c.moveToFirst();return c.getLong(0);}
    }
    public long currentBalance() {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT base_cents,("+NET_SQL+") FROM balance_state WHERE id=1",null)){
            if(!c.moveToFirst())throw new IllegalStateException("请先初始化目前余额");
            return Ledger.balance(c.getLong(0),c.getLong(1));
        }
    }
    public void setCurrentBalance(long cents) {
        if(cents < -Ledger.MAX_BALANCE_CENTS || cents > Ledger.MAX_BALANCE_CENTS)throw new IllegalArgumentException("余额超出支持范围");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            ContentValues v=new ContentValues();v.put("id",1);v.put("base_cents",Ledger.balanceBase(cents,net(db)));v.put("updated_at",System.currentTimeMillis());
            db.insertWithOnConflict("balance_state",null,v,SQLiteDatabase.CONFLICT_REPLACE);
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public long budget(String month) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT cents FROM budgets WHERE month=?", new String[]{month})) { return c.moveToFirst() ? c.getLong(0) : 0; }
    }
    public void setBudget(String month, long cents) {
        if (!Ledger.validMonth(month) || cents < 0 || cents > Ledger.MAX_CENTS) throw new IllegalArgumentException("预算无效");
        ContentValues v = new ContentValues(); v.put("month", month); v.put("cents", cents);
        getWritableDatabase().insertWithOnConflict("budgets", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }
    public String backup() throws Exception {
        SQLiteDatabase db=getReadableDatabase();db.beginTransaction();
        try {
        JSONObject root = new JSONObject(); root.put("app", "cn.jianji.app"); root.put("version", 3); root.put("exportedAt", System.currentTimeMillis());
        JSONArray entries = new JSONArray();
        for (Ledger.Entry e : list(null)) {
            JSONObject j = new JSONObject(); j.put("id", e.id); j.put("type", e.type); j.put("cents", e.cents); j.put("category", e.category);
            j.put("date", e.date); j.put("note", e.note); j.put("createdAt", e.createdAt);
            j.put("subcategory", e.subcategory);entries.put(j);
        }
        root.put("entries", entries);
        JSONArray budgets = new JSONArray();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT month,cents FROM budgets ORDER BY month", null)) {
            while(c.moveToNext()) { JSONObject j = new JSONObject(); j.put("month", c.getString(0)); j.put("cents", c.getLong(1)); budgets.put(j); }
        }
        root.put("budgets", budgets);root.put("balance",JSONObject.NULL);
        try(Cursor c=db.rawQuery("SELECT base_cents,updated_at FROM balance_state WHERE id=1",null)){
            if(c.moveToFirst()){JSONObject state=new JSONObject();state.put("baseCents",c.getLong(0));state.put("updatedAt",c.getLong(1));root.put("balance",state);}
        }
        String result=root.toString(2);db.setTransactionSuccessful();return result;
        }finally{db.endTransaction();}
    }
    private static long integer(JSONObject j, String key) throws Exception {
        Object value = j.get(key);
        if (!(value instanceof Integer) && !(value instanceof Long)) throw new IllegalArgumentException("备份中的数值格式无效");
        return ((Number)value).longValue();
    }
    /** Fully validate before an atomic, non-destructive merge. Existing IDs and budgets win. */
    public int[] restore(String data) throws Exception {
        JSONObject root = new JSONObject(data);
        long version = integer(root, "version");
        if (!"cn.jianji.app".equals(root.getString("app")) || (version < 1 || version > 3)) throw new IllegalArgumentException("请选择简记的 JSON 备份");
        JSONObject balanceState=null;
        if(version>=3){
            Object value=root.get("balance");
            if(value!=JSONObject.NULL){
                if(!(value instanceof JSONObject))throw new IllegalArgumentException("备份中的余额格式无效");
                balanceState=(JSONObject)value;long base=integer(balanceState,"baseCents"),updated=integer(balanceState,"updatedAt");
                if(base < -Ledger.MAX_BALANCE_BASE || base > Ledger.MAX_BALANCE_BASE || updated<0)throw new IllegalArgumentException("备份中的余额无效");
            }
        }
        JSONArray arr = root.getJSONArray("entries");
        if (arr.length() > 100000) throw new IllegalArgumentException("一次最多导入 100,000 条记录");
        List<Ledger.Entry> entries = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject j = arr.getJSONObject(i); Ledger.Entry e = new Ledger.Entry();
            e.id = j.getString("id"); e.type = j.getString("type"); e.cents = integer(j, "cents"); e.category = j.getString("category");
            e.date = j.getString("date"); e.note = j.getString("note"); e.createdAt = integer(j, "createdAt");
            e.subcategory = version == 1 ? "" : j.getString("subcategory"); Ledger.validate(e); entries.add(e);
        }
        JSONArray budgets = root.optJSONArray("budgets");
        if (budgets == null) budgets = new JSONArray();
        if (budgets.length() > 2412) throw new IllegalArgumentException("预算记录过多");
        for (int i = 0; i < budgets.length(); i++) {
            JSONObject b = budgets.getJSONObject(i); long amount = integer(b, "cents");
            if (!Ledger.validMonth(b.getString("month")) || amount < 0 || amount > Ledger.MAX_CENTS) throw new IllegalArgumentException("备份中的预算无效");
        }
        SQLiteDatabase db = getWritableDatabase(); int added = 0;
        db.beginTransaction();
        try {
            for (Ledger.Entry e : entries) if (db.insertWithOnConflict("entries", null, values(e), SQLiteDatabase.CONFLICT_IGNORE) != -1) added++;
            for (int i = 0; i < budgets.length(); i++) { JSONObject b = budgets.getJSONObject(i); ContentValues v = new ContentValues(); v.put("month", b.getString("month")); v.put("cents", integer(b,"cents")); db.insertWithOnConflict("budgets", null, v, SQLiteDatabase.CONFLICT_IGNORE); }
            if(balanceState!=null&&!hasBalance(db)){
                ContentValues v=new ContentValues();v.put("id",1);v.put("base_cents",integer(balanceState,"baseCents"));v.put("updated_at",integer(balanceState,"updatedAt"));
                db.insertOrThrow("balance_state",null,v);
            }
            if(hasBalance(db))currentBalance();
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        return new int[]{added, entries.size() - added};
    }
}

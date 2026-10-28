"""Exercise the exact SQL migration with a populated version-1 SQLite fixture."""
from pathlib import Path
import re
import sqlite3
import tempfile
import unittest

SOURCE = (Path(__file__).resolve().parents[1] / "app/src/main/java/cn/jianji/app/LedgerDb.java").read_text(encoding="utf-8")
CREATE = re.findall(r'db\.execSQL\("([^"\n]+)"\)', SOURCE.split("@Override public void onUpgrade")[0])
UPGRADE = re.findall(r'if \(oldVersion < (\d+)\) db\.execSQL\("([^"\n]+)"\)', SOURCE.split("@Override public void onUpgrade")[1].split("private ContentValues")[0])
NET_SQL = re.search(r'NET_SQL = "([^"\n]+)"', SOURCE).group(1)
BALANCE_SQL = "SELECT base_cents,(" + NET_SQL + ") FROM balance_state WHERE id=1"
LEGACY = """
CREATE TABLE entries(id TEXT PRIMARY KEY,type TEXT NOT NULL CHECK(type IN ('income','expense')),cents INTEGER NOT NULL CHECK(cents>0 AND cents<=9999999999),category TEXT NOT NULL,date TEXT NOT NULL,note TEXT NOT NULL,created_at INTEGER NOT NULL);
CREATE INDEX entry_date ON entries(date DESC,created_at DESC);
CREATE TABLE budgets(month TEXT PRIMARY KEY,cents INTEGER NOT NULL CHECK(cents>=0 AND cents<=9999999999));
"""


class MigrationTest(unittest.TestCase):
    def migrated(self):
        db = sqlite3.connect(":memory:")
        self.addCleanup(db.close)
        db.executescript(LEGACY)
        db.executemany("INSERT INTO entries VALUES(?,?,?,?,?,?,?)", [
            ("meal", "expense", 1234, "餐饮", "2026-10-28", "旧账早餐", 100),
            ("travel", "expense", 222, "交通", "2026-10-27", "旧交通", 200),
            ("salary", "income", 999999, "工资", "2026-10-01", "月收入", 300),
        ])
        db.execute("INSERT INTO budgets VALUES('2026-10', 500000)")
        for version, sql in UPGRADE:
            if 1 < int(version):
                db.execute(sql)
        return db

    def test_preserves_all_old_data(self):
        db = self.migrated()
        self.assertEqual(db.execute("SELECT COUNT(*),SUM(cents) FROM entries").fetchone(), (3, 1001455))
        self.assertEqual(db.execute("SELECT note,subcategory FROM entries WHERE id='meal'").fetchone(), ("旧账早餐", ""))
        self.assertEqual(db.execute("SELECT cents FROM budgets WHERE month='2026-10'").fetchone()[0], 500000)
        self.assertEqual(db.execute("PRAGMA integrity_check").fetchone()[0], "ok")

    def test_new_and_upgraded_schemas_match(self):
        old = self.migrated()
        fresh = sqlite3.connect(":memory:")
        self.addCleanup(fresh.close)
        for sql in CREATE:
            fresh.execute(sql)
        self.assertEqual(old.execute("PRAGMA table_info(entries)").fetchall(), fresh.execute("PRAGMA table_info(entries)").fetchall())
        self.assertEqual(old.execute("PRAGMA table_info(balance_state)").fetchall(), fresh.execute("PRAGMA table_info(balance_state)").fetchall())

    def test_can_add_subcategory_without_replacing_record(self):
        db = self.migrated()
        db.execute("UPDATE entries SET subcategory='早餐' WHERE id='meal'")
        self.assertEqual(db.execute("SELECT id,cents,note,subcategory FROM entries WHERE id='meal'").fetchone(), ("meal", 1234, "旧账早餐", "早餐"))
        self.assertEqual(db.execute("SELECT COUNT(*) FROM entries").fetchone()[0], 3)

    def test_version_2_upgrade_keeps_subcategories_and_requires_balance(self):
        db=sqlite3.connect(":memory:");self.addCleanup(db.close)
        for sql in CREATE:
            if "CREATE TABLE balance_state" not in sql:
                db.execute(sql)
        db.execute("INSERT INTO entries VALUES('v2','expense',500,'交通','2026-10-28','充电',123,'电动车充电')")
        for version,sql in UPGRADE:
            if 2<int(version):db.execute(sql)
        self.assertEqual(db.execute("SELECT subcategory FROM entries").fetchone()[0],"电动车充电")
        self.assertIsNone(db.execute(BALANCE_SQL).fetchone())

    def initialize(self, db, actual):
        net=db.execute(NET_SQL).fetchone()[0]
        db.execute("INSERT OR REPLACE INTO balance_state VALUES(1,?,1000)",(actual-net,))

    def current(self, db):
        row=db.execute(BALANCE_SQL).fetchone()
        return None if row is None else row[0]+row[1]

    def test_zero_is_initialized_and_survives_restart_state(self):
        db=self.migrated();self.assertIsNone(self.current(db));self.initialize(db,0)
        self.assertEqual(self.current(db),0)
        self.assertEqual(db.execute("SELECT COUNT(*) FROM balance_state").fetchone()[0],1)
        db.commit()
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/"balance.sqlite"
            saved=sqlite3.connect(path);db.backup(saved);saved.close()
            reopened=sqlite3.connect(path)
            try:
                self.assertEqual(self.current(reopened),0)
                self.assertEqual(reopened.execute("SELECT COUNT(*) FROM balance_state").fetchone()[0],1)
            finally:reopened.close()

    def test_initializing_with_existing_entries_does_not_double_count(self):
        db=self.migrated();net=db.execute(NET_SQL).fetchone()[0];self.initialize(db,10000)
        self.assertEqual(self.current(db),10000)
        self.assertEqual(db.execute(NET_SQL).fetchone()[0],net)
        self.assertEqual(db.execute("SELECT COUNT(*) FROM entries").fetchone()[0],3)

    def test_add_edit_type_change_and_delete_update_balance(self):
        db=self.migrated();self.initialize(db,10000)
        db.execute("INSERT INTO entries VALUES('new','expense',250,'餐饮','2025-01-01','补记',400,'午餐')")
        self.assertEqual(self.current(db),9750)
        db.execute("UPDATE entries SET cents=500 WHERE id='new'");self.assertEqual(self.current(db),9500)
        db.execute("UPDATE entries SET type='income',category='工资',subcategory='' WHERE id='new'");self.assertEqual(self.current(db),10500)
        db.execute("DELETE FROM entries WHERE id='new'");self.assertEqual(self.current(db),10000)
        db.execute("DELETE FROM entries WHERE id='meal'");self.assertEqual(self.current(db),11234)

    def test_recalibration_keeps_transactions_and_budget(self):
        db=self.migrated();self.initialize(db,10000);self.initialize(db,-100)
        self.assertEqual(self.current(db),-100)
        self.assertEqual(db.execute("SELECT COUNT(*) FROM entries").fetchone()[0],3)
        self.assertEqual(db.execute("SELECT cents FROM budgets").fetchone()[0],500000)

    def test_balance_state_rejects_duplicate_accounts_and_invalid_base(self):
        db=self.migrated();self.initialize(db,0)
        with self.assertRaises(sqlite3.IntegrityError):db.execute("INSERT INTO balance_state VALUES(2,0,0)")
        with self.assertRaises(sqlite3.IntegrityError):db.execute("UPDATE balance_state SET base_cents=9000000000000001")


if __name__ == "__main__":
    unittest.main(verbosity=2)

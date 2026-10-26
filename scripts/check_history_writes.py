#!/usr/bin/env python3
"""Check that every write to a table the history covers goes through its one door.

The history of writes (docs/DATA.md, « History of writes ») keeps each row a step touches, whole
before and after, so that an undo puts it back. It can only keep what it sees: one class,
core/history/Writes.kt, reads the row before a write, makes the write and keeps both copies.
A write made anywhere else -- a DAO's insert called from a service, a DELETE in plain SQL --
escapes the history, and the undo that comes to it later finds a row it never saw.

This check finds the DAO methods that write a covered table, from the DAOs themselves (an
@Insert, @Update, @Delete or @Upsert of a covered entity, an @Query that inserts, updates or
deletes in a covered table), and refuses a call to one outside the door: a call on the DAO
AppDatabase hands out (`database.zoneDao().insertZone(...)`), or on a value holding it
(`val dao = database.fileDao()`, a parameter typed with the DAO). It refuses as well a
statement that writes a covered table in plain SQL.

Allowed besides the door: the migrations (core/versioning, AppDatabase.kt), which bring the
history along with the data, and the restoration of a backup (BackupService.kt), which replaces
the whole database and empties the history. A conversation's session and messages are written
outside the door, as the history does not keep them: ai_sessions and session_messages are
written through it for a routine's message alone, which this check cannot tell from a
conversation by reading the sources. A client's last use (touchClient) is the app's alone to
write, a part the history leaves aside.

Usage:
    ./scripts/check_history_writes.py
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCES = ROOT / "app" / "src" / "main" / "java"
DATABASE = SOURCES / "app" / "treelune" / "core" / "database" / "AppDatabase.kt"

COVERED = {
    "zones", "tool_instances", "tool_data", "variables", "events", "automations", "files",
    "identities", "mcp_clients", "app_settings_categories", "tasks", "apis",
}

ALLOWED_FILES = {
    "app/treelune/core/database/AppDatabase.kt",
    "app/treelune/core/services/BackupService.kt",
}
ALLOWED_DIRS = ("app/treelune/core/history/", "app/treelune/core/versioning/")

# Writes of a covered table's parts the history leaves to the app (Kept)
ALLOWED_METHODS = {"touchClient"}

WRITE_SQL = re.compile(r'\b(?:INSERT(?:\s+OR\s+\w+)?\s+INTO|UPDATE|DELETE\s+FROM|REPLACE\s+INTO)\s+`?(\w+)`?', re.I)
ANNOTATED = re.compile(
    r'@(Insert|Update|Delete|Upsert|Query)\b(\((?:[^()]|\([^()]*\))*\))?\s*(?:@\w+(?:\([^)]*\))?\s*)*'
    r'(?:abstract\s+|open\s+)?(?:suspend\s+)?fun\s+(\w+)\s*\(([^)]*)\)',
    re.S,
)
DAO_DECLARATION = re.compile(r'@Dao\s*(?:interface|abstract\s+class)\s+(\w+)')
ACCESSOR = re.compile(r'abstract\s+fun\s+(\w+)\(\)\s*:\s*([\w.]+)')
# Overlapping: in `database.zoneDao().insertZone(`, zoneDao is the method of one call and the receiver of the next
CALL = re.compile(r'(?=\b(\w+)(\(\))?\s*\.\s*(\w+)\s*\()')
HOLDER = re.compile(r'\b(?:val|var)\s+(\w+)\s*(?::\s*([\w.]+))?\s*(?:by\s+lazy\s*\{|get\(\)\s*=|=)([^\n]*)')
PARAMETER = re.compile(r'\b(\w+)\s*:\s*([\w.]*Dao)\b')


def entities(texts):
    """Entity class name -> table name, from every @Entity of the sources."""
    found = {}
    for text in texts:
        for match in re.finditer(r'@Entity\s*\((.*?)\)\s*(?:data\s+)?class\s+(\w+)', text, re.S):
            table = re.search(r'tableName\s*=\s*"(\w+)"', match.group(1))
            if table:
                found[match.group(2)] = table.group(1)
    return found


def writing_methods(texts, tables):
    """DAO type -> the names of its methods that write a covered table."""
    by_dao = {}
    for text in texts:
        declarations = list(DAO_DECLARATION.finditer(text))
        for i, declaration in enumerate(declarations):
            body = text[declaration.end(): declarations[i + 1].start() if i + 1 < len(declarations) else len(text)]
            names = set()
            for kind, args, name, params in ANNOTATED.findall(body):
                if kind == "Query":
                    sql = WRITE_SQL.search(args or "")
                    if sql and sql.group(1) in COVERED:
                        names.add(name)
                elif any(tables.get(t) in COVERED for t in re.findall(r':\s*(?:List<)?(\w+)', params)):
                    names.add(name)
            names -= ALLOWED_METHODS
            if names:
                by_dao[declaration.group(1)] = names
    return by_dao


def short(type_name):
    return type_name.rsplit(".", 1)[-1]


def allowed(relative):
    return relative in ALLOWED_FILES or relative.startswith(ALLOWED_DIRS)


def main():
    files = sorted(SOURCES.rglob("*.kt"))
    texts = {path: path.read_text(encoding="utf-8") for path in files}
    methods = writing_methods(texts.values(), entities(texts.values()))
    if not methods:
        print("no DAO method found that writes a covered table: the check reads nothing")
        return 1
    accessors = {name: short(kind) for name, kind in ACCESSOR.findall(DATABASE.read_text(encoding="utf-8"))}

    found = []
    for path, text in texts.items():
        relative = path.relative_to(SOURCES).as_posix()
        if allowed(relative):
            continue
        # The values of this file that hold a DAO: declared with its type, or given one of AppDatabase's
        holders = {}
        for name, kind, initializer in HOLDER.findall(text):
            if kind and short(kind) in methods:
                holders[name] = short(kind)
            else:
                accessor = re.search(r'\b(\w+)\(\)\s*\}?\s*$', initializer.strip())
                if accessor and accessor.group(1) in accessors:
                    holders[name] = accessors[accessor.group(1)]
        for name, kind in PARAMETER.findall(text):
            holders.setdefault(name, short(kind))

        is_dao = "@Dao" in text
        for number, line in enumerate(text.splitlines(), start=1):
            if line.lstrip().startswith(("//", "*", "/*")):
                continue
            for receiver, called, method in CALL.findall(line):
                dao = accessors.get(receiver) if called else holders.get(receiver)
                if dao and method in methods.get(dao, ()):
                    found.append(f"{relative}:{number}: {dao}.{method}() writes a covered table -- go through Writes")
            # A DAO's own @Query declares a write its method names; anywhere else, plain SQL writes past the door
            if not is_dao and '"' in line:
                for sql in WRITE_SQL.finditer(line):
                    if sql.group(1) in COVERED:
                        found.append(f"{relative}:{number}: plain SQL writes {sql.group(1)} -- go through Writes")

    for entry in found:
        print(entry)
    if found:
        print(f"{len(found)} write(s) to a covered table past the history's door")
        return 1
    watched = sum(len(names) for names in methods.values())
    print(f"Every write to a covered table goes through the history ({watched} DAO methods watched).")
    return 0


if __name__ == "__main__":
    sys.exit(main())

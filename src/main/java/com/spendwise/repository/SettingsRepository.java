package com.spendwise.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Key/value persistence for application preferences ({@code settings} table). */
public final class SettingsRepository {

    private final DatabaseManager database;

    public SettingsRepository(DatabaseManager database) {
        this.database = database;
    }

    public Map<String, String> loadAll() {
        Map<String, String> values = new LinkedHashMap<>();
        for (String[] row : database.query("SELECT key, value FROM settings ORDER BY key",
                SettingsRepository::mapRow)) {
            values.put(row[0], row[1]);
        }
        return values;
    }

    /** Upsert of a single preference. */
    public void save(String key, String value) {
        database.update("""
                        INSERT INTO settings (key, value) VALUES (?, ?)
                        ON CONFLICT(key) DO UPDATE SET value = excluded.value
                        """,
                key, value);
    }

    private static String[] mapRow(ResultSet rs) throws SQLException {
        return new String[]{rs.getString(1), rs.getString(2)};
    }
}
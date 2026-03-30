package de.drremote.db.postgresql.api;

import java.util.List;

public interface QueryRunner {

    <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) throws DbCoreException;

    <T> T queryOne(String sql, RowMapper<T> mapper, Object... params) throws DbCoreException;

    int update(String sql, Object... params) throws DbCoreException;
}

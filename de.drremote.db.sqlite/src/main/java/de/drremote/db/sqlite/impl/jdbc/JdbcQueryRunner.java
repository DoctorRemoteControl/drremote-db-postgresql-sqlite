package de.drremote.db.sqlite.impl.jdbc;

import de.drremote.db.sqlite.api.DbCoreException;
import de.drremote.db.sqlite.api.QueryRunner;
import de.drremote.db.sqlite.api.RowMapper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class JdbcQueryRunner implements QueryRunner {

    private final Connection connection;

    public JdbcQueryRunner(Connection connection) {
        this.connection = connection;
    }

    @Override
    public <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) throws DbCoreException {
        try (PreparedStatement ps = prepareStatement(sql, params);
            ResultSet rs = ps.executeQuery()) {
            List<T> result = new ArrayList<>();
            while (rs.next()) {
                result.add(mapper.mapRow(rs));
            }
            return result;
        } catch (SQLException e) {
            throw new DbCoreException("Error executing query: " + sql, e);
        }
    }

    @Override
    public <T> T queryOne(String sql, RowMapper<T> mapper, Object... params) throws DbCoreException {
        List<T> list = query(sql, mapper, params);
        if (list.isEmpty()) {
            return null;
        }
        if (list.size() > 1) {
            throw new DbCoreException("Expected single result, got " + list.size());
        }
        return list.get(0);
    }

    @Override
    public int update(String sql, Object... params) throws DbCoreException {
        try (PreparedStatement ps = prepareStatement(sql, params)) {
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new DbCoreException("Error executing update: " + sql, e);
        }
    }

    private PreparedStatement prepareStatement(String sql, Object... params) throws SQLException {
        PreparedStatement ps = connection.prepareStatement(sql);
        if (params != null) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
        }
        return ps;
    }
}
package io.openharness4j.memory;

import io.openharness4j.api.Message;
import io.openharness4j.api.MessageRole;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class JdbcMemoryStore implements MemoryStore {

    private final DataSource dataSource;
    private final String tableName;

    public JdbcMemoryStore(DataSource dataSource) {
        this(dataSource, "openharness_memory");
    }

    public JdbcMemoryStore(DataSource dataSource, String tableName) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("tableName must not be blank");
        }
        this.tableName = tableName;
    }

    @Override
    public List<Message> load(String sessionId) {
        String sql = "select role, content, name, tool_call_id from " + tableName + " where session_id = ? order by message_index asc";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, MemoryStore.normalizeSessionId(sessionId));
            try (ResultSet resultSet = statement.executeQuery()) {
                List<Message> messages = new ArrayList<>();
                while (resultSet.next()) {
                    messages.add(new Message(
                            MessageRole.valueOf(resultSet.getString("role")),
                            resultSet.getString("content"),
                            resultSet.getString("name"),
                            resultSet.getString("tool_call_id"),
                            List.of()
                    ));
                }
                return List.copyOf(messages);
            }
        } catch (SQLException ex) {
            throw new MemoryStoreException("failed to load memory", ex);
        }
    }

    @Override
    public void save(String sessionId, Message message) {
        if (message == null) {
            return;
        }
        List<Message> messages = new ArrayList<>(load(sessionId));
        messages.add(message);
        replace(sessionId, messages);
    }

    @Override
    public void replace(String sessionId, List<Message> messages) {
        String normalizedSessionId = MemoryStore.normalizeSessionId(sessionId);
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                delete(connection, normalizedSessionId);
                insertAll(connection, normalizedSessionId, messages);
                connection.commit();
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException ex) {
            throw new MemoryStoreException("failed to replace memory", ex);
        }
    }

    @Override
    public void clear(String sessionId) {
        try (Connection connection = dataSource.getConnection()) {
            delete(connection, MemoryStore.normalizeSessionId(sessionId));
        } catch (SQLException ex) {
            throw new MemoryStoreException("failed to clear memory", ex);
        }
    }

    public String createTableSql() {
        return "create table if not exists " + tableName + " ("
                + "session_id varchar(255) not null, "
                + "message_index int not null, "
                + "role varchar(32) not null, "
                + "content text, "
                + "name varchar(255), "
                + "tool_call_id varchar(255), "
                + "primary key (session_id, message_index)"
                + ")";
    }

    private void delete(Connection connection, String sessionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("delete from " + tableName + " where session_id = ?")) {
            statement.setString(1, sessionId);
            statement.executeUpdate();
        }
    }

    private void insertAll(Connection connection, String sessionId, List<Message> messages) throws SQLException {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        String sql = "insert into " + tableName + " (session_id, message_index, role, content, name, tool_call_id) values (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < messages.size(); i++) {
                Message message = messages.get(i);
                statement.setString(1, sessionId);
                statement.setInt(2, i);
                statement.setString(3, message.role().name());
                statement.setString(4, message.content());
                statement.setString(5, message.name());
                statement.setString(6, message.toolCallId());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }
}

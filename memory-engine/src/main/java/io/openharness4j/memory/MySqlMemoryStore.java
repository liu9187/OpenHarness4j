package io.openharness4j.memory;

import javax.sql.DataSource;

public class MySqlMemoryStore extends JdbcMemoryStore {

    public MySqlMemoryStore(DataSource dataSource) {
        super(dataSource);
    }

    public MySqlMemoryStore(DataSource dataSource, String tableName) {
        super(dataSource, tableName);
    }
}

package ru.sklad.app.config;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.util.List;
public class ManagedUserDetailsManager extends JdbcUserDetailsManager {
    private final JdbcTemplate jdbc;
    public ManagedUserDetailsManager(DataSource source) {
        super(source);
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE IF NOT EXISTS users (username VARCHAR(50) PRIMARY KEY, password VARCHAR(100) NOT NULL, enabled BOOLEAN NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS authorities (username VARCHAR(50) NOT NULL, authority VARCHAR(50) NOT NULL, CONSTRAINT fk_authorities_users FOREIGN KEY(username) REFERENCES users(username), CONSTRAINT uq_authorities UNIQUE(username, authority))");
    }
    public List<String> usernames() { return jdbc.queryForList("SELECT username FROM users ORDER BY username", String.class); }
}

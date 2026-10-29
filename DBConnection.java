import java.sql.Connection;
import java.sql.DriverManager;

public class DBConnection {
    private static final String URL  = "jdbc:mysql://localhost:3306/grazia_db";
    private static final String USER = "grazia_app";   // limited user created by grazia_schema.sql

    public static Connection getConnection() throws Exception {
        // Change this password (and in grazia_schema.sql) or set a DB_PASSWORD environment variable.
        String password = System.getenv("DB_PASSWORD");
        if (password == null) password = "Grazia_App#2026x";
        return DriverManager.getConnection(URL, USER, password);
    }
}

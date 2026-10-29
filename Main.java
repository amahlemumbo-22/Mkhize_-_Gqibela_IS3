import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                Util.ensureStatuses();   // adds PENDING, APPROVED, etc. to your statuses table if missing
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null,
                        "Could not connect to the database:\n" + e.getMessage()
                      + "\n\nCheck DBConnection.java and that grazia_schema.sql was run and that MySQL is running.",
                        "Database error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            new loginFrame().setVisible(true);
        });
    }
}

import javax.swing.*;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class loginFrame extends JFrame {
    final JTextField txtUsername = new JTextField(18);
    final JPasswordField txtPassword = new JPasswordField(18);
    final JButton btnLogin, btnRegister, btnForgot;

    public loginFrame() {
        setTitle("Grazia Fine Food & Wine - Login");
        setSize(500, 350);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Util.BEIGE);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(8, 10, 8, 10);
        g.fill = GridBagConstraints.HORIZONTAL;

        JLabel title = new JLabel("Grazia Fine Food & Wine", SwingConstants.CENTER);
        title.setFont(new Font("Serif", Font.BOLD, 24));
        title.setForeground(Util.MAROON);
        g.gridx = 0; g.gridy = 0; g.gridwidth = 2;
        panel.add(title, g);

        btnLogin    = Util.button("Login", Util.OLIVE, this, this::login);
        btnRegister = Util.button("Register", Util.MAROON, this, () -> { new RegisterFrame().setVisible(true); dispose(); });
        btnForgot   = Util.button("Forgot Password", null, this, () -> { new ForgotPasswordFrame().setVisible(true); dispose(); });

        g.gridwidth = 1;
        g.gridy = 1; g.gridx = 0; panel.add(new JLabel("Username:"), g);
        g.gridx = 1; panel.add(txtUsername, g);
        g.gridy = 2; g.gridx = 0; panel.add(new JLabel("Password:"), g);
        g.gridx = 1; panel.add(txtPassword, g);
        g.gridy = 3; g.gridx = 0; panel.add(btnLogin, g);
        g.gridx = 1; panel.add(btnRegister, g);
        g.gridy = 4; g.gridx = 0; g.gridwidth = 2;
        panel.add(btnForgot, g);

        add(panel);
        getRootPane().setDefaultButton(btnLogin);   // Enter key logs in
    }

    private void login() throws Exception {
        String username = txtUsername.getText().trim();
        String password = new String(txtPassword.getPassword());
        if (username.isEmpty() || password.isEmpty())
            throw new IllegalArgumentException("Please enter your username and password.");

        String sql = """
            SELECT p.password, a.admin_id, m.manager_id, m.restaurant_id, c.customer_id
            FROM persons p
            LEFT JOIN admins    a ON a.person_id = p.person_id
            LEFT JOIN managers  m ON m.person_id = p.person_id
            LEFT JOIN customers c ON c.person_id = p.person_id
            WHERE p.username = ?
            """;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || !Util.verify(password, rs.getString("password")))
                    throw new IllegalStateException("Invalid username or password.");

                String stored = rs.getString("password");
                Object adminId = rs.getObject("admin_id");
                Object managerId = rs.getObject("manager_id");
                Object customerId = rs.getObject("customer_id");
                int restaurantId = rs.getInt("restaurant_id");

                // Upgrade old plain-text passwords to a hash the first time the person logs in
                if (!stored.startsWith("pbkdf2:")) {
                    try { Util.exec(conn, "UPDATE persons SET password=? WHERE username=?", Util.hash(password), username); }
                    catch (Exception ignored) { /* column too short? run setup.sql */ }
                }

                if (adminId != null) {
                    new AdminFrame(username).setVisible(true);
                } else if (managerId != null) {
                    new ManagerFrame(username, restaurantId).setVisible(true);
                } else if (customerId != null) {
                    new CustomerFrame(username, ((Number) customerId).intValue()).setVisible(true);
                } else {
                    throw new IllegalStateException("This account has no role assigned.");
                }
                dispose();
            }
        }
    }
}

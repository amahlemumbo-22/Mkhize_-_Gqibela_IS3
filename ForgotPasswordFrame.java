import javax.swing.*;
import java.awt.*;

/** Simple reset: username + email on file must match, then a new password is set. */
public class ForgotPasswordFrame extends JFrame {

    public ForgotPasswordFrame() {
        setTitle("Reset Password - Grazia Fine Food & Wine");
        setSize(450, 350);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JTextField username = new JTextField(15), email = new JTextField(15);
        JPasswordField pw = new JPasswordField(15), confirm = new JPasswordField(15);

        JLabel title = new JLabel("Reset your password", SwingConstants.CENTER);
        title.setFont(new Font("Serif", Font.BOLD, 22));
        title.setForeground(Util.MAROON);

        JButton reset = Util.button("Reset Password", Util.OLIVE, this, () -> {
            Util.require(username, email);
            String newPw = new String(pw.getPassword());
            if (!newPw.equals(new String(confirm.getPassword())))
                throw new IllegalArgumentException("The two passwords do not match.");
            if (!PasswordValidator.isValidPassword(newPw))
                throw new IllegalArgumentException(PasswordValidator.RULES);

            int n = Util.update("UPDATE persons SET password=? WHERE username=? AND email=?",
                    Util.hash(newPw), Util.t(username), Util.t(email));
            if (n == 0) throw new IllegalStateException("No account matches that username and email.");
            JOptionPane.showMessageDialog(this, "Password updated. You can now log in.");
            new loginFrame().setVisible(true);
            dispose();
        });
        JButton back = Util.button("Back to Login", Util.MAROON, this, () -> { new loginFrame().setVisible(true); dispose(); });

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBackground(Util.BEIGE);
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        root.add(title, BorderLayout.NORTH);
        root.add(Util.form("Username:", username, "Email on file:", email,
                           "New password:", pw, "Confirm password:", confirm), BorderLayout.CENTER);
        root.add(Util.flow(reset, back), BorderLayout.SOUTH);
        add(root);
    }
}

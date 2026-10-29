import javax.swing.*;
import java.awt.*;

public class RegisterFrame extends JFrame {

    public RegisterFrame() {
        setTitle("Register - Grazia Fine Food & Wine");
        setSize(480, 520);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        Util.PersonFields pf = new Util.PersonFields();
        JPasswordField confirm = new JPasswordField(15);

        JLabel title = new JLabel("Create a customer account", SwingConstants.CENTER);
        title.setFont(new Font("Serif", Font.BOLD, 22));
        title.setForeground(Util.MAROON);

        JButton submit = Util.button("Register", Util.OLIVE, this, () -> {
            pf.validate();
            if (!pf.pw().equals(new String(confirm.getPassword())))
                throw new IllegalArgumentException("The two passwords do not match.");
            Util.tx(c -> {
                int personId = pf.create(c);
                Util.exec(c, "INSERT INTO customers (person_id) VALUES (?)", personId);
            });
            JOptionPane.showMessageDialog(this, "Registration successful! You can now log in.");
            new loginFrame().setVisible(true);
            dispose();
        });
        JButton back = Util.button("Back to Login", Util.MAROON, this, () -> { new loginFrame().setVisible(true); dispose(); });

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBackground(Util.BEIGE);
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        root.add(title, BorderLayout.NORTH);
        root.add(Util.form(pf.pairs("Confirm password:", confirm)), BorderLayout.CENTER);
        root.add(Util.flow(submit, back), BorderLayout.SOUTH);
        add(root);
        getRootPane().setDefaultButton(submit);
    }
}

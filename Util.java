import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.sql.*;
import java.util.Base64;

/** Shared helpers used by every screen: database, passwords and small UI builders. */
public class Util {
    static final Color MAROON = new Color(128, 0, 0);
    static final Color OLIVE  = new Color(85, 107, 47);
    static final Color BEIGE  = new Color(245, 245, 220);

    interface Task { void run() throws Exception; }
    interface Work { void run(Connection c) throws Exception; }
    interface RowTask { void run(int row) throws Exception; }

    // ================= passwords =================
    static String hash(String pw) {
        try {
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            return "pbkdf2:" + Base64.getEncoder().encodeToString(salt) + ":" + pbkdf2(pw, salt);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static String pbkdf2(String pw, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(pw.toCharArray(), salt, 65536, 256);
        byte[] out = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        return Base64.getEncoder().encodeToString(out);
    }

    /** Works for new hashed passwords AND old plain-text ones (they get upgraded at login). */
    static boolean verify(String pw, String stored) {
        if (stored == null) return false;
        if (!stored.startsWith("pbkdf2:")) return stored.equals(pw);
        try {
            String[] p = stored.split(":");
            return pbkdf2(pw, Base64.getDecoder().decode(p[1])).equals(p[2]);
        } catch (Exception e) { return false; }
    }

    // ================= database =================
    static void bind(PreparedStatement ps, Object[] p) throws SQLException {
        for (int i = 0; i < p.length; i++) ps.setObject(i + 1, p[i]);
    }

    static DefaultTableModel query(String sql, Object... p) throws Exception {
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, p);
            try (ResultSet rs = ps.executeQuery()) { return model(rs); }
        }
    }

    static int exec(Connection c, String sql, Object... p) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) { bind(ps, p); return ps.executeUpdate(); }
    }

    static int update(String sql, Object... p) throws Exception {
        try (Connection c = DBConnection.getConnection()) { return exec(c, sql, p); }
    }

    static int insert(Connection c, String sql, Object... p) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, p);
            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) { return k.next() ? k.getInt(1) : 0; }
        }
    }

    static Object scalar(Connection c, String sql, Object... p) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, p);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getObject(1) : null; }
        }
    }

    static Object scalar(String sql, Object... p) throws Exception {
        try (Connection c = DBConnection.getConnection()) { return scalar(c, sql, p); }
    }

    /** Runs several statements as one unit: all succeed or none are saved. */
    static void tx(Work w) throws Exception {
        try (Connection c = DBConnection.getConnection()) {
            c.setAutoCommit(false);
            try { w.run(c); c.commit(); }
            catch (Exception e) { c.rollback(); throw e; }
        }
    }

    static DefaultTableModel readOnly(String[] cols) {
        return new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
    }

    static DefaultTableModel model(ResultSet rs) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int n = md.getColumnCount();
        String[] cols = new String[n];
        for (int i = 0; i < n; i++) cols[i] = md.getColumnLabel(i + 1);
        DefaultTableModel m = readOnly(cols);
        while (rs.next()) {
            Object[] row = new Object[n];
            for (int i = 0; i < n; i++)
                row[i] = md.getColumnType(i + 1) == Types.TIME ? rs.getString(i + 1) : rs.getObject(i + 1);
            m.addRow(row);
        }
        return m;
    }

    // ---- statuses (table is called "statuses" in your Workbench) ----
    static int statusId(Connection c, String name) throws SQLException {
        Object o = scalar(c, "SELECT status_id FROM statuses WHERE status_name=?", name);
        if (o == null) throw new SQLException("Status '" + name + "' not found in statuses table.");
        return ((Number) o).intValue();
    }

    static int statusId(String name) throws Exception {
        try (Connection c = DBConnection.getConnection()) { return statusId(c, name); }
    }

    /** Adds any status names the app needs that are not in the table yet. */
    static void ensureStatuses() throws Exception {
        String[] names = {"AVAILABLE", "NOT AVAILABLE", "PENDING", "CONFIRMED", "PREPARING",
                "READY", "COMPLETED", "CANCELLED", "APPROVED", "DECLINED"};
        for (String s : names)
            update("INSERT INTO statuses (status_name) SELECT ? FROM DUAL "
                    + "WHERE NOT EXISTS (SELECT 1 FROM statuses WHERE status_name=?)", s, s);
    }

    // ---- people ----
    static int createPerson(Connection c, String username, String name, String surname,
                            String contact, String email, String password) throws SQLException {
        if (scalar(c, "SELECT 1 FROM persons WHERE username=?", username) != null)
            throw new IllegalStateException("The username '" + username + "' is already taken.");
        return insert(c, "INSERT INTO persons (username, name, surname, contact, email, password) VALUES (?,?,?,?,?,?)",
                username, name, surname, contact, email, hash(password));
    }

    /** The six person fields shared by Register, Admin->Add Manager and Admin->Add Customer. */
    static class PersonFields {
        final JTextField username = new JTextField(15), name = new JTextField(15),
                surname = new JTextField(15), contact = new JTextField(15), email = new JTextField(15);
        final JPasswordField password = new JPasswordField(15);

        Object[] pairs(Object... extra) {
            Object[] base = {"Username:", username, "Name:", name, "Surname:", surname,
                    "Contact:", contact, "Email:", email, "Password:", password};
            Object[] all = new Object[base.length + extra.length];
            System.arraycopy(base, 0, all, 0, base.length);
            System.arraycopy(extra, 0, all, base.length, extra.length);
            return all;
        }

        String pw() { return new String(password.getPassword()); }

        void validate() {
            require(username, name, surname, contact, email);
            requireLetters(username, "Username");
            requireLetters(name, "Name");
            requireLetters(surname, "Surname");
            if (!t(email).matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))
                throw new IllegalArgumentException("Please enter a valid email address.");
            if (!t(contact).matches("\\d{9,12}"))
                throw new IllegalArgumentException("Contact must be 9 to 12 digits.");
            if (!PasswordValidator.isValidPassword(pw()))
                throw new IllegalArgumentException(PasswordValidator.RULES);
        }

        int create(Connection c) throws SQLException {
            return createPerson(c, t(username), t(name), t(surname), t(contact), t(email), pw());
        }

        void clear() {
            for (JTextField f : new JTextField[]{username, name, surname, contact, email, password}) f.setText("");
        }
    }

    // ================= small helpers =================
    static String t(JTextField f) { return f.getText().trim(); }

    static String str(Object o) { return o == null ? "" : o.toString(); }

    static void require(JTextField... fields) {
        for (JTextField f : fields)
            if (f.getText().trim().isEmpty()) throw new IllegalArgumentException("Please fill in all the fields.");
    }

    /** Letters only (any language, so accented names like "Zoë" are fine). */
    static void requireLetters(JTextField f, String label) {
        if (!t(f).matches("\\p{L}+"))
            throw new IllegalArgumentException(label + " must contain letters only (no numbers, spaces or symbols).");
    }

    static BigDecimal money(JTextField f) {
        BigDecimal v = new BigDecimal(f.getText().trim().replace("R", "").replace(",", ".").trim());
        if (v.signum() < 0) throw new IllegalArgumentException("A price can't be negative.");
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    static boolean confirm(Component parent, String msg) {
        return JOptionPane.showConfirmDialog(parent, msg, "Please confirm", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
    }

    static String friendly(Exception e) {
        String m = e.getMessage() == null ? e.toString() : e.getMessage();
        if (m.contains("foreign key"))
            return "This record is still linked to other data (for example past orders), so it can't be deleted.\n"
                    + "For menu items, set them to NOT AVAILABLE instead.";
        if (m.contains("Duplicate entry")) return "That already exists (duplicate): " + m;
        return m;
    }

    /** Runs a task and shows a dialog instead of crashing if something goes wrong. */
    static void run(Component parent, Task t) {
        try { t.run(); }
        catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(parent, "Please enter a valid number.", "Please check", JOptionPane.WARNING_MESSAGE);
        } catch (IllegalArgumentException | IllegalStateException e) {
            JOptionPane.showMessageDialog(parent, e.getMessage(), "Please check", JOptionPane.WARNING_MESSAGE);
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(parent, friendly(e), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ================= combo boxes =================
    static class Choice {
        final int id; final String label;
        Choice(int id, String label) { this.id = id; this.label = label; }
        @Override public String toString() { return label; }
    }

    /** Fills a combo from a query whose first column is the id and second is the label. */
    static void fill(JComboBox<Choice> box, String sql, Object... p) throws Exception {
        Object cur = box.getSelectedItem();
        int keep = cur instanceof Choice ? ((Choice) cur).id : -1;
        DefaultTableModel t = query(sql, p);
        DefaultComboBoxModel<Choice> m = new DefaultComboBoxModel<>();
        for (int i = 0; i < t.getRowCount(); i++)
            m.addElement(new Choice(((Number) t.getValueAt(i, 0)).intValue(), String.valueOf(t.getValueAt(i, 1))));
        box.setModel(m);
        selectId(box, keep);
    }

    static void selectId(JComboBox<Choice> box, int id) {
        for (int i = 0; i < box.getItemCount(); i++)
            if (box.getItemAt(i).id == id) { box.setSelectedIndex(i); return; }
    }

    static void selectLabel(JComboBox<Choice> box, String label) {
        for (int i = 0; i < box.getItemCount(); i++)
            if (box.getItemAt(i).label.equals(label)) { box.setSelectedIndex(i); return; }
    }

    // ================= tables / layout =================
    static JTable table() {
        JTable t = new JTable();
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        t.setRowHeight(24);
        t.getTableHeader().setReorderingAllowed(false);
        return t;
    }

    static void load(JTable t, String sql, Object... p) throws Exception { t.setModel(query(sql, p)); }

    /** The id (first column) of the selected row. */
    static int selectedId(JTable t) {
        int r = t.getSelectedRow();
        if (r < 0) throw new IllegalStateException("Please select a row in the table first.");
        return ((Number) t.getValueAt(r, 0)).intValue();
    }

    static void onSelect(JTable t, Component parent, RowTask a) {
        t.getSelectionModel().addListSelectionListener(e -> {
            int r = t.getSelectedRow();
            if (!e.getValueIsAdjusting() && r >= 0) run(parent, () -> a.run(r));
        });
    }

    static JButton button(String text, Color bg, Component parent, Task a) {
        JButton b = new JButton(text);
        if (bg != null) {
            b.setBackground(bg); b.setForeground(Color.WHITE);
            b.setOpaque(true); b.setBorderPainted(false);
        }
        b.addActionListener(e -> run(parent, a));
        return b;
    }

    static JPanel flow(Component... cs) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        p.setBackground(BEIGE);
        for (Component c : cs) p.add(c);
        return p;
    }

    /** Label + field rows. Pass: "Label:", component, "Label:", component, ... */
    static JPanel form(Object... lf) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(BEIGE);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 6, 4, 6);
        g.fill = GridBagConstraints.HORIZONTAL;
        for (int i = 0; i < lf.length; i += 2) {
            g.gridy = i / 2;
            g.gridx = 0; g.weightx = 0; p.add(new JLabel((String) lf[i]), g);
            g.gridx = 1; g.weightx = 1; p.add((Component) lf[i + 1], g);
        }
        return p;
    }

    /** Standard "table on the left, form + buttons on the right" page. */
    static JPanel page(JTable t, JPanel form, JButton... btns) {
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBackground(BEIGE);
        p.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JPanel grid = new JPanel(new GridLayout(0, 1, 6, 6));
        grid.setOpaque(false);
        for (JButton b : btns) grid.add(b);
        JPanel gridWrap = new JPanel(new BorderLayout());
        gridWrap.setOpaque(false);
        gridWrap.add(grid, BorderLayout.NORTH);
        JPanel east = new JPanel(new BorderLayout(0, 10));
        east.setOpaque(false);
        east.add(form, BorderLayout.NORTH);
        east.add(gridWrap, BorderLayout.CENTER);
        p.add(new JScrollPane(t), BorderLayout.CENTER);
        p.add(east, BorderLayout.EAST);
        return p;
    }

    static void addLogout(JFrame f) {
        JMenuBar bar = new JMenuBar();
        JMenu menu = new JMenu("Account");
        JMenuItem out = new JMenuItem("Logout");
        out.addActionListener(e -> { new loginFrame().setVisible(true); f.dispose(); });
        menu.add(out);
        bar.add(menu);
        f.setJMenuBar(bar);
    }

    /** Puts an Exit button under a tab's content; it returns to the login screen. */
    static JPanel withExit(JFrame f, JComponent content) {
        JPanel p = new JPanel(new BorderLayout());
        p.add(content, BorderLayout.CENTER);
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
        bar.setBackground(BEIGE);
        bar.add(button("Exit", MAROON, f, () -> { new loginFrame().setVisible(true); f.dispose(); }));
        p.add(bar, BorderLayout.SOUTH);
        return p;
    }


    /** Editable profile: name, surname, contact and email can be changed; the password can be changed below. */
    static JPanel profileEditor(Component parent, String username, String extraSql) {
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBackground(BEIGE);
        p.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        JLabel title = new JLabel("My Profile");
        title.setFont(new Font("Serif", Font.BOLD, 22));
        title.setForeground(MAROON);
        p.add(title, BorderLayout.NORTH);

        JTextField fUser = new JTextField(24), fName = new JTextField(24), fSurname = new JTextField(24),
                fContact = new JTextField(24), fEmail = new JTextField(24);
        fUser.setEditable(false);                      // the username is the login identity, so it stays fixed
        JPasswordField fCurrent = new JPasswordField(24), fNew = new JPasswordField(24), fConfirm = new JPasswordField(24);

        java.util.List<Object> pairs = new java.util.ArrayList<>();
        pairs.add("Username:"); pairs.add(fUser);
        pairs.add("Name:");     pairs.add(fName);
        pairs.add("Surname:");  pairs.add(fSurname);
        pairs.add("Contact:");  pairs.add(fContact);
        pairs.add("Email:");    pairs.add(fEmail);
        run(parent, () -> {                            // optional read-only extras (e.g. a manager's restaurant)
            if (extraSql == null) return;
            DefaultTableModel x = query(extraSql, username);
            if (x.getRowCount() == 0) return;
            for (int i = 0; i < x.getColumnCount(); i++) {
                JTextField f = new JTextField(str(x.getValueAt(0, i)), 24);
                f.setEditable(false);
                pairs.add(x.getColumnName(i) + ":");
                pairs.add(f);
            }
        });

        Task reload = () -> {
            DefaultTableModel m = query("SELECT username, name, surname, contact, email FROM persons WHERE username=?", username);
            if (m.getRowCount() == 0) throw new IllegalStateException("Profile not found.");
            fUser.setText(str(m.getValueAt(0, 0)));
            fName.setText(str(m.getValueAt(0, 1)));
            fSurname.setText(str(m.getValueAt(0, 2)));
            fContact.setText(str(m.getValueAt(0, 3)));
            fEmail.setText(str(m.getValueAt(0, 4)));
        };
        run(parent, reload);

        JButton save = button("Save Changes", OLIVE, parent, () -> {
            require(fName, fSurname, fContact, fEmail);
            requireLetters(fName, "Name");
            requireLetters(fSurname, "Surname");
            if (!t(fContact).matches("\\d{9,12}"))
                throw new IllegalArgumentException("Contact must be 9 to 12 digits.");
            if (!t(fEmail).matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))
                throw new IllegalArgumentException("Please enter a valid email address.");
            update("UPDATE persons SET name=?, surname=?, contact=?, email=? WHERE username=?",
                    t(fName), t(fSurname), t(fContact), t(fEmail), username);
            JOptionPane.showMessageDialog(parent, "Your profile has been updated.");
        });
        JButton undo = button("Undo Changes", null, parent, reload);

        JButton changePw = button("Change Password", MAROON, parent, () -> {
            String current = new String(fCurrent.getPassword());
            String fresh = new String(fNew.getPassword());
            Object stored = scalar("SELECT password FROM persons WHERE username=?", username);
            if (stored == null || !verify(current, stored.toString()))
                throw new IllegalArgumentException("Your current password is incorrect.");
            if (!fresh.equals(new String(fConfirm.getPassword())))
                throw new IllegalArgumentException("The new passwords do not match.");
            if (!PasswordValidator.isValidPassword(fresh))
                throw new IllegalArgumentException(PasswordValidator.RULES);
            update("UPDATE persons SET password=? WHERE username=?", hash(fresh), username);
            fCurrent.setText(""); fNew.setText(""); fConfirm.setText("");
            JOptionPane.showMessageDialog(parent, "Your password has been changed.");
        });

        JLabel pwTitle = new JLabel("Change password");
        pwTitle.setFont(pwTitle.getFont().deriveFont(Font.BOLD, 15f));
        pwTitle.setForeground(MAROON);
        pwTitle.setBorder(BorderFactory.createEmptyBorder(14, 6, 2, 0));

        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setOpaque(false);
        for (JComponent c : new JComponent[]{
                form(pairs.toArray()), flow(save, undo), pwTitle,
                form("Current password:", fCurrent, "New password:", fNew, "Confirm new password:", fConfirm),
                flow(changePw)}) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
            box.add(c);
        }
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(box, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(wrap);
        sp.setBorder(null);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        p.add(sp, BorderLayout.CENTER);
        return p;
    }

    /** Read-only profile card. The query must return ONE row; each column becomes a labelled field. */
    static JPanel profilePanel(Component parent, String sql, Object... params) {
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBackground(BEIGE);
        p.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        JLabel title = new JLabel("My Profile");
        title.setFont(new Font("Serif", Font.BOLD, 22));
        title.setForeground(MAROON);
        p.add(title, BorderLayout.NORTH);
        run(parent, () -> {
            DefaultTableModel m = query(sql, params);
            if (m.getRowCount() == 0) throw new IllegalStateException("Profile not found.");
            java.util.List<Object> pairs = new java.util.ArrayList<>();
            for (int i = 0; i < m.getColumnCount(); i++) {
                JTextField f = new JTextField(str(m.getValueAt(0, i)), 24);
                f.setEditable(false);
                pairs.add(m.getColumnName(i) + ":");
                pairs.add(f);
            }
            JPanel wrap = new JPanel(new BorderLayout());
            wrap.setOpaque(false);
            wrap.add(form(pairs.toArray()), BorderLayout.NORTH);
            p.add(wrap, BorderLayout.CENTER);
        });
        return p;
    }

    /** Lines (items/menus) of one order. Parameter: order_id. */
    static final String LINES_SQL =
            "SELECT CASE WHEN oi.menu_id IS NOT NULL THEN CONCAT('Menu: ', m.menu_name) ELSE mi.item_name END AS item, "
                    + "oi.quantity, oi.price_at_order, oi.quantity * oi.price_at_order AS subtotal "
                    + "FROM order_items oi "
                    + "LEFT JOIN menu_items mi ON oi.menu_item_id = mi.menu_item_id "
                    + "LEFT JOIN menus m ON oi.menu_id = m.menu_id "
                    + "WHERE oi.order_id = ?";
}
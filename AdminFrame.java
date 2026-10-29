import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class AdminFrame extends JFrame {
    private final String username;
    private final List<Util.Task> refreshers = new ArrayList<>();

    public AdminFrame(String adminUsername) {
        this.username = adminUsername;
        setTitle("Admin Dashboard - " + adminUsername);
        setSize(1000, 650);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        Util.addLogout(this);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Restaurants", Util.withExit(this, restaurantsTab()));
        tabs.addTab("Managers", Util.withExit(this, managersTab()));
        tabs.addTab("Customers", Util.withExit(this, customersTab()));
        tabs.addTab("My Profile", Util.withExit(this, profileTab()));
        tabs.addChangeListener(e -> refreshers.forEach(r -> Util.run(this, r)));
        add(tabs);
        refreshers.forEach(r -> Util.run(this, r));
    }

    // ---------------- Restaurants ----------------
    private JPanel restaurantsTab() {
        JTable t = Util.table();
        JTextField name = new JTextField(15), contact = new JTextField(15),
                   email = new JTextField(15), address = new JTextField(15);
        Util.Task refresh = () -> Util.load(t,
                "SELECT restaurant_id, restaurant_name, contact, email, address FROM restaurants ORDER BY restaurant_id");
        refreshers.add(refresh);

        return Util.page(t, Util.form("Name:", name, "Contact:", contact, "Email:", email, "Address:", address),
            Util.button("Add Restaurant", Util.OLIVE, this, () -> {
                Util.require(name, contact, email, address);
                Util.update("INSERT INTO restaurants (restaurant_name, contact, email, address) VALUES (?,?,?,?)",
                        Util.t(name), Util.t(contact), Util.t(email), Util.t(address));
                name.setText(""); contact.setText(""); email.setText(""); address.setText("");
                refresh.run();
            }),
            Util.button("Delete Selected", Util.MAROON, this, () -> {
                int id = Util.selectedId(t);
                if (!Util.confirm(this, "Delete this restaurant?")) return;
                Util.update("DELETE FROM restaurants WHERE restaurant_id=?", id);
                refresh.run();
            }),
            Util.button("Refresh", null, this, refresh));
    }

    // ---------------- Managers ----------------
    private JPanel managersTab() {
        JTable t = Util.table();
        Util.PersonFields pf = new Util.PersonFields();
        JComboBox<Util.Choice> cbRest = new JComboBox<>();
        Util.Task refresh = () -> {
            Util.load(t, """
                SELECT m.manager_id, p.username, p.name, p.surname, p.contact, p.email, r.restaurant_name
                FROM managers m
                JOIN persons p ON m.person_id = p.person_id
                JOIN restaurants r ON m.restaurant_id = r.restaurant_id
                ORDER BY m.manager_id""");
            Util.fill(cbRest, "SELECT restaurant_id, restaurant_name FROM restaurants "
                            + "WHERE NOT EXISTS (SELECT 1 FROM managers m WHERE m.restaurant_id = restaurants.restaurant_id) "
                            + "ORDER BY restaurant_name");
        };
        refreshers.add(refresh);

        return Util.page(t, Util.form(pf.pairs("Manages:", cbRest)),
            Util.button("Add Manager", Util.OLIVE, this, () -> {
                pf.validate();
                Util.Choice r = (Util.Choice) cbRest.getSelectedItem();
                if (r == null) throw new IllegalStateException("Every restaurant already has a manager. Add a new restaurant first.");
                Util.tx(c -> {
                    int personId = pf.create(c);
                    Util.exec(c, "INSERT INTO managers (person_id, restaurant_id) VALUES (?,?)", personId, r.id);
                });
                pf.clear();
                refresh.run();
            }),
            Util.button("Remove Selected", Util.MAROON, this, () -> {
                int id = Util.selectedId(t);
                if (!Util.confirm(this, "Remove this manager account?")) return;
                Util.tx(c -> {
                    Object personId = Util.scalar(c, "SELECT person_id FROM managers WHERE manager_id=?", id);
                    Util.exec(c, "DELETE FROM managers WHERE manager_id=?", id);
                    if (personId != null) Util.exec(c, "DELETE FROM persons WHERE person_id=?", personId);
                });
                refresh.run();
            }),
            Util.button("Refresh", null, this, refresh));
    }

    // ---------------- Customers ----------------
    private JPanel customersTab() {
        JTable t = Util.table();
        Util.PersonFields pf = new Util.PersonFields();
        Util.Task refresh = () -> Util.load(t, """
                SELECT c.customer_id, p.username, p.name, p.surname, p.contact, p.email
                FROM customers c JOIN persons p ON c.person_id = p.person_id
                ORDER BY c.customer_id""");
        refreshers.add(refresh);

        return Util.page(t, Util.form(pf.pairs()),
            Util.button("Add Customer", Util.OLIVE, this, () -> {
                pf.validate();
                Util.tx(c -> {
                    int personId = pf.create(c);
                    Util.exec(c, "INSERT INTO customers (person_id) VALUES (?)", personId);
                });
                pf.clear();
                refresh.run();
            }),
            Util.button("Remove Selected", Util.MAROON, this, () -> {
                int id = Util.selectedId(t);
                if (!Util.confirm(this, "Remove this customer account?")) return;
                Util.tx(c -> {
                    Object personId = Util.scalar(c, "SELECT person_id FROM customers WHERE customer_id=?", id);
                    Util.exec(c, "DELETE FROM customers WHERE customer_id=?", id);
                    if (personId != null) Util.exec(c, "DELETE FROM persons WHERE person_id=?", personId);
                });
                refresh.run();
            }),
            Util.button("Refresh", null, this, refresh));
    }

    // ---------------- Profile ----------------
    private JPanel profileTab() {
        return Util.profileEditor(this, username, null);
    }
}

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** Every query and update below is filtered by restaurantId, so a manager only touches their own restaurant. */
public class ManagerFrame extends JFrame {
    private final int restaurantId;
    private final List<Util.Task> refreshers = new ArrayList<>();

    public ManagerFrame(String managerUsername, int restaurantId) {
        this.restaurantId = restaurantId;

        String rname = "";
        try { rname = Util.str(Util.scalar("SELECT restaurant_name FROM restaurants WHERE restaurant_id=?", restaurantId)); }
        catch (Exception ignored) { }
        setTitle("Manager Dashboard - " + managerUsername + "  |  " + rname);
        setSize(1050, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        Util.addLogout(this);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Menus", Util.withExit(this, menusTab()));
        tabs.addTab("Items", Util.withExit(this, itemsTab()));
        tabs.addTab("Orders", Util.withExit(this, ordersTab()));
        tabs.addTab("Reservations", Util.withExit(this, reservationsTab()));
        tabs.addTab("Tables", Util.withExit(this, tablesTab()));
        tabs.addTab("My Profile", Util.withExit(this, Util.profileEditor(this, managerUsername, """
                SELECT r.restaurant_name AS `Restaurant`, r.address AS `Restaurant address`
                FROM managers m
                JOIN persons p ON m.person_id = p.person_id
                JOIN restaurants r ON m.restaurant_id = r.restaurant_id
                WHERE p.username = ?""")));
        tabs.addChangeListener(e -> refreshers.forEach(r -> Util.run(this, r)));
        add(tabs);
        refreshers.forEach(r -> Util.run(this, r));
    }

    // ---------------- Menus ----------------
    private JPanel menusTab() {
        JTable t = Util.table();
        JTextField name = new JTextField(15);
        Util.Task refresh = () -> Util.load(t,
                "SELECT menu_id, menu_name FROM menus WHERE restaurant_id=? ORDER BY menu_name", restaurantId);
        refreshers.add(refresh);
        Util.onSelect(t, this, r -> name.setText(Util.str(t.getValueAt(r, 1))));

        return Util.page(t, Util.form("Menu name:", name),
                Util.button("Add Menu", Util.OLIVE, this, () -> {
                    Util.require(name);
                    Util.update("INSERT INTO menus (menu_name, restaurant_id) VALUES (?,?)", Util.t(name), restaurantId);
                    name.setText("");
                    refresh.run();
                }),
                Util.button("Update Selected", Util.OLIVE, this, () -> {
                    int id = Util.selectedId(t);
                    Util.require(name);
                    Util.update("UPDATE menus SET menu_name=? WHERE menu_id=? AND restaurant_id=?", Util.t(name), id, restaurantId);
                    refresh.run();
                }),
                Util.button("Delete Selected (and its items)", Util.MAROON, this, () -> {
                    int id = Util.selectedId(t);
                    if (!Util.confirm(this, "Delete this menu and all of its items?")) return;
                    Util.tx(c -> {
                        Util.exec(c, "DELETE FROM menu_items WHERE menu_id=? "
                                + "AND menu_id IN (SELECT menu_id FROM menus WHERE restaurant_id=?)", id, restaurantId);
                        Util.exec(c, "DELETE FROM menus WHERE menu_id=? AND restaurant_id=?", id, restaurantId);
                    });
                    refresh.run();
                }),
                Util.button("Refresh", null, this, refresh));
    }

    // ---------------- Items ----------------
    private JPanel itemsTab() {
        JTable t = Util.table();
        JComboBox<Util.Choice> cbMenu = new JComboBox<>(), cbStatus = new JComboBox<>();
        JTextField name = new JTextField(15), desc = new JTextField(15), price = new JTextField(15);
        Util.Task refresh = () -> {
            Util.load(t, """
                SELECT mi.menu_item_id, m.menu_name, mi.item_name, mi.description, mi.item_price, s.status_name
                FROM menu_items mi
                JOIN menus m ON mi.menu_id = m.menu_id
                LEFT JOIN statuses s ON mi.status_id = s.status_id
                WHERE m.restaurant_id = ?
                ORDER BY m.menu_name, mi.item_name""", restaurantId);
            Util.fill(cbMenu, "SELECT menu_id, menu_name FROM menus WHERE restaurant_id=? ORDER BY menu_name", restaurantId);
            Util.fill(cbStatus, "SELECT status_id, status_name FROM statuses WHERE status_name IN ('AVAILABLE','NOT AVAILABLE') ORDER BY status_id");
        };
        refreshers.add(refresh);
        Util.onSelect(t, this, r -> {
            Util.selectLabel(cbMenu, Util.str(t.getValueAt(r, 1)));
            name.setText(Util.str(t.getValueAt(r, 2)));
            desc.setText(Util.str(t.getValueAt(r, 3)));
            price.setText(Util.str(t.getValueAt(r, 4)));
            Util.selectLabel(cbStatus, Util.str(t.getValueAt(r, 5)));
        });

        return Util.page(t, Util.form("Menu:", cbMenu, "Item name:", name, "Description:", desc,
                        "Price (R):", price, "Status:", cbStatus),
                Util.button("Add Item", Util.OLIVE, this, () -> {
                    Util.Choice menu = (Util.Choice) cbMenu.getSelectedItem();
                    Util.Choice status = (Util.Choice) cbStatus.getSelectedItem();
                    if (menu == null) throw new IllegalStateException("Create a menu first (Menus tab).");
                    Util.require(name, price);
                    int n = Util.update("INSERT INTO menu_items (menu_id, item_name, description, item_price, status_id) "
                                    + "SELECT ?,?,?,?,? FROM menus WHERE menu_id=? AND restaurant_id=?",
                            menu.id, Util.t(name), Util.t(desc), Util.money(price), status.id, menu.id, restaurantId);
                    if (n == 0) throw new IllegalStateException("That menu does not belong to your restaurant.");
                    name.setText(""); desc.setText(""); price.setText("");
                    refresh.run();
                }),
                Util.button("Update Selected", Util.OLIVE, this, () -> {
                    int id = Util.selectedId(t);
                    Util.Choice menu = (Util.Choice) cbMenu.getSelectedItem();
                    Util.Choice status = (Util.Choice) cbStatus.getSelectedItem();
                    if (menu == null) throw new IllegalStateException("Please choose a menu.");
                    Util.require(name, price);
                    int n = Util.update("UPDATE menu_items mi JOIN menus m ON mi.menu_id = m.menu_id "
                                    + "SET mi.menu_id=?, mi.item_name=?, mi.description=?, mi.item_price=?, mi.status_id=? "
                                    + "WHERE mi.menu_item_id=? AND m.restaurant_id=? "
                                    + "AND EXISTS (SELECT 1 FROM menus m2 WHERE m2.menu_id=? AND m2.restaurant_id=?)",
                            menu.id, Util.t(name), Util.t(desc), Util.money(price), status.id, id, restaurantId, menu.id, restaurantId);
                    if (n == 0) throw new IllegalStateException("Item not found for your restaurant.");
                    refresh.run();
                }),
                Util.button("Delete Selected", Util.MAROON, this, () -> {
                    int id = Util.selectedId(t);
                    if (!Util.confirm(this, "Delete this item?")) return;
                    Util.update("DELETE mi FROM menu_items mi JOIN menus m ON mi.menu_id = m.menu_id "
                            + "WHERE mi.menu_item_id=? AND m.restaurant_id=?", id, restaurantId);
                    refresh.run();
                }),
                Util.button("Refresh", null, this, refresh));
    }

    // ---------------- Orders ----------------
    private JPanel ordersTab() {
        JTable tOrders = Util.table();
        JComboBox<Util.Choice> cbStatus = new JComboBox<>();
        Util.Task refresh = () -> {
            Util.load(tOrders, """
                SELECT o.order_id, o.order_date, o.order_time, o.pickup_time,
                       CONCAT(p.name, ' ', p.surname) AS customer, o.total_amount, s.status_name
                FROM orders o
                JOIN customers c ON o.customer_id = c.customer_id
                JOIN persons p ON c.person_id = p.person_id
                LEFT JOIN statuses s ON o.status_id = s.status_id
                WHERE o.restaurant_id = ?
                ORDER BY o.order_id DESC""", restaurantId);
            Util.fill(cbStatus, "SELECT status_id, status_name FROM statuses WHERE status_name IN "
                    + "('PENDING','CONFIRMED','PREPARING','READY','COMPLETED','CANCELLED') ORDER BY status_id");
        };
        refreshers.add(refresh);
        Util.onSelect(tOrders, this, r -> Util.selectLabel(cbStatus, Util.str(tOrders.getValueAt(r, 6))));

        JPanel bar = Util.flow(new JLabel("Set order status:"), cbStatus,
                Util.button("Update Status", Util.OLIVE, this, () -> {
                    int id = Util.selectedId(tOrders);
                    Util.Choice s = (Util.Choice) cbStatus.getSelectedItem();
                    int n = Util.update("UPDATE orders SET status_id=? WHERE order_id=? AND restaurant_id=?", s.id, id, restaurantId);
                    if (n == 0) throw new IllegalStateException("Order not found for your restaurant.");
                    refresh.run();
                }),
                Util.button("Refresh", null, this, refresh));

        JPanel p = new JPanel(new BorderLayout(6, 6));
        p.add(bar, BorderLayout.NORTH);
        p.add(new JScrollPane(tOrders), BorderLayout.CENTER);
        return p;
    }

    // ---------------- Reservations ----------------
    private JPanel reservationsTab() {
        JTable t = Util.table();
        Util.Task refresh = () -> Util.load(t, """
                SELECT r.reservation_id, CONCAT(p.name, ' ', p.surname) AS customer, r.table_id, tb.capacity,
                       tb.location, r.reservation_date, r.reservation_time, r.number_of_guests, s.status_name
                FROM reservations r
                JOIN customers c ON r.customer_id = c.customer_id
                JOIN persons p ON c.person_id = p.person_id
                JOIN restaurant_tables tb ON r.table_id = tb.table_id
                LEFT JOIN statuses s ON r.status_id = s.status_id
                WHERE r.restaurant_id = ?
                ORDER BY r.reservation_date DESC, r.reservation_time DESC""", restaurantId);
        refreshers.add(refresh);

        return Util.page(t, Util.form(),
                Util.button("Approve", Util.OLIVE, this, () -> { setReservation(t, "APPROVED"); refresh.run(); }),
                Util.button("Decline", Util.MAROON, this, () -> { setReservation(t, "DECLINED"); refresh.run(); }),
                Util.button("Refresh", null, this, refresh));
    }

    private void setReservation(JTable t, String status) throws Exception {
        int id = Util.selectedId(t);
        int n = Util.update("UPDATE reservations SET status_id=? WHERE reservation_id=? AND restaurant_id=?",
                Util.statusId(status), id, restaurantId);
        if (n == 0) throw new IllegalStateException("Reservation not found for your restaurant.");
    }

    // ---------------- Tables ----------------
    private JPanel tablesTab() {
        JTable t = Util.table();
        JTextField capacity = new JTextField(15), location = new JTextField(15);
        Util.Task refresh = () -> Util.load(t, """
                SELECT tb.table_id, tb.capacity, tb.location, s.status_name
                FROM restaurant_tables tb LEFT JOIN statuses s ON tb.status_id = s.status_id
                WHERE tb.restaurant_id = ? ORDER BY tb.table_id""", restaurantId);
        refreshers.add(refresh);

        return Util.page(t, Util.form("Seats:", capacity, "Location:", location),
                Util.button("Add Table", Util.OLIVE, this, () -> {
                    Util.require(capacity, location);
                    int seats = Integer.parseInt(Util.t(capacity));
                    if (seats < 1) throw new IllegalArgumentException("A table must seat at least 1 guest.");
                    Util.update("INSERT INTO restaurant_tables (restaurant_id, capacity, location, status_id) VALUES (?,?,?,?)",
                            restaurantId, seats, Util.t(location), Util.statusId("AVAILABLE"));
                    capacity.setText(""); location.setText("");
                    refresh.run();
                }),
                Util.button("Mark Available", Util.OLIVE, this, () -> { setTable(t, "AVAILABLE"); refresh.run(); }),
                Util.button("Mark Not Available", Util.MAROON, this, () -> { setTable(t, "NOT AVAILABLE"); refresh.run(); }),
                Util.button("Delete Selected", Util.MAROON, this, () -> {
                    int id = Util.selectedId(t);
                    if (!Util.confirm(this, "Delete this table?")) return;
                    Util.update("DELETE FROM restaurant_tables WHERE table_id=? AND restaurant_id=?", id, restaurantId);
                    refresh.run();
                }));
    }

    private void setTable(JTable t, String status) throws Exception {
        int id = Util.selectedId(t);
        Util.update("UPDATE restaurant_tables SET status_id=? WHERE table_id=? AND restaurant_id=?",
                Util.statusId(status), id, restaurantId);
    }
}
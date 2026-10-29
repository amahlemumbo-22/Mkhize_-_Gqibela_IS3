import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class CustomerFrame extends JFrame {

    /** One line in the cart: a single menu item. */
    static class Line {
        final int id; final String name; final double price; int qty = 1;
        Line(int id, String name, double price) { this.id = id; this.name = name; this.price = price; }
    }

    private final String username;
    private final int customerId;
    private Integer restaurantId = null;               // set once the customer clicks "Browse"
    private final List<Line> cart = new ArrayList<>();

    private final JComboBox<Util.Choice> cbRestaurant = new JComboBox<>();
    private final JTabbedPane tabs = new JTabbedPane();
    private final JTable tblMenus = Util.table(), tblItems = Util.table(), tblCart = Util.table(),
            tblFloor = Util.table(), tblMyRes = Util.table(), tblHistory = Util.table(), tblLines = Util.table();
    private final JLabel lblTotal = new JLabel("Total to pay: R0.00");
    private final JComboBox<String> cbPay = new JComboBox<>(new String[]{"Cash on pickup", "Card on pickup", "EFT"});
    private final JSpinner spPickup = new JSpinner(new SpinnerNumberModel(30, 10, 240, 5));
    private final JSpinner spDate = new JSpinner(new SpinnerDateModel());
    private final JSpinner spTime = new JSpinner(new SpinnerDateModel());
    private final JSpinner spGuests = new JSpinner(new SpinnerNumberModel(2, 1, 50, 1));

    public CustomerFrame(String username, int customerId) {
        this.username = username;
        this.customerId = customerId;
        setTitle("Customer Portal - " + username);
        setSize(1100, 720);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        Util.addLogout(this);

        spDate.setEditor(new JSpinner.DateEditor(spDate, "yyyy-MM-dd"));
        spTime.setEditor(new JSpinner.DateEditor(spTime, "HH:mm"));
        lblTotal.setFont(lblTotal.getFont().deriveFont(Font.BOLD, 15f));

        JPanel top = Util.flow(new JLabel("Step 1 - choose a restaurant:"), cbRestaurant,
                Util.button("Browse", Util.OLIVE, this, this::browse));

        tabs.addTab("Menus & Items", Util.withExit(this, menusTab()));
        tabs.addTab("My Cart", Util.withExit(this, cartTab()));
        tabs.addTab("Reservations", Util.withExit(this, reservationsTab()));
        tabs.addTab("Order History", Util.withExit(this, historyTab()));
        tabs.addTab("My Profile", Util.withExit(this, Util.profileEditor(this, username, null)));
        for (int i = 0; i < 3; i++) tabs.setEnabledAt(i, false);   // unlocked after a restaurant is chosen
        tabs.setSelectedIndex(3);

        setLayout(new BorderLayout());
        add(top, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);

        Util.run(this, () -> {
            Util.fill(cbRestaurant, "SELECT restaurant_id, restaurant_name FROM restaurants ORDER BY restaurant_name");
            loadHistory();
        });
    }

    // ---------------- choose restaurant ----------------
    private void browse() throws Exception {
        Util.Choice ch = (Util.Choice) cbRestaurant.getSelectedItem();
        if (ch == null) throw new IllegalStateException("Please choose a restaurant.");
        if (restaurantId != null && restaurantId != ch.id && !cart.isEmpty()) {
            if (!Util.confirm(this, "Switching restaurant will empty your cart. Continue?")) {
                Util.selectId(cbRestaurant, restaurantId);
                return;
            }
            cart.clear();
        }
        restaurantId = ch.id;
        for (int i = 0; i < 3; i++) tabs.setEnabledAt(i, true);
        refreshCart();
        loadCatalog();
        loadFloor();
        loadMyReservations();
        tabs.setSelectedIndex(0);
    }

    // ---------------- Menus & Items ----------------
    private JPanel menusTab() {
        JPanel left = new JPanel(new BorderLayout(4, 4));
        left.add(new JLabel("MENUS - click one to see its items", SwingConstants.CENTER), BorderLayout.NORTH);
        left.add(new JScrollPane(tblMenus), BorderLayout.CENTER);
        left.add(Util.button("Show all items", null, this, () -> { tblMenus.clearSelection(); loadItems(null); }), BorderLayout.SOUTH);

        JPanel right = new JPanel(new BorderLayout(4, 4));
        right.add(new JLabel("ITEMS (available only) - double-click to add to cart", SwingConstants.CENTER), BorderLayout.NORTH);
        right.add(new JScrollPane(tblItems), BorderLayout.CENTER);
        right.add(Util.flow(
                Util.button("Add selected ITEM to cart", Util.OLIVE, this, this::addToCart),
                Util.button("Add ALL items shown", Util.OLIVE, this, this::addAllShown)), BorderLayout.SOUTH);

        Util.onSelect(tblMenus, this, r -> loadItems(((Number) tblMenus.getValueAt(r, 0)).intValue()));
        tblItems.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) Util.run(CustomerFrame.this, () -> addToCart());
            }
        });

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setResizeWeight(0.3);
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        p.add(split, BorderLayout.CENTER);
        return p;
    }

    private void loadCatalog() throws Exception {
        Util.load(tblMenus, "SELECT menu_id, menu_name FROM menus WHERE restaurant_id=? ORDER BY menu_name", restaurantId);
        loadItems(null);
    }

    /** menuId == null shows every available item of the restaurant. */
    private void loadItems(Integer menuId) throws Exception {
        String sql = """
                SELECT mi.menu_item_id, m.menu_name, mi.item_name, mi.description, mi.item_price
                FROM menu_items mi JOIN menus m ON mi.menu_id = m.menu_id
                WHERE m.restaurant_id = ?
                  AND mi.status_id = (SELECT status_id FROM statuses WHERE status_name = 'AVAILABLE')
                """ + (menuId == null ? "" : " AND m.menu_id = ? ") + " ORDER BY m.menu_name, mi.item_name";
        if (menuId == null) Util.load(tblItems, sql, restaurantId);
        else Util.load(tblItems, sql, restaurantId, menuId);
    }

    private void addToCart() {
        int id = Util.selectedId(tblItems);
        int r = tblItems.getSelectedRow();
        put(id, Util.str(tblItems.getValueAt(r, 2)), ((Number) tblItems.getValueAt(r, 4)).doubleValue());
        refreshCart();
    }

    /** Adds every item currently shown (e.g. a whole menu after clicking it) to the cart. */
    private void addAllShown() {
        if (tblItems.getRowCount() == 0) throw new IllegalStateException("There are no items to add.");
        for (int r = 0; r < tblItems.getRowCount(); r++)
            put(((Number) tblItems.getValueAt(r, 0)).intValue(), Util.str(tblItems.getValueAt(r, 2)),
                    ((Number) tblItems.getValueAt(r, 4)).doubleValue());
        refreshCart();
    }

    private void put(int id, String name, double price) {
        for (Line l : cart) if (l.id == id) { l.qty++; return; }
        cart.add(new Line(id, name, price));
    }

    // ---------------- Cart ----------------
    private JPanel cartTab() {
        JPanel bar = Util.flow(
                Util.button("+ Qty", Util.OLIVE, this, () -> changeQty(1)),
                Util.button("- Qty", Util.OLIVE, this, () -> changeQty(-1)),
                Util.button("Remove", Util.MAROON, this, () -> { cart.remove(cartIndex()); refreshCart(); }),
                Util.button("Clear cart", Util.MAROON, this, () -> { cart.clear(); refreshCart(); }));
        JPanel bottom = Util.flow(lblTotal, new JLabel("   Ready for pickup in (min):"), spPickup,
                new JLabel("Payment:"), cbPay,
                Util.button("PLACE ORDER", Util.OLIVE, this, this::placeOrder));
        JPanel p = new JPanel(new BorderLayout(6, 6));
        p.add(bar, BorderLayout.NORTH);
        p.add(new JScrollPane(tblCart), BorderLayout.CENTER);
        p.add(bottom, BorderLayout.SOUTH);
        return p;
    }

    private int cartIndex() {
        int i = tblCart.getSelectedRow();
        if (i < 0 || i >= cart.size()) throw new IllegalStateException("Please select a row in your cart first.");
        return i;
    }

    private void changeQty(int delta) {
        int i = cartIndex();
        Line l = cart.get(i);
        l.qty += delta;
        if (l.qty <= 0) cart.remove(i);
        refreshCart();
        if (i < cart.size()) tblCart.setRowSelectionInterval(i, i);
    }

    private void refreshCart() {
        DefaultTableModel m = Util.readOnly(new String[]{"Item", "Unit price", "Qty", "Subtotal"});
        double total = 0;
        for (Line l : cart) {
            double sub = l.price * l.qty;
            total += sub;
            m.addRow(new Object[]{l.name, String.format("R%.2f", l.price), l.qty, String.format("R%.2f", sub)});
        }
        tblCart.setModel(m);
        lblTotal.setText(String.format("Total to pay: R%.2f", total));
    }

    private void placeOrder() throws Exception {
        if (cart.isEmpty()) throw new IllegalStateException("Your cart is empty.");
        int minutes = (Integer) spPickup.getValue();
        String method = (String) cbPay.getSelectedItem();
        int[] orderId = {0};
        double[] totalOut = {0};

        Util.tx(c -> {
            // Prices are re-read from the database so the total can't be out of date or tampered with
            double[] prices = new double[cart.size()];
            double total = 0;
            int available = Util.statusId(c, "AVAILABLE");
            for (int i = 0; i < cart.size(); i++) {
                Line l = cart.get(i);
                Object p = Util.scalar(c, "SELECT mi.item_price FROM menu_items mi JOIN menus m ON mi.menu_id = m.menu_id "
                        + "WHERE mi.menu_item_id=? AND m.restaurant_id=? AND mi.status_id=?", l.id, restaurantId, available);
                if (p == null) throw new IllegalStateException("'" + l.name + "' is no longer available. Please remove it from your cart.");
                prices[i] = ((Number) p).doubleValue();
                total += prices[i] * l.qty;
            }
            total = Math.round(total * 100) / 100.0;

            int id = Util.insert(c, "INSERT INTO orders (order_date, order_time, pickup_time, total_amount, customer_id, restaurant_id, status_id) "
                            + "VALUES (CURDATE(), CURTIME(), SEC_TO_TIME(MOD(TIME_TO_SEC(CURTIME()) + ?, 86400)), ?, ?, ?, ?)",
                    minutes * 60, total, customerId, restaurantId, Util.statusId(c, "PENDING"));
            for (int i = 0; i < cart.size(); i++) {
                Line l = cart.get(i);
                Util.exec(c, "INSERT INTO order_items (order_id, menu_item_id, quantity, price_at_order) VALUES (?,?,?,?)",
                        id, l.id, l.qty, prices[i]);
            }
            Util.exec(c, "INSERT INTO payments (amount, payment_date, payment_method, order_id, customer_id, restaurant_id) "
                    + "VALUES (?, CURDATE(), ?, ?, ?, ?)", total, method, id, customerId, restaurantId);
            orderId[0] = id;
            totalOut[0] = total;
        });

        cart.clear();
        refreshCart();
        loadHistory();
        JOptionPane.showMessageDialog(this, String.format("Order #%d placed!\nTotal to pay: R%.2f (%s)", orderId[0], totalOut[0], method));
    }

    // ---------------- Reservations ----------------
    private JPanel reservationsTab() {
        JPanel upper = new JPanel(new BorderLayout(4, 4));
        upper.add(new JLabel("Tables at this restaurant - select one, then pick date, time and guests:"), BorderLayout.NORTH);
        upper.add(new JScrollPane(tblFloor), BorderLayout.CENTER);
        upper.add(Util.flow(new JLabel("Date:"), spDate, new JLabel("Time:"), spTime,
                new JLabel("Guests:"), spGuests,
                Util.button("Book Table", Util.OLIVE, this, this::book)), BorderLayout.SOUTH);

        JPanel lower = new JPanel(new BorderLayout(4, 4));
        lower.add(new JLabel("My reservations at this restaurant:"), BorderLayout.NORTH);
        lower.add(new JScrollPane(tblMyRes), BorderLayout.CENTER);
        lower.add(Util.button("Cancel selected reservation", Util.MAROON, this, () -> {
            int id = Util.selectedId(tblMyRes);
            if (!Util.confirm(this, "Cancel this reservation?")) return;
            Util.update("UPDATE reservations SET status_id=? WHERE reservation_id=? AND customer_id=?",
                    Util.statusId("CANCELLED"), id, customerId);
            loadMyReservations();
        }), BorderLayout.SOUTH);

        JPanel p = new JPanel(new GridLayout(2, 1, 8, 8));
        p.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        p.add(upper);
        p.add(lower);
        return p;
    }

    private void loadFloor() throws Exception {
        Util.load(tblFloor, """
                SELECT tb.table_id, tb.capacity, tb.location
                FROM restaurant_tables tb
                WHERE tb.restaurant_id = ?
                  AND tb.status_id = (SELECT status_id FROM statuses WHERE status_name = 'AVAILABLE')
                ORDER BY tb.capacity""", restaurantId);
    }

    private void loadMyReservations() throws Exception {
        Util.load(tblMyRes, """
                SELECT r.reservation_id, tb.table_id, tb.capacity, tb.location,
                       r.reservation_date, r.reservation_time, r.number_of_guests, s.status_name
                FROM reservations r
                JOIN restaurant_tables tb ON r.table_id = tb.table_id
                LEFT JOIN statuses s ON r.status_id = s.status_id
                WHERE r.customer_id = ? AND r.restaurant_id = ?
                ORDER BY r.reservation_date DESC, r.reservation_time DESC""", customerId, restaurantId);
    }

    private void book() throws Exception {
        int tableId = Util.selectedId(tblFloor);
        int guests = (Integer) spGuests.getValue();
        String date = new SimpleDateFormat("yyyy-MM-dd").format((java.util.Date) spDate.getValue());
        String time = new SimpleDateFormat("HH:mm:00").format((java.util.Date) spTime.getValue());
        if (LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(time)).isBefore(LocalDateTime.now()))
            throw new IllegalArgumentException("Please choose a date and time in the future.");

        Util.tx(c -> {
            Object cap = Util.scalar(c, "SELECT capacity FROM restaurant_tables WHERE table_id=? AND restaurant_id=? AND status_id=?",
                    tableId, restaurantId, Util.statusId(c, "AVAILABLE"));
            if (cap == null) throw new IllegalStateException("That table is not available.");
            if (guests > ((Number) cap).intValue())
                throw new IllegalStateException("That table seats " + cap + " guests at most. Please pick a bigger table.");
            long clash = ((Number) Util.scalar(c, """
                    SELECT COUNT(*) FROM reservations r JOIN statuses s ON r.status_id = s.status_id
                    WHERE r.table_id = ? AND r.reservation_date = ? AND s.status_name IN ('PENDING','APPROVED')
                      AND ABS(TIME_TO_SEC(TIMEDIFF(r.reservation_time, ?))) < 7200""", tableId, date, time)).longValue();
            if (clash > 0) throw new IllegalStateException("That table is already booked around that time. Try another time or table.");
            Util.insert(c, "INSERT INTO reservations (table_id, customer_id, restaurant_id, reservation_date, reservation_time, "
                            + "number_of_guests, created_date, status_id) VALUES (?,?,?,?,?,?,CURDATE(),?)",
                    tableId, customerId, restaurantId, date, time, guests, Util.statusId(c, "PENDING"));
        });
        loadMyReservations();
        JOptionPane.showMessageDialog(this, "Reservation sent! It is PENDING until the manager approves it.");
    }

    // ---------------- Order history ----------------
    private JPanel historyTab() {
        JPanel p = new JPanel(new BorderLayout(6, 6));
        p.add(Util.flow(Util.button("Refresh", null, this, this::loadHistory)), BorderLayout.NORTH);
        p.add(new JScrollPane(tblHistory), BorderLayout.CENTER);
        return p;
    }

    private void loadHistory() throws Exception {
        Util.load(tblHistory, """
                SELECT o.order_id, r.restaurant_name, o.order_date, o.order_time, o.pickup_time, o.total_amount, s.status_name
                FROM orders o
                JOIN restaurants r ON o.restaurant_id = r.restaurant_id
                LEFT JOIN statuses s ON o.status_id = s.status_id
                WHERE o.customer_id = ?
                ORDER BY o.order_id DESC""", customerId);
    }

}
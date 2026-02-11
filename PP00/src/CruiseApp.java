import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.security.MessageDigest;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class CruiseApp extends JFrame {
    private DBHelper db;
    private User currentUser;
    private DefaultTableModel tableModel;
    private JTable cruiseTable;
    private JTextField tfSearch;
    private JSpinner spMinPrice;
    private JSpinner spMaxPrice;
    private JLabel lblUser;
    private JButton btnManageCruises;
    private JButton btnBook;
    private JButton btnViewBookings;
    private JButton btnExport;

    public CruiseApp() {
        super("Туристическое агентство \"Круиз\"");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 650);
        setLocationRelativeTo(null);

        db = new DBHelper("jdbc:sqlite:cruise.db");
        db.initDatabase();
        initUI();

        SwingUtilities.invokeLater(this::showAuthDialog);
    }

    private void initUI() {
        // Главная панель
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // ========== ВЕРХНЯЯ ПАНЕЛЬ ==========
        JPanel topPanel = new JPanel(new BorderLayout(10, 10));

        // Панель фильтров
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 5));
        filterPanel.setBorder(BorderFactory.createTitledBorder("🔍 Поиск круизов"));

        filterPanel.add(new JLabel("Направление:"));
        tfSearch = new JTextField(15);
        tfSearch.addKeyListener(new KeyAdapter() {
            public void keyReleased(KeyEvent e) {
                applyFilters();
            }
        });
        filterPanel.add(tfSearch);

        filterPanel.add(new JLabel("Цена от:"));
        spMinPrice = new JSpinner(new SpinnerNumberModel(0, 0, 1000000, 100));
        spMinPrice.setPreferredSize(new Dimension(80, 25));
        filterPanel.add(spMinPrice);

        filterPanel.add(new JLabel("до:"));
        spMaxPrice = new JSpinner(new SpinnerNumberModel(10000, 0, 1000000, 100));
        spMaxPrice.setPreferredSize(new Dimension(80, 25));
        filterPanel.add(spMaxPrice);

        JButton btnApplyFilter = new JButton("Применить");
        btnApplyFilter.addActionListener(e -> applyFilters());
        filterPanel.add(btnApplyFilter);

        JButton btnClearFilter = new JButton("Сбросить");
        btnClearFilter.addActionListener(e -> {
            tfSearch.setText("");
            spMinPrice.setValue(0);
            spMaxPrice.setValue(10000);
            updateTable(db.getAllCruises());
        });
        filterPanel.add(btnClearFilter);

        // Панель пользователя
        JPanel userPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        userPanel.setBorder(BorderFactory.createTitledBorder("👤 Пользователь"));

        lblUser = new JLabel("Гость");
        lblUser.setFont(new Font("Arial", Font.BOLD, 12));
        userPanel.add(lblUser);

        JButton btnLogin = new JButton("Вход");
        btnLogin.addActionListener(e -> showAuthDialog());
        userPanel.add(btnLogin);

        JButton btnLogout = new JButton("Выход");
        btnLogout.addActionListener(e -> {
            currentUser = null;
            lblUser.setText("Гость");
            btnManageCruises.setVisible(false);
            JOptionPane.showMessageDialog(this, "Вы вышли из системы");
        });
        userPanel.add(btnLogout);

        btnManageCruises = new JButton("⚙ Управление круизами");
        btnManageCruises.addActionListener(e -> {
            if (currentUser != null && currentUser.isAdmin()) {
                showCruiseManagementDialog();
            } else {
                JOptionPane.showMessageDialog(this, "❌ Доступ запрещен! Только для администраторов.");
            }
        });
        btnManageCruises.setVisible(false);
        userPanel.add(btnManageCruises);

        topPanel.add(filterPanel, BorderLayout.CENTER);
        topPanel.add(userPanel, BorderLayout.EAST);

        // ========== ЦЕНТРАЛЬНАЯ ПАНЕЛЬ (ТАБЛИЦА) ==========
        String[] cols = {"ID", "Направление", "Дата отправления", "Дней", "Цена (₽)", "Доступно мест", "Статус"};
        tableModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int row, int column) { return false; }
        };

        cruiseTable = new JTable(tableModel);
        cruiseTable.setRowHeight(25);
        cruiseTable.getTableHeader().setFont(new Font("Arial", Font.BOLD, 12));
        cruiseTable.setSelectionBackground(new Color(184, 207, 229));
        cruiseTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane scrollPane = new JScrollPane(cruiseTable);
        scrollPane.setBorder(BorderFactory.createTitledBorder("🚢 Доступные круизы"));

        // ========== НИЖНЯЯ ПАНЕЛЬ (КНОПКИ) ==========
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        bottomPanel.setBorder(BorderFactory.createTitledBorder("📋 Действия"));

        btnBook = new JButton("✅ Забронировать");
        btnBook.addActionListener(e -> bookSelectedCruise());
        bottomPanel.add(btnBook);

        btnViewBookings = new JButton("📜 Мои бронирования");
        btnViewBookings.addActionListener(e -> {
            if (requireLogin()) showBookingsDialog();
        });
        bottomPanel.add(btnViewBookings);

        btnExport = new JButton("📊 Экспорт броней (CSV)");
        btnExport.addActionListener(e -> {
            if (requireLogin() && currentUser.isAdmin()) {
                exportBookings();
            } else {
                JOptionPane.showMessageDialog(this, "❌ Только администраторы могут экспортировать бронирования.");
            }
        });
        bottomPanel.add(btnExport);

        JButton btnRefresh = new JButton("🔄 Обновить");
        btnRefresh.addActionListener(e -> {
            updateTable(db.getAllCruises());
            JOptionPane.showMessageDialog(this, "Данные обновлены");
        });
        bottomPanel.add(btnRefresh);

        // Сборка главного окна
        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        add(mainPanel);

        // Загрузка данных
        updateTable(db.getAllCruises());
    }

    private boolean requireLogin() {
        if (currentUser == null) {
            int result = JOptionPane.showConfirmDialog(this,
                    "Необходимо войти в систему. Выполнить вход?",
                    "Требуется авторизация",
                    JOptionPane.YES_NO_OPTION);
            if (result == JOptionPane.YES_OPTION) {
                showAuthDialog();
            }
            return currentUser != null;
        }
        return true;
    }

    private void showAuthDialog() {
        AuthDialog dlg = new AuthDialog(this, db);
        dlg.setVisible(true);
        User u = dlg.getAuthenticatedUser();
        if (u != null) {
            currentUser = u;
            lblUser.setText(u.getFullname() + (u.isAdmin() ? " (Администратор)" : ""));
            btnManageCruises.setVisible(u.isAdmin());
            btnExport.setVisible(u.isAdmin());
        }
    }

    private void showCruiseManagementDialog() {
        CruiseManagementDialog dlg = new CruiseManagementDialog(this, db);
        dlg.setVisible(true);
        updateTable(db.getAllCruises());
    }

    private void applyFilters() {
        String searchText = tfSearch.getText().trim().toLowerCase();
        double minPrice = ((Number) spMinPrice.getValue()).doubleValue();
        double maxPrice = ((Number) spMaxPrice.getValue()).doubleValue();

        List<Cruise> filtered = new ArrayList<>();
        for (Cruise c : db.getAllCruises()) {
            boolean matchSearch = searchText.isEmpty() ||
                    c.getDestination().toLowerCase().contains(searchText);
            boolean matchPrice = c.getPricePerPerson() >= minPrice &&
                    c.getPricePerPerson() <= maxPrice;

            if (matchSearch && matchPrice) {
                filtered.add(c);
            }
        }
        updateTable(filtered);
    }

    private void updateTable(List<Cruise> cruises) {
        tableModel.setRowCount(0);
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy");

        for (Cruise c : cruises) {
            String status = c.getAvailableSeats() > 0 ?
                    "✅ Есть места" : "❌ Нет мест";

            tableModel.addRow(new Object[]{
                    c.getId(),
                    c.getDestination(),
                    sdf.format(c.getDeparture()),
                    c.getDurationDays(),
                    String.format("%,.2f", c.getPricePerPerson()),
                    c.getAvailableSeats(),
                    status
            });
        }
    }

    private void bookSelectedCruise() {
        if (!requireLogin()) return;

        int row = cruiseTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "❌ Выберите круиз для бронирования");
            return;
        }

        int cruiseId = (int) cruiseTable.getValueAt(row, 0);
        Cruise cruise = db.findCruiseById(cruiseId);

        if (cruise == null) {
            JOptionPane.showMessageDialog(this, "❌ Круиз не найден");
            return;
        }

        if (cruise.getAvailableSeats() <= 0) {
            JOptionPane.showMessageDialog(this, "❌ Свободных мест нет");
            return;
        }

        BookingDialog dlg = new BookingDialog(this, cruise);
        dlg.setVisible(true);
        Booking booking = dlg.getBooking();

        if (booking != null) {
            booking.setUserId(currentUser.getId());
            booking.setCruise(cruise);

            if (db.insertBooking(booking)) {
                cruise.decrementAvailableSeats(booking.getSeats());
                db.updateCruiseSeats(cruise);
                updateTable(db.getAllCruises());
                JOptionPane.showMessageDialog(this, "✅ Бронирование успешно оформлено!");
            } else {
                JOptionPane.showMessageDialog(this, "❌ Ошибка при бронировании");
            }
        }
    }

    private void showBookingsDialog() {
        BookingsDialog dlg = new BookingsDialog(this, db, currentUser);
        dlg.setVisible(true);
        updateTable(db.getAllCruises());
    }

    private void exportBookings() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("bookings_export.csv"));

        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            try {
                db.exportBookingsToCSV(file);
                JOptionPane.showMessageDialog(this,
                        "✅ Экспорт выполнен: " + file.getAbsolutePath());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                        "❌ Ошибка экспорта: " + ex.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> {
            CruiseApp app = new CruiseApp();
            app.setVisible(true);
        });
    }
}

// ===================================================================
// ДИАЛОГ АВТОРИЗАЦИИ
// ===================================================================
class AuthDialog extends JDialog {
    private User authenticatedUser = null;
    private DBHelper db;

    public AuthDialog(JFrame owner, DBHelper db) {
        super(owner, "Авторизация", true);
        this.db = db;
        setSize(450, 350);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout());

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("Arial", Font.BOLD, 12));

        // ========== ВКЛАДКА ВХОДА ==========
        JPanel loginPanel = new JPanel(new GridBagLayout());
        loginPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JTextField tfUsername = new JTextField(15);
        JPasswordField pfPassword = new JPasswordField(15);

        gbc.gridx = 0; gbc.gridy = 0;
        loginPanel.add(new JLabel("Имя пользователя:"), gbc);
        gbc.gridx = 1;
        loginPanel.add(tfUsername, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        loginPanel.add(new JLabel("Пароль:"), gbc);
        gbc.gridx = 1;
        loginPanel.add(pfPassword, gbc);

        gbc.gridx = 1; gbc.gridy = 2;
        JButton btnLogin = new JButton("🔑 Войти");
        btnLogin.setPreferredSize(new Dimension(150, 35));
        btnLogin.addActionListener(e -> {
            String username = tfUsername.getText().trim();
            String password = new String(pfPassword.getPassword());

            if (username.isEmpty() || password.isEmpty()) {
                JOptionPane.showMessageDialog(this, "❌ Заполните все поля");
                return;
            }

            User user = db.authenticateUser(username, password);
            if (user != null) {
                authenticatedUser = user;
                JOptionPane.showMessageDialog(this, "✅ Добро пожаловать, " + user.getFullname() + "!");
                dispose();
            } else {
                JOptionPane.showMessageDialog(this, "❌ Неверное имя или пароль");
            }
        });
        loginPanel.add(btnLogin, gbc);

        // ========== ВКЛАДКА РЕГИСТРАЦИИ ==========
        JPanel regPanel = new JPanel(new GridBagLayout());
        regPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JTextField tfRegUsername = new JTextField(15);
        JPasswordField pfRegPassword = new JPasswordField(15);
        JTextField tfFullname = new JTextField(15);

        gbc.gridx = 0; gbc.gridy = 0;
        regPanel.add(new JLabel("Имя пользователя:"), gbc);
        gbc.gridx = 1;
        regPanel.add(tfRegUsername, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        regPanel.add(new JLabel("Пароль:"), gbc);
        gbc.gridx = 1;
        regPanel.add(pfRegPassword, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        regPanel.add(new JLabel("ФИО:"), gbc);
        gbc.gridx = 1;
        regPanel.add(tfFullname, gbc);

        gbc.gridx = 1; gbc.gridy = 3;
        JButton btnRegister = new JButton("📝 Зарегистрироваться");
        btnRegister.setPreferredSize(new Dimension(180, 35));
        btnRegister.addActionListener(e -> {
            String username = tfRegUsername.getText().trim();
            String password = new String(pfRegPassword.getPassword());
            String fullname = tfFullname.getText().trim();

            if (username.isEmpty() || password.isEmpty() || fullname.isEmpty()) {
                JOptionPane.showMessageDialog(this, "❌ Заполните все поля");
                return;
            }

            if (db.registerUser(username, password, fullname)) {
                JOptionPane.showMessageDialog(this,
                        "✅ Регистрация успешна! Теперь войдите в систему.");
                tfRegUsername.setText("");
                pfRegPassword.setText("");
                tfFullname.setText("");
                tabs.setSelectedIndex(0);
            } else {
                JOptionPane.showMessageDialog(this,
                        "❌ Ошибка регистрации. Возможно, имя занято.");
            }
        });
        regPanel.add(btnRegister, gbc);

        tabs.addTab("Вход", loginPanel);
        tabs.addTab("Регистрация", regPanel);

        add(tabs, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnClose = new JButton("Закрыть");
        btnClose.addActionListener(e -> dispose());
        bottomPanel.add(btnClose);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    public User getAuthenticatedUser() {
        return authenticatedUser;
    }
}

// ===================================================================
// ДИАЛОГ БРОНИРОВАНИЯ
// ===================================================================
class BookingDialog extends JDialog {
    private Booking result = null;

    public BookingDialog(JFrame owner, Cruise cruise) {
        super(owner, "Бронирование круиза", true);
        setSize(500, 450);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(10, 10));

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Информация о круизе
        JPanel infoPanel = new JPanel(new GridLayout(6, 2, 10, 10));
        infoPanel.setBorder(BorderFactory.createTitledBorder("🚢 Информация о круизе"));
        infoPanel.setBackground(new Color(240, 248, 255));

        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy");

        infoPanel.add(new JLabel("Направление:"));
        infoPanel.add(new JLabel(cruise.getDestination()));

        infoPanel.add(new JLabel("Дата отправления:"));
        infoPanel.add(new JLabel(sdf.format(cruise.getDeparture())));

        infoPanel.add(new JLabel("Длительность:"));
        infoPanel.add(new JLabel(cruise.getDurationDays() + " дней"));

        infoPanel.add(new JLabel("Цена за человека:"));
        infoPanel.add(new JLabel(String.format("%,.2f ₽", cruise.getPricePerPerson())));

        infoPanel.add(new JLabel("Доступно мест:"));
        infoPanel.add(new JLabel(String.valueOf(cruise.getAvailableSeats())));

        // Панель ввода данных
        JPanel inputPanel = new JPanel(new GridBagLayout());
        inputPanel.setBorder(BorderFactory.createTitledBorder("📝 Данные для бронирования"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JTextField tfName = new JTextField(15);
        JTextField tfContact = new JTextField(15);
        JSpinner spSeats = new JSpinner(new SpinnerNumberModel(1, 1, cruise.getAvailableSeats(), 1));
        spSeats.setPreferredSize(new Dimension(80, 25));

        gbc.gridx = 0; gbc.gridy = 0;
        inputPanel.add(new JLabel("Имя клиента:"), gbc);
        gbc.gridx = 1;
        inputPanel.add(tfName, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        inputPanel.add(new JLabel("Контакт (тел/email):"), gbc);
        gbc.gridx = 1;
        inputPanel.add(tfContact, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        inputPanel.add(new JLabel("Количество мест:"), gbc);
        gbc.gridx = 1;
        inputPanel.add(spSeats, gbc);

        JLabel lblTotalPrice = new JLabel("0.00 ₽");
        lblTotalPrice.setFont(new Font("Arial", Font.BOLD, 14));
        lblTotalPrice.setForeground(new Color(0, 100, 0));

        gbc.gridx = 0; gbc.gridy = 3;
        inputPanel.add(new JLabel("Итого:"), gbc);
        gbc.gridx = 1;
        inputPanel.add(lblTotalPrice, gbc);

        spSeats.addChangeListener(e -> {
            int seats = (int) spSeats.getValue();
            double total = seats * cruise.getPricePerPerson();
            lblTotalPrice.setText(String.format("%,.2f ₽", total));
        });

        mainPanel.add(infoPanel, BorderLayout.NORTH);
        mainPanel.add(inputPanel, BorderLayout.CENTER);

        // Кнопки
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 10));
        JButton btnBook = new JButton("✅ Подтвердить бронирование");
        btnBook.setFont(new Font("Arial", Font.BOLD, 12));
        btnBook.setBackground(new Color(60, 179, 113));
        btnBook.setForeground(Color.WHITE);

        JButton btnCancel = new JButton("❌ Отмена");
        btnCancel.setFont(new Font("Arial", Font.BOLD, 12));

        btnBook.addActionListener(e -> {
            String name = tfName.getText().trim();
            String contact = tfContact.getText().trim();
            int seats = (int) spSeats.getValue();

            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(this, "❌ Введите имя клиента");
                return;
            }
            if (contact.isEmpty()) {
                JOptionPane.showMessageDialog(this, "❌ Введите контактные данные");
                return;
            }

            result = new Booking(-1, -1, cruise, seats, contact, new Date());
            result.setCustomerName(name);
            dispose();
        });

        btnCancel.addActionListener(e -> {
            result = null;
            dispose();
        });

        btnPanel.add(btnBook);
        btnPanel.add(btnCancel);

        add(mainPanel, BorderLayout.CENTER);
        add(btnPanel, BorderLayout.SOUTH);

        // Инициализация суммы
        spSeats.setValue(1);
    }

    public Booking getBooking() {
        return result;
    }
}

// ===================================================================
// ДИАЛОГ УПРАВЛЕНИЯ КРУИЗАМИ (ДЛЯ АДМИНИСТРАТОРОВ)
// ===================================================================
class CruiseManagementDialog extends JDialog {
    private DBHelper db;
    private DefaultTableModel tableModel;
    private JTable cruiseTable;

    public CruiseManagementDialog(JFrame owner, DBHelper db) {
        super(owner, "Управление круизами", true);
        this.db = db;
        setSize(800, 500);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(10, 10));

        // Таблица круизов
        String[] cols = {"ID", "Направление", "Дата", "Дней", "Цена", "Мест"};
        tableModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int row, int column) { return false; }
        };

        cruiseTable = new JTable(tableModel);
        cruiseTable.setRowHeight(25);
        cruiseTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        loadCruises();

        // Панель кнопок
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        btnPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JButton btnAdd = new JButton("➕ Добавить круиз");
        btnAdd.addActionListener(e -> showAddCruiseDialog());

        JButton btnEdit = new JButton("✏ Редактировать");
        btnEdit.addActionListener(e -> showEditCruiseDialog());

        JButton btnDelete = new JButton("🗑 Удалить");
        btnDelete.addActionListener(e -> deleteCruise());

        JButton btnRefresh = new JButton("🔄 Обновить");
        btnRefresh.addActionListener(e -> loadCruises());

        btnPanel.add(btnAdd);
        btnPanel.add(btnEdit);
        btnPanel.add(btnDelete);
        btnPanel.add(btnRefresh);

        add(new JScrollPane(cruiseTable), BorderLayout.CENTER);
        add(btnPanel, BorderLayout.SOUTH);
    }

    private void loadCruises() {
        tableModel.setRowCount(0);
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy");

        for (Cruise c : db.getAllCruises()) {
            tableModel.addRow(new Object[]{
                    c.getId(),
                    c.getDestination(),
                    sdf.format(c.getDeparture()),
                    c.getDurationDays(),
                    String.format("%,.0f", c.getPricePerPerson()),
                    c.getAvailableSeats()
            });
        }
    }

    private void showAddCruiseDialog() {
        CruiseDialog dlg = new CruiseDialog((JFrame) getOwner());
        dlg.setVisible(true);
        Cruise cruise = dlg.getCruise();

        if (cruise != null) {
            db.insertCruise(cruise);
            loadCruises();
            JOptionPane.showMessageDialog(this, "✅ Круиз успешно добавлен");
        }
    }

    private void showEditCruiseDialog() {
        int row = cruiseTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "❌ Выберите круиз для редактирования");
            return;
        }

        int cruiseId = (int) cruiseTable.getValueAt(row, 0);
        Cruise cruise = db.findCruiseById(cruiseId);

        if (cruise != null) {
            CruiseDialog dlg = new CruiseDialog((JFrame) getOwner(), cruise);
            dlg.setVisible(true);
            Cruise updated = dlg.getCruise();

            if (updated != null) {
                updated.setId(cruiseId);
                db.updateCruise(updated);
                loadCruises();
                JOptionPane.showMessageDialog(this, "✅ Круиз обновлен");
            }
        }
    }

    private void deleteCruise() {
        int row = cruiseTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "❌ Выберите круиз для удаления");
            return;
        }

        int cruiseId = (int) cruiseTable.getValueAt(row, 0);
        String destination = (String) cruiseTable.getValueAt(row, 1);

        int confirm = JOptionPane.showConfirmDialog(this,
                "Удалить круиз \"" + destination + "\"?\nВсе связанные бронирования будут также удалены.",
                "Подтверждение удаления",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            db.deleteCruise(cruiseId);
            loadCruises();
            JOptionPane.showMessageDialog(this, "✅ Круиз удален");
        }
    }
}

// ===================================================================
// ДИАЛОГ ДОБАВЛЕНИЯ/РЕДАКТИРОВАНИЯ КРУИЗА
// ===================================================================
class CruiseDialog extends JDialog {
    private Cruise result = null;
    private boolean editMode = false;

    public CruiseDialog(JFrame owner) {
        this(owner, null);
    }

    public CruiseDialog(JFrame owner, Cruise cruise) {
        super(owner, cruise == null ? "Добавление круиза" : "Редактирование круиза", true);
        this.editMode = (cruise != null);
        setSize(500, 400);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout());

        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        // Поля ввода
        JTextField tfDestination = new JTextField(20);
        if (editMode) tfDestination.setText(cruise.getDestination());

        JSpinner spDay = new JSpinner(new SpinnerNumberModel(1, 1, 31, 1));
        JSpinner spMonth = new JSpinner(new SpinnerNumberModel(1, 1, 12, 1));
        JSpinner spYear = new JSpinner(new SpinnerNumberModel(2026, 2024, 2030, 1));

        if (editMode) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy");
            String dateStr = sdf.format(cruise.getDeparture());
            String[] parts = dateStr.split("\\.");
            spDay.setValue(Integer.parseInt(parts[0]));
            spMonth.setValue(Integer.parseInt(parts[1]));
            spYear.setValue(Integer.parseInt(parts[2]));
        }

        JSpinner spDuration = new JSpinner(new SpinnerNumberModel(7, 1, 30, 1));
        if (editMode) spDuration.setValue(cruise.getDurationDays());

        JSpinner spPrice = new JSpinner(new SpinnerNumberModel(1000.0, 0.0, 100000.0, 100.0));
        if (editMode) spPrice.setValue(cruise.getPricePerPerson());

        JSpinner spSeats = new JSpinner(new SpinnerNumberModel(50, 1, 1000, 1));
        if (editMode) spSeats.setValue(cruise.getAvailableSeats());

        gbc.gridx = 0; gbc.gridy = 0;
        mainPanel.add(new JLabel("Направление:"), gbc);
        gbc.gridx = 1;
        mainPanel.add(tfDestination, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        mainPanel.add(new JLabel("Дата отправления:"), gbc);
        gbc.gridx = 1;
        JPanel datePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        datePanel.add(spDay);
        datePanel.add(new JLabel("."));
        datePanel.add(spMonth);
        datePanel.add(new JLabel("."));
        datePanel.add(spYear);
        mainPanel.add(datePanel, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        mainPanel.add(new JLabel("Длительность (дней):"), gbc);
        gbc.gridx = 1;
        mainPanel.add(spDuration, gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        mainPanel.add(new JLabel("Цена (₽):"), gbc);
        gbc.gridx = 1;
        mainPanel.add(spPrice, gbc);

        gbc.gridx = 0; gbc.gridy = 4;
        mainPanel.add(new JLabel("Доступно мест:"), gbc);
        gbc.gridx = 1;
        mainPanel.add(spSeats, gbc);

        // Кнопки
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 10));

        JButton btnSave = new JButton(editMode ? "💾 Сохранить" : "➕ Добавить");
        btnSave.setFont(new Font("Arial", Font.BOLD, 12));
        btnSave.addActionListener(e -> {
            try {
                String destination = tfDestination.getText().trim();
                if (destination.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "❌ Введите направление");
                    return;
                }

                int day = (Integer) spDay.getValue();
                int month = (Integer) spMonth.getValue();
                int year = (Integer) spYear.getValue();

                SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy");
                Date departure = sdf.parse(day + "." + month + "." + year);

                int duration = (Integer) spDuration.getValue();
                double price = (Double) spPrice.getValue();
                int seats = (Integer) spSeats.getValue();

                result = new Cruise(-1, destination, departure, duration, price, seats);
                dispose();

            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "❌ Ошибка в данных: " + ex.getMessage());
            }
        });

        JButton btnCancel = new JButton("Отмена");
        btnCancel.addActionListener(e -> {
            result = null;
            dispose();
        });

        btnPanel.add(btnSave);
        btnPanel.add(btnCancel);

        add(mainPanel, BorderLayout.CENTER);
        add(btnPanel, BorderLayout.SOUTH);
    }

    public Cruise getCruise() {
        return result;
    }
}

// ===================================================================
// ДИАЛОГ ПРОСМОТРА БРОНИРОВАНИЙ
// ===================================================================
class BookingsDialog extends JDialog {
    private DBHelper db;
    private User currentUser;
    private DefaultTableModel tableModel;

    public BookingsDialog(JFrame owner, DBHelper db, User user) {
        super(owner, "Мои бронирования", true);
        this.db = db;
        this.currentUser = user;
        setSize(800, 500);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout());

        // Таблица бронирований
        String[] cols = {"ID", "Круиз", "Направление", "Дата круиза", "Мест", "Контакт", "Дата брони", "Сумма"};
        tableModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int row, int column) { return false; }
        };

        JTable bookingsTable = new JTable(tableModel);
        bookingsTable.setRowHeight(25);
        bookingsTable.getTableHeader().setFont(new Font("Arial", Font.BOLD, 12));

        loadBookings();

        // Панель кнопок
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 10));

        JButton btnDelete = new JButton("🗑 Отменить бронирование");
        btnDelete.addActionListener(e -> {
            int row = bookingsTable.getSelectedRow();
            if (row == -1) {
                JOptionPane.showMessageDialog(this, "❌ Выберите бронирование");
                return;
            }

            int bookingId = (int) bookingsTable.getValueAt(row, 0);
            String cruiseName = (String) bookingsTable.getValueAt(row, 2);

            int confirm = JOptionPane.showConfirmDialog(this,
                    "Отменить бронирование на круиз \"" + cruiseName + "\"?",
                    "Подтверждение",
                    JOptionPane.YES_NO_OPTION);

            if (confirm == JOptionPane.YES_OPTION) {
                db.deleteBookingById(bookingId);
                loadBookings();
                JOptionPane.showMessageDialog(this, "✅ Бронирование отменено");
            }
        });

        JButton btnClose = new JButton("Закрыть");
        btnClose.addActionListener(e -> dispose());

        btnPanel.add(btnDelete);
        btnPanel.add(btnClose);

        add(new JScrollPane(bookingsTable), BorderLayout.CENTER);
        add(btnPanel, BorderLayout.SOUTH);
    }

    private void loadBookings() {
        tableModel.setRowCount(0);
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy");
        SimpleDateFormat sdfDateTime = new SimpleDateFormat("dd.MM.yyyy HH:mm");

        List<Booking> bookings = db.getBookingsByUser(currentUser.getId());
        for (Booking b : bookings) {
            double total = b.getSeats() * b.getCruise().getPricePerPerson();

            tableModel.addRow(new Object[]{
                    b.getId(),
                    b.getCruise().getId(),
                    b.getCruise().getDestination(),
                    sdf.format(b.getCruise().getDeparture()),
                    b.getSeats(),
                    b.getContact(),
                    sdfDateTime.format(b.getBookingDate()),
                    String.format("%,.2f ₽", total)
            });
        }
    }
}

// ===================================================================
// МОДЕЛЬНЫЕ КЛАССЫ
// ===================================================================

class Cruise {
    private int id;
    private String destination;
    private Date departure;
    private int durationDays;
    private double pricePerPerson;
    private int availableSeats;

    public Cruise(int id, String destination, Date departure, int durationDays,
                  double pricePerPerson, int availableSeats) {
        this.id = id;
        this.destination = destination;
        this.departure = departure;
        this.durationDays = durationDays;
        this.pricePerPerson = pricePerPerson;
        this.availableSeats = availableSeats;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }

    public Date getDeparture() { return departure; }
    public void setDeparture(Date departure) { this.departure = departure; }

    public int getDurationDays() { return durationDays; }
    public void setDurationDays(int durationDays) { this.durationDays = durationDays; }

    public double getPricePerPerson() { return pricePerPerson; }
    public void setPricePerPerson(double pricePerPerson) { this.pricePerPerson = pricePerPerson; }

    public int getAvailableSeats() { return availableSeats; }
    public void setAvailableSeats(int availableSeats) { this.availableSeats = availableSeats; }

    public void decrementAvailableSeats(int seats) {
        this.availableSeats -= seats;
    }

    public void incrementAvailableSeats(int seats) {
        this.availableSeats += seats;
    }
}

class Booking {
    private int id;
    private int userId;
    private Cruise cruise;
    private int seats;
    private String contact;
    private Date bookingDate;
    private String customerName;

    public Booking(int id, int userId, Cruise cruise, int seats, String contact, Date bookingDate) {
        this.id = id;
        this.userId = userId;
        this.cruise = cruise;
        this.seats = seats;
        this.contact = contact;
        this.bookingDate = bookingDate;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public Cruise getCruise() { return cruise; }
    public void setCruise(Cruise cruise) { this.cruise = cruise; }

    public int getSeats() { return seats; }
    public void setSeats(int seats) { this.seats = seats; }

    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact; }

    public Date getBookingDate() { return bookingDate; }
    public void setBookingDate(Date bookingDate) { this.bookingDate = bookingDate; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
}

class User {
    private int id;
    private String username;
    private String fullname;
    private boolean isAdmin;

    public User(int id, String username, String fullname, boolean isAdmin) {
        this.id = id;
        this.username = username;
        this.fullname = fullname;
        this.isAdmin = isAdmin;
    }

    public int getId() { return id; }
    public String getUsername() { return username; }
    public String getFullname() { return fullname; }
    public boolean isAdmin() { return isAdmin; }
}

// ===================================================================
// DBHelper - РАБОТА С БАЗОЙ ДАННЫХ
// ===================================================================
class DBHelper {
    private String url;

    public DBHelper(String url) {
        this.url = url;
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url);
    }

    public void initDatabase() {
        String createUsersTable = "CREATE TABLE IF NOT EXISTS users (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "username TEXT UNIQUE NOT NULL," +
                "password TEXT NOT NULL," +
                "fullname TEXT NOT NULL," +
                "is_admin INTEGER DEFAULT 0)";

        String createCruisesTable = "CREATE TABLE IF NOT EXISTS cruises (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "destination TEXT NOT NULL," +
                "departure INTEGER NOT NULL," +
                "duration INTEGER NOT NULL," +
                "price REAL NOT NULL," +
                "available_seats INTEGER NOT NULL)";

        String createBookingsTable = "CREATE TABLE IF NOT EXISTS bookings (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "user_id INTEGER NOT NULL," +
                "cruise_id INTEGER NOT NULL," +
                "customer_name TEXT," +
                "seats INTEGER NOT NULL," +
                "contact TEXT NOT NULL," +
                "booking_date INTEGER NOT NULL," +
                "FOREIGN KEY(user_id) REFERENCES users(id)," +
                "FOREIGN KEY(cruise_id) REFERENCES cruises(id))";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute(createUsersTable);
            stmt.execute(createCruisesTable);
            stmt.execute(createBookingsTable);

            // Создание администратора по умолчанию
            String checkAdmin = "SELECT COUNT(*) FROM users WHERE username = 'admin'";
            ResultSet rs = stmt.executeQuery(checkAdmin);
            if (rs.next() && rs.getInt(1) == 0) {
                String insertAdmin = "INSERT INTO users (username, password, fullname, is_admin) VALUES (?, ?, ?, 1)";
                try (PreparedStatement ps = conn.prepareStatement(insertAdmin)) {
                    ps.setString(1, "admin");
                    ps.setString(2, hash("admin123"));
                    ps.setString(3, "Администратор");
                    ps.executeUpdate();
                }
            }

            // Проверка наличия круизов
            String checkCruises = "SELECT COUNT(*) FROM cruises";
            rs = stmt.executeQuery(checkCruises);
            if (rs.next() && rs.getInt(1) == 0) {
                insertSampleCruises();
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void insertSampleCruises() {
        String sql = "INSERT INTO cruises (destination, departure, duration, price, available_seats) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy");

            ps.setString(1, "Средиземное море (Италия, Франция, Испания)");
            ps.setLong(2, sdf.parse("15.06.2026").getTime());
            ps.setInt(3, 10);
            ps.setDouble(4, 145000);
            ps.setInt(5, 150);
            ps.executeUpdate();

            ps.setString(1, "Балтийское море (Санкт-Петербург, Таллин, Стокгольм)");
            ps.setLong(2, sdf.parse("01.07.2026").getTime());
            ps.setInt(3, 7);
            ps.setDouble(4, 89000);
            ps.setInt(5, 80);
            ps.executeUpdate();

            ps.setString(1, "Норвежские фьорды");
            ps.setLong(2, sdf.parse("10.08.2026").getTime());
            ps.setInt(3, 8);
            ps.setDouble(4, 156000);
            ps.setInt(5, 60);
            ps.executeUpdate();

            ps.setString(1, "Карибский бассейн");
            ps.setLong(2, sdf.parse("20.12.2026").getTime());
            ps.setInt(3, 12);
            ps.setDouble(4, 234000);
            ps.setInt(5, 200);
            ps.executeUpdate();

            ps.setString(1, "Аляска (ледники)");
            ps.setLong(2, sdf.parse("05.09.2026").getTime());
            ps.setInt(3, 9);
            ps.setDouble(4, 189000);
            ps.setInt(5, 45);
            ps.executeUpdate();

            ps.setString(1, "Япония (Токио, Осака, Хоккайдо)");
            ps.setLong(2, sdf.parse("10.10.2026").getTime());
            ps.setInt(3, 11);
            ps.setDouble(4, 278000);
            ps.setInt(5, 120);
            ps.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<Cruise> getAllCruises() {
        List<Cruise> cruises = new ArrayList<>();
        String sql = "SELECT * FROM cruises ORDER BY departure";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                Cruise cruise = new Cruise(
                        rs.getInt("id"),
                        rs.getString("destination"),
                        new Date(rs.getLong("departure")),
                        rs.getInt("duration"),
                        rs.getDouble("price"),
                        rs.getInt("available_seats")
                );
                cruises.add(cruise);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return cruises;
    }

    public Cruise findCruiseById(int id) {
        String sql = "SELECT * FROM cruises WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return new Cruise(
                        rs.getInt("id"),
                        rs.getString("destination"),
                        new Date(rs.getLong("departure")),
                        rs.getInt("duration"),
                        rs.getDouble("price"),
                        rs.getInt("available_seats")
                );
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null;
    }

    public boolean registerUser(String username, String password, String fullname) {
        String sql = "INSERT INTO users (username, password, fullname) VALUES (?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            ps.setString(2, hash(password));
            ps.setString(3, fullname);
            ps.executeUpdate();
            return true;

        } catch (SQLException e) {
            return false;
        }
    }

    public User authenticateUser(String username, String password) {
        String sql = "SELECT * FROM users WHERE username = ? AND password = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            ps.setString(2, hash(password));
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return new User(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("fullname"),
                        rs.getInt("is_admin") == 1
                );
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null;
    }

    public boolean insertBooking(Booking booking) {
        String sql = "INSERT INTO bookings (user_id, cruise_id, customer_name, seats, contact, booking_date) " +
                "VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, booking.getUserId());
            ps.setInt(2, booking.getCruise().getId());
            ps.setString(3, booking.getCustomerName());
            ps.setInt(4, booking.getSeats());
            ps.setString(5, booking.getContact());
            ps.setLong(6, booking.getBookingDate().getTime());
            ps.executeUpdate();

            return true;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public List<Booking> getBookingsByUser(int userId) {
        List<Booking> bookings = new ArrayList<>();
        String sql = "SELECT b.*, c.* FROM bookings b " +
                "JOIN cruises c ON b.cruise_id = c.id " +
                "WHERE b.user_id = ? " +
                "ORDER BY b.booking_date DESC";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                Cruise cruise = new Cruise(
                        rs.getInt("cruise_id"),
                        rs.getString("destination"),
                        new Date(rs.getLong("departure")),
                        rs.getInt("duration"),
                        rs.getDouble("price"),
                        rs.getInt("available_seats")
                );

                Booking booking = new Booking(
                        rs.getInt("id"),
                        rs.getInt("user_id"),
                        cruise,
                        rs.getInt("seats"),
                        rs.getString("contact"),
                        new Date(rs.getLong("booking_date"))
                );
                booking.setCustomerName(rs.getString("customer_name"));

                bookings.add(booking);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return bookings;
    }

    public void deleteBookingById(int bookingId) {
        // Сначала получаем информацию о бронировании
        String getBookingSql = "SELECT cruise_id, seats FROM bookings WHERE id = ?";
        String deleteSql = "DELETE FROM bookings WHERE id = ?";

        try (Connection conn = getConnection()) {
            // Получаем данные брони
            try (PreparedStatement ps = conn.prepareStatement(getBookingSql)) {
                ps.setInt(1, bookingId);
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    int cruiseId = rs.getInt("cruise_id");
                    int seats = rs.getInt("seats");

                    // Возвращаем места обратно
                    Cruise cruise = findCruiseById(cruiseId);
                    if (cruise != null) {
                        cruise.incrementAvailableSeats(seats);
                        updateCruiseSeats(cruise);
                    }
                }
            }

            // Удаляем бронь
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setInt(1, bookingId);
                ps.executeUpdate();
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void insertCruise(Cruise cruise) {
        String sql = "INSERT INTO cruises (destination, departure, duration, price, available_seats) " +
                "VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, cruise.getDestination());
            ps.setLong(2, cruise.getDeparture().getTime());
            ps.setInt(3, cruise.getDurationDays());
            ps.setDouble(4, cruise.getPricePerPerson());
            ps.setInt(5, cruise.getAvailableSeats());
            ps.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void updateCruise(Cruise cruise) {
        String sql = "UPDATE cruises SET destination = ?, departure = ?, duration = ?, " +
                "price = ?, available_seats = ? WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, cruise.getDestination());
            ps.setLong(2, cruise.getDeparture().getTime());
            ps.setInt(3, cruise.getDurationDays());
            ps.setDouble(4, cruise.getPricePerPerson());
            ps.setInt(5, cruise.getAvailableSeats());
            ps.setInt(6, cruise.getId());
            ps.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void updateCruiseSeats(Cruise cruise) {
        String sql = "UPDATE cruises SET available_seats = ? WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, cruise.getAvailableSeats());
            ps.setInt(2, cruise.getId());
            ps.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void deleteCruise(int cruiseId) {
        // Удаляем связанные бронирования
        String deleteBookings = "DELETE FROM bookings WHERE cruise_id = ?";
        String deleteCruise = "DELETE FROM cruises WHERE id = ?";

        try (Connection conn = getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(deleteBookings)) {
                ps.setInt(1, cruiseId);
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(deleteCruise)) {
                ps.setInt(1, cruiseId);
                ps.executeUpdate();
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void exportBookingsToCSV(File file) throws Exception {
        String sql = "SELECT b.*, u.username, u.fullname, c.destination, c.departure, c.price " +
                "FROM bookings b " +
                "JOIN users u ON b.user_id = u.id " +
                "JOIN cruises c ON b.cruise_id = c.id " +
                "ORDER BY b.booking_date DESC";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery();
             java.io.PrintWriter pw = new java.io.PrintWriter(file)) {

            pw.println("ID;Пользователь;ФИО;Круиз;Дата круиза;Имя клиента;Мест;Контакт;Дата брони;Сумма");

            SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy");
            SimpleDateFormat sdfDateTime = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");

            while (rs.next()) {
                double total = rs.getInt("seats") * rs.getDouble("price");

                pw.printf("%d;%s;%s;%s;%s;%s;%d;%s;%s;%.2f%n",
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("fullname"),
                        rs.getString("destination"),
                        sdf.format(new Date(rs.getLong("departure"))),
                        rs.getString("customer_name"),
                        rs.getInt("seats"),
                        rs.getString("contact"),
                        sdfDateTime.format(new Date(rs.getLong("booking_date"))),
                        total
                );
            }
        }
    }

    private String hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
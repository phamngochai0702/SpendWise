package com.spendwise.ui.components;

import com.spendwise.service.ValidationException;
import com.spendwise.util.DateUtil;
import com.spendwise.util.Dialogs;
import com.spendwise.util.Icons;
import com.spendwise.util.Theme;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;

import javax.swing.border.LineBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;

import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Date input with a built-in month-calendar popup (no external library).
 * The text field stays editable; the calendar button opens a grid where the
 * user picks a day with one click, prev/next buttons change the month and a
 * "Hôm nay" shortcut jumps back to today.
 */
public final class DateField extends JPanel {

    private static final String[] WEEKDAYS = {"T2", "T3", "T4", "T5", "T6", "T7", "CN"};

    private final JTextField input = new JTextField(9);
    private final JButton calendarButton = Dialogs.iconButton("Chọn ngày từ lịch",
            Icons.icon(Icons.Kind.CALENDAR, 14, Theme.MUTED));
    private final List<Runnable> changeListeners = new ArrayList<>();

    private final String pattern;
    private LocalDate value;
    private JPopupMenu popup;

    public DateField(String pattern, LocalDate initialValue) {
        super(new BorderLayout(4, 0));
        setOpaque(false);
        this.pattern = pattern == null || pattern.isBlank() ? "dd/MM/yyyy" : pattern;
        this.value = initialValue;
        input.setHorizontalAlignment(JTextField.CENTER);
        input.setText(DateUtil.format(initialValue, this.pattern));
        calendarButton.addActionListener(event -> showCalendar());
        add(input, BorderLayout.CENTER);
        add(calendarButton, BorderLayout.LINE_END);
        input.addActionListener(event -> commitText());
        input.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent event) {
                commitText();
            }
        });
    }

    public LocalDate getValue() {
        return value;
    }

    public void setValue(LocalDate date) {
        value = date;
        input.setText(date == null ? "" : DateUtil.format(date, pattern));
    }

    /** Notified whenever the user picks a day in the calendar or commits typed text. */
    public void addDateChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    private void fireChange() {
        for (Runnable listener : changeListeners) {
            listener.run();
        }
    }

    private void commitText() {
        String text = input.getText().trim();
        try {
            LocalDate parsed = DateUtil.parse(text, pattern, "Ngày không hợp lệ");
            setValue(parsed);
            fireChange();
        } catch (ValidationException invalid) {
            setValue(value); // revert typed garbage
        }
    }

    private void pickDay(LocalDate date) {
        hidePopupIfOpen();
        setValue(date);
        fireChange();
    }

    private void showCalendar() {
        hidePopupIfOpen();
        popup = new JPopupMenu();
        popup.setBorder(new LineBorder(Theme.BORDER));
        popup.add(new CalendarGrid(value == null ? YearMonth.now() : YearMonth.from(value),
                this::pickDay));
        popup.show(calendarButton, 0, calendarButton.getHeight() + 2);
    }

    private void hidePopupIfOpen() {
        if (popup != null) {
            popup.setVisible(false);
        }
    }

    /** Dark-panel grid of one month with prev/next navigation and "today" shortcut. */
    private final class CalendarGrid extends JPanel {

        private YearMonth month;
        private final Consumer<LocalDate> onPick;

        CalendarGrid(YearMonth initialMonth, Consumer<LocalDate> onPick) {
            super(new BorderLayout(0, 6));
            this.month = initialMonth;
            this.onPick = onPick;
            setBackground(Theme.CARD);
            setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            rebuild();
        }

        private void rebuild() {
            removeAll();

            JPanel navBar = new JPanel(new BorderLayout(4, 0));
            navBar.setOpaque(false);
            JButton prev = new JButton("<");
            JButton next = new JButton(">");
            for (JButton arrow : new JButton[]{prev, next}) {
                arrow.setMargin(new java.awt.Insets(2, 8, 2, 8));
                arrow.setFocusPainted(false);
            }
            JLabel monthLabel = new JLabel(
                    "Tháng " + month.getMonthValue() + "/" + month.getYear(),
                    javax.swing.SwingConstants.CENTER);
            monthLabel.setFont(monthLabel.getFont().deriveFont(Font.BOLD, 12.5f));
            monthLabel.setForeground(Theme.TEXT);
            prev.addActionListener(event -> {
                month = month.minusMonths(1);
                rebuild();
                repaint();
            });
            next.addActionListener(event -> {
                month = month.plusMonths(1);
                rebuild();
                repaint();
            });
            navBar.add(prev, BorderLayout.LINE_START);
            navBar.add(monthLabel, BorderLayout.CENTER);
            navBar.add(next, BorderLayout.LINE_END);
            add(navBar, BorderLayout.NORTH);

            JPanel week = new JPanel(new GridLayout(0, 7, 2, 2));
            week.setOpaque(false);
            for (int cellWidth = 0; cellWidth < WEEKDAYS.length; cellWidth++) {
                JLabel header = new JLabel(WEEKDAYS[cellWidth], javax.swing.SwingConstants.CENTER);
                header.setForeground(Theme.MUTED);
                header.setFont(header.getFont().deriveFont(Font.BOLD, 10.5f));
                week.add(header);
            }

            LocalDate firstDay = month.atDay(1);
            int leadingBlanks = firstDay.getDayOfWeek().getValue() - 1; // Monday = 0 blanks
            LocalDate today = LocalDate.now();
            for (int i = 0; i < leadingBlanks; i++) {
                week.add(new JLabel(""));
            }
            for (int day = 1; day <= month.lengthOfMonth(); day++) {
                LocalDate date = month.atDay(day);
                week.add(dayButton(date, today));
            }
            add(week, BorderLayout.CENTER);

            JButton todayButton = new JButton("Hôm nay");
            todayButton.setFont(todayButton.getFont().deriveFont(11f));
            todayButton.setFocusPainted(false);
            todayButton.addActionListener(event -> onPick.accept(today));
            JPanel shortcutBar = new JPanel(new BorderLayout());
            shortcutBar.setOpaque(false);
            shortcutBar.add(todayButton, BorderLayout.LINE_START);
            add(shortcutBar, BorderLayout.SOUTH);

            revalidate();
            repaint();
        }

        private JButton dayButton(LocalDate date, LocalDate today) {
            JButton button = new JButton(Integer.toString(date.getDayOfMonth()));
            button.setMargin(new java.awt.Insets(1, 1, 1, 1));
            button.setFocusPainted(false);
            button.setPreferredSize(new Dimension(34, 26));
            button.addActionListener(event -> {
                hidePopupIfOpen();
                onPick.accept(date);
            });
            if (date.equals(value)) {
                button.setBackground(Theme.PRIMARY);
                button.setForeground(Color.WHITE);
                button.setOpaque(true);
            } else if (date.equals(today)) {
                button.setBackground(Theme.PRIMARY_PALE);
                button.setForeground(Theme.PRIMARY_DARK);
                button.setOpaque(true);
            }
            return button;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(264, 246);
        }
    }
}
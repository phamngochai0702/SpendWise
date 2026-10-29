package com.spendwise.model;

import java.time.LocalDateTime;

/** A transaction category ("Ăn uống", "Lương", ...). */
public final class Category {

    private final long id;
    private String name;
    private final TransactionType type;
    private String color;
    private final LocalDateTime createdAt;

    public Category(long id, String name, TransactionType type, String color, LocalDateTime createdAt) {
        this.id = id;
        this.name = name == null ? "" : name.trim();
        this.type = type;
        this.color = color == null || color.isBlank() ? "#0F766E" : color.trim();
        this.createdAt = createdAt;
    }

    public long id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void rename(String newName) {
        this.name = newName == null ? "" : newName.trim();
    }

    public TransactionType type() {
        return type;
    }

    public String color() {
        return color;
    }

    public void setColor(String color) {
        this.color = color == null || color.isBlank() ? this.color : color.trim();
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return name;
    }
}

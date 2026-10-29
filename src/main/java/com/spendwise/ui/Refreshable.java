package com.spendwise.ui;

/** Screens that can reload all of their data from the database on demand. */
public interface Refreshable {

    void refresh();
}
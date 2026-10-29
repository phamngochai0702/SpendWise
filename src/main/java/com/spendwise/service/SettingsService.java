package com.spendwise.service;

import com.spendwise.model.AppSettings;
import com.spendwise.model.PeriodKey;
import com.spendwise.repository.SettingsRepository;

import java.util.Map;

/** Loads and persists user preferences with robust defaults. */
public final class SettingsService {

    public static final String KEY_PROFILE = "profile.name";
    public static final String KEY_CURRENCY = "display.currency";
    public static final String KEY_DATE_FORMAT = "display.date_format";
    public static final String KEY_DEFAULT_PERIOD = "display.default_period";
    public static final String KEY_CSV_DELIMITER = "export.csv_delimiter";

    private final SettingsRepository repository;

    public SettingsService(SettingsRepository repository) {
        this.repository = repository;
    }

    public AppSettings load() {
        Map<String, String> values = repository.loadAll();
        AppSettings settings = new AppSettings();
        String value;

        value = values.get(KEY_PROFILE);
        if (value != null) {
            settings.setProfileName(value);
        }
        value = values.get(KEY_CURRENCY);
        if (value != null && !value.isBlank()) {
            settings.setCurrencySymbol(value);
        }
        value = values.get(KEY_DATE_FORMAT);
        if (value != null && !value.isBlank()) {
            settings.setDateFormat(value);
        }
        value = values.get(KEY_DEFAULT_PERIOD);
        if (value != null && !value.isBlank()) {
            try {
                settings.setDefaultPeriod(PeriodKey.valueOf(value));
            } catch (IllegalArgumentException ignored) {
                // keep default
            }
        }
        value = values.get(KEY_CSV_DELIMITER);
        if (value != null && !value.isBlank()) {
            settings.setCsvDelimiter(value);
        }
        return settings;
    }

    public void save(AppSettings settings) {
        repository.save(KEY_PROFILE, settings.profileName());
        repository.save(KEY_CURRENCY, settings.currencySymbol());
        repository.save(KEY_DATE_FORMAT, settings.dateFormat());
        repository.save(KEY_DEFAULT_PERIOD, settings.defaultPeriod().name());
        repository.save(KEY_CSV_DELIMITER, settings.csvDelimiter());
    }

    /** Immediate single-key update (first-run profile). */
    public void set(String key, String value) {
        repository.save(key, value);
    }
}
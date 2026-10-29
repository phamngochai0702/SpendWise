package com.spendwise.model;

/** User-editable application preferences with sensible Vietnamese defaults. */
public final class AppSettings {

    /** Friendly profile name shown in the greeting; may be empty. */
    private String profileName = "";

    /** Currency symbol used when displaying money ("₫" by default). */
    private String currencySymbol = "₫";

    /** Display date pattern (numeric patterns only, locale independent). */
    private String dateFormat = "dd/MM/yyyy";

    /** Default period preset for the dashboard. */
    private PeriodKey defaultPeriod = PeriodKey.THIS_MONTH;

    /** CSV field delimiter used by exports. */
    private String csvDelimiter = ",";

    public String profileName() {
        return profileName;
    }

    public void setProfileName(String profileName) {
        this.profileName = profileName == null ? "" : profileName.trim();
    }

    public String currencySymbol() {
        return currencySymbol;
    }

    public void setCurrencySymbol(String currencySymbol) {
        this.currencySymbol = currencySymbol == null || currencySymbol.isBlank()
                ? "₫" : currencySymbol.trim();
    }

    public String dateFormat() {
        return dateFormat;
    }

    public void setDateFormat(String dateFormat) {
        this.dateFormat = dateFormat == null || dateFormat.isBlank()
                ? "dd/MM/yyyy" : dateFormat.trim();
    }

    public PeriodKey defaultPeriod() {
        return defaultPeriod;
    }

    public void setDefaultPeriod(PeriodKey defaultPeriod) {
        this.defaultPeriod = defaultPeriod == null ? PeriodKey.THIS_MONTH : defaultPeriod;
    }

    public String csvDelimiter() {
        return csvDelimiter;
    }

    public void setCsvDelimiter(String csvDelimiter) {
        this.csvDelimiter = csvDelimiter == null || csvDelimiter.isBlank()
                ? "," : csvDelimiter.trim();
    }
}

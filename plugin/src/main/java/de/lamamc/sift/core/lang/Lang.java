package de.lamamc.sift.core.lang;

/**
 * The two languages of the plugin's texts (alpha.42c). German for every German locale (de_de, de_at, de_ch …), English
 * for all others (also when the locale is unknown).
 */
public enum Lang {
    DE, EN;

    /** From a locale tag or language ("de_DE", "de-AT", "de", "en_us", "fr_fr" …). */
    public static Lang of(String locale) {
        if (locale == null) {
            return EN;
        }
        String language = locale.toLowerCase(java.util.Locale.ROOT).split("[_-]")[0];
        return "de".equals(language) ? DE : EN;
    }

    /** From the config value language: de|en (anything else: German, the admins' language so far). */
    public static Lang ofSetting(String value) {
        return "en".equalsIgnoreCase(value == null ? "" : value.trim()) ? EN : DE;
    }

    public String code() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}

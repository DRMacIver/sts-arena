package stsarena.localization;

import basemod.BaseMod;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.localization.UIStrings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import stsarena.STSArena;

/**
 * Handles localization for STS Arena mod.
 *
 * Loads UI strings based on the game's language setting and provides
 * convenient access methods for localized text.
 */
public class LocalizedStrings {
    private static final Logger logger = LogManager.getLogger(LocalizedStrings.class.getName());

    // String IDs
    public static final String ARENA_MENU = STSArena.MOD_ID + ":ArenaMenu";
    public static final String LOADOUT_SELECT = STSArena.MOD_ID + ":LoadoutSelect";
    public static final String LOADOUT_CREATOR = STSArena.MOD_ID + ":LoadoutCreator";
    public static final String ENCOUNTER_SELECT = STSArena.MOD_ID + ":EncounterSelect";
    public static final String HISTORY = STSArena.MOD_ID + ":History";
    public static final String STATS = STSArena.MOD_ID + ":Stats";
    public static final String PAUSE_MENU = STSArena.MOD_ID + ":PauseMenu";
    public static final String RESULTS = STSArena.MOD_ID + ":Results";

    /**
     * Load localization strings for the current language.
     * Call this from EditStringsSubscriber.receiveEditStrings().
     */
    public static void loadStrings() {
        String langFolder = getLangFolder();
        logger.info("Loading STS Arena localization for language: " + langFolder);

        String path = STSArena.MOD_ID + "/localization/" + langFolder + "/UIStrings.json";
        BaseMod.loadCustomStringsFile(UIStrings.class, path);
    }

    /**
     * Get the folder name for the current language.
     * Falls back to English if the language is not supported.
     */
    private static String getLangFolder() {
        switch (Settings.language) {
            case ZHS:
                return "zhs";  // Simplified Chinese
            case ZHT:
                return "zht";  // Traditional Chinese
            case JPN:
                return "jpn";  // Japanese
            case KOR:
                return "kor";  // Korean
            case RUS:
                return "rus";  // Russian
            case DEU:
                return "deu";  // German
            case FRA:
                return "fra";  // French
            case SPA:
                return "spa";  // Spanish (Spain)
            case SRB:
                return "srb";  // Serbian (Cyrillic)
            case ITA:
                return "ita";  // Italian
            case POL:
                return "pol";  // Polish
            case PTB:
                return "ptb";  // Portuguese (Brazil)
            case THA:
                return "tha";  // Thai
            case IND:
                return "ind";  // Indonesian
            case TUR:
                return "tur";  // Turkish
            case VIE:
                return "vie";  // Vietnamese
            case UKR:
                return "ukr";  // Ukrainian
            case EPO:
                return "epo";  // Esperanto
            case GRE:
                return "gre";  // Greek
            case DUT:
                return "dut";  // Dutch
            default:
                return "eng";  // English (default)
        }
    }

    /**
     * Get UI strings by ID.
     * Returns null if not found.
     */
    public static UIStrings getUIStrings(String id) {
        try {
            return com.megacrit.cardcrawl.core.CardCrawlGame.languagePack.getUIString(id);
        } catch (Exception e) {
            logger.warn("Could not find UI string: " + id);
            return null;
        }
    }

    /**
     * Get a specific text string by ID and index.
     * Returns a fallback string if not found.
     */
    public static String getText(String id, int index) {
        UIStrings strings = getUIStrings(id);
        if (strings != null && strings.TEXT != null && index < strings.TEXT.length) {
            return strings.TEXT[index];
        }
        return "[Missing: " + id + "[" + index + "]]";
    }

    /**
     * Get all text strings for an ID.
     * Returns empty array if not found.
     */
    public static String[] getAllText(String id) {
        UIStrings strings = getUIStrings(id);
        if (strings != null && strings.TEXT != null) {
            return strings.TEXT;
        }
        return new String[0];
    }
}

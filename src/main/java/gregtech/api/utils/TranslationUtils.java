package gregtech.api.utils;

import net.minecraft.util.text.translation.I18n;

import mcjty.theoneprobe.api.IProbeInfo;

import java.util.IllegalFormatException;

/*
 * From : https://github.com/Supernoobv/GregicProbeCEu/blob/master/src/main/java/vfyjxf/gregicprobe/util/TranslationUtils.java
 */
public class TranslationUtils {

    public static String translate(String key, Object... params) {
        try {
            var localTranslated = I18n.translateToLocalFormatted(key, params);
            if (!localTranslated.equals(key)) return localTranslated;

            var fallbackTranslated = I18n.translateToFallback(key);
            if (!fallbackTranslated.equals(key) && params.length != 0) {
                try {
                    fallbackTranslated = String.format(fallbackTranslated, params);
                } catch (IllegalFormatException err) {
                    fallbackTranslated = "Format error: " + fallbackTranslated;
                }
            }
            return fallbackTranslated;
        } catch (Exception e) {
            return key;
        }
    }

    public static String topTranslate(String key) {
        return IProbeInfo.STARTLOC + key + IProbeInfo.ENDLOC;
    }
}

package gregtech.api.mui;

import com.cleanroommc.modularui.api.IThemeApi;
import com.cleanroommc.modularui.theme.WidgetTheme;
import com.cleanroommc.modularui.theme.WidgetThemeKey;
import com.cleanroommc.modularui.utils.Color;

public final class GTWidgetThemes {

    private static final IThemeApi themeApi = IThemeApi.get();

    public static WidgetThemeKey<WidgetTheme> DISPLAY_TEXT = themeApi
        .widgetThemeKeyBuilder("displayText", WidgetTheme.class)
        .defaultTheme(new WidgetTheme(0, 0, null, Color.WHITE.main, 0xFAFAFA, false, 0))
        .defaultHoverTheme(null)
        .register();
}

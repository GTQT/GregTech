package gregtech.client.gui.gl;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Collections;
import java.util.List;

/**
 * Base class for the self-drawn widgets used by the multiblock tool screens.
 *
 * <p>
 * Widgets are positioned by {@link FlexBox} (or directly by the owning screen) and draw in GUI-scaled coordinates.
 * They intentionally do not know about any external UI framework.
 */
@SideOnly(Side.CLIENT)
public abstract class GWidget implements FlexBox.Item {

    protected int x;
    protected int y;
    protected int width;
    protected int height;
    protected boolean visible = true;
    protected boolean enabled = true;
    protected int prefWidth = -1;
    protected int prefHeight = -1;
    protected float grow = 0F;

    protected GWidget(int prefWidth, int prefHeight) {
        this.prefWidth = prefWidth;
        this.prefHeight = prefHeight;
    }

    // ---------------------------------------------------------------------------------------------
    // Layout
    // ---------------------------------------------------------------------------------------------

    @Override
    public int prefWidth() {
        return prefWidth;
    }

    @Override
    public int prefHeight() {
        return prefHeight;
    }

    @Override
    public float grow() {
        return grow;
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        onBoundsChanged();
    }

    protected void onBoundsChanged() {}

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public GWidget prefWidth(int prefWidth) {
        this.prefWidth = prefWidth;
        return this;
    }

    public GWidget prefHeight(int prefHeight) {
        this.prefHeight = prefHeight;
        return this;
    }

    public GWidget grow(float grow) {
        this.grow = grow;
        return this;
    }

    // ---------------------------------------------------------------------------------------------
    // State
    // ---------------------------------------------------------------------------------------------

    public boolean isVisible() {
        return visible;
    }

    public GWidget visible(boolean visible) {
        this.visible = visible;
        return this;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public GWidget enabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public boolean contains(int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    /** Tooltip lines shown while the cursor is inside this widget; empty for none. */
    public List<String> tooltip() {
        return Collections.emptyList();
    }

    // ---------------------------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------------------------

    public void update() {}

    public abstract void draw(int mouseX, int mouseY, float partialTicks);

    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        return false;
    }

    public void mouseReleased(int mouseX, int mouseY, int button) {}

    public void mouseDragged(int mouseX, int mouseY, int button) {}

    /** @return true when the widget consumed the wheel event. */
    public boolean mouseScrolled(int mouseX, int mouseY, int delta) {
        return false;
    }

    public boolean keyTyped(char typedChar, int keyCode) {
        return false;
    }

    protected static Minecraft mc() {
        return Minecraft.getMinecraft();
    }
}

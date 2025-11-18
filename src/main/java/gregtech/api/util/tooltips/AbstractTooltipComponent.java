package gregtech.api.util.tooltips;

public abstract class AbstractTooltipComponent implements ITooltipComponent {
    protected final boolean condition;

    public AbstractTooltipComponent(boolean condition) {
        this.condition = condition;
    }

    public AbstractTooltipComponent() {
        this(true); // 默认总是显示
    }
}

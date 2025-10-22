package gtqt.common;

// 定义难度枚举
public enum Difficulty {
    EASY(0, "简单"),
    NORMAL(1, "普通"),
    HARD(2, "困难");

    private final int level;
    private final String displayName;

    Difficulty(int level, String displayName) {
        this.level = level;
        this.displayName = displayName;
    }

    public int getLevel() {
        return level;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static Difficulty fromLevel(int level) {
        for (Difficulty diff : values()) {
            if (diff.level == level) {
                return diff;
            }
        }
        return EASY; // 默认值
    }
}

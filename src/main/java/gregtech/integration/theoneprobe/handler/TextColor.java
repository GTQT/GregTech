package gregtech.integration.theoneprobe.handler;

import mcjty.theoneprobe.api.TextStyleClass;

public class TextColor {
    /**
     * 获取温度进度条颜色
     *
     * @param currentTemp 当前温度
     * @param maxTemp     最大温度
     * @return RGB颜色值
     */
    public static int getTemperatureProgressColor(int currentTemp, int maxTemp) {
        if (maxTemp <= 0) return 0xFF00FF00; // 默认绿色

        float ratio = currentTemp / (float) maxTemp;

        if (ratio < 0.3) {
            return 0xFF00FF00; // 绿色
        } else if (ratio < 0.6) {
            return 0xFFFFFF00; // 黄色
        } else if (ratio < 0.8) {
            return 0xFFFFA500; // 橙色
        } else {
            return 0xFFFF0000; // 红色
        }
    }

    /**
     * 根据温度获取显示颜色
     * @param currentTemp 当前温度
     * @param maxTemp 最大温度
     * @return 颜色代码
     */
    public static String getTemperatureColor(int currentTemp, int maxTemp) {
        if (maxTemp <= 0) return TextStyleClass.INFO.toString();

        float ratio = currentTemp / (float) maxTemp;

        if (ratio < 0.6) {
            return TextStyleClass.OK.toString(); // 绿色 - 低温
        } else if (ratio < 0.8) {
            return TextStyleClass.WARNING.toString(); // 黄色 - 中等温度
        } else {
            return TextStyleClass.ERROR.toString(); // 红色 - 接近极限
        }
    }
}

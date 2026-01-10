package gregtech.api.capability;

public interface IHeatable {

    /**
     * 传输热量和温度
     * 这是主要的热量传输方法，处理热量从高温向低温的传导
     *
     * @param heatToTransfer 要传输的热量（单位：HU - Heat Unit）
     * @param sourceTemperature 热源温度（开尔文）
     * @return 实际接收的热量
     */
    long transferHeat(long heatToTransfer, int sourceTemperature);

    /**
     * 改变当前存储的热量
     *
     * @param heatToAdd 要添加的热量（单位：HU - Heat Unit）
     * @return 添加后的实际热量
     */
    long changeHeat(long heatToAdd);

    /**
     * 获取当前温度
     *
     * @return 当前温度（开尔文）
     */
    int getTemperature();

    /**
     * 设置当前温度
     *
     * @param temperature 要设置的温度（开尔文）
     */
    void setTemperature(int temperature);

    /**
     * 获取最大工作温度
     *
     * @return 最大温度（开尔文）
     */
    int getMaxTemperature();

    /**
     * 设置最大工作温度
     *
     * @param maxTemperature 最大温度（开尔文）
     */
    void setMaxTemperature(int maxTemperature);

    /**
     * 获取热量存储容量
     *
     * @return 最大可存储的热量（HU）
     */
    long getHeatCapacity();

    /**
     * 获取当前存储的热量
     *
     * @return 当前热量（HU）
     */
    long getHeatStored();

    /**
     * 是否可以接受热量
     *
     * @return 是否可以接受热量
     */
    boolean canAcceptHeat();

    /**
     * 是否可以输出热量
     *
     * @return 是否可以输出热量
     */
    default boolean canOutputHeat() {
        return false;
    }

    /**
     * 获取输入热流量（每秒）
     *
     * @return 输入热流量（HU/s）
     */
    default long getInputPerSec() {
        return 0L;
    }

    /**
     * 获取输出热流量（每秒）
     *
     * @return 输出热流量（HU/s）
     */
    default long getOutputPerSec() {
        return 0L;
    }

    /**
     * 默认实现 - 不进行任何热交互
     */
    IHeatable DEFAULT = new IHeatable() {
        @Override
        public long transferHeat(long heatToTransfer, int sourceTemperature) {
            return 0;
        }

        @Override
        public long changeHeat(long heatToAdd) {
            return 0;
        }

        @Override
        public int getTemperature() {
            return 293; // 室温
        }

        @Override
        public void setTemperature(int temperature) {
            // 默认实现什么都不做
        }

        @Override
        public int getMaxTemperature() {
            return 373; // 100°C
        }

        @Override
        public void setMaxTemperature(int maxTemperature) {
            // 默认实现什么都不做
        }

        @Override
        public long getHeatCapacity() {
            return 0;
        }

        @Override
        public long getHeatStored() {
            return 0;
        }

        @Override
        public boolean canAcceptHeat() {
            return false;
        }
    };
}

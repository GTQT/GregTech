package gregtech.api.capability;

/**
 * 量子计算机 (Quantum Computer) 的不确定性舱对外接口。
 * <p>
 * 舱内是 GT5U 式 4×4 矩阵平衡小游戏：玩家通过交换格子数值维持对称平衡，
 * 全部对称组平衡（{@link #isResolved()}）时量子计算机才允许产出算力。
 * 模式（1~5）由控制器依据结构层数设定，玩家不可自选。
 */
public interface IQCUncertaintyHatch {

    /**
     * 当前解析模式（1~5），由控制器在成型时写入。
     */
    int getUncertaintyMode();

    /**
     * 由控制器设置解析模式（结构层数决定：min(5, max(1, rackCount/3))）。
     * 模式改变时矩阵重新生成并立即重算状态。
     */
    void updateUncertaintyMode(int mode);

    /**
     * 矩阵是否完全平衡（status == 0）。false 时量子计算机无法产出算力。
     */
    boolean isResolved();
}

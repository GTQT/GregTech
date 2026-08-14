package gregtech.api.capability;

import com.github.bsideup.jabel.Desugar;

/**
 * 量子计算机 (Quantum Computer) 计算组件属性。
 * <p>
 * 由 {@link QCComponentRegistry} 依据矿辞（circuit + tier）解析得出：
 * <ul>
 * <li>{@code computation} —— 单件每 tick 产出的 CWU/t</li>
 * <li>{@code heatConstant} —— 满负载时每 tick 的产热需求（由水冷系统抵消）</li>
 * </ul>
 */
@Desugar
public record QCComponentStats(int computation, int heatConstant) {}

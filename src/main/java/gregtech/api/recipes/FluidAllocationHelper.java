package gregtech.api.recipes;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * Exact bipartite augmenting-path solver for assigning potentially overlapping fluid ingredient demands
 * (e.g. {@code NBTMatcher.ANY} alongside a specific {@code NBTCondition} on the same fluid) across fluid tanks/buckets
 * without order sensitivity.
 */
@ApiStatus.Internal
public final class FluidAllocationHelper {

    private FluidAllocationHelper() {}

    /**
     * Computes an exact allocation matrix {@code flow[m][n]} satisfying {@code sum_j flow[i][j] == demands[i]} for all
     * {@code 0 <= i < m} and {@code sum_i flow[i][j] <= supplies[j]} for all {@code 0 <= j < n}, using only pairs where
     * {@code compat[i][j]} is {@code true}. Returns {@code null} if and only if no valid allocation exists.
     */
    @Nullable
    public static long[][] solveBipartiteAllocation(@NotNull long[] demands,
                                                    @NotNull long[] supplies,
                                                    @NotNull boolean[][] compat) {
        int m = demands.length;
        int n = supplies.length;
        long[][] flow = new long[m][n];
        if (m == 0) {
            return flow;
        }

        long[] remDemand = demands.clone();
        long[] remSupply = supplies.clone();

        // Fast initial greedy pass in declaration order before residual augmentations
        for (int i = 0; i < m; i++) {
            long need = remDemand[i];
            for (int j = 0; j < n && need > 0L; j++) {
                if (!compat[i][j] || remSupply[j] <= 0L) continue;
                long take = Math.min(need, remSupply[j]);
                flow[i][j] += take;
                remSupply[j] -= take;
                need -= take;
            }
            remDemand[i] = need;
        }

        int[] prevUFromW = new int[n];
        int[] prevWFromU = new int[m];
        int[] queueU = new int[m];

        while (true) {
            Arrays.fill(prevUFromW, -1);
            Arrays.fill(prevWFromU, -1);
            int head = 0;
            int tail = 0;
            for (int i = 0; i < m; i++) {
                if (remDemand[i] > 0L) {
                    prevWFromU[i] = -2;
                    queueU[tail++] = i;
                }
            }
            if (tail == 0) {
                return flow;
            }

            int sinkW = -1;
            while (head < tail && sinkW < 0) {
                int u = queueU[head++];
                for (int w = 0; w < n; w++) {
                    if (!compat[u][w] || prevUFromW[w] != -1) continue;
                    prevUFromW[w] = u;
                    if (remSupply[w] > 0L) {
                        sinkW = w;
                        break;
                    }
                    for (int u2 = 0; u2 < m; u2++) {
                        if (prevWFromU[u2] == -1 && flow[u2][w] > 0L) {
                            prevWFromU[u2] = w;
                            queueU[tail++] = u2;
                        }
                    }
                }
            }

            if (sinkW < 0) {
                return null;
            }

            long aug = remSupply[sinkW];
            int w = sinkW;
            while (true) {
                int u = prevUFromW[w];
                int pw = prevWFromU[u];
                if (pw == -2) {
                    aug = Math.min(aug, remDemand[u]);
                    break;
                }
                aug = Math.min(aug, flow[u][pw]);
                w = pw;
            }

            remSupply[sinkW] -= aug;
            w = sinkW;
            while (true) {
                int u = prevUFromW[w];
                flow[u][w] += aug;
                int pw = prevWFromU[u];
                if (pw == -2) {
                    remDemand[u] -= aug;
                    break;
                }
                flow[u][pw] -= aug;
                w = pw;
            }
        }
    }
}

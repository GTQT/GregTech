package gregtech.client.renderer.scene;

import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Immutable spatial snapshot. Picking never queries a world or reads GPU memory. */
public final class PreviewPickIndex {
    private final Node root;

    public PreviewPickIndex(Map<BlockPos, AxisAlignedBB> boxes) {
        List<Node> leaves = new ArrayList<>();
        boxes.forEach((pos, box) -> leaves.add(new Node(pos.toImmutable(), box, null, null)));
        root = build(leaves);
    }

    private static Node build(List<Node> nodes) {
        if (nodes.isEmpty()) return null;
        if (nodes.size() == 1) return nodes.get(0);
        AxisAlignedBB bounds = nodes.get(0).box;
        for (Node node : nodes) bounds = bounds.union(node.box);
        double x = bounds.maxX - bounds.minX, y = bounds.maxY - bounds.minY, z = bounds.maxZ - bounds.minZ;
        int axis = x >= y && x >= z ? 0 : y >= z ? 1 : 2;
        nodes.sort(Comparator.comparingDouble(n -> axis == 0 ? n.box.minX : axis == 1 ? n.box.minY : n.box.minZ));
        int mid = nodes.size() / 2;
        return new Node(null, bounds, build(nodes.subList(0, mid)), build(nodes.subList(mid, nodes.size())));
    }

    public RayTraceResult trace(Vec3d start, Vec3d end, Predicate<BlockPos> visible) {
        return trace(root, start, end, visible);
    }

    private static RayTraceResult trace(Node node, Vec3d start, Vec3d end, Predicate<BlockPos> visible) {
        if (node == null) return null;
        RayTraceResult intersection = node.box.calculateIntercept(start, end);
        if (intersection == null && !node.box.contains(start)) return null;
        if (node.pos != null) {
            return intersection != null && visible.test(node.pos)
                    ? new RayTraceResult(intersection.hitVec, intersection.sideHit, node.pos) : null;
        }
        RayTraceResult left = trace(node.left, start, end, visible);
        RayTraceResult right = trace(node.right, start, left == null ? end : left.hitVec, visible);
        return right == null ? left : right;
    }

    private static final class Node {
        final BlockPos pos;
        final AxisAlignedBB box;
        final Node left, right;

        Node(BlockPos pos, AxisAlignedBB box, Node left, Node right) {
            this.pos = pos;
            this.box = box;
            this.left = left;
            this.right = right;
        }
    }
}

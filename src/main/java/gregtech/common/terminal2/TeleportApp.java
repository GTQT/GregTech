package gregtech.common.terminal2;

import gregtech.api.GTValues;
import gregtech.api.terminal2.ITerminalApp;
import gregtech.api.terminal2.Terminal2Theme;
import gregtech.api.util.TeleportHandler;
import gregtech.common.ConfigHolder;
import gregtech.common.entities.PortalEntity;
import gregtech.common.mui.widget.GTTextFieldWidget;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.Chunk;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.factory.HandGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;

import java.util.function.Consumer;

/**
 * Opens a linked pair of portals: one a few blocks in front of the player, and one at the chosen
 * coordinates, so stepping through either sends the player to the other. A port of the mui1
 * teleporter app.
 * <p>
 * Because this can put a player anywhere in any dimension, the whole app sits behind
 * {@link ConfigHolder.ClientOptions#enableTeleporter}, which the server re-checks on every request.
 */
public class TeleportApp implements ITerminalApp {

    private static final int SYNC_LAST_DESTINATION = 1;
    private static final int SPAWN_PORTAL = 2;

    private static final int ROW_HEIGHT = 22;
    private static final int FIELD_WIDTH = 90;
    private static final int MIN_COORDINATE = -30_000_000;
    private static final int MAX_COORDINATE = 30_000_000;

    @Override
    public IWidget buildWidgets(HandGuiData guiData, PanelSyncManager guiSyncManager, UISettings settings,
                                ModularPanel panel) {
        var teleportHandler = new TeleportSyncHandler();
        guiSyncManager.syncValue("teleporter", teleportHandler);

        boolean enabled = ConfigHolder.client.enableTeleporter;

        // The handler fills these in once the last destination arrives from the server.
        var x = coordinateField(MIN_COORDINATE, MAX_COORDINATE, 9);
        var y = coordinateField(1, 255, 3);
        var z = coordinateField(MIN_COORDINATE, MAX_COORDINATE, 9);
        var dimension = coordinateField(Short.MIN_VALUE, Short.MAX_VALUE, 6);
        teleportHandler.setDestinationListener(destination -> {
            x.setText(String.valueOf(destination.getX()));
            y.setText(String.valueOf(destination.getY()));
            z.setText(String.valueOf(destination.getZ()));
        });

        var engage = new ButtonWidget<>()
                .overlay(IKey.lang("terminal.teleporter.spawn_portal"))
                .size(80, 18)
                .setEnabledIf(widget -> enabled)
                .onMousePressed(mouseButton -> {
                    teleportHandler.syncToServer(SPAWN_PORTAL, buf -> {
                        buf.writeInt(numberOf(x, 0));
                        buf.writeInt(numberOf(y, 1));
                        buf.writeInt(numberOf(z, 0));
                        buf.writeInt(numberOf(dimension, 0));
                    });
                    return true;
                });
        if (!enabled) {
            engage.addTooltipLine(IKey.lang("terminal.teleporter.disabled"));
        }

        var content = Flow.column()
                .crossAxisAlignment(Alignment.CrossAxis.START)
                .widthRel(0.95F)
                .child(IKey.lang("terminal.teleporter.title").asWidget())
                .child(field("X: ", x))
                .child(field("Y: ", y))
                .child(field("Z: ", z))
                .child(field("terminal.teleporter.dimension", dimension))
                .child(Flow.row()
                        .widthRel(1.0F)
                        .height(ROW_HEIGHT + 6)
                        .child(engage.center()));

        return new ParentWidget<>()
                .sizeRel(0.98F)
                .posRel(0.5F, 0.5F)
                .background(Terminal2Theme.COLOR_BACKGROUND_1)
                .child(content);
    }

    @Override
    public IDrawable getIcon() {
        return UITexture.fullImage(GTValues.MODID, "textures/gui/terminal/teleport/icon.png");
    }

    private static GTTextFieldWidget coordinateField(int min, int max, int maxLength) {
        return new GTTextFieldWidget()
                .setNumbers(min, max)
                .setMaxLength(maxLength);
    }

    private static int numberOf(GTTextFieldWidget field, int fallback) {
        String text = field.getText();
        if (text.isEmpty() || text.equals("-")) return fallback;
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static IWidget field(String label, GTTextFieldWidget widget) {
        return Flow.row()
                .sizeRel(1.0F, 0.0F)
                .height(ROW_HEIGHT)
                .child(IKey.lang(label).asWidget())
                .child(widget
                        .size(FIELD_WIDTH, 16)
                        .posRel(Alignment.CenterRight));
    }

    /** Owns the last destination, mirrors it to the client, and spawns the portal pair on request. */
    private static class TeleportSyncHandler extends SyncHandler {

        private BlockPos lastTeleport;
        private int lastDimension;
        private Consumer<BlockPos> destinationListener;

        void setDestinationListener(Consumer<BlockPos> listener) {
            this.destinationListener = listener;
        }

        @Override
        public void detectAndSendChanges(boolean init) {
            super.detectAndSendChanges(init);
            if (!init) return;

            if (!getSyncManager().isClient()) {
                // Default to wherever the player is standing, so the fields open on something sensible.
                if (lastTeleport == null) {
                    var player = getSyncManager().getPlayer();
                    lastTeleport = player.getPosition();
                    lastDimension = player.dimension;
                }
                syncToClient(SYNC_LAST_DESTINATION, buf -> {
                    buf.writeLong(lastTeleport.toLong());
                    buf.writeInt(lastDimension);
                });
            } else {
                syncToServer(SYNC_LAST_DESTINATION);
            }
        }

        @Override
        public void readOnClient(int id, PacketBuffer buf) {
            if (id != SYNC_LAST_DESTINATION) return;
            lastTeleport = BlockPos.fromLong(buf.readLong());
            lastDimension = buf.readInt();
            if (destinationListener != null) {
                destinationListener.accept(lastTeleport);
            }
        }

        @Override
        public void readOnServer(int id, PacketBuffer buf) {
            if (id == SYNC_LAST_DESTINATION) {
                if (lastTeleport == null) {
                    var player = getSyncManager().getPlayer();
                    lastTeleport = player.getPosition();
                    lastDimension = player.dimension;
                }
                syncToClient(SYNC_LAST_DESTINATION, out -> {
                    out.writeLong(lastTeleport.toLong());
                    out.writeInt(lastDimension);
                });
                return;
            }
            if (id != SPAWN_PORTAL) return;
            // Re-checked on the server so a modified client cannot skip the config gate.
            if (!ConfigHolder.client.enableTeleporter) return;

            int x = buf.readInt();
            int y = buf.readInt();
            int z = buf.readInt();
            int dimension = buf.readInt();

            if (spawnPortals(getSyncManager().getPlayer(), new BlockPos(x, y, z), dimension)) {
                lastTeleport = new BlockPos(x, y, z);
                lastDimension = dimension;
            }
        }

        /** Spawns the two linked portals. Returns false if the destination cannot be reached. */
        private boolean spawnPortals(EntityPlayer player, BlockPos destination, int dimension) {
            var destinationWorld = TeleportHandler.getWorldByDimensionID(dimension);
            if (destinationWorld == null) return false;

            Vec3d origin = new Vec3d(
                    player.getPosition().getX() + player.getLookVec().x * 5,
                    player.getPosition().getY(),
                    player.getPosition().getZ() + player.getLookVec().z * 5);

            var originPortal = new PortalEntity(player.getEntityWorld(), origin.x, origin.y, origin.z);
            originPortal.setRotation(player.rotationYaw, 0F);
            originPortal.setTargetCoordinates(dimension, destination.getX(), destination.getY(), destination.getZ());

            var destinationPortal = new PortalEntity(destinationWorld, destination.getX(), destination.getY(),
                    destination.getZ());
            destinationPortal.setRotation(player.rotationYaw, 0F);
            destinationPortal.setTargetCoordinates(player.dimension, origin.x, origin.y, origin.z);

            player.getEntityWorld().spawnEntity(originPortal);
            // The destination chunk may not be loaded, so load it long enough to accept the entity.
            Chunk destinationChunk = destinationWorld.getChunkProvider()
                    .provideChunk(destination.getX() >> 4, destination.getZ() >> 4);
            destinationWorld.spawnEntity(destinationPortal);
            destinationWorld.getChunkProvider().queueUnload(destinationChunk);
            return true;
        }
    }
}

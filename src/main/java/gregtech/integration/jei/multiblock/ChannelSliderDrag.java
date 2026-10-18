package gregtech.integration.jei.multiblock;

import java.util.function.BiConsumer;

/** A captured slider gesture. Only release publishes the pending slider index. */
public final class ChannelSliderDrag {
    private int channel = -1;
    private int trackX;
    private int trackWidth;
    private int steps;
    private int pendingIndex;

    public void begin(int channel, int trackX, int trackWidth, int steps, int mouseX) {
        this.channel = channel;
        this.trackX = trackX;
        this.trackWidth = Math.max(1, trackWidth);
        this.steps = Math.max(0, steps);
        move(mouseX);
    }

    public boolean isActive() {
        return channel >= 0;
    }

    public int indexFor(int channel, int committedIndex) {
        return this.channel == channel ? pendingIndex : committedIndex;
    }

    public void update(int mouseX, boolean held, BiConsumer<Integer, Integer> commit) {
        if (!isActive()) return;
        move(mouseX);
        if (!held) {
            int releasedChannel = channel;
            cancel();
            commit.accept(releasedChannel, pendingIndex);
        }
    }

    private void move(int mouseX) {
        float fraction = Math.max(0, Math.min(1, (mouseX - trackX) / (float) trackWidth));
        pendingIndex = Math.round(fraction * steps);
    }

    public void cancel() {
        channel = -1;
    }
}

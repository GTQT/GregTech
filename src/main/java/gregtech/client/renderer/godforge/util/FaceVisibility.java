package gregtech.client.renderer.godforge.util;

public class FaceVisibility {

    public boolean top = true;
    public boolean bottom = true;
    public boolean front = true;
    public boolean back = true;
    public boolean left = true;
    public boolean right = true;

    public boolean isEntireObscured() {
        return !top && !bottom && !front && !back && !left && !right;
    }
}

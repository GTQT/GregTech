package gregtech.common.misc;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.UUID;

public final class GlobalVariableStorage {

    private GlobalVariableStorage() {}

    public static final HashMap<UUID, BigInteger> GlobalEnergy = new HashMap<>(100, 0.9f);
}

package gregtech.api.util;

import lombok.experimental.UtilityClass;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import gregtech.SCValues;

@UtilityClass
public final class SCLog {

    public static Logger logger = LogManager.getLogger(SCValues.MODID);
}

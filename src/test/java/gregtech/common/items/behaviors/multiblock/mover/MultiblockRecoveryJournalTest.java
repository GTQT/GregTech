package gregtech.common.items.behaviors.multiblock.mover;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiblockRecoveryJournalTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void writesVerifiedCompressedNbtAndRemovesTemporaryFile() throws Exception {
        NBTTagCompound expected = new NBTTagCompound();
        expected.setInteger("Version", 1);
        expected.setString("Marker", "multiblock-mover-journal");
        File target = temporaryDirectory.resolve("transaction.dat").toFile();

        MultiblockRecoveryJournal.writeAtomic(target, expected);

        assertTrue(target.isFile());
        assertFalse(temporaryDirectory.resolve("transaction.dat.tmp").toFile().exists());
        try (FileInputStream input = new FileInputStream(target)) {
            assertEquals(expected, CompressedStreamTools.readCompressed(input));
        }
    }
}


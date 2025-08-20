// VoidGenerator.java
package n.plugins.NewMultiverso;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;

import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Gerador VOID compatível com 1.7.10 (usa API antiga baseada em byte[]).
 * Gera um bloco de GRAMA no chunk (0,0) na altura Y=64 para não cair no vazio.
 */
@SuppressWarnings("deprecation")
public class VoidGenerator extends ChunkGenerator {

    @Override
    public List<BlockPopulator> getDefaultPopulators(World world) {
        return Collections.emptyList();
    }

    /**
     * 1.7.10 ainda chama este método em alguns servidores/mods.
     * Retorna seções vazias (void).
     */
    @Override
    public byte[][] generateBlockSections(World world, Random random, int chunkX, int chunkZ, BiomeGrid biome) {
        // cria array de seções vazio
        byte[][] result = new byte[world.getMaxHeight() / 16][];
        // garante um chãozinho no chunk 0,0 (y=64)
        if (chunkX == 0 && chunkZ == 0) {
            // Inicializa a seção onde y=64 fica (64/16 = seção 4)
            result[4] = new byte[4096];
            int x = 0, y = 64, z = 0;
            int index = ((y & 0xF) << 8) | (z << 4) | x; // dentro da seção
            result[4][index] = (byte) Material.GRASS.getId(); // id 2 em 1.7.10
        }
        return result;
    }

    /**
     * Alguns forks antigos usam este método.
     */
    public byte[] generate(World world, Random random, int chunkX, int chunkZ) {
        byte[] result = new byte[32768]; // 16*16*128
        if (chunkX == 0 && chunkZ == 0) {
            int x = 0, y = 64, z = 0;
            int index = y * 256 + z * 16 + x;
            result[index] = (byte) Material.GRASS.getId();
        }
        return result;
    }
}

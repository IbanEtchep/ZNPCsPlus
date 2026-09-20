package lol.pyr.znpcsplus.tasks;

import lol.pyr.znpcsplus.npc.NpcEntryImpl;
import lol.pyr.znpcsplus.npc.NpcImpl;
import lol.pyr.znpcsplus.npc.NpcRegistryImpl;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

// heals clients that silently lost an npc (Viewable has no way to detect that) without needing a plugin reload
public class NpcResyncTask extends BukkitRunnable {
    private final NpcRegistryImpl npcRegistry;

    public NpcResyncTask(NpcRegistryImpl npcRegistry) {
        this.npcRegistry = npcRegistry;
    }

    @Override
    public void run() {
        for (NpcEntryImpl entry : npcRegistry.getProcessable()) {
            NpcImpl npc = entry.getNpc();
            if (!npc.isEnabled()) continue;
            for (Player viewer : npc.getViewers()) npc.respawn(viewer);
        }
    }
}

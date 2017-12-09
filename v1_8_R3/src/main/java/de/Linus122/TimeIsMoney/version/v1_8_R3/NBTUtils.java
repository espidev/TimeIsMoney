package de.Linus122.TimeIsMoney.version.v1_8_R3;

import de.Linus122.TimeIsMoney.tools.ActionBarUtils;
import net.minecraft.server.v1_8_R3.IChatBaseComponent;
import net.minecraft.server.v1_8_R3.PacketPlayOutChat;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;

import static de.Linus122.TimeIsMoney.tools.Utils.CC;

/**
 * NBT Utils for v1_8_R3.
 *
 * @author Linus122
 * @since 1.9.6.1
 */
public class NBTUtils implements ActionBarUtils {
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendActionBarMessage(Player p, String message) {
		IChatBaseComponent icbc = IChatBaseComponent.ChatSerializer.a("{\"text\": \"" + CC(message) + "\"}");
		PacketPlayOutChat bar = new PacketPlayOutChat(icbc, (byte) 2);
		((CraftPlayer) p).getHandle().playerConnection.sendPacket(bar);
	}
}

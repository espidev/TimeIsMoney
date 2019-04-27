package de.Linux122.TimeIsMoney.version.v1_14_R1;

import de.Linus122.TimeIsMoney.tools.ActionBarUtils;
import net.minecraft.server.v1_14_R1.ChatMessageType;
import net.minecraft.server.v1_14_R1.IChatBaseComponent;
import net.minecraft.server.v1_14_R1.PacketPlayOutChat;
import org.bukkit.craftbukkit.v1_14_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;

import static de.Linus122.TimeIsMoney.tools.Utils.CC;

/**
 * NBT Utils for v1_13_R1.
 *
 * @author Linus122
 * @since 1.9.6.4
 */
public class NBTUtils implements ActionBarUtils {
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendActionBarMessage(Player p, String message) {
		IChatBaseComponent icbc = IChatBaseComponent.ChatSerializer.a("{\"text\": \"" + CC(message) + "\"}");
		PacketPlayOutChat bar = new PacketPlayOutChat(icbc, ChatMessageType.GAME_INFO);
		((CraftPlayer) p).getHandle().playerConnection.sendPacket(bar);
	}
}

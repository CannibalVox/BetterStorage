package net.mcft.copy.betterstorage.network.packet;

import io.netty.channel.ChannelHandlerContext;

import java.io.IOException;

import net.mcft.copy.betterstorage.network.AbstractPacket;
import net.mcft.copy.betterstorage.utils.PlayerUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.PacketBuffer;

/** Opens a BetterStorage GUI on the client. */
public class PacketOpenGui extends AbstractPacket {
	
	public int windowId;
	public String name;
	public int columns;
	public int rows;
	public String title;
	
	public PacketOpenGui() {  }
	public PacketOpenGui(int windowId, String name, int columns, int rows, String title) {
		this.windowId = windowId;
		this.name = name;
		this.columns = columns;
		this.rows = rows;
		this.title = title;
	}
	
	@Override
	public void encode(ChannelHandlerContext context, PacketBuffer buffer) throws IOException {
		buffer.writeInt(windowId);
		buffer.writeStringToBuffer(name);
		buffer.writeByte(columns);
		buffer.writeByte(rows);
		buffer.writeStringToBuffer(title);
	}
	
	@Override
	public void decode(ChannelHandlerContext context, PacketBuffer buffer) throws IOException {
		windowId = buffer.readInt();
		name     = buffer.readStringFromBuffer(256);
		columns  = buffer.readByte();
		rows     = buffer.readByte();
		title    = buffer.readStringFromBuffer(256);
	}
	
	@Override
	public void handleClientSide(EntityPlayer player) {
		PlayerUtils.openGui(player, name, columns, rows, title);
		player.openContainer.windowId = windowId;
	}
	
}

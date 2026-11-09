package com.nisovin.shopkeepers.container;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.inventory.Inventory;

import com.nisovin.shopkeepers.util.bukkit.BlockUtils;
import com.nisovin.shopkeepers.util.inventory.ItemUtils;
import com.nisovin.shopkeepers.util.java.Validate;

/**
 * Utilities related to shop containers.
 */
public class ShopContainers {

	private ShopContainers() {
	}

	public static boolean isAccessible(Block block) {
		if (!block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)
				|| !Bukkit.isOwnedByCurrentRegion(block)) {
			return false;
		}

		Block connectedChest = BlockUtils.getConnectedChestBlock(block);
		return connectedChest == null
				|| (connectedChest.getWorld().isChunkLoaded(
						connectedChest.getX() >> 4, connectedChest.getZ() >> 4)
						&& Bukkit.isOwnedByCurrentRegion(connectedChest));
	}

	/**
	 * Checks if the given material is a supported shop container.
	 * 
	 * @param material
	 *            the material
	 * @return <code>true</code> if the material is a supported shop container
	 */
	public static boolean isSupportedContainer(Material material) {
		return ItemUtils.isChest(material)
				|| material == Material.BARREL
				|| ItemUtils.isShulkerBox(material);
	}

	/**
	 * Gets the {@link Inventory} of a supported type of shop container block.
	 * <p>
	 * For double chests this returns the complete double chest inventory.
	 * <p>
	 * The returned inventory is directly backed by the container in the world and any changes to
	 * the inventory are therefore directly reflected.
	 * 
	 * @param containerBlock
	 *            the container block
	 * @return the inventory, not <code>null</code>
	 */
	public static Inventory getInventory(Block containerBlock) {
		Validate.notNull(containerBlock, "containerBlock is null");
		Validate.isTrue(isSupportedContainer(containerBlock.getType()),
				() -> "containerBlock is of unsupported type: " + containerBlock.getType());
		BlockState state = containerBlock.getState();
		assert state instanceof Container;
		Container container = (Container) state;
		// Note: For double chests this returns the complete double chest inventory.
		return container.getInventory(); // Not null
	}
}

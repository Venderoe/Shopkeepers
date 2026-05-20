package com.nisovin.shopkeepers.testutil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Server;
import org.bukkit.Tag;
import org.bukkit.craftbukkit.CraftRegistry;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.bukkit.craftbukkit.inventory.CraftInventoryCustom;
import org.bukkit.craftbukkit.inventory.CraftItemFactory;
import org.bukkit.craftbukkit.tag.CraftBlockTag;
import org.bukkit.craftbukkit.tag.CraftDamageTag;
import org.bukkit.craftbukkit.tag.CraftEntityTag;
import org.bukkit.craftbukkit.tag.CraftFluidTag;
import org.bukkit.craftbukkit.tag.CraftItemTag;
import org.bukkit.craftbukkit.util.CraftMagicNumbers;
import org.bukkit.craftbukkit.util.CraftNamespacedKey;
import org.bukkit.craftbukkit.util.Versioning;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.tag.DamageTypeTags;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.nisovin.shopkeepers.api.internal.util.Unsafe;
import com.nisovin.shopkeepers.util.java.Validate;

import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup.RegistryLookup;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry.PendingTags;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;

/**
 * Mocks the Server (at least the functions required for our tests).
 * <p>
 * Adopted from CraftBukkit: See AbstractTestingBase and DummyServer.
 */
class ServerMock extends ProxyHandler<Server> {

	private static final RegistryAccess.Frozen REGISTRY_CUSTOM;

	// Static initializer: Ensures that this is only setup once across all tests.
	static {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		// Populate available packs:
		PackRepository packRepository = ServerPacksSource.createVanillaTrustedRepository();
		packRepository.reload();
		// Set up resource manager:
		MultiPackResourceManager resourceManager = new MultiPackResourceManager(
				PackType.SERVER_DATA,
				packRepository.getAvailablePacks().stream().map(Pack::open).toList()
		);
		LayeredRegistryAccess<RegistryLayer> layers = Unsafe.castNonNull(RegistryLayer.createRegistryAccess());
		List<PendingTags<?>> tags = Unsafe.castNonNull(TagLoader.loadTagsForExistingRegistries(
				resourceManager,
				layers.getLayer(RegistryLayer.STATIC)
		));
		RegistryAccess.Frozen worldgenAccess = layers.getAccessForLoading(RegistryLayer.WORLDGEN);
		List<RegistryLookup<?>> lookups = Unsafe.castNonNull(
				TagLoader.buildUpdatedLookups(worldgenAccess, Unsafe.castNonNull(tags))
		);
		RegistryAccess.Frozen loadedWorldgen = RegistryDataLoader.load(
				resourceManager,
				Unsafe.castNonNull(lookups),
				RegistryDataLoader.WORLDGEN_REGISTRIES,
				Runnable::run
		).join();
		layers = Unsafe.castNonNull(
				layers.replaceFrom(RegistryLayer.WORLDGEN, loadedWorldgen)
		);
		REGISTRY_CUSTOM = Unsafe.castNonNull(layers.compositeAccess().freeze());

		// Set up the server mock as Bukkit API provider:
		Server serverMock = new ServerMock().newProxy();
		Bukkit.setServer(serverMock);

		CraftRegistry.setMinecraftRegistry(REGISTRY_CUSTOM);
	}

	// Calling this method ensures that the static initializer is invoked.
	public static void setup() {
	}

	private final Map<Class<?>, Registry<?>> registers = new HashMap<>();

	private ServerMock() {
		super(Server.class);
	}

	@Override
	protected void setupMethodHandlers() throws Exception {
		this.addHandler(Server.class.getMethod("getItemFactory"), (proxy, args) -> {
			return Unsafe.assertNonNull(CraftItemFactory.instance());
		});

		this.addHandler(Server.class.getMethod("getRegistry", Class.class), (proxy, args) -> {
			assert args != null;
			Class<? extends Keyed> clazz = Unsafe.castNonNull(args[0]);
			return registers.computeIfAbsent(
					clazz,
					key -> io.papermc.paper.registry.RegistryAccess.registryAccess().getRegistry(clazz)
			);
		});

		this.addHandler(Server.class.getMethod("getName"), (proxy, args) -> {
			return ServerMock.class.getName();
		});

		this.addHandler(Server.class.getMethod("getVersion"), (proxy, args) -> {
			Package pkg = Unsafe.assertNonNull(ServerMock.class.getPackage());
			return pkg.getImplementationVersion();
		});

		this.addHandler(Server.class.getMethod("getBukkitVersion"), (proxy, args) -> {
			return Versioning.getBukkitVersion();
		});

		final Logger logger = Logger.getLogger(ServerMock.class.getCanonicalName());
		this.addHandler(Server.class.getMethod("getLogger"), (proxy, args) -> {
			return logger;
		});

		this.addHandler(Server.class.getMethod("getUnsafe"), (proxy, args) -> {
			return CraftMagicNumbers.INSTANCE;
		});

		this.addHandler(
				Server.class.getMethod("createBlockData", Material.class),
				(proxy, args) -> {
					Validate.notNull(args, "args is null");
					assert args != null;
					Material material = Unsafe.castNonNull(args[0]);
					return CraftBlockData.fromString(material.asBlockType(), Unsafe.uncheckedNull());
				}
		);

		this.addHandler(
				Server.class.getMethod("createInventory", InventoryHolder.class, int.class),
				(proxy, args) -> {
					Validate.notNull(args, "args is null");
					assert args != null;
					@Nullable InventoryHolder holder = Unsafe.cast(args[0]);
					int size = Unsafe.cast(args[1]);
					return new CraftInventoryCustom(holder, size);
				}
		);

		this.addHandler(Server.class.getMethod("getTag", String.class, NamespacedKey.class, Class.class), (proxy, args) -> {
			assert args != null;
			String registry = Unsafe.castNonNull(args[0]);
			NamespacedKey bukkitKey = Unsafe.castNonNull(args[1]);
			Class<?> clazz = Unsafe.castNonNull(args[2]);

			Identifier key = CraftNamespacedKey.toMinecraft(bukkitKey);

			switch (registry) {
			case Tag.REGISTRY_BLOCKS -> {
				Validate.isTrue(clazz == Material.class, "Block registry requires Material.class");
				var tagKey = TagKey.create(Registries.BLOCK, key);
				if (BuiltInRegistries.BLOCK.get(tagKey).isPresent()) {
					return new CraftBlockTag(BuiltInRegistries.BLOCK, tagKey);
				}
			}
			case Tag.REGISTRY_ITEMS -> {
				Validate.isTrue(clazz == Material.class, "Item registry requires Material.class");
				var tagKey = TagKey.create(Registries.ITEM, key);
				if (BuiltInRegistries.ITEM.get(tagKey).isPresent()) {
					return new CraftItemTag(BuiltInRegistries.ITEM, tagKey);
				}
			}
			case Tag.REGISTRY_FLUIDS -> {
				Validate.isTrue(clazz == org.bukkit.Fluid.class, "Fluid registry requires Fluid.class");
				var tagKey = TagKey.create(Registries.FLUID, key);
				if (BuiltInRegistries.FLUID.get(tagKey).isPresent()) {
					return new CraftFluidTag(BuiltInRegistries.FLUID, tagKey);
				}
			}
			case Tag.REGISTRY_ENTITY_TYPES -> {
				Validate.isTrue(clazz == org.bukkit.entity.EntityType.class, "Entity registry requires EntityType.class");
				var tagKey = TagKey.create(Registries.ENTITY_TYPE, key);
				if (BuiltInRegistries.ENTITY_TYPE.get(tagKey).isPresent()) {
					return new CraftEntityTag(BuiltInRegistries.ENTITY_TYPE, tagKey);
				}
			}
			case DamageTypeTags.REGISTRY_DAMAGE_TYPES -> {
				Validate.isTrue(clazz == org.bukkit.damage.DamageType.class, "Damage type registry requires DamageType.class");
				var tagKey = TagKey.create(Registries.DAMAGE_TYPE, key);
				var damageRegistry = CraftRegistry.getMinecraftRegistry(Registries.DAMAGE_TYPE);
				if (damageRegistry.get(tagKey).isPresent()) {
					return new CraftDamageTag(damageRegistry, tagKey);
				}
			}
			default -> throw new IllegalArgumentException("Unknown registry: " + registry);
			}

			return null;
		});
	}
}

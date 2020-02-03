package com.simibubi.create;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.simibubi.create.foundation.block.IHaveColoredVertices;
import com.simibubi.create.foundation.block.connected.CTModel;
import com.simibubi.create.foundation.block.connected.IHaveConnectedTextures;
import com.simibubi.create.foundation.block.render.ColoredVertexModel;
import com.simibubi.create.foundation.block.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.block.render.SpriteShiftEntry;
import com.simibubi.create.foundation.item.IHaveCustomItemModel;
import com.simibubi.create.foundation.utility.SuperByteBufferCache;
import com.simibubi.create.modules.contraptions.base.KineticTileEntityRenderer;
import com.simibubi.create.modules.contraptions.components.contraptions.ContraptionRenderer;
import com.simibubi.create.modules.curiosities.partialWindows.WindowInABlockModel;
import com.simibubi.create.modules.schematics.ClientSchematicLoader;
import com.simibubi.create.modules.schematics.client.SchematicAndQuillHandler;
import com.simibubi.create.modules.schematics.client.SchematicHandler;
import com.simibubi.create.modules.schematics.client.SchematicHologram;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockModelShapes;
import net.minecraft.client.renderer.model.IBakedModel;
import net.minecraft.client.renderer.model.ModelResourceLocation;
import net.minecraft.resources.IReloadableResourceManager;
import net.minecraft.resources.IResourceManager;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public class CreateClient {

	public static ClientSchematicLoader schematicSender;
	public static SchematicHandler schematicHandler;
	public static SchematicHologram schematicHologram;
	public static SchematicAndQuillHandler schematicAndQuillHandler;
	public static SuperByteBufferCache bufferCache;
	public static int renderTicks;

	public static ModConfig config;

	public static void addListeners(IEventBus modEventBus) {
		DistExecutor.runWhenOn(Dist.CLIENT, () -> () -> {
			modEventBus.addListener(CreateClient::clientInit);
			modEventBus.addListener(CreateClient::createConfigs);
			modEventBus.addListener(CreateClient::onModelBake);
			modEventBus.addListener(CreateClient::onModelRegistry);
			modEventBus.addListener(CreateClient::onTextureStitch);
			modEventBus.addListener(AllParticles::registerFactories);
		});
	}

	public static void clientInit(FMLClientSetupEvent event) {
		schematicSender = new ClientSchematicLoader();
		schematicHandler = new SchematicHandler();
		schematicHologram = new SchematicHologram();
		schematicAndQuillHandler = new SchematicAndQuillHandler();

		bufferCache = new SuperByteBufferCache();
		bufferCache.registerCompartment(KineticTileEntityRenderer.KINETIC_TILE);
		bufferCache.registerCompartment(ContraptionRenderer.CONTRAPTION, 20);

		AllKeys.register();
		AllContainers.registerScreenFactories();
		AllTileEntities.registerRenderers();
		AllItems.registerColorHandlers();
		AllBlocks.registerColorHandlers();
		AllEntities.registerRenderers();

		IResourceManager resourceManager = Minecraft.getInstance().getResourceManager();
		if (resourceManager instanceof IReloadableResourceManager)
			((IReloadableResourceManager) resourceManager).addReloadListener(new ResourceReloadHandler());
	}

	public static void createConfigs(ModConfig.ModConfigEvent event) {
		if (event.getConfig().getSpec() == CreateConfig.specification)
			return;

		config = event.getConfig();
	}

	public static void gameTick() {
		schematicSender.tick();
		schematicAndQuillHandler.tick();
		schematicHandler.tick();
		schematicHologram.tick();
	}

	@OnlyIn(Dist.CLIENT)
	public static void onTextureStitch(TextureStitchEvent.Pre event) {
		if (!event.getMap().getBasePath().equals("textures"))
			return;

		event.addSprite(new ResourceLocation(Create.ID, "block/belt_animated"));
		for (AllBlocks allBlocks : AllBlocks.values()) {
			Block block = allBlocks.get();
			if (!(block instanceof IHaveConnectedTextures))
				continue;
			for (SpriteShiftEntry spriteShiftEntry : ((IHaveConnectedTextures) block).getBehaviour().getAllCTShifts())
				event.addSprite(spriteShiftEntry.getTargetResourceLocation());
		}
	}

	@OnlyIn(Dist.CLIENT)
	public static void onModelBake(ModelBakeEvent event) {
		Map<ResourceLocation, IBakedModel> modelRegistry = event.getModelRegistry();

		// Swap Models for CT Blocks and Blocks with colored Vertices
		for (AllBlocks allBlocks : AllBlocks.values()) {
			Block block = allBlocks.get();
			if (block == null)
				continue;

			List<ModelResourceLocation> blockModelLocations = getAllBlockStateModelLocations(allBlocks);
			if (block instanceof IHaveConnectedTextures)
				swapModels(modelRegistry, blockModelLocations, t -> new CTModel(t, (IHaveConnectedTextures) block));
			if (block instanceof IHaveColoredVertices)
				swapModels(modelRegistry, blockModelLocations,
						t -> new ColoredVertexModel(t, (IHaveColoredVertices) block));

		}

		// Swap Models for custom rendered item models
		for (AllItems item : AllItems.values()) {
			if (!(item.get() instanceof IHaveCustomItemModel))
				continue;

			IHaveCustomItemModel specialItem = (IHaveCustomItemModel) item.get();
			ModelResourceLocation location = new ModelResourceLocation(item.get().getRegistryName(), "inventory");
			CustomRenderedItemModel model = specialItem.createModel(modelRegistry.get(location));
			model.loadPartials(event);
			modelRegistry.put(location, model);
		}

		swapModels(modelRegistry, getAllBlockStateModelLocations(AllBlocks.WINDOW_IN_A_BLOCK),
				WindowInABlockModel::new);
		swapModels(modelRegistry,
				getBlockModelLocation(AllBlocks.WINDOW_IN_A_BLOCK,
						BlockModelShapes.getPropertyMapString(AllBlocks.WINDOW_IN_A_BLOCK.get().getDefaultState()
								.with(BlockStateProperties.WATERLOGGED, true).getValues())),
				WindowInABlockModel::new);
	}

	@OnlyIn(Dist.CLIENT)
	public static void onModelRegistry(ModelRegistryEvent event) {
		// Register submodels for custom rendered item models
		for (AllItems item : AllItems.values()) {
			if (!(item.get() instanceof IHaveCustomItemModel))
				continue;

			IHaveCustomItemModel specialItem = (IHaveCustomItemModel) item.get();
			CustomRenderedItemModel model = specialItem.createModel(null);
			model.getModelLocations().forEach(ModelLoader::addSpecialModel);
		}
	}

	@OnlyIn(Dist.CLIENT)
	protected static ModelResourceLocation getItemModelLocation(AllItems item) {
		return new ModelResourceLocation(item.get().getRegistryName(), "inventory");
	}

	@OnlyIn(Dist.CLIENT)
	protected static List<ModelResourceLocation> getAllBlockStateModelLocations(AllBlocks block) {
		List<ModelResourceLocation> models = new ArrayList<>();
		block.get().getStateContainer().getValidStates().forEach(state -> {
			models.add(getBlockModelLocation(block, BlockModelShapes.getPropertyMapString(state.getValues())));
		});
		return models;
	}

	@OnlyIn(Dist.CLIENT)
	protected static ModelResourceLocation getBlockModelLocation(AllBlocks block, String suffix) {
		return new ModelResourceLocation(block.block.getRegistryName(), suffix);
	}

	@OnlyIn(Dist.CLIENT)
	protected static <T extends IBakedModel> void swapModels(Map<ResourceLocation, IBakedModel> modelRegistry,
			ModelResourceLocation location, Function<IBakedModel, T> factory) {
		modelRegistry.put(location, factory.apply(modelRegistry.get(location)));
	}

	@OnlyIn(Dist.CLIENT)
	protected static <T extends IBakedModel> void swapModels(Map<ResourceLocation, IBakedModel> modelRegistry,
			List<ModelResourceLocation> locations, Function<IBakedModel, T> factory) {
		locations.forEach(location -> {
			swapModels(modelRegistry, location, factory);
		});
	}

}

package com.simibubi.create.content.contraptions.render;

import com.jozufozu.flywheel.api.instance.InstancerProvider;
import com.jozufozu.flywheel.api.visual.Visual;
import com.jozufozu.flywheel.api.visualization.VisualizationContext;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;

import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LightLayer;

public abstract class ActorVisual implements Visual {
    protected final VisualizationContext visualizationContext;
	protected final InstancerProvider instancerProvider;
	protected final BlockAndTintGetter simulationWorld;
	protected final MovementContext context;

	private boolean deleted;

    public ActorVisual(VisualizationContext visualizationContext, BlockAndTintGetter world, MovementContext context) {
        this.visualizationContext = visualizationContext;
		this.instancerProvider = visualizationContext.instancerProvider();
        this.simulationWorld = world;
        this.context = context;
    }

	public void tick() { }

    public void beginFrame() { }

    protected int localBlockLight() {
        return simulationWorld.getBrightness(LightLayer.BLOCK, context.localPos);
    }

	@Override
	public void update(float partialTick) {
	}

	@Override
	public boolean shouldReset() {
		return false;
	}

	protected abstract void _delete();

	@Override
	public final void delete() {
		if (deleted) {
			return;
		}

		_delete();
		deleted = true;
	}
}
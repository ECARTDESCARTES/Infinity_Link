package infinitylink.mc.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Bloc de réserve : cube de pierre, dureté 1,5 ; forme et collision par état depuis la table SGB2 (capacité « formes »,
 *  SageShapes), cube plein tant qu'elle est vide. dynamicShape : aucune forme mise en cache à l'enregistrement. */
public final class SageBlock extends Block {
    public static final IntegerProperty S=IntegerProperty.create("s",0,15);
    public SageBlock(Properties properties) { super(properties.strength(1.5f).dynamicShape()); registerDefaultState(stateDefinition.any().setValue(S,0)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b) { b.add(S); }
    private static int id(BlockState s) { return Block.BLOCK_STATE_REGISTRY.getId(s); }
    @Override protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        VoxelShape v = SageShapes.contour(id(s)); return v != null ? v : super.getShape(s, l, p, c);
    }
    @Override protected VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        VoxelShape v = SageShapes.collision(id(s)); return v != null ? v : super.getCollisionShape(s, l, p, c);
    }
    @Override protected VoxelShape getInteractionShape(BlockState s, BlockGetter l, BlockPos p) {
        VoxelShape v = SageShapes.contour(id(s)); return v != null ? v : super.getInteractionShape(s, l, p);
    }
    @Override protected VoxelShape getVisualShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        VoxelShape v = SageShapes.collision(id(s)); return v != null ? v : super.getVisualShape(s, l, p, c);
    }
}

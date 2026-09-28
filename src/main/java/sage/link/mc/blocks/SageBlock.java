package sage.link.mc.blocks;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** Squelette L3 : cube de pierre, dureté 1,5. Application de la table réservée à L5a. */
public final class SageBlock extends Block {
    public static final IntegerProperty S=IntegerProperty.create("s",0,15);
    public SageBlock(Properties properties) { super(properties.strength(1.5f).dynamicShape()); registerDefaultState(stateDefinition.any().setValue(S,0)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b) { b.add(S); }
}

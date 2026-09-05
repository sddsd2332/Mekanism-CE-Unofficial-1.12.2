package mekanism.common.tile.prefab;

/**
 * Compatibility alias for the public planner contract. New code should normally
 * import {@link mekanism.api.IAsyncMachinePlanner}.
 */
@Deprecated
public interface IAsyncMachinePlanner<SNAPSHOT, PLAN> extends mekanism.api.IAsyncMachinePlanner<SNAPSHOT, PLAN> {
}

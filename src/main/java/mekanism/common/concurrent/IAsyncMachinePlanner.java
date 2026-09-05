package mekanism.common.concurrent;

/** Compatibility alias for integrations which historically kept scheduler APIs here. */
@Deprecated
public interface IAsyncMachinePlanner<SNAPSHOT, PLAN> extends mekanism.api.IAsyncMachinePlanner<SNAPSHOT, PLAN> {
}

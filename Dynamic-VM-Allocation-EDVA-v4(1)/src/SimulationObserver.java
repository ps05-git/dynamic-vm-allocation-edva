/** Optional callbacks for external integrations; default simulation behavior is unchanged. */
public interface SimulationObserver {
    SimulationObserver NO_OP = new SimulationObserver() {};
    default void onAssigned(Job job, int vmId, int simulationTime, String reason) {}
    default void onPreempted(Job job, int vmId, int simulationTime, boolean cancelled) {}
    default void onCompleted(Job job, int vmId, int simulationTime) {}
}

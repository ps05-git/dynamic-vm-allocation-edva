import java.io.PrintWriter;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Maps simulated scheduler assignment decisions to real Windows process submissions via MiniCloud API. */
public final class CloudIntegrationObserver implements SimulationObserver {
    private final String algorithmName;
    private final MiniCloudClient cloud;
    private final PrintWriter log;
    private final Map<Integer, Integer> cloudInstancesBySimVm = new HashMap<>();
    private final Set<String> submittedJobIds = new HashSet<>();

    public CloudIntegrationObserver(String algorithmName, MiniCloudClient cloud, PrintWriter log) throws Exception {
        this.algorithmName = algorithmName;
        this.cloud = cloud;
        this.log = log;
        for (int vmId = 1; vmId <= 2; vmId++) {
            int cloudId = cloud.createActiveInstance(
                    algorithmName.toLowerCase() + "-vm-" + vmId,
                    "host-" + vmId);
            cloudInstancesBySimVm.put(vmId, cloudId);
            write("INSTANCE_CREATED", "-", vmId, "cloudInstance=" + cloudId + ";state=ACTIVE");
            System.out.printf("[MiniCloud] %s VM-%d -> simulated instance %d ACTIVE%n", algorithmName, vmId, cloudId);
        }
    }

    @Override
    public void onAssigned(Job job, int vmId, int simulationTime, String reason) {
        write("SCHEDULE_ASSIGN", job.getId(), vmId, "t=" + simulationTime + ";reason=" + clean(reason));
        // A resumed job is not relaunched: one real Windows process is submitted per logical job.
        if (submittedJobIds.add(job.getId())) {
            try {
                Integer cloudInstanceId = cloudInstancesBySimVm.get(vmId);
                if (cloudInstanceId == null) {
                    cloudInstanceId = cloud.createActiveInstance(algorithmName.toLowerCase() + "-vm-extra-" + vmId, "host-extra");
                    cloudInstancesBySimVm.put(vmId, cloudInstanceId);
                }
                int cloudJobId = cloud.submitJob(cloudInstanceId, job.getExecutionTime());
                write("PROCESS_SUBMITTED", job.getId(), vmId,
                        "t=" + simulationTime + ";cloudInstance=" + cloudInstanceId + ";cloudJob=" + cloudJobId + ";requestedSeconds=" + job.getExecutionTime());
                System.out.printf("[MiniCloud] %s job %s assigned at simulated t=%d -> real Windows process cloudJob=%d (%ds requested)%n",
                        algorithmName, job.getId(), simulationTime, cloudJobId, job.getExecutionTime());
            } catch (Exception e) {
                write("SUBMIT_ERROR", job.getId(), vmId, "t=" + simulationTime + ";error=" + clean(e.toString()));
                throw new RuntimeException("Could not submit job " + job.getId() + " to MiniCloud API", e);
            }
        }
    }

    @Override
    public void onPreempted(Job job, int vmId, int simulationTime, boolean cancelled) {
        write(cancelled ? "SCHEDULER_CANCEL" : "SCHEDULER_SUSPEND", job.getId(), vmId,
                "t=" + simulationTime + ";executionControl=SIMULATED_ONLY");
        System.out.printf("[%s simulation] %s job %s at t=%d. The already-submitted Windows process cannot be paused by this prototype.%n",
                algorithmName, cancelled ? "cancelled" : "suspended", job.getId(), simulationTime);
    }

    @Override
    public void onCompleted(Job job, int vmId, int simulationTime) {
        write("SCHEDULER_COMPLETE", job.getId(), vmId, "t=" + simulationTime);
    }

    public void close() { log.flush(); }

    private void write(String event, String jobId, int vmId, String detail) {
        log.printf("%s,%s,%s,%d,%s%n", Instant.now(), algorithmName, event, vmId,
                csv("job=" + jobId + ";" + detail));
        log.flush();
    }
    private static String clean(String s) { return s == null ? "" : s.replace(';', ':').replace('\n', ' '); }
    private static String csv(String s) { return "\"" + s.replace("\"", "\"\"") + "\""; }
}

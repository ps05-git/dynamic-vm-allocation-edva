import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Runs PAPER and EDVA schedulers and submits each first-time assignment as a real Windows process. */
public class CloudIntegrationMain {
    private static final String BASE_URL = System.getProperty("minicloud.url", "http://127.0.0.1:8080");
    private static final long SEED = 1L;
    private static final int JOB_COUNT = 3;

    public static void main(String[] args) throws Exception {
        System.out.println("========================================================");
        System.out.println("EDVA -> MINI-OPENSTACK API INTEGRATION (WINDOWS)");
        System.out.println("========================================================");
        System.out.println("API: " + BASE_URL);
        System.out.println("Scheduler decisions are simulated; each first assignment submits a real Windows process.");
        System.out.println("Preemption/suspend events are logged but do not pause an already-running Windows process.\n");

        Path dataDir = Path.of("data", "cloud-integration");
        Files.createDirectories(dataDir);
        Path logPath = dataDir.resolve("integration-events.csv");
        Path jobsPath = dataDir.resolve("cloud-jobs-final.json");
        MiniCloudClient cloud = new MiniCloudClient(BASE_URL);
        cloud.resetDemoState();
        System.out.println("MiniCloud in-memory records reset; this run will have isolated instance/job IDs.");
        List<Job> workload = createPolicyDifferenceWorkload();
        System.out.println("Using targeted 3-job workload to exercise different preemption choices:");
        for (Job job : workload) System.out.println("  " + job);

        try (PrintWriter log = new PrintWriter(Files.newBufferedWriter(logPath, StandardCharsets.UTF_8))) {
            log.println("timestamp,algorithm,event,sim_vm_id,details");
            runOne("PAPER", new PaperAlgorithm(), workload, cloud, log);
            runOne("EDVA", new ModifiedAlgorithm(), workload, cloud, log);
        }

        System.out.println("\nWaiting for submitted Windows processes to finish (up to 60 seconds)...");
        String latest = waitForJobs(cloud, Duration.ofSeconds(60));
        Files.writeString(jobsPath, latest + System.lineSeparator(), StandardCharsets.UTF_8);
        System.out.println("\nCloud job status JSON saved to: " + jobsPath.toAbsolutePath());
        System.out.println("Scheduler/API event log saved to: " + logPath.toAbsolutePath());
        System.out.println("\nIntegration run complete. The JSON contains only this run's jobs. Check SUCCEEDED/FAILED and exit_code values.");
    }


    /**
     * Deliberately targeted workload for demonstrating the policy difference.
     * At t=3, J3 has an earlier deadline than both running jobs and triggers
     * preemption. PAPER chooses the suspendable job with maximum remaining
     * execution (J1); EDVA chooses the one with greatest deadline slack (J2).
     * This is a demonstration workload, not a claim that EDVA always wins.
     */
    private static List<Job> createPolicyDifferenceWorkload() {
        List<Job> jobs = new ArrayList<>();
        jobs.add(new Job("J1", 0, 12, 20, 1, LeaseType.SUSPENDABLE));
        jobs.add(new Job("J2", 0, 8, 30, 2, LeaseType.SUSPENDABLE));
        jobs.add(new Job("J3", 3, 3, 10, 5, LeaseType.NON_PREEMPTABLE));
        return jobs;
    }

    private static void runOne(String name, AllocationAlgorithm algorithm, List<Job> original,
                               MiniCloudClient cloud, PrintWriter log) throws Exception {
        System.out.println("\n--- Running " + name + " scheduler on seed=" + SEED + ", jobs=" + JOB_COUNT + " ---");
        CloudIntegrationObserver observer = new CloudIntegrationObserver(name, cloud, log);
        Simulator simulator = new Simulator(copyJobs(original), algorithm, false, 2, observer);
        Simulator.SimulationResult result = simulator.run(true);
        result.printSummary(name + " SCHEDULER (SIMULATED TIME)");
        observer.close();
    }

    private static String waitForJobs(MiniCloudClient cloud, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        String latest = cloud.getJobsJson();
        Pattern pending = Pattern.compile("\\\"state\\\"\\s*:\\s*\\\"(?:QUEUED|RUNNING)\\\"");
        while (System.nanoTime() < deadline) {
            Matcher m = pending.matcher(latest);
            if (!m.find()) return latest;
            Thread.sleep(1000);
            latest = cloud.getJobsJson();
        }
        System.out.println("WARNING: polling timed out; some cloud jobs may still be running.");
        return latest;
    }

    private static List<Job> copyJobs(List<Job> source) {
        List<Job> copy = new ArrayList<>();
        for (Job j : source) {
            copy.add(new Job(j.getId(), j.getArrivalTime(), j.getExecutionTime(),
                    j.getDeadline(), j.getPriority(), j.getLeaseType()));
        }
        return copy;
    }
}

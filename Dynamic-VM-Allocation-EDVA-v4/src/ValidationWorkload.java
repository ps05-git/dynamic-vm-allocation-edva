import java.util.ArrayList;
import java.util.List;

/**
 * Small deterministic workloads used only to verify the paper rules before
 * running large random experiments.
 */
public class ValidationWorkload {

    /**
     * Test 1: CANCELLABLE is preferred over SUSPENDABLE.
     * J1/J2 occupy the two VMs. J3 has the earliest deadline.
     */
    public static List<Job> cancellablePreference() {
        List<Job> jobs = new ArrayList<>();
        jobs.add(new Job("J1", 0, 8, 40, 3, LeaseType.SUSPENDABLE));
        jobs.add(new Job("J2", 0, 8, 50, 4, LeaseType.CANCELLABLE));
        jobs.add(new Job("J3", 1, 3, 10, 1, LeaseType.SUSPENDABLE));
        return jobs;
    }

    /**
     * Test 2: when only suspendable candidates exist, choose maximum
     * remaining execution time.
     */
    public static List<Job> maximumRemaining() {
        List<Job> jobs = new ArrayList<>();
        jobs.add(new Job("J1", 0, 12, 40, 3, LeaseType.SUSPENDABLE));
        jobs.add(new Job("J2", 0, 6, 50, 4, LeaseType.SUSPENDABLE));
        jobs.add(new Job("J3", 1, 3, 10, 1, LeaseType.CANCELLABLE));
        return jobs;
    }

    /**
     * Test 3: non-preemptable jobs must never be selected.
     */
    public static List<Job> nonPreemptableProtection() {
        List<Job> jobs = new ArrayList<>();
        jobs.add(new Job("J1", 0, 8, 40, 3, LeaseType.NON_PREEMPTABLE));
        jobs.add(new Job("J2", 0, 8, 50, 4, LeaseType.SUSPENDABLE));
        jobs.add(new Job("J3", 1, 3, 10, 1, LeaseType.CANCELLABLE));
        return jobs;
    }

    /**
     * Test 4: resume a suspendable job after the high-priority job finishes.
     */
    public static List<Job> resumeSuspended() {
        List<Job> jobs = new ArrayList<>();
        jobs.add(new Job("J1", 0, 10, 50, 3, LeaseType.SUSPENDABLE));
        jobs.add(new Job("J2", 0, 5, 60, 4, LeaseType.SUSPENDABLE));
        jobs.add(new Job("J3", 1, 2, 10, 1, LeaseType.CANCELLABLE));
        return jobs;
    }
}

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Baseline implementation of the algorithm described in the supplied paper.
 *
 * Important source-derived rules:
 * 1. A smaller deadline means a higher-priority job.
 * 2. When all VMs are occupied, a newly arrived job is considered for
 *    priority preemption only when its deadline is smaller than the deadline
 *    of every currently running job (the condition in Algorithm 1).
 * 3. Preemption candidates are only CANCELLABLE or SUSPENDABLE.
 * 4. A candidate whose deadline is smaller than the new job's deadline is
 *    removed from the candidate set.
 * 5. CANCELLABLE is preferred over SUSPENDABLE.
 * 6. If multiple candidates remain in the preferred lease class, the paper's
 *    experiment selects the job with maximum remaining execution time.
 *
 * The paper includes an execution-threshold test but does not provide a
 * numeric threshold in the supplied article. Therefore the simulator keeps
 * that test configurable and disables it by default rather than inventing a
 * value and presenting it as a paper parameter.
 */
public class PaperAlgorithm implements AllocationAlgorithm {

    private final boolean useExecutionThreshold;
    private final int executionThreshold;

    public PaperAlgorithm() {
        this(false, Integer.MAX_VALUE);
    }

    public PaperAlgorithm(boolean useExecutionThreshold, int executionThreshold) {
        this.useExecutionThreshold = useExecutionThreshold;
        this.executionThreshold = executionThreshold;
    }

    /**
     * Algorithm 1 condition: the new job must have a smaller deadline than
     * every currently running job in the host.
     */
    @Override
    public boolean isHighPriority(Job newJob, List<Job> runningJobs, int currentTime) {
        if (runningJobs.isEmpty()) {
            return false;
        }

        for (Job running : runningJobs) {
            if (newJob.getDeadline() >= running.getDeadline()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Implements Procedure 1 from the paper.
     */
    @Override
    public Job selectPreemptionJob(Job newJob, List<Job> runningJobs, int currentTime) {
        List<Job> candidates = new ArrayList<>();

        // Lines 3-6: only suspendable or cancellable jobs enter candidate set.
        for (Job job : runningJobs) {
            if (job.getLeaseType() == LeaseType.CANCELLABLE
                    || job.getLeaseType() == LeaseType.SUSPENDABLE) {
                candidates.add(job);
            }
        }

        // Lines 8-11: do not preempt a job whose deadline is more urgent
        // than the arriving job's deadline.
        candidates.removeIf(job ->
                job.getDeadline() < newJob.getDeadline());

        // Lines 12-16: source contains an execution-threshold condition,
        // but no numeric value is specified in the article.
        if (useExecutionThreshold) {
            candidates.removeIf(job ->
                    job.getExecutionTime() > executionThreshold);
        }

        if (candidates.isEmpty()) {
            return null;
        }

        // The paper gives preference to cancellable leases because they may
        // be killed; suspendable leases should be resumed later.
        List<Job> cancellable = new ArrayList<>();
        List<Job> suspendable = new ArrayList<>();

        for (Job job : candidates) {
            if (job.getLeaseType() == LeaseType.CANCELLABLE) {
                cancellable.add(job);
            } else {
                suspendable.add(job);
            }
        }

        List<Job> preferred = !cancellable.isEmpty()
                ? cancellable
                : suspendable;

        // Paper experiment: when multiple eligible jobs remain, choose the
        // job with maximum remaining execution time.
        preferred.sort(
                Comparator.comparingInt(Job::getRemainingTime)
                          .reversed()
                          .thenComparingInt(Job::getDeadline)
                          .thenComparing(Job::getId)
        );

        return preferred.get(0);
    }
    @Override
    public String name() {
        return "PAPER";
    }

}

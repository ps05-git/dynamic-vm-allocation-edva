import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * EDVA v4 - Enhanced Deadline-aware VM Allocation.
 *
 * Fair-comparison constraint:
 * - Exactly the same two VMs are available to PAPER and EDVA.
 * - EDVA never provisions an additional VM in the primary experiment.
 *
 * Modification over the paper's candidate selection:
 * 1. Keep the paper's high-priority trigger (new deadline must be earlier
 *    than every currently running job).
 * 2. Exclude NON_PREEMPTABLE jobs.
 * 3. Preserve the paper's CANCELLABLE-over-SUSPENDABLE lease preference.
 * 4. Within the preferred lease class, select the job with the greatest
 *    deadline slack instead of the paper's maximum remaining execution.
 * 5. Use smaller remaining execution and earlier deadline as tie-breakers.
 *
 * Slack = deadline - currentTime - remainingExecution.
 * A larger slack means the running job has more time cushion before its
 * deadline and is therefore a safer preemption candidate.
 */
public class ModifiedAlgorithm implements AllocationAlgorithm {

    @Override
    public boolean isHighPriority(Job newJob, List<Job> runningJobs, int currentTime) {
        if (runningJobs.isEmpty()) {
            return false;
        }

        // Same priority trigger as the paper.
        for (Job running : runningJobs) {
            if (newJob.getDeadline() >= running.getDeadline()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Job selectPreemptionJob(Job newJob,
                                   List<Job> runningJobs,
                                   int currentTime) {
        List<Job> candidates = new ArrayList<>();

        // NON_PREEMPTABLE jobs are never candidates.
        for (Job job : runningJobs) {
            if (job.getLeaseType() == LeaseType.CANCELLABLE
                    || job.getLeaseType() == LeaseType.SUSPENDABLE) {
                // Do not preempt a job whose deadline is more urgent than the
                // arriving high-priority job.
                if (job.getDeadline() >= newJob.getDeadline()) {
                    candidates.add(job);
                }
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }

        // Preserve the paper's lease preference.
        List<Job> preferred = new ArrayList<>();
        for (Job job : candidates) {
            if (job.getLeaseType() == LeaseType.CANCELLABLE) {
                preferred.add(job);
            }
        }

        if (preferred.isEmpty()) {
            for (Job job : candidates) {
                if (job.getLeaseType() == LeaseType.SUSPENDABLE) {
                    preferred.add(job);
                }
            }
        }

        // EDVA improvement:
        // choose the candidate with the largest deadline slack rather than
        // the maximum remaining execution time used by the paper.
        preferred.sort(
                Comparator.comparingInt((Job job) -> job.getSlack(currentTime))
                        .reversed()
                        .thenComparingInt(Job::getRemainingTime)
                        .thenComparingInt(Job::getDeadline)
                        .thenComparing(Job::getId)
        );

        return preferred.get(0);
    }

    @Override
    public String name() {
        return "EDVA";
    }
}

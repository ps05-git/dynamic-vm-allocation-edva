import java.util.List;

public interface AllocationAlgorithm {
    boolean isHighPriority(Job newJob, List<Job> runningJobs, int currentTime);
    Job selectPreemptionJob(Job newJob, List<Job> runningJobs, int currentTime);
    String name();
}

public class Job {
    private final String id;
    private final int arrivalTime;
    private final int executionTime;
    private int remainingTime;
    private final int deadline;
    private final int priority;
    private final LeaseType leaseType;

    private JobStatus status = JobStatus.WAITING;
    private int startTime = -1;
    private int finishTime = -1;
    private int waitingTime = 0;
    private int preemptionCount = 0;
    private int vmId = -1;

    public Job(String id, int arrivalTime, int executionTime,
               int deadline, int priority, LeaseType leaseType) {
        this.id = id;
        this.arrivalTime = arrivalTime;
        this.executionTime = executionTime;
        this.remainingTime = executionTime;
        this.deadline = deadline;
        this.priority = priority;
        this.leaseType = leaseType;
    }

    public String getId() { return id; }
    public int getArrivalTime() { return arrivalTime; }
    public int getExecutionTime() { return executionTime; }
    public int getRemainingTime() { return remainingTime; }
    public int getDeadline() { return deadline; }
    public int getPriority() { return priority; }
    public LeaseType getLeaseType() { return leaseType; }
    public JobStatus getStatus() { return status; }
    public int getStartTime() { return startTime; }
    public int getFinishTime() { return finishTime; }
    public int getWaitingTime() { return waitingTime; }
    public int getPreemptionCount() { return preemptionCount; }
    public int getVmId() { return vmId; }

    public void setStatus(JobStatus status) { this.status = status; }
    public void setStartTime(int startTime) {
        if (this.startTime == -1) this.startTime = startTime;
    }
    public void setFinishTime(int finishTime) { this.finishTime = finishTime; }
    public void setVmId(int vmId) { this.vmId = vmId; }

    public void tick() {
        if (remainingTime > 0) remainingTime--;
    }

    public void addWaitingTime() {
        waitingTime++;
    }

    public void incrementPreemptionCount() {
        preemptionCount++;
    }

    public boolean isCompleted() {
        return remainingTime == 0;
    }

    public int getSlack(int currentTime) {
        return deadline - currentTime - remainingTime;
    }

    @Override
    public String toString() {
        return id +
                "{arrival=" + arrivalTime +
                ", exec=" + executionTime +
                ", remaining=" + remainingTime +
                ", deadline=" + deadline +
                ", priority=" + priority +
                ", lease=" + leaseType +
                ", status=" + status + "}";
    }
}

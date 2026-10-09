public class VM {
    private final int id;
    private final int hostId;
    private Job runningJob;

    public VM(int id, int hostId) {
        this.id = id;
        this.hostId = hostId;
    }

    public int getId() { return id; }
    public int getHostId() { return hostId; }
    public Job getRunningJob() { return runningJob; }

    public boolean isFree() {
        return runningJob == null;
    }

    public void assign(Job job) {
        runningJob = job;
        job.setVmId(id);
        job.setStatus(JobStatus.RUNNING);
    }

    public Job release() {
        Job old = runningJob;
        runningJob = null;
        return old;
    }

    @Override
    public String toString() {
        return "VM-" + id + " (Host-" + hostId + ")" +
                (runningJob == null ? " FREE" : " -> " + runningJob.getId());
    }
}

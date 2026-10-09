import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * Discrete-time simulation of the paper baseline.
 *
 * Paper experimental setup described in Section 4:
 * - two hosts, two PEs each
 * - two VMs initially, one PE per VM
 * - jobs initially allocated using FCFS
 * - higher-priority jobs can reuse the VM of a preempted lower-priority job
 *
 * We deliberately do not create additional VMs during the baseline run,
 * because the paper's proposed method is intended to avoid creating a new VM
 * for a newly arrived high-priority job.
 */
public class Simulator {

    private final List<Job> jobs;
    private final List<VM> vms = new ArrayList<>();
    private final AllocationAlgorithm algorithm;
    private final boolean dynamicProvisioning;
    private final int maxVMs;
    private int totalVMsCreated = 2;

    private final List<Job> waiting = new ArrayList<>();
    private final List<Job> suspended = new ArrayList<>();
    private final List<Job> completed = new ArrayList<>();
    private final List<Job> cancelled = new ArrayList<>();

    private int currentTime = 0;
    private int totalPreemptions = 0;
    private final SimulationObserver observer;

    public Simulator(List<Job> jobs) {
        this(jobs, new PaperAlgorithm(), false, 2, SimulationObserver.NO_OP);
    }

    public Simulator(List<Job> jobs, AllocationAlgorithm algorithm) {
        this(jobs, algorithm, false, 2, SimulationObserver.NO_OP);
    }

    public Simulator(List<Job> jobs, AllocationAlgorithm algorithm, boolean dynamicProvisioning, int maxVMs) {
        this(jobs, algorithm, dynamicProvisioning, maxVMs, SimulationObserver.NO_OP);
    }

    public Simulator(List<Job> jobs, AllocationAlgorithm algorithm, boolean dynamicProvisioning,
                     int maxVMs, SimulationObserver observer) {
        this.jobs = new ArrayList<>(jobs);
        this.algorithm = algorithm;
        this.dynamicProvisioning = dynamicProvisioning;
        this.maxVMs = Math.max(2, maxVMs);
        this.observer = observer == null ? SimulationObserver.NO_OP : observer;

        // Two hosts, two initial VMs: one VM per host.
        Host host1 = new Host(1);
        Host host2 = new Host(2);

        VM vm1 = new VM(1, host1.getId());
        VM vm2 = new VM(2, host2.getId());

        host1.addVM(vm1);
        host2.addVM(vm2);

        vms.add(vm1);
        vms.add(vm2);
    }

    public SimulationResult run(boolean verbose) {
        int nextArrivalIndex = 0;
        int safetyLimit = 100000;

        while (!allJobsTerminal() && currentTime < safetyLimit) {

            // A. Arrivals are processed at the beginning of the time slot.
            // A newly arrived job gets any VM that is already free before we
            // resume an older suspended job or fill the waiting queue.
            while (nextArrivalIndex < jobs.size()
                    && jobs.get(nextArrivalIndex).getArrivalTime() <= currentTime) {

                Job arriving = jobs.get(nextArrivalIndex++);
                handleArrival(arriving, verbose);
            }

            // A VM that became free at the previous time boundary can now be
            // used. The paper specifically says a suspended job can be resumed
            // when a running job completes, so suspended jobs are resumed before
            // ordinary waiting jobs.
            resumeSuspendedJobs(verbose);
            allocateWaitingJobs(verbose);

            // B. Execute one unit of every running job. The execution interval
            // is [currentTime, currentTime + 1). A completed VM becomes free
            // at the NEXT simulation time, not at the beginning of this slot.
            for (VM vm : vms) {
                Job running = vm.getRunningJob();

                if (running != null) {
                    running.tick();

                    if (running.isCompleted()) {
                        running.setFinishTime(currentTime + 1);
                        running.setStatus(JobStatus.COMPLETED);
                        completed.add(running);
                        observer.onCompleted(running, vm.getId(), currentTime + 1);
                        vm.release();

                        if (verbose) {
                            System.out.printf(
                                    "[t=%d] %s completed on VM-%d%n",
                                    currentTime + 1,
                                    running.getId(),
                                    vm.getId());
                        }
                    }
                }
            }

            // C. A job that remains in the waiting queue for this full time
            // interval receives one unit of waiting time.
            for (Job job : waiting) {
                job.addWaitingTime();
            }

            // Advance to the next discrete event time. Newly free VMs will be
            // filled at the beginning of that next time slot. This avoids the
            // incorrect situation where a job finishing at t+1 is replaced
            // at t.
            currentTime++;
        }

        return new SimulationResult(
                jobs,
                completed,
                cancelled,
                suspended,
                totalPreemptions,
                currentTime,
                totalVMsCreated
        );
    }

    private void handleArrival(Job arriving, boolean verbose) {
        VM free = findFreeVM();

        // Paper: if VM is available, allocate immediately.
        if (free != null) {
            assign(free, arriving, verbose, "FCFS allocation");
            return;
        }

        List<Job> runningJobs = getRunningJobs();

        // Paper Algorithm 1: only a higher-priority arrival can trigger the
        // preemption procedure.
        if (algorithm.isHighPriority(arriving, runningJobs, currentTime)) {
            Job selected = algorithm.selectPreemptionJob(arriving, runningJobs, currentTime);

            if (selected != null) {
                VM selectedVM = findVMRunning(selected);

                if (selectedVM != null) {
                    preempt(selected, selectedVM, verbose);
                    assign(selectedVM, arriving, verbose,
                            algorithm.name().equals("PAPER")
                                    ? "allocated after paper preemption"
                                    : "allocated after EDVA preemption");
                    return;
                }
            }
        }

        // EDVA extension: if preempting a running job would risk another
        // deadline and capacity still exists on the simulated hosts, provision
        // one additional VM instead of forcing the job to wait.
        if (dynamicProvisioning && vms.size() < maxVMs) {
            VM newVM = provisionVM(verbose);
            assign(newVM, arriving, verbose, "allocated on dynamically provisioned VM");
            return;
        }

        // No free VM, no safe preemption, and no remaining capacity: wait.
        arriving.setStatus(JobStatus.WAITING);
        waiting.add(arriving);

        if (verbose) {
            System.out.printf(
                    "[t=%d] %s -> WAITING%n",
                    currentTime,
                    arriving.getId());
        }
    }

    private void preempt(Job job, VM vm, boolean verbose) {
        totalPreemptions++;
        job.incrementPreemptionCount();
        vm.release();

        if (job.getLeaseType() == LeaseType.CANCELLABLE) {
            // Paper definition: cancellable requests need not be resumed.
            job.setStatus(JobStatus.CANCELLED);
            cancelled.add(job);
            observer.onPreempted(job, vm.getId(), currentTime, true);

            if (verbose) {
                System.out.printf(
                        "[t=%d] PREEMPT/CANCEL %s from VM-%d%n",
                        currentTime,
                        job.getId(),
                        vm.getId());
            }
        } else {
            // Paper definition: suspendable requests should be resumed later.
            job.setStatus(JobStatus.SUSPENDED);
            suspended.add(job);
            observer.onPreempted(job, vm.getId(), currentTime, false);

            if (verbose) {
                System.out.printf(
                        "[t=%d] PREEMPT/SUSPEND %s from VM-%d (remaining=%d)%n",
                        currentTime,
                        job.getId(),
                        vm.getId(),
                        job.getRemainingTime());
            }
        }
    }

    private VM provisionVM(boolean verbose) {
        int id = vms.size() + 1;
        // Two hosts, two one-PE VMs per host in the simulated capacity model.
        int hostId = ((id - 1) / 2) + 1;
        VM vm = new VM(id, hostId);
        vms.add(vm);
        totalVMsCreated++;
        if (verbose) {
            System.out.printf("[t=%d] VM-%d provisioned on Host-%d%n",
                    currentTime, id, hostId);
        }
        return vm;
    }

    private void allocateWaitingJobs(boolean verbose) {
        waiting.sort(
                Comparator.comparingInt(Job::getArrivalTime)
                          .thenComparing(Job::getId)
        );

        Iterator<Job> iterator = waiting.iterator();

        while (iterator.hasNext()) {
            VM free = findFreeVM();
            if (free == null) {
                break;
            }

            Job job = iterator.next();
            iterator.remove();
            assign(free, job, verbose, "FCFS waiting-queue allocation");
        }
    }

    private void resumeSuspendedJobs(boolean verbose) {
        Iterator<Job> iterator = suspended.iterator();

        while (iterator.hasNext()) {
            VM free = findFreeVM();
            if (free == null) {
                break;
            }

            Job job = iterator.next();
            iterator.remove();
            assign(free, job, verbose, "resume suspended job");
        }
    }

    private void assign(VM vm, Job job, boolean verbose, String reason) {
        job.setStartTime(currentTime);
        vm.assign(job);
        observer.onAssigned(job, vm.getId(), currentTime, reason);

        if (verbose) {
            System.out.printf(
                    "[t=%d] %s -> VM-%d (%s)%n",
                    currentTime,
                    job.getId(),
                    vm.getId(),
                    reason);
        }
    }

    private VM findFreeVM() {
        for (VM vm : vms) {
            if (vm.isFree()) return vm;
        }
        return null;
    }

    private VM findVMRunning(Job job) {
        for (VM vm : vms) {
            if (vm.getRunningJob() == job) return vm;
        }
        return null;
    }

    private List<Job> getRunningJobs() {
        List<Job> running = new ArrayList<>();
        for (VM vm : vms) {
            if (!vm.isFree()) running.add(vm.getRunningJob());
        }
        return running;
    }

    private boolean allJobsTerminal() {
        for (Job job : jobs) {
            if (job.getStatus() == JobStatus.WAITING
                    || job.getStatus() == JobStatus.RUNNING
                    || job.getStatus() == JobStatus.SUSPENDED) {
                return false;
            }
        }
        return true;
    }

    public static class SimulationResult {
        private final List<Job> jobs;
        private final List<Job> completed;
        private final List<Job> cancelled;
        private final List<Job> suspendedAtEnd;
        private final int totalPreemptions;
        private final int endTime;
        private final int totalVMsCreated;

        public SimulationResult(
                List<Job> jobs,
                List<Job> completed,
                List<Job> cancelled,
                List<Job> suspendedAtEnd,
                int totalPreemptions,
                int endTime,
                int totalVMsCreated) {
            this.jobs = jobs;
            this.completed = completed;
            this.cancelled = cancelled;
            this.suspendedAtEnd = suspendedAtEnd;
            this.totalPreemptions = totalPreemptions;
            this.endTime = endTime;
            this.totalVMsCreated = totalVMsCreated;
        }

        public int getDeadlineViolations() {
            int count = 0;
            for (Job job : completed) {
                if (job.getFinishTime() > job.getDeadline()) count++;
            }
            return count;
        }

        public double getDeadlineSuccessRate() {
            int terminalJobs = completed.size() + cancelled.size();
            if (terminalJobs == 0) return 0.0;
            return 100.0 * (completed.size() - getDeadlineViolations()) / terminalJobs;
        }

        public double getAverageWaitingTime() {
            if (jobs.isEmpty()) return 0.0;
            double total = 0;
            for (Job job : jobs) total += job.getWaitingTime();
            return total / jobs.size();
        }

        public int getTotalPreemptions() { return totalPreemptions; }

        public int getEndTime() { return endTime; }

        public int getTotalVMsCreated() { return totalVMsCreated; }

        public double getAverageTurnaroundTime() {
            if (completed.isEmpty()) return 0.0;
            double total = 0;
            for (Job job : completed) {
                total += job.getFinishTime() - job.getArrivalTime();
            }
            return total / completed.size();
        }

        public void printSummary(String name) {
            System.out.println();
            System.out.println("========================================");
            System.out.println(name);
            System.out.println("========================================");
            System.out.println("Completed Jobs       : " + completed.size());
            System.out.println("Cancelled Jobs       : " + cancelled.size());
            System.out.println("Deadline Violations  : " + getDeadlineViolations());
            System.out.printf("Deadline Success     : %.2f%%%n", getDeadlineSuccessRate());
            System.out.println("Total Preemptions    : " + totalPreemptions);
            System.out.printf("Average Waiting Time : %.2f%n", getAverageWaitingTime());
            System.out.printf("Average Turnaround   : %.2f%n", getAverageTurnaroundTime());
            System.out.println("Simulation End Time  : " + endTime);
            System.out.println("VMs Created          : " + totalVMsCreated);
        }

        public void printJobTable() {
            System.out.println();
            System.out.printf(
                    "%-5s %-16s %-8s %-8s %-10s %-8s %-8s%n",
                    "ID", "Status", "Start", "Finish", "Deadline", "Remain", "Preempt");

            for (Job job : jobs) {
                System.out.printf(
                        "%-5s %-16s %-8d %-8d %-10d %-8d %-8d%n",
                        job.getId(),
                        job.getStatus(),
                        job.getStartTime(),
                        job.getFinishTime(),
                        job.getDeadline(),
                        job.getRemainingTime(),
                        job.getPreemptionCount());
            }
        }
    }
}

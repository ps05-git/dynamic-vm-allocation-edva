import java.util.List;

public class Main {

    private static final long SEED = 1L;
    private static final int JOB_COUNT = 12;

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("DYNAMIC VM ALLOCATION - PAPER BASELINE");
        System.out.println("========================================");
        System.out.println("Java simulation of the supplied paper");
        System.out.println("Hosts: 2 | Initial VMs: 2");

        runValidationTests();
        runRandomExperiment();
        runSingleComparison();
    }

    private static void runValidationTests() {
        System.out.println();
        System.out.println("========== PAPER RULE VALIDATION ==========");

        runTest("1. Cancellable preferred",
                ValidationWorkload.cancellablePreference());
        runTest("2. Maximum remaining execution",
                ValidationWorkload.maximumRemaining());
        runTest("3. Non-preemptable protection",
                ValidationWorkload.nonPreemptableProtection());
        runTest("4. Suspend/resume",
                ValidationWorkload.resumeSuspended());
    }

    private static void runTest(String name, List<Job> jobs) {
        System.out.println();
        System.out.println("--- " + name + " ---");
        Simulator simulator = new Simulator(jobs);
        Simulator.SimulationResult result = simulator.run(true);
        result.printSummary(name);
    }

    private static void runRandomExperiment() {
        System.out.println();
        System.out.println("========== RANDOM BASELINE EXPERIMENT ==========");

        List<Job> jobs = WorkloadGenerator.generate(SEED, JOB_COUNT);

        System.out.printf(
                "%-5s %-8s %-8s %-10s %-9s %-18s%n",
                "ID", "Arrival", "Exec", "Deadline", "Priority", "Lease");

        for (Job job : jobs) {
            System.out.printf(
                    "%-5s %-8d %-8d %-10d %-9d %-18s%n",
                    job.getId(), job.getArrivalTime(), job.getExecutionTime(),
                    job.getDeadline(), job.getPriority(), job.getLeaseType());
        }

        Simulator simulator = new Simulator(jobs);
        Simulator.SimulationResult result = simulator.run(true);

        result.printSummary("PAPER ALGORITHM - RANDOM SEED " + SEED);
        result.printJobTable();
    }
    private static void runSingleComparison() {
        System.out.println();
        System.out.println("========== PAPER vs EDVA - SAME WORKLOAD (FIXED 2 VMs) ==========");
        List<Job> paperJobs = WorkloadGenerator.generate(SEED, JOB_COUNT);
        List<Job> edvaJobs = new java.util.ArrayList<>();
        for (Job j : paperJobs) {
            edvaJobs.add(new Job(j.getId(), j.getArrivalTime(), j.getExecutionTime(),
                    j.getDeadline(), j.getPriority(), j.getLeaseType()));
        }

        Simulator.SimulationResult paper =
                new Simulator(paperJobs, new PaperAlgorithm()).run(false);
        Simulator.SimulationResult edva =
                new Simulator(edvaJobs, new ModifiedAlgorithm(), false, 2).run(false);

        System.out.printf("%-28s %-14s %-14s%n", "Metric", "Paper", "EDVA");
        System.out.printf("%-28s %-14.2f %-14.2f%n", "Deadline Success %",
                paper.getDeadlineSuccessRate(), edva.getDeadlineSuccessRate());
        System.out.printf("%-28s %-14.2f %-14.2f%n", "Avg Waiting Time",
                paper.getAverageWaitingTime(), edva.getAverageWaitingTime());
        System.out.printf("%-28s %-14.2f %-14.2f%n", "Avg Turnaround",
                paper.getAverageTurnaroundTime(), edva.getAverageTurnaroundTime());
        System.out.printf("%-28s %-14d %-14d%n", "Preemptions",
                paper.getTotalPreemptions(), edva.getTotalPreemptions());
        System.out.printf("%-28s %-14d %-14d%n", "Makespan",
                paper.getEndTime(), edva.getEndTime());
        System.out.printf("%-28s %-14d %-14d%n", "VMs Created",
                paper.getTotalVMsCreated(), edva.getTotalVMsCreated());
    }

}

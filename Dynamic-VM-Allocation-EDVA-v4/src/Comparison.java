import java.util.ArrayList;
import java.util.List;

public class Comparison {
    public static void main(String[] args) {
        int runs = 100;
        int jobsPerRun = 50;

        double paperDeadlineSuccess = 0;
        double edvaDeadlineSuccess = 0;
        double paperWaiting = 0;
        double edvaWaiting = 0;
        double paperTurnaround = 0;
        double edvaTurnaround = 0;
        double paperPreemptions = 0;
        double edvaPreemptions = 0;
        double paperMakespan = 0;
        double edvaMakespan = 0;
        double paperVMs = 0;
        double edvaVMs = 0;

        for (int seed = 1; seed <= runs; seed++) {
            List<Job> base = WorkloadGenerator.generate(seed, jobsPerRun);

            Simulator.SimulationResult p =
                    new Simulator(copyJobs(base), new PaperAlgorithm()).run(false);
            Simulator.SimulationResult m =
                    new Simulator(copyJobs(base), new ModifiedAlgorithm(), false, 2).run(false);

            paperDeadlineSuccess += p.getDeadlineSuccessRate();
            edvaDeadlineSuccess += m.getDeadlineSuccessRate();
            paperWaiting += p.getAverageWaitingTime();
            edvaWaiting += m.getAverageWaitingTime();
            paperTurnaround += p.getAverageTurnaroundTime();
            edvaTurnaround += m.getAverageTurnaroundTime();
            paperPreemptions += p.getTotalPreemptions();
            edvaPreemptions += m.getTotalPreemptions();
            paperMakespan += p.getEndTime();
            edvaMakespan += m.getEndTime();
            paperVMs += p.getTotalVMsCreated();
            edvaVMs += m.getTotalVMsCreated();
        }

        System.out.println("========================================");
        System.out.println("PAPER vs EDVA - 100 IDENTICAL WORKLOADS (FIXED 2 VMs)");
        System.out.println("Jobs per workload: " + jobsPerRun);
        System.out.println("========================================");
        System.out.printf("%-28s %-14s %-14s%n", "Metric", "Paper", "EDVA");
        System.out.printf("%-28s %-14.2f %-14.2f%n", "Deadline Success %", paperDeadlineSuccess / runs, edvaDeadlineSuccess / runs);
        System.out.printf("%-28s %-14.2f %-14.2f%n", "Avg Waiting Time", paperWaiting / runs, edvaWaiting / runs);
        System.out.printf("%-28s %-14.2f %-14.2f%n", "Avg Turnaround", paperTurnaround / runs, edvaTurnaround / runs);
        System.out.printf("%-28s %-14.2f %-14.2f%n", "Avg Preemptions", paperPreemptions / runs, edvaPreemptions / runs);
        System.out.printf("%-28s %-14.2f %-14.2f%n", "Avg Makespan", paperMakespan / runs, edvaMakespan / runs);
        System.out.printf("%-28s %-14.2f %-14.2f%n", "Avg VMs Created", paperVMs / runs, edvaVMs / runs);
        System.out.println();
        System.out.println("VM capacity is fixed at 2 for BOTH algorithms in this primary comparison.");
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

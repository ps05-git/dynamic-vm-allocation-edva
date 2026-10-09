import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class WorkloadGenerator {

    public static List<Job> generate(long seed, int count) {
        Random random = new Random(seed);
        List<Job> jobs = new ArrayList<>();

        int arrival = 0;

        for (int i = 1; i <= count; i++) {
            if (i > 1) {
                arrival += random.nextInt(3); // 0, 1 or 2 time units
            }

            int execution = 3 + random.nextInt(10); // 3..12
            int deadline = arrival + execution + 5 + random.nextInt(16);
            int priority = 1 + random.nextInt(5);

            LeaseType lease;
            int type = random.nextInt(3);
            if (type == 0) lease = LeaseType.CANCELLABLE;
            else if (type == 1) lease = LeaseType.SUSPENDABLE;
            else lease = LeaseType.NON_PREEMPTABLE;

            jobs.add(new Job(
                    "J" + i,
                    arrival,
                    execution,
                    deadline,
                    priority,
                    lease
            ));
        }

        return jobs;
    }
}

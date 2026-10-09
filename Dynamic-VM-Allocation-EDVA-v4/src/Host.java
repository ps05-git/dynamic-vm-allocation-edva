import java.util.ArrayList;
import java.util.List;

public class Host {
    private final int id;
    private final List<VM> vms = new ArrayList<>();

    public Host(int id) {
        this.id = id;
    }

    public int getId() { return id; }

    public void addVM(VM vm) {
        vms.add(vm);
    }

    public List<VM> getVms() {
        return vms;
    }
}

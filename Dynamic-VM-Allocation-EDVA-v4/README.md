# Dynamic VM Allocation - Final Paper vs EDVA Baseline

## Project stages

1. Implement the supplied paper as the baseline.
2. Validate the paper's lease/preemption behavior.
3. Implement EDVA as a modified preemption policy.
4. Compare PAPER and EDVA on identical workloads with exactly two VMs.
5. Later connect the validated controller to OpenStack.
6. Later expose live results through a web dashboard.

## Paper baseline

The implementation follows the source-derived rules used in this project:
- Smaller deadline means higher priority.
- A high-priority arrival can trigger preemption when all VMs are occupied.
- NON_PREEMPTABLE jobs are excluded from the candidate set.
- CANCELLABLE is preferred over SUSPENDABLE.
- When multiple candidates remain, the paper uses maximum remaining execution.
- SUSPENDABLE jobs can be resumed after the preempting job completes.

The supplied paper includes an execution-threshold condition but does not state a numerical threshold. The implementation therefore keeps that parameter configurable instead of inventing a value.

## EDVA v4 modification

EDVA keeps the same two-VM environment and changes only the candidate-selection policy.

For every eligible candidate:

    Slack = Deadline - CurrentTime - RemainingExecution

A larger slack means more deadline cushion. EDVA therefore selects the candidate with the greatest slack within the paper's preferred lease class. Remaining execution, deadline, and job ID are deterministic tie-breakers.

This is intentionally a conservative modification: EDVA does not create extra VMs in the primary comparison.

## Fair experiment

Both algorithms receive:
- the same generated workload;
- the same arrival times;
- the same execution times;
- the same deadlines;
- the same priorities;
- the same lease types;
- the same two-VM capacity.

Run 100 workloads of 50 jobs each with:

    javac -d out src/*.java
    java -cp out Comparison

## Important interpretation

EDVA is not assumed to be better before testing. The experiment should be reported honestly. The current v4 policy gives a small deadline-success improvement in the supplied 100-workload experiment, while some other metrics remain close or slightly worse. This is a baseline for the next research stage, not a claim that every metric is superior.

## Next project stage

After the simulation is frozen, add:
- CPU/PE utilization;
- host utilization;
- VM utilization;
- deadline violations caused by preemption;
- CSV export;
- plots over low/medium/high workloads;
- OpenStack integration;
- live dashboard/API deployment.

## Windows MiniCloud integration (new)

This integration preserves the original simulator and adds optional observer callbacks. Run the MiniCloud API in one PowerShell window first:

```powershell
cd .\mini-cloud
.\run-mini-cloud.bat
```

Keep that window open. In a second PowerShell window, from the project root run:

```powershell
.\run-cloud-integration.bat
```

What it does:
- Runs the existing PAPER and EDVA schedulers on copies of the same generated workload (seed 1, 12 jobs).
- Creates two simulated MiniCloud instance records for each scheduler run.
- When a job is assigned for the first time, submits a real Windows `ping` process through the MiniCloud jobs API, using the job's execution time as a bounded requested duration.
- Logs scheduler assignment, completion, and preemption events to `data\cloud-integration\integration-events.csv`.
- Saves final MiniCloud job records to `data\cloud-integration\cloud-jobs-final.json`.

Important scientific limitation: instance/VM records and scheduler time remain simulated. Submitted processes run on the Windows host, not inside independent VMs. The MiniCloud API does not currently pause/kill a Windows process when the simulated scheduler preempts a job; preemption is logged as a scheduler event only. Therefore this is an OpenStack-inspired educational prototype, not a real OpenStack deployment or a VM-level preemption experiment.


## Final integration notes

- `run-cloud-integration.bat` no longer prints the malformed `necho` warning.
- At the beginning of an integration run, the client clears MiniCloud's in-memory instance/job records so `cloud-jobs-final.json` describes only the current run. This reset does not terminate already-running Windows processes.
- JSON output now escapes carriage returns, newlines, tabs, quotes, backslashes, and control characters correctly.
- This remains an educational OpenStack-like prototype: instance/host lifecycle is simulated, while submitted jobs execute as Windows host processes. It is not a real OpenStack deployment and does not implement actual VM-level preemption.


## Integration demonstration workload
The MiniCloud integration uses a targeted 3-job workload designed to exercise the preemption policy difference. At simulated time 3, J3 arrives with an earlier deadline than both running jobs. PAPER chooses the eligible suspendable job with the maximum remaining execution (J1); EDVA chooses the eligible suspendable job with the greatest deadline slack (J2). This makes the policy decision observable. It is a controlled demonstration workload, not evidence that EDVA is always better. The normal simulator's random experiments remain available through `Main`.

---

## Hosted dashboard (static demo)

This project now includes `index.html`, a static EDVA Cloud Console dashboard intended for Vercel hosting. It displays recorded demonstration metrics and clearly labels the cloud instances as simulated. It is not connected to the local MiniOpenStack API, and it does not deploy real OpenStack VMs.

### Deploy on Vercel

1. Upload the **contents of this `Dynamic-VM-Allocation-EDVA-v4` folder** to the root of a GitHub repository (so `index.html`, `src/`, and `mini-cloud/` are at the repository root).
2. In Vercel, import that repository.
3. Set **Root Directory** to `.` / repository root. Keep Framework Preset as **Other**.
4. Leave Build Command and Output Directory empty/default for static HTML, then deploy.
5. Open the deployment URL and verify the EDVA Cloud Console loads.

The Java simulation and Windows batch scripts are run locally on Windows. Vercel does not execute these `.bat` scripts. The existing API's Windows process execution is not made public by hosting this static page; a separately deployed backend or secure relay would be required for live API data.

### Honest demo limitations

- The dashboard's numbers are saved demo values, not live values fetched from the API.
- VM instances shown in the dashboard are simulated.
- Scheduler suspend/resume events are simulated; the current prototype does not physically pause and resume Windows processes.
- This is an OpenStack-style academic prototype, not a real OpenStack deployment.

## Live dashboard API and OpenStack adapter (new backend)

The `backend/` directory adds a Node API that compiles and runs the existing Java `Comparison` class, and includes a real OpenStack Keystone/Nova adapter. This is code support; it is **not live until the backend is deployed and configured**.

### Backend endpoints
- `GET /api/health` — backend, Java build, and OpenStack configuration status.
- `POST /api/scheduler/run` — runs the Java PAPER vs. EDVA comparison and returns the actual console output and parsed metrics.
- `GET /api/openstack/servers` — lists real Nova servers.
- `POST /api/openstack/servers` — requests a real VM from Nova; requires `X-Admin-Key`.
- `DELETE /api/openstack/servers/:id` — deletes a Nova VM; requires `X-Admin-Key`.

### Deploy the backend
Use a Docker-capable web service host. The backend Dockerfile is `backend/Dockerfile`; set the service's root directory to this project folder (the folder containing `src/`, `mini-cloud/`, and `backend/`). Set environment variables from `backend/.env.example`. Set `ALLOWED_ORIGIN` to the exact Vercel domain and set a strong `ADMIN_API_KEY`. Deploy and check `/api/health`.

Then open the Vercel dashboard and enter the deployed backend base URL in the **Live backend connection** card on the Overview page. Click **Save URL**, then **Check connection**, and **Run Java comparison**. The URL is stored only in that browser. The API key field is not stored.

### Enable real OpenStack VM creation
Real VM provisioning requires access to an actual OpenStack cloud. In the backend host's environment settings (never in frontend code), configure `OS_AUTH_URL`, `OS_USERNAME`, `OS_PASSWORD`, `OS_PROJECT_NAME`, domain and region values, plus valid `OS_IMAGE_ID`, `OS_FLAVOR_ID`, and `OS_NETWORK_ID`. Optional `OS_KEY_NAME` selects an SSH key pair. Without these settings, the backend deliberately returns `503` instead of pretending that simulated instances are real VMs. Creating servers may use quota or incur charges.

See `backend/README.md` for deployment and configuration details. The existing local `mini-cloud/` remains an educational simulator and is not itself a hypervisor or a real OpenStack cloud.

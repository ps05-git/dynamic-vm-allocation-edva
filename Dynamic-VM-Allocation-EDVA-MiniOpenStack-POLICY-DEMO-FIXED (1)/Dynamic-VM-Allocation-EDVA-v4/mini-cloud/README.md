# Mini OpenStack-like environment (Windows)

This is an educational control-plane prototype, not OpenStack itself.

- A Keystone-like demo token endpoint is provided at `POST /v1/auth/tokens`.
- A Nova-like instance API creates/lists/starts/stops/deletes *simulated instance records*.
- The jobs API executes a bounded `timeout` process on the Windows host and records its state/output.
- All state is in memory and disappears when the server stops.
- No cloud resources are created and no Google Cloud services are called.
- The demo token is not production security; bind address is localhost only.

## Run

Requires a JDK (Java 17+ recommended; Java 26 is fine). In PowerShell, enter this folder and run:

```powershell
.\run-mini-cloud.bat
```

API base URL: `http://127.0.0.1:8080`

## Test from another PowerShell window

```powershell
# Health check
Invoke-RestMethod http://127.0.0.1:8080/health

# Obtain demo token
$auth = Invoke-RestMethod -Method Post http://127.0.0.1:8080/v1/auth/tokens
$token = $auth.token
$headers = @{ 'X-Auth-Token' = $token }

# Create a simulated instance
$vm = Invoke-RestMethod -Method Post 'http://127.0.0.1:8080/v1/instances?name=edva-vm-1&host=host-1' -Headers $headers
$vm

# Mark the instance active
Invoke-RestMethod -Method Post ("http://127.0.0.1:8080/v1/instances/{0}/start" -f $vm.id) -Headers $headers

# Submit a real Windows process (runs for 3 seconds)
Invoke-RestMethod -Method Post ("http://127.0.0.1:8080/v1/jobs?instance_id={0}&seconds=3" -f $vm.id) -Headers $headers

# Inspect jobs and instances
Invoke-RestMethod http://127.0.0.1:8080/v1/jobs -Headers $headers
Invoke-RestMethod http://127.0.0.1:8080/v1/instances -Headers $headers

# Stop instance
Invoke-RestMethod -Method Post ("http://127.0.0.1:8080/v1/instances/{0}/stop" -f $vm.id) -Headers $headers
```

## Research reporting

Correct description: "A Windows-based OpenStack-inspired prototype with simulated host/instance lifecycle and real local process execution."

Do not claim this is a deployed OpenStack cloud or that jobs execute inside independent VMs. The current version is a foundation for a later adapter to a real cloud API and for integration with the EDVA scheduling policy.

# EDVA API backend

This backend connects the hosted dashboard to the existing Java experiment and provides an optional **real OpenStack Nova** adapter.

## What it does
- `GET /api/health`: Java build status and OpenStack configuration status.
- `POST /api/scheduler/run`: runs the existing Java `Comparison` class on the backend and returns the metrics table plus console output.
- `GET /api/openstack/servers`: lists servers from a real OpenStack Nova API.
- `POST /api/openstack/servers`: requests a real Nova server, using server-side `OS_*` environment variables and `X-Admin-Key`.
- `DELETE /api/openstack/servers/:id`: deletes a Nova server and requires `X-Admin-Key`.

The OpenStack endpoints return HTTP 503 until valid credentials and image/flavor/network IDs are configured. This prevents the application from pretending simulated instances are real VMs.

## Deploy backend (Docker-capable host such as Render)
1. Push this project folder to GitHub.
2. Create a Docker web service from the repository; Dockerfile path: `Dynamic-VM-Allocation-EDVA-v4/backend/Dockerfile` if the repo contains the parent directory, or `backend/Dockerfile` if this project folder is the repository root.
3. Set `ALLOWED_ORIGIN` to the exact deployed Vercel origin.
4. Set `ADMIN_API_KEY` to a long random secret.
5. Deploy and test `https://YOUR-BACKEND/api/health`.
6. In the Vercel dashboard, set the API base URL to the backend URL in the API Connection section, then click Save.

## Enable actual OpenStack
Only do this if you have an OpenStack project and permission to create servers. Set these backend environment variables in the hosting provider, never in frontend code:
- `OS_AUTH_URL` (Keystone v3 URL)
- `OS_USERNAME`, `OS_PASSWORD`, `OS_PROJECT_NAME`
- `OS_USER_DOMAIN_NAME`, `OS_PROJECT_DOMAIN_NAME`, `OS_REGION_NAME`
- `OS_IMAGE_ID`, `OS_FLAVOR_ID`, `OS_NETWORK_ID`
- optional `OS_KEY_NAME`

Nova server creation can incur cost and consume quota. Test with a small allowed image/flavor, and delete demo instances when finished. Never commit credentials to GitHub or expose them in Vercel frontend environment variables.

## Local test
From `backend/`, install Node 20+ and JDK 21+, then run `npm start`. The server compiles `../src/*.java` at startup. Check `/api/health`, then POST `/api/scheduler/run` with `{}`. Without OpenStack credentials, scheduler functionality still works while provisioning endpoints report not configured.

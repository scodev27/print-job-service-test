# Print/Render Service - Technical Interview

### Context

We run a rendering system. Clients submit a **render job** (a template id plus some parameters);
the job is processed **asynchronously**, and processing can occasionally fail transiently.
Clients poll for the job's status and fetch the result once it is done.

The set of available templates already exists (`RenderTemplate` / `GET /templates`) - you do not
need to build template management. Your job is to implement the render job lifecycle end to end,
**including how the service is packaged and run**.

### Prerequisites

- **JDK 25** installed locally (the Maven wrapper handles Maven itself, but you need a JDK to run
  `./mvnw` or your IDE).
- **Docker Desktop** (Mac/Windows) or **Docker Engine + the Compose plugin** (Linux), installed
  and running. Everything containerization-related in this exercise (the `Dockerfile` you're
  given, and the `docker-compose.yml` you write) is public/open-source - no account, license, or
  paid service is required. `docker compose version` should print a v2.x version.
- Ports **8080** (the app) and **5432** (Postgres) free on your machine, or be ready to remap them
  in your `docker-compose.yml` if something else is already using them.
- Git, to fork/push your solution.

### Functional Requirements

- **Submit a job**: `POST /jobs`
  - Body: `{ "templateId": "<uuid>", "parameters": { "any": "key-value data" } }`
  - Must return immediately (do not block the HTTP response on the actual rendering work).
  - Reject with `400` if `templateId` does not match an existing template.
  - On success, return `201` with the created job (id, status `QUEUED`, timestamps).

- **Process a job asynchronously**: once queued, a job must move through
  `QUEUED -> PROCESSING -> DONE` or `QUEUED -> PROCESSING -> FAILED`, driven by a background
  worker - not by an incoming HTTP request. "Rendering" can be simulated (e.g. a short delay);
  it does not need to produce a real document.
  - Some renders fail transiently. A failed attempt should be retried a bounded number of times
    before the job is marked `FAILED` with an error reason recorded.

- **Get job status**: `GET /jobs/{id}` - current status, attempt count, error message if failed,
  and whether a result is available.

- **List jobs**: `GET /jobs` - optionally filterable by status, e.g. `GET /jobs?status=FAILED`.

- **Fetch a result**: `GET /jobs/{id}/result` - returns the rendered output once the job is
  `DONE`. Decide yourself what should happen if it's called before the job finishes, or if the
  job failed.

### Required

- Your solution - code, commit messages, and README - must be in English.
- Java 25.
- This repo contains the existing project skeleton (template lookup, project setup, and a starter
  `Job` entity with the fields implied by the API contract above); fork or create a public
  repository with your solution. The `Job` entity does not model retry scheduling/backoff - that's
  part of what you're designing.
- You decide the scope of automated tests - we expect to see some.
  `RenderTemplateResourceTest` shows the MockMvc setup used in this repo, if that's a useful
  starting point.
- **The service must be containerized and runnable via Docker Compose alongside an actual
  PostgreSQL server** - i.e. a `postgres` Docker image running as its own container, not H2 or
  any other in-memory/embedded database. A `Dockerfile` for this service is provided; you need to
  write the `docker-compose.yml` that wires this app together with that `postgres` service (the
  app already reads its DB connection from `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` /
  `DB_PASSWORD`, see `application.properties`). H2 is fine for your own test suite - it must not
  be what the running application connects to.
- The service must expose:
  - A liveness endpoint.
  - A readiness endpoint that reflects more than "the HTTP server is up" - think about what else
    a caller would want to know before considering this service ready to take traffic.
  - Some form of basic metrics (job counts by status is enough; format is up to you).
  - Paths for all three are up to you - just document them in your README so we know where to
    look.
- **Add a short "Design Decisions" section to your README** covering the points listed below
  under "A few things we deliberately left open." A few sentences per point is enough - this is
  the starting point for the design discussion, not a design doc.
- **Once the code is complete, reply to your hiring contact with a link to your repository.**

### A few things we deliberately left open

We're not going to tell you how to implement the queue/worker, the retry policy, or what your
readiness check should verify - that's for you to decide.


### Optional (not required to complete the exercise)

- Demonstrate that running two instances of your app against the same database does not cause a
  job to be processed twice.
- A Kubernetes `Deployment`/`Service` manifest for this app (it does not need to be applied to a
  real cluster - we're interested in the manifest itself, e.g. how you wire up probes).

### How to run

Building
```shell
$ ./mvnw compile
```

Test
```shell
$ ./mvnw test
```

Start the application (once you've written `docker-compose.yml`)
```shell
$ docker compose up --build
```

Listing available templates
```shell
$ curl localhost:8080/templates
```

---

## Solution
 
### Operational endpoints
 
| Purpose    | Path                          |
|------------|--------------------------------|
| Liveness   | `GET /actuator/health/liveness`  |
| Readiness  | `GET /actuator/health/readiness` |
| Metrics    | `GET /actuator/jobmetrics`       |
 
`GET /actuator/jobmetrics` returns job counts grouped by status, e.g.:
 
```json
{ "QUEUED": 0, "PROCESSING": 0, "DONE": 3, "FAILED": 1 }
```
 
### Design Decisions
 
**Queue / worker implementation.** A single `@Scheduled` poller (`JobWorker`) runs on a fixed
delay and queries for jobs in `QUEUED` status whose backoff window (if any) has elapsed. Each
candidate is handed to `JobProcessor`, which claims and processes it inside its own transaction.
Polling was chosen over an event-driven approach (e.g. publishing an in-process event on submit)
because it guarantees no job is ever silently lost if the instance that received the `POST`
crashes before processing starts - any other instance's next poll cycle will still pick it up.
The trade-off is latency bounded by the poll interval (configurable via
`job.worker.poll-interval-ms`, default 2s), which is acceptable for this exercise.
 
**Retry policy.** Jobs carry an `attempts` counter and a `nextAttemptAt` timestamp (added to the
`Job` entity, which deliberately did not model retry scheduling). On a transient failure, the job
is requeued with `attempts` incremented and `nextAttemptAt` pushed forward using exponential
backoff (`5s * 2^attempts`), up to a bounded maximum of 3 attempts, after which it is marked
`FAILED` with the failure reason recorded in `errorMessage`. Rendering itself is simulated via a
pluggable `RenderExecutor` (a 30% chance of throwing `RenderException`), kept separate from
`JobProcessor` so retry/backoff logic can be unit-tested independently of the rendering strategy.
 
**Preventing double processing across instances.** Rather than an external lock (e.g. Redis) or
`SELECT ... FOR UPDATE SKIP LOCKED`, claiming a job is done with a single atomic conditional
`UPDATE`:
 
```sql
update job set status = 'PROCESSING', updated_at = :now
where id = :id and status = 'QUEUED'
```
 
The number of affected rows tells the caller whether it won the race (`1`) or another instance
already claimed the job first (`0`), with no coordination needed beyond what the database already
guarantees for a single statement. This was chosen over `SKIP LOCKED` for simplicity - it's
easier to reason about and verify in a short exercise - at the cost of each worker still issuing
a preceding `SELECT` to find candidates, which `SKIP LOCKED` would fold into a single statement.
This was verified empirically by running two instances against the same database and submitting
10 jobs concurrently: both instances polled and attempted the same candidates, but exactly one
`UPDATE` succeeded per job (see "Running multiple instances" below) - all 10 jobs ended up `DONE`
with no duplicate processing.
 
**Readiness check.** The readiness endpoint includes Spring Boot Actuator's `db` health indicator
alongside the default `readinessState`, so it reports `DOWN` if the database is unreachable - not
just "the HTTP server accepted the connection." This matters because a caller (e.g. a Kubernetes
load balancer) should stop routing traffic to an instance that is up but cannot actually serve
requests.
 
**`GET /jobs/{id}/result` semantics.** Three cases are distinguished by status code:
- `DONE` → `200` with the rendered content.
- `FAILED` → `409 Conflict` - the job exists and is in a valid, terminal state, but a result will
  never exist for it.
- `QUEUED` / `PROCESSING` → `202 Accepted` - not an error, the job is simply not finished yet;
  distinct from `404`, since the job does exist.
  
### Running multiple instances
 
`docker-compose.yml` maps the `app` service to a port range (`8080-8081:8080`) so it can be
scaled without a port conflict:
 
```shell
$ docker compose up --build --scale app=2
```
 
Both instances poll and attempt to claim the same queued jobs; the atomic claim query ensures
each job is processed by exactly one of them. This was verified by submitting 10 jobs while 2
instances were running: the logs show both instances issuing the same `UPDATE ... WHERE
status='QUEUED'` for overlapping candidates, and `GET /actuator/jobmetrics` afterwards reported
all 10 jobs `DONE` with none duplicated or lost.
 
### Kubernetes manifest
 
`k8s/deployment.yaml` and `k8s/service.yaml` provide a `Deployment` (2 replicas, since the atomic
claim makes horizontal scaling safe with no extra coordination) and a `ClusterIP` `Service`. The
`Deployment` wires the liveness and readiness probes to the same Actuator endpoints described
above, and reads database credentials from a `Secret` rather than plain environment values (unlike
the local `docker-compose.yml`, where inline credentials are acceptable for development).

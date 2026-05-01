# Spring Boot File Streaming

A Spring Boot experiment for uploading and downloading large files while observing JVM behaviour through OpenTelemetry, Prometheus, and Grafana.

## What this explores

Large files uploaded via `multipart/form-data` don't spike the JVM heap. This project exists to validate that claim with real metrics.

**Why the heap stays flat:**
- Tomcat writes incoming multipart data to a temp file on disk immediately (`file-size-threshold=0B` by default), never buffering it in memory
- `InputStream.transferTo()` copies in ~8KB chunks, so only a tiny buffer ever lives on the heap regardless of file size
- The result: uploading a 200MB file looks the same to the GC as uploading a 1MB file

**Metrics to watch in Grafana:**
- `jvm_memory_used_bytes` — should stay flat during uploads
- `jvm_gc_memory_allocated_bytes_total` — small rate spike from Spring request objects, not file content
- `jvm_memory_committed_bytes` — JVM shouldn't need to expand committed memory

## Architecture

```
Spring Boot App
      |
      | OTLP (HTTP :4318)
      v
OTel Collector
      |
      | Prometheus exporter (:8889)
      v
Prometheus (:9090)
      |
      v
Grafana (:3000)
```

## Stack

| Component | Version |
|-----------|---------|
| Spring Boot | 4.0.5 |
| Java | 25 |
| OTel Collector | contrib:latest |
| Prometheus | latest |
| Grafana | latest |

## API

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/files/upload` | Upload a file (`multipart/form-data`, field: `document`) |
| `GET` | `/api/v1/files` | List uploaded files |
| `GET` | `/api/v1/files/{fileIdentifier}` | Download a file |

Uploaded files are stored in `./uploads/`. Max file size: 1GB.

## Running with Docker Compose

Starts the app, OTel Collector, Prometheus, and Grafana:

```bash
docker compose up --build
```

| Service | URL |
|---------|-----|
| App | http://localhost:8080 |
| Grafana | http://localhost:3000 (admin / admin) |
| Prometheus | http://localhost:9090 |

Grafana comes with the Prometheus datasource pre-configured. Open it and query `jvm_memory_used_bytes` to start exploring.

## Running locally

```bash
./mvnw spring-boot:run
```

Requires an OTel Collector running on `localhost:4318`. If you just want the app without observability, comment out the `management.otlp.*` lines in `application.properties`.

## Stress testing

The `scripts/stress.sh` script fires concurrent uploads of random-sized files to generate observable load:

```bash
# defaults: 10 concurrent, 3 waves, 1–300MB files
./scripts/stress.sh

# custom run
./scripts/stress.sh --concurrency 20 --waves 5 --min-size 10 --max-size 500
```

| Flag | Default | Description |
|------|---------|-------------|
| `--concurrency` | 10 | Parallel uploads per wave |
| `--waves` | 3 | Number of upload rounds |
| `--url` | http://localhost:8080 | App base URL |
| `--min-size` | 1 | Min file size (MB) |
| `--max-size` | 300 | Max file size (MB) |

Files are generated from `/dev/urandom` into a temp dir and cleaned up automatically on exit.

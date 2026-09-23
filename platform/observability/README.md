# Observability standard

All modern workloads emit OpenTelemetry traces, metrics, and structured logs.
Inbound requests accept or create W3C `traceparent`; services propagate it to
outbound calls and message envelopes. Logs include timestamp, severity,
service.name, deployment.environment, trace_id, span_id, route ID, and a
non-sensitive correlation ID. They never include tokens, passwords, full
payment data, or unclassified business payloads.

Required service signals are request rate, error rate, duration, saturation,
dependency duration/errors, instance/revision count, queue age/depth, dead
letters, outbox age, and business completion/failure counters. Dashboards and
alerts link to a runbook and identify owner, environment, revision, and route
generation.

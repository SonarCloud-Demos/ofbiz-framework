# Paved-road service operations

Start with the service dashboard, active alerts, revision health, request/error
rate, latency, dependencies, queue age, and database saturation. Use correlation
and trace IDs; do not search logs using sensitive business fields.

For an incident, assign an incident commander, preserve evidence, select
rollback or forward recovery, and communicate user impact. Rollback authority
is the environment owner recorded in the deployment approval. Escalate security
events immediately and rotate affected credentials through Key Vault.

Cost alerts trigger at 80% of the environment budget. Investigate unexpected
replica counts, log ingestion, database tier/storage, Service Bus capacity, and
orphaned resources before increasing a budget.

# Helm chart

Built in step 11 (packaging). Planned contents: PostgreSQL profile, at least
two replicas spread across machines, liveness and readiness checks
(`/healthz`, `/readyz`), an autoscaler, a PodDisruptionBudget, secrets for the
master key, certificates and admin bootstrap, and network policies that keep
the admin port private.

Real high availability needs three or more machines, with PostgreSQL and
Valkey run with failover.

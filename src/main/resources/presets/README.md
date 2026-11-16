# Rule presets

Default rule presets ship here as YAML, one file per preset, in the same format
the config export uses. They load on first start and can then be edited in the
admin panel.

```yaml
# example.yaml — format only, not real values
name: example-preset
version: 1
limits:
  requests_per_minute: 0
  requests_per_hour: 0
  requests_per_day: 0
  tokens_per_minute: 0
  tokens_per_day: 0
  concurrent_requests: 0
```

Real presets (for example the Google AI Studio free tier) are still an open
question in the spec: their values must be checked against each provider's
current published limits when they're written.

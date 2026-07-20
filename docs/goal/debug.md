# k3s:debug

Debugs k3s instance:

- collects k3s logs
- collects container logs
- collects exsting manifests

| Name             | User Property        | Description                                 | Default          |
| ---------------- | -------------------- | ------------------------------------------- | ---------------- |
| `debugDirectory` | `k3s.debugDirectory` | Path where debug data should by written to. | target/k3s/debug |
| `debugToStdout`  | `k3s.debugToStdout`  | Write debug data to stdout?                 | false            |
| `skipDebug`      | `k3s.skipDebug`      | Skip debugging manifests.                   | false            |

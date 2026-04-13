# Distributed Payment System

Java prototype of a fault-tolerant, replicated payment service: HTTP API, Raft-style consensus, replicated ledger, optional NTP time sync, and basic failure detection.

## Requirements

- Java 17+
- Maven 3.9+

## Quick start

```bash
mvn clean package
mvn test
```

Run a **single** process with three nodes (ports `5001`–`5003`):

```bash
mvn exec:java "-Dexec.args=--nodes 3 --port 5001"
```

Run **one node per terminal** (useful for fault demos). Same cluster shape, distinct index each time:

```powershell
mvn exec:java "-Dexec.args=--nodes 3 --port 5001 --node-index 0"
mvn exec:java "-Dexec.args=--nodes 3 --port 5001 --node-index 1"
mvn exec:java "-Dexec.args=--nodes 3 --port 5001 --node-index 2"
```

On Windows PowerShell, keep `"-Dexec.args=..."` quoted so Maven receives the full argument.

### Environment variables

| Variable | Purpose |
|----------|---------|
| `DS_NO_NTP=1` | Skip NTP when UDP 123 is blocked |
| `DS_UNSAFE_LONE_LEADER=0` | Disable demo-only “last node becomes leader” when peers are unreachable (not production-safe) |

Optional payment-provider placeholders: copy `.env.example` and set variables as needed.

## HTTP API

**Submit a payment** (followers forward to the Raft leader):

```bash
curl -X POST http://localhost:5001/pay -H "Content-Type: application/json" -d "{\"amount\":10.50,\"currency\":\"USD\"}"
```

**List payments:**

```bash
curl http://localhost:5001/payments
```

With multiple nodes, use any node’s port (`5001`, `5002`, …). The web dashboard is served on **node 0** when using `--node-index` or the first node in the multi-threaded cluster.

## Configuration

- **CLI**: `--nodes`, `--port`, `--host`, `--node-index` (see `com.dsproject.Main`).
- **Sample cluster YAML**: `config/cluster.example.yaml` (illustrative; the prototype is driven mainly by CLI args).

## Project layout

```
.
├── pom.xml
├── config/
│   └── cluster.example.yaml
├── src/main/java/com/dsproject/
│   ├── Main.java
│   ├── Config.java
│   ├── server/PaymentServer.java
│   ├── payment/
│   ├── replication/
│   ├── timesync/
│   ├── consensus/
│   ├── faulttolerance/
│   └── util/HttpJson.java
└── src/test/java/com/dsproject/
```

## Team

| Area | Name | Reg. no. | Email |
|------|------|----------|-------|
| Fault tolerance | _TBD_ | _TBD_ | _TBD_ |
| Replication & consistency | _TBD_ | _TBD_ | _TBD_ |
| Time synchronization | _TBD_ | _TBD_ | _TBD_ |
| Consensus | _TBD_ | _TBD_ | _TBD_ |

## License

Academic use — distributed systems coursework.

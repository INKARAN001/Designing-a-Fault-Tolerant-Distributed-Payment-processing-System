package com.dsproject.server;

import com.dsproject.consensus.RaftNode;
import com.dsproject.faulttolerance.Failover;
import com.dsproject.payment.PaymentProcessor;
import com.dsproject.payment.PaymentRecord;
import com.dsproject.replication.ReplicatedLedger;
import com.dsproject.timesync.TimeSync;
import com.dsproject.faulttolerance.FailureDetector;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP API (replaces Python Flask routes in {@code src/main.py}).
 */
@SuppressWarnings("unchecked")
public final class PaymentServer {

    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {
    }.getType();

    private PaymentServer() {
    }

    public static Javalin createApp(String nodeId, String nodeUrl, List<String> allPeerUrls) {
        ReplicatedLedger replicated = new ReplicatedLedger();
        PaymentProcessor processor = new PaymentProcessor();

        List<String> peers = new ArrayList<>(allPeerUrls);
        peers.removeIf(u -> normalize(u).equals(normalize(nodeUrl)));

        RaftNode raft = new RaftNode(nodeId, peers, replicated::applyCommitted);
        raft.start();

        TimeSync.setNtpOffset(null);

        FailureDetector failureDetector = new FailureDetector(peers, url ->
                System.getLogger(PaymentServer.class.getName()).log(
                        System.Logger.Level.WARNING, "Node detected as failed: {0}", url));
        failureDetector.start();

        Javalin app = Javalin.create();

        app.get("/health", ctx -> {
            Map<String, Object> m = new HashMap<>();
            m.put("status", "ok");
            m.put("node_id", nodeId);
            m.put("is_leader", raft.isLeader());
            ctx.json(m);
        });

        app.post("/raft/request-vote", ctx -> {
            Map<String, Object> body = parseBody(ctx.body());
            int term = (int) Math.round(toDouble(body.get("term")));
            String candidateId = body.get("candidate_id") != null ? String.valueOf(body.get("candidate_id")) : "";
            int lastLogIndex = (int) Math.round(toDouble(body.get("last_log_index")));
            int lastLogTerm = (int) Math.round(toDouble(body.get("last_log_term")));
            ctx.json(raft.requestVote(term, candidateId, lastLogIndex, lastLogTerm));
        });

        app.post("/raft/append-entries", ctx -> {
            Map<String, Object> body = parseBody(ctx.body());
            int term = (int) Math.round(toDouble(body.get("term")));
            String leaderId = body.get("leader_id") != null ? String.valueOf(body.get("leader_id")) : "";
            int prevLogIndex = (int) Math.round(toDouble(body.get("prev_log_index")));
            int prevLogTerm = (int) Math.round(toDouble(body.get("prev_log_term")));
            int leaderCommit = (int) Math.round(toDouble(body.get("leader_commit")));
            List<Map<String, Object>> entries = toEntryList(body.get("entries"));
            ctx.json(raft.appendEntries(term, leaderId, prevLogIndex, prevLogTerm, entries, leaderCommit));
        });

        app.post("/pay", ctx -> {
            Map<String, Object> body = parseBody(ctx.body());
            if (!raft.isLeader()) {
                Map<String, Object> err = new HashMap<>();
                err.put("error", "not_leader");
                err.put("message", "Submit payment to the leader");
                try {
                    err.put("retry_at", Failover.getLeaderOrAny(allPeerUrls));
                } catch (RuntimeException ignored) {
                    // no healthy node hint available
                }
                ctx.status(HttpStatus.SERVICE_UNAVAILABLE);
                ctx.json(err);
                return;
            }
            double amount = toDouble(body.get("amount"));
            String currency = body.get("currency") != null ? String.valueOf(body.get("currency")) : "USD";
            String idem = body.get("idempotency_key") != null ? String.valueOf(body.get("idempotency_key")) : null;

            PaymentRecord record = processor.process(amount, currency, idem);
            record = new PaymentRecord(
                    record.getTransactionId(),
                    record.getAmount(),
                    record.getCurrency(),
                    record.getStatus(),
                    TimeSync.getCorrectedTimestamp(),
                    -1,
                    record.getMetadata()
            );
            Map<String, Object> data = record.toMap();
            if (raft.propose(data)) {
                Map<String, Object> ok = new HashMap<>();
                ok.put("status", "accepted");
                ok.put("transaction_id", record.getTransactionId());
                ok.put("record", record.toMap());
                ctx.status(HttpStatus.CREATED);
                ctx.json(ok);
            } else {
                Map<String, Object> err = new HashMap<>();
                err.put("error", "proposal_failed");
                ctx.status(HttpStatus.SERVICE_UNAVAILABLE);
                ctx.json(err);
            }
        });

        app.get("/payments", ctx -> {
            boolean ordered = !"false".equalsIgnoreCase(ctx.queryParam("ordered"));
            String byTs = ctx.queryParam("by_timestamp");
            List<PaymentRecord> records = replicated.getAll(ordered);
            if ("true".equalsIgnoreCase(byTs)) {
                List<PaymentRecord> reordered = TimeSync.reorderByTimestamp(records);
                List<Map<String, Object>> list = new ArrayList<>();
                for (PaymentRecord r : reordered) {
                    list.add(r.toMap());
                }
                Map<String, Object> out = new HashMap<>();
                out.put("payments", list);
                ctx.json(out);
                return;
            }
            List<Map<String, Object>> list = new ArrayList<>();
            for (PaymentRecord r : records) {
                list.add(r.toMap());
            }
            Map<String, Object> out = new HashMap<>();
            out.put("payments", list);
            ctx.json(out);
        });

        app.get("/payments/{txId}", ctx -> {
            String txId = ctx.pathParam("txId");
            PaymentRecord record = replicated.getByTransactionId(txId);
            if (record == null) {
                Map<String, Object> err = new HashMap<>();
                err.put("error", "not_found");
                ctx.status(HttpStatus.NOT_FOUND);
                ctx.json(err);
                return;
            }
            ctx.json(record.toMap());
        });

        return app;
    }

    private static String normalize(String url) {
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }

    private static Map<String, Object> parseBody(String body) {
        if (body == null || body.isBlank()) {
            return new HashMap<>();
        }
        Map<String, Object> m = GSON.fromJson(body, MAP_TYPE);
        return m != null ? m : new HashMap<>();
    }

    private static List<Map<String, Object>> toEntryList(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof List) {
            List<?> list = (List<?>) raw;
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof Map) {
                    out.add((Map<String, Object>) o);
                }
            }
            return out;
        }
        return List.of();
    }

    private static double toDouble(Object o) {
        if (o == null) {
            return 0;
        }
        if (o instanceof Number) {
            return ((Number) o).doubleValue();
        }
        return Double.parseDouble(String.valueOf(o));
    }
}

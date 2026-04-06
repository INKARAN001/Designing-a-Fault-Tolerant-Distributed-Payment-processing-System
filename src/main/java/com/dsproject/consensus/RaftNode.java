package com.dsproject.consensus;

import com.dsproject.Config;
import com.dsproject.util.HttpJson;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * Raft-style consensus (replaces Python {@code src/consensus/raft.py}).
 */
public class RaftNode {

    private final String nodeId;
    private final List<String> peerUrls;
    private final Consumer<Map<String, Object>> onCommit;

    private int term;
    private Role role = Role.FOLLOWER;
    private String votedFor;
    private final List<LogEntry> log = new ArrayList<>();
    private int commitIndex = -1;

    private volatile double electionDeadlineSeconds;
    private volatile boolean running = true;
    private volatile boolean active = true;
    private Thread electionThread;
    private Thread heartbeatThread;

    public RaftNode(String nodeId, List<String> peerUrls, Consumer<Map<String, Object>> onCommit) {
        this.nodeId = nodeId;
        this.peerUrls = new ArrayList<>(peerUrls);
        this.onCommit = onCommit;
        resetElectionTimer();
    }

    private void resetElectionTimer() {
        int ms = ThreadLocalRandom.current().nextInt(Config.ELECTION_TIMEOUT_MIN_MS, Config.ELECTION_TIMEOUT_MAX_MS + 1);
        electionDeadlineSeconds = System.nanoTime() / 1_000_000_000.0 + ms / 1000.0;
    }

    public void start() {
        running = true;
        electionThread = new Thread(this::electionLoop, "raft-election-" + nodeId);
        electionThread.setDaemon(true);
        electionThread.start();
    }

    public void stop() {
        running = false;
    }

    private void electionLoop() {
        while (running) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException ignored) {
            }
            synchronized (this) {
                if (!active) {
                    continue;
                }
                if (role == Role.LEADER) {
                    continue;
                }
                double now = System.nanoTime() / 1_000_000_000.0;
                if (now < electionDeadlineSeconds) {
                    continue;
                }
                startElection();
            }
        }
    }

    private void startElection() {
        if (!active) {
            return;
        }
        term++;
        votedFor = nodeId;
        role = Role.CANDIDATE;
        resetElectionTimer();
        int votes = 1;
        int lastLogIndex = log.size() - 1;
        int lastLogTerm = log.isEmpty() ? 0 : log.get(log.size() - 1).getTerm();

        int peersUnreachable = 0;
        for (String url : peerUrls) {
            Map<String, Object> body = new HashMap<>();
            body.put("term", term);
            body.put("candidate_id", nodeId);
            body.put("last_log_index", lastLogIndex);
            body.put("last_log_term", lastLogTerm);
            Map<String, Object> res = HttpJson.postJson(url + "/raft/request-vote", body, Duration.ofMillis(300));
            if (Boolean.TRUE.equals(res.get("vote_granted"))) {
                votes++;
            }
            if (res.isEmpty()) {
                peersUnreachable++;
            }
        }

        if (role != Role.CANDIDATE) {
            return;
        }
        boolean majority = votes > peerUrls.size() / 2;
        boolean loneSurvivorDemo = unsafeLoneLeaderEnabled()
                && votes == 1
                && !peerUrls.isEmpty()
                && peersUnreachable == peerUrls.size();
        if (!majority && !loneSurvivorDemo) {
            return;
        }
        if (loneSurvivorDemo) {
            System.getLogger(RaftNode.class.getName()).log(System.Logger.Level.WARNING,
                    "UNSAFE demo mode: {0} became leader with no peer responses (no Raft quorum). "
                            + "Set DS_UNSAFE_LONE_LEADER=0 to disable.",
                    nodeId);
        }
        becomeLeader();
    }

    private void becomeLeader() {
        role = Role.LEADER;
        resetElectionTimer();
        if (heartbeatThread == null || !heartbeatThread.isAlive()) {
            heartbeatThread = new Thread(this::heartbeatLoop, "raft-heartbeat-" + nodeId);
            heartbeatThread.setDaemon(true);
            heartbeatThread.start();
        }
    }

    /**
     * When two peers are down, a real Raft cluster cannot elect a leader (no majority).
     * For demos, allow the last reachable node to take leadership when all peer RPCs fail.
     * Disable with {@code DS_UNSAFE_LONE_LEADER=0}.
     */
    private static boolean unsafeLoneLeaderEnabled() {
        String v = System.getenv("DS_UNSAFE_LONE_LEADER");
        if (v == null) {
            return true;
        }
        return !"0".equals(v) && !"false".equalsIgnoreCase(v);
    }

    private void heartbeatLoop() {
        while (running && role == Role.LEADER) {
            if (!active) {
                synchronized (this) {
                    role = Role.FOLLOWER;
                    votedFor = null;
                    resetElectionTimer();
                }
                continue;
            }
            try {
                Thread.sleep((long) (Config.HEARTBEAT_INTERVAL_SEC * 1000));
            } catch (InterruptedException ignored) {
            }
            broadcastAppendEntries();
        }
    }

    private void broadcastAppendEntries() {
        if (!active) {
            return;
        }
        Map<String, Object> payload;
        synchronized (this) {
            int prevIndex = log.size() - 1;
            int prevTerm = prevIndex >= 0 ? log.get(prevIndex).getTerm() : 0;
            // Heartbeats carry no new entries; payload stays small (see broadcastAppendEntriesSync for replication).
            List<Map<String, Object>> entries = new ArrayList<>();
            payload = new HashMap<>();
            payload.put("term", term);
            payload.put("leader_id", nodeId);
            payload.put("prev_log_index", prevIndex);
            payload.put("prev_log_term", prevTerm);
            payload.put("entries", entries);
            payload.put("leader_commit", commitIndex);
        }
        for (String url : peerUrls) {
            Map<String, Object> data = HttpJson.postJson(url + "/raft/append-entries", payload, Duration.ofMillis(500));
            if (Boolean.TRUE.equals(data.get("success")) && data.get("commit_index") != null) {
                synchronized (this) {
                    int ci = (int) Math.round(toDouble(data.get("commit_index")));
                    commitIndex = Math.max(commitIndex, ci);
                }
            }
        }
    }

    public synchronized Map<String, Object> requestVote(int term, String candidateId, int lastLogIndex, int lastLogTerm) {
        if (!active) {
            return mapOf("term", this.term, "vote_granted", false);
        }
        if (term < this.term) {
            return mapOf("term", this.term, "vote_granted", false);
        }
        if (term > this.term) {
            this.term = term;
            this.votedFor = null;
            this.role = Role.FOLLOWER;
        }
        int myLast = log.size() - 1;
        int myLastTerm = myLast >= 0 ? log.get(myLast).getTerm() : 0;
        if (votedFor != null && !votedFor.equals(candidateId)) {
            return mapOf("term", this.term, "vote_granted", false);
        }
        if (compareLog(lastLogTerm, lastLogIndex, myLastTerm, myLast) < 0) {
            return mapOf("term", this.term, "vote_granted", false);
        }
        votedFor = candidateId;
        resetElectionTimer();
        return mapOf("term", this.term, "vote_granted", true);
    }

    private static int compareLog(int lastTerm, int lastIndex, int myTerm, int myIndex) {
        if (lastTerm != myTerm) {
            return Integer.compare(lastTerm, myTerm);
        }
        return Integer.compare(lastIndex, myIndex);
    }

    public synchronized Map<String, Object> appendEntries(
            int term,
            String leaderId,
            int prevLogIndex,
            int prevLogTerm,
            List<Map<String, Object>> entries,
            int leaderCommit
    ) {
        if (!active) {
            return mapOf("term", this.term, "success", false);
        }
        if (term < this.term) {
            return mapOf("term", this.term, "success", false);
        }
        this.term = term;
        this.role = Role.FOLLOWER;
        this.votedFor = null;
        resetElectionTimer();

        if (prevLogIndex >= 0 && (prevLogIndex >= log.size() || log.get(prevLogIndex).getTerm() != prevLogTerm)) {
            return mapOf("term", this.term, "success", false);
        }

        for (int i = 0; i < entries.size(); i++) {
            Map<String, Object> e = entries.get(i);
            int idx = prevLogIndex + 1 + i;
            LogEntry le = LogEntry.fromMap(e);
            if (idx < log.size()) {
                if (log.get(idx).getTerm() != le.getTerm()) {
                    while (log.size() > idx) {
                        log.remove(log.size() - 1);
                    }
                }
            }
            if (idx >= log.size()) {
                log.add(le);
            }
        }

        int newCommit = Math.min(leaderCommit, log.size() - 1);
        if (newCommit > commitIndex) {
            for (int j = commitIndex + 1; j <= newCommit; j++) {
                if (j < log.size()) {
                    onCommit.accept(log.get(j).getData());
                }
            }
            commitIndex = newCommit;
        }

        Map<String, Object> out = new HashMap<>();
        out.put("term", this.term);
        out.put("success", true);
        out.put("commit_index", commitIndex);
        return out;
    }

    public synchronized boolean propose(Map<String, Object> data) {
        if (!active || role != Role.LEADER) {
            return false;
        }
        int idx = log.size();
        log.add(new LogEntry(idx, term, data));
        broadcastAppendEntriesSync();
        return true;
    }

    private void broadcastAppendEntriesSync() {
        if (!active) {
            return;
        }
        int prevIndex = log.size() - 2;
        int prevTerm = prevIndex >= 0 ? log.get(prevIndex).getTerm() : 0;
        List<Map<String, Object>> entries = new ArrayList<>();
        entries.add(log.get(log.size() - 1).toMap());
        Map<String, Object> payload = new HashMap<>();
        payload.put("term", term);
        payload.put("leader_id", nodeId);
        payload.put("prev_log_index", prevIndex);
        payload.put("prev_log_term", prevTerm);
        payload.put("entries", entries);
        payload.put("leader_commit", commitIndex);

        int acks = 0;
        int peersUnreachable = 0;
        for (String url : peerUrls) {
            Map<String, Object> res = HttpJson.postJson(url + "/raft/append-entries", payload, Duration.ofSeconds(1));
            if (Boolean.TRUE.equals(res.get("success"))) {
                acks++;
            }
            if (res.isEmpty()) {
                peersUnreachable++;
            }
        }
        boolean quorum = acks >= peerUrls.size() / 2;
        if (!quorum && unsafeLoneLeaderEnabled() && !peerUrls.isEmpty()
                && peersUnreachable == peerUrls.size() && acks == 0) {
            quorum = true;
            System.getLogger(RaftNode.class.getName()).log(System.Logger.Level.WARNING,
                    "UNSAFE demo mode: committing without follower acks (no quorum).");
        }
        if (quorum) {
            commitIndex = log.size() - 1;
            onCommit.accept(log.get(log.size() - 1).getData());
        }
    }

    public synchronized boolean isLeader() {
        return active && role == Role.LEADER;
    }

    public synchronized void setActive(boolean active) {
        this.active = active;
        if (!active) {
            role = Role.FOLLOWER;
            votedFor = null;
        }
        resetElectionTimer();
    }

    public synchronized List<Map<String, Object>> getLogForReplication() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (LogEntry e : log) {
            out.add(e.toMap());
        }
        return out;
    }

    private static Map<String, Object> mapOf(String k1, Object v1, String k2, Object v2) {
        Map<String, Object> m = new HashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        return m;
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

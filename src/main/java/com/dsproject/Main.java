package com.dsproject;

import com.dsproject.server.PaymentServer;
import io.javalin.Javalin;

import java.util.ArrayList;
import java.util.List;

/**
 * Cluster entry point (replaces Python {@code src/main.py}).
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        int numNodes = 3;
        int basePort = 5001;
        String host = "localhost";
        Integer nodeIndex = null;

        for (int i = 0; i < args.length; i++) {
            if ("--nodes".equals(args[i]) && i + 1 < args.length) {
                numNodes = Integer.parseInt(args[++i]);
            } else if ("--port".equals(args[i]) && i + 1 < args.length) {
                basePort = Integer.parseInt(args[++i]);
            } else if ("--host".equals(args[i]) && i + 1 < args.length) {
                host = args[++i];
            } else if ("--node-index".equals(args[i]) && i + 1 < args.length) {
                nodeIndex = Integer.parseInt(args[++i]);
            }
        }

        List<String> urls = Config.getNodeUrls(host, numNodes, basePort);

        if (nodeIndex != null) {
            if (nodeIndex < 0 || nodeIndex >= numNodes) {
                System.err.println("--node-index must be between 0 and " + (numNodes - 1) + " for --nodes " + numNodes);
                System.exit(1);
                return;
            }
            String nodeId = "node" + nodeIndex;
            int port = basePort + nodeIndex;
            boolean dashboard = nodeIndex == 0;
            Javalin app = PaymentServer.createApp(nodeId, urls.get(nodeIndex), urls, dashboard);
            app.start(port);
            return;
        }

        if (numNodes == 1) {
            String nodeId = "node0";
            Javalin app = PaymentServer.createApp(nodeId, urls.get(0), urls, true);
            app.start(basePort);
            return;
        }

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < numNodes; i++) {
            final int idx = i;
            final int port = basePort + i;
            String nodeId = "node" + i;
            Thread t = new Thread(() -> {
                boolean dashboard = idx == 0;
                Javalin app = PaymentServer.createApp(nodeId, urls.get(idx), urls, dashboard);
                app.start(port);
            }, "node-" + i);
            t.setDaemon(false);
            t.start();
            threads.add(t);
            try {
                Thread.sleep(300);
            } catch (InterruptedException ignored) {
            }
        }

        System.getLogger(Main.class.getName()).log(System.Logger.Level.INFO, "Cluster started: {0}", urls);

        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException ignored) {
            }
        }
    }
}

package com.dsproject.consensus;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unchecked")
public class LogEntry {
    private final int index;
    private final int term;
    private final Map<String, Object> data;

    public LogEntry(int index, int term, Map<String, Object> data) {
        this.index = index;
        this.term = term;
        this.data = data == null ? new HashMap<>() : new HashMap<>(data);
    }

    public int getIndex() {
        return index;
    }

    public int getTerm() {
        return term;
    }

    public Map<String, Object> getData() {
        return new HashMap<>(data);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("index", index);
        m.put("term", term);
        m.put("data", new HashMap<>(data));
        return m;
    }

    public static LogEntry fromMap(Map<String, Object> e) {
        int index = (int) Math.round(toDouble(e.get("index")));
        int term = (int) Math.round(toDouble(e.get("term")));
        Object dataObj = e.get("data");
        Map<String, Object> data = new HashMap<>();
        if (dataObj instanceof Map) {
            data.putAll((Map<String, Object>) dataObj);
        }
        return new LogEntry(index, term, data);
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

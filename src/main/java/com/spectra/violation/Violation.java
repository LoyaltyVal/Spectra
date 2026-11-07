package com.spectra.violation;

/** One recorded flag: the evidence staff see in alerts, /spectra logs and the log files. */
public record Violation(
        long timestamp,
        String checkId,
        String checkName,
        double vl,
        String info,
        int ping,
        double tps,
        AlertLevel level,
        boolean experimental
) {
}

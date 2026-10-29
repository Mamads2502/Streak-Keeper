package fr.streakkeeper.app;

/** A local observation of submission, not a confirmation of restoration. */
final class RecentRequest {
    static final long MINUTE = 60_000L;
    static final long HOUR = 60 * MINUTE;
    static final long DAY = 24 * HOUR;
    private RecentRequest() {}

    static String elapsed(long sentAt, long now) {
        if (sentAt <= 0 || now < sentAt) return null;
        long elapsed = now - sentAt;
        if (elapsed >= DAY) return null;
        return elapsed < HOUR ? (elapsed / MINUTE) + " min" : (elapsed / HOUR) + " h";
    }
}

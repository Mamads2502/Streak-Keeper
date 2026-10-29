package fr.streakkeeper.app;

import java.text.Collator;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Pure list projection: never reorders or modifies persisted friends. */
final class FriendList {
    interface Entry {
        String displayName();
        String snapchatUsername();
        long addedAt();
    }
    enum Order {
        NAME("Nom A–Z"), NEWEST("Plus récents"), OLDEST("Plus anciens");
        final String label;
        Order(String label) { this.label = label; }
        static Order restore(String value) {
            try { return valueOf(value); } catch (IllegalArgumentException e) { return OLDEST; }
        }
    }

    static <T extends Entry> List<T> select(List<T> source, String query, Order order) {
        String normalized = normalize(query).replace('@', ' ').trim();
        String[] terms = normalized.isEmpty() ? new String[0] : normalized.split("\\s+");
        Map<T, Integer> positions = new IdentityHashMap<>();
        List<T> result = new ArrayList<>();
        for (int i = 0; i < source.size(); i++) {
            T friend = source.get(i);
            positions.put(friend, i);
            String name = normalize(friend.displayName());
            String username = normalize(friend.snapchatUsername());
            boolean matches = true;
            for (String term : terms) {
                if (!name.contains(term) && !username.contains(term)) { matches = false; break; }
            }
            if (matches) result.add(friend);
        }
        Comparator<T> comparator;
        if (order == Order.NAME) {
            Collator collator = Collator.getInstance(Locale.FRENCH);
            collator.setStrength(Collator.PRIMARY);
            comparator = (a, b) -> {
                int comparison = collator.compare(a.displayName(), b.displayName());
                return comparison != 0 ? comparison : collator.compare(a.snapchatUsername(), b.snapchatUsername());
            };
        } else {
            comparator = Comparator.comparingLong((T f) -> f.addedAt()).thenComparingInt(positions::get);
            if (order == Order.NEWEST) comparator = comparator.reversed();
        }
        result.sort(comparator);
        return result;
    }

    static <T extends Entry> boolean containsUsername(List<T> friends, String username, T except) {
        for (T friend : friends) {
            if (friend != except && friend.snapchatUsername().equalsIgnoreCase(username.trim())) return true;
        }
        return false;
    }

    static <T extends Entry> boolean copyTo(T source, List<T> destination,
            java.util.function.BiFunction<String, String, T> create) {
        if (containsUsername(destination, source.snapchatUsername(), null)) return false;
        destination.add(create.apply(source.displayName(), source.snapchatUsername()));
        return true;
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).trim();
    }
}

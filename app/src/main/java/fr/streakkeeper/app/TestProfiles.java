package fr.streakkeeper.app;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Helpers for locally generated test data. */
final class TestProfiles {
    static final int MAX_FRIENDS = 500;
    private static final String LETTERS = "abcdefghijklmnopqrstuvwxyz";
    private static final String ALPHABET = LETTERS + "0123456789_";

    private TestProfiles() {}

    static int count(String input) {
        String value = input.trim();
        if (value.isEmpty()) return 10;
        if (!value.matches("[0-9]+")) return -1;
        try {
            int count = Integer.parseInt(value);
            return count > 0 && count <= MAX_FRIENDS ? count : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    static List<String> usernames(int count, Random random) {
        Set<String> used = new HashSet<>();
        List<String> result = new ArrayList<>();
        while (result.size() < count) {
            int length = 7 + random.nextInt(9);
            StringBuilder name = new StringBuilder("test_");
            name.append(LETTERS.charAt(random.nextInt(LETTERS.length())));
            while (name.length() < length) name.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            if (used.add(name.toString())) result.add(name.toString());
        }
        return result;
    }
}

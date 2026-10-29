package fr.streakkeeper.app;

import java.util.HashSet;
import java.util.List;
import java.util.Random;

public class TestProfilesTest {
    public static void main(String[] args) {
        if (TestProfiles.count("") != 10 || TestProfiles.count(" 7 ") != 7
                || TestProfiles.count("500") != 500 || TestProfiles.count("0") != -1
                || TestProfiles.count("501") != -1 || TestProfiles.count("-1") != -1
                || TestProfiles.count("1.5") != -1 || TestProfiles.count("999999999999999999999") != -1)
            throw new AssertionError("Invalid friend count handling");
        List<String> names = TestProfiles.usernames(500, new Random(1));
        if (names.size() != 500 || new HashSet<>(names).size() != 500)
            throw new AssertionError("Usernames must be unique");
        boolean shortName = false, longName = false;
        for (String name : names) {
            if (!name.matches("test_[a-z][a-z0-9_]{1,9}")) throw new AssertionError(name);
            shortName |= name.length() == 7;
            longName |= name.length() == 15;
        }
        if (!shortName || !longName) throw new AssertionError("Expected varying lengths");
        System.out.println("OK test profile counts and unique usernames");
    }
}

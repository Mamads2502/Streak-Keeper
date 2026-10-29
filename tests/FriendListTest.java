package fr.streakkeeper.app;

import java.util.ArrayList;
import java.util.List;

/** Run with javac/java; independent of Android and network dependencies. */
public final class FriendListTest {
    private record Item(String displayName, String snapchatUsername, long addedAt) implements FriendList.Entry {}
    private static void check(String name, List<Item> actual, Item... expected) {
        if (!actual.equals(List.of(expected))) throw new AssertionError(name + ": " + actual);
        System.out.println("OK " + name);
    }
    public static void main(String[] args) {
        Item zoe = new Item("Zoé du lycée", "z_92", 0);
        Item elodie = new Item("Élodie", "LOLO.paris", 0);
        Item adam = new Item("Adam", "adam75", 100);
        Item emile = new Item("Émile", "milo", 100);
        List<Item> input = new ArrayList<>(List.of(zoe, elodie, adam, emile));
        check("Legacy insertion order and timestamp ties", FriendList.select(input, "", FriendList.Order.OLDEST), zoe, elodie, adam, emile);
        check("Newest first, including imports", FriendList.select(input, "", FriendList.Order.NEWEST), emile, adam, elodie, zoe);
        check("French alphabetical order", FriendList.select(input, "", FriendList.Order.NAME), adam, elodie, emile, zoe);
        check("Case and accent insensitive", FriendList.select(input, "ELODIE", FriendList.Order.NAME), elodie);
        check("Snapchat username with @", FriendList.select(input, " @lolo.PAR ", FriendList.Order.NAME), elodie);
        check("Terms spanning both names", FriendList.select(input, "lycee @Z_92", FriendList.Order.NAME), zoe);
        check("Partial nickname", FriendList.select(input, "mil", FriendList.Order.NAME), emile);
        check("Whitespace query", FriendList.select(input, "  \t ", FriendList.Order.OLDEST), zoe, elodie, adam, emile);
        check("No match", FriendList.select(input, "inconnu", FriendList.Order.NAME));
        check("Filter retains selected sort", FriendList.select(input, "e", FriendList.Order.NEWEST), emile, elodie, zoe);
        check("Original list unchanged", input, zoe, elodie, adam, emile);
        if (FriendList.Order.restore("unknown") != FriendList.Order.OLDEST) throw new AssertionError("Invalid saved sort");
        System.out.println("OK invalid saved sort fallback");
        List<Item> destination = new ArrayList<>();
        if (!FriendList.copyTo(zoe, destination, (name, username) -> new Item(name, username, 200)))
            throw new AssertionError("First copy rejected");
        Item copy = destination.get(0);
        if (copy == zoe || !copy.displayName().equals(zoe.displayName())
                || !copy.snapchatUsername().equals(zoe.snapchatUsername()) || copy.addedAt() != 200)
            throw new AssertionError("Copy must be independent with a new date");
        System.out.println("OK independent copy with new date");
        if (zoe.addedAt() != 0 || input.size() != 4) throw new AssertionError("Source changed");
        System.out.println("OK copy preserves source");
        Item duplicate = new Item("Another nickname", "Z_92", 300);
        if (FriendList.copyTo(duplicate, destination, (name, username) -> new Item(name, username, 300)))
            throw new AssertionError("Case insensitive duplicate accepted");
        System.out.println("OK copy rejects duplicate username");
        if (destination.size() != 1 || destination.get(0) != copy) throw new AssertionError("Destination overwritten");
        System.out.println("OK duplicate does not overwrite nickname or date");
        if (FriendList.containsUsername(input, " Z_92 ", zoe)) throw new AssertionError("Unchanged username rejected");
        System.out.println("OK edit excludes its own entry");
        if (!FriendList.containsUsername(input, "lolo.PARIS", zoe)) throw new AssertionError("Edit collision missed");
        System.out.println("OK edit detects another friend's username");
    }
}

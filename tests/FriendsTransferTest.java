package fr.streakkeeper.app;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Executed on Android with app_process; all data below are fictitious. */
public final class FriendsTransferTest {
    private static ByteArrayInputStream input(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }
    private static void check(boolean condition) { if (!condition) throw new AssertionError(); }
    private static void reject(String text) throws Exception {
        try { FriendsTransfer.read(input(text)); throw new AssertionError("invalid file accepted"); }
        catch (IOException expected) { }
    }
    public static void main(String[] args) throws Exception {
        FriendsTransfer.Entry friend = new FriendsTransfer.Entry("Pseudo choisi", "ami_fictif", 1234, true, 5678);
        List<FriendsTransfer.Entry> roundTrip = FriendsTransfer.read(input(FriendsTransfer.write(Arrays.asList(friend))));
        check(roundTrip.size() == 1);
        FriendsTransfer.Entry restored = roundTrip.get(0);
        check(restored.name.equals(friend.name) && restored.username.equals(friend.username));
        check(restored.addedAt == 1234 && restored.favorite && restored.lastRequestAt == 5678);
        check(FriendsTransfer.read(input(FriendsTransfer.write(Arrays.asList()))).isEmpty());
        reject("{}");
        reject("{\"format\":\"streak-keeper-friends\",\"version\":2,\"friends\":[]}");
        reject("{\"format\":\"streak-keeper-friends\",\"version\":1,\"friends\":[{}]}");
        reject("not json or zip");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("json/friends.json"));
            zip.write(("{\"Friends\":[{\"Username\":\"fake_user\",\"Display Name\":\"Surnom\"}],"
                    + "\"Deleted Friends\":[{\"Username\":\"old_user\"}]}").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        List<FriendsTransfer.Entry> zipFriends = FriendsTransfer.read(new ByteArrayInputStream(bytes.toByteArray()));
        check(zipFriends.size() == 1 && zipFriends.get(0).username.equals("fake_user")
                && zipFriends.get(0).name.equals("Surnom"));
        System.out.println("FriendsTransferTest: round-trip, empty, invalid, version and ZIP OK");
    }
}

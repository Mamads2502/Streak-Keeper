package fr.streakkeeper.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Versioned, local friend-list interchange format. */
final class FriendsTransfer {
    private static final String FORMAT = "streak-keeper-friends";
    private static final int VERSION = 1;
    private static final int MAX_BYTES = 8 * 1024 * 1024;

    static final class Entry {
        final String name, username;
        final long addedAt, lastRequestAt;
        final boolean favorite;
        Entry(String name, String username, long addedAt, boolean favorite, long lastRequestAt) {
            this.name = name; this.username = username; this.addedAt = addedAt;
            this.favorite = favorite; this.lastRequestAt = lastRequestAt;
        }
    }

    static String write(List<Entry> entries) throws JSONException {
        JSONArray friends = new JSONArray();
        for (Entry entry : entries) friends.put(new JSONObject()
                .put("username", entry.username).put("name", entry.name)
                .put("addedAt", entry.addedAt).put("favorite", entry.favorite)
                .put("lastRequestAt", entry.lastRequestAt));
        return new JSONObject().put("format", FORMAT).put("version", VERSION)
                .put("friends", friends).toString(2);
    }

    static List<Entry> read(InputStream stream) throws IOException, JSONException {
        PushbackInputStream input = new PushbackInputStream(stream, 4);
        byte[] signature = new byte[4];
        int n = 0;
        while (n < signature.length) {
            int count = input.read(signature, n, signature.length - n);
            if (count < 0) break;
            if (count == 0) continue;
            n += count;
        }
        input.unread(signature, 0, n);
        if (n >= 2 && signature[0] == 'P' && signature[1] == 'K') {
            List<Entry> result = new ArrayList<>();
            for (SnapchatImport.Entry entry : SnapchatImport.read(input))
                result.add(new Entry(entry.name, entry.username, System.currentTimeMillis(), false, 0));
            return result;
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (bytes.size() + count > MAX_BYTES) throw new IOException("Le fichier JSON est trop volumineux.");
            bytes.write(buffer, 0, count);
        }
        JSONObject root;
        try {
            String json = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
            if (json.startsWith("\ufeff")) json = json.substring(1);
            root = new JSONObject(json);
        } catch (JSONException e) {
            throw new IOException("Fichier invalide : sélectionne un export JSON de l’application ou un ZIP Snapchat.");
        }
        if (!FORMAT.equals(root.optString("format")))
            throw new IOException("Format JSON non reconnu : sélectionne un export de Streak keeper ou un ZIP Snapchat.");
        if (root.optInt("version", -1) != VERSION)
            throw new IOException("Cette version du fichier JSON n’est pas prise en charge.");
        JSONArray friends = root.optJSONArray("friends");
        if (friends == null) throw new IOException("Le fichier JSON ne contient pas de liste d’amis valide.");
        List<Entry> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < friends.length(); i++) {
            JSONObject friend = friends.optJSONObject(i);
            if (friend == null || !(friend.opt("username") instanceof String)
                    || !(friend.opt("name") instanceof String)
                    || !(friend.opt("favorite") instanceof Boolean)
                    || !(friend.opt("addedAt") instanceof Number)
                    || !(friend.opt("lastRequestAt") instanceof Number))
                throw new IOException("Le fichier JSON contient un ami incomplet ou invalide.");
            String username = friend.optString("username").trim();
            String name = friend.optString("name");
            long addedAt = friend.optLong("addedAt", -1), lastRequestAt = friend.optLong("lastRequestAt", -1);
            if (username.isEmpty() || addedAt < 0 || lastRequestAt < 0)
                throw new IOException("Le fichier JSON contient un ami incomplet ou invalide.");
            if (seen.add(username.toLowerCase(Locale.ROOT)))
                result.add(new Entry(name, username, addedAt, friend.optBoolean("favorite"), lastRequestAt));
        }
        return result;
    }
}

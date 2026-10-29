package fr.streakkeeper.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

/** Reads only the current friends list; all processing stays on the device. */
final class SnapchatImport {
    private static final int MAX_FRIENDS_BYTES = 8 * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 128L * 1024 * 1024;

    static final class Entry {
        final String name, username;
        Entry(String name, String username) { this.name = name; this.username = username; }
    }

    static List<Entry> read(InputStream input) throws IOException, JSONException {
        byte[] buffer = new byte[8192];
        long total = 0;
        boolean sawEntry = false;
        byte[] friends = null;
        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                sawEntry = true;
                String path = entry.getName().replace('\\', '/').toLowerCase(Locale.ROOT);
                boolean wanted = !entry.isDirectory() && (path.equals("json/friends.json") || path.endsWith("/json/friends.json"));
                if (wanted && friends != null) throw new IOException("L’archive contient plusieurs fichiers friends.json. Sélectionne un export non modifié.");
                ByteArrayOutputStream out = wanted ? new ByteArrayOutputStream() : null;
                int count;
                while ((count = zip.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_TOTAL_BYTES) throw new IOException("L’export est trop volumineux pour être analysé.");
                    if (wanted) {
                        if (out.size() + count > MAX_FRIENDS_BYTES) throw new IOException("La liste d’amis est trop volumineuse.");
                        out.write(buffer, 0, count);
                    }
                }
                if (wanted) friends = out.toByteArray();
                zip.closeEntry();
            }
        } catch (ZipException | java.io.EOFException e) {
            throw new IOException("Le fichier ZIP est invalide ou endommagé.");
        }
        if (!sawEntry) throw new IOException("Le fichier ZIP est vide ou invalide.");
        if (friends == null) throw new IOException("Fichier json/friends.json absent. Redemande l’export en sélectionnant les données utilisateur et le format JSON.");
        JSONObject root;
        try { root = new JSONObject(new String(friends, StandardCharsets.UTF_8)); }
        catch (JSONException e) { throw new IOException("Le fichier friends.json est invalide ou incomplet."); }
        JSONArray array = root.optJSONArray("Friends");
        if (array == null) throw new IOException("La catégorie Friends est absente ou invalide dans friends.json.");
        List<Entry> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            String username = field(item, "Username", "username", "User Name");
            if (username.startsWith("@")) username = username.substring(1).trim();
            if (username.isEmpty() || !seen.add(username.toLowerCase(Locale.ROOT))) continue;
            String name = field(item, "Display Name", "display_name", "displayName");
            result.add(new Entry(name.isEmpty() ? username : name, username));
        }
        return result;
    }

    private static String field(JSONObject item, String... keys) {
        for (String key : keys) {
            Object value = item.opt(key);
            if (value instanceof String && !((String) value).trim().isEmpty()) return ((String) value).trim();
        }
        return "";
    }
}

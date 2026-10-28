package fr.streakpresets.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.drawerlayout.widget.DrawerLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Unofficial personal experiment. Never submits Snapchat forms in the background.
 * The user sees the official page and submits it manually.
 */
public class MainActivity extends Activity {
    private static final String FORM_URL =
            "https://help.snapchat.com/hc/fr-fr/requests/new?co=true&ticket_form_id=149423";
    private static final String STORAGE_KEY = "streak_presets_v1";

    private final List<Profile> profiles = new ArrayList<>();
    private Profile active;
    private Profile pendingProfile;
    private Friend pendingFriend;
    private ScrollView home;
    private LinearLayout homePanel;
    private DrawerLayout drawerLayout;
    private LinearLayout drawerPanel;
    private LinearLayout drawerProfiles;
    private Button menuButton;
    private WebView browser;
    private TextView browserStatus;

    static final class Friend {
        String name, username;

        Friend(String name, String username) {
            this.name = name;
            this.username = username;
        }

        JSONObject json() throws JSONException {
            return new JSONObject().put("name", name).put("username", username);
        }
    }

    static final class Profile {
        String id = UUID.randomUUID().toString();
        String name = "";
        String username = "";
        String email = "";
        String phone = "";
        String description = "";
        boolean descriptionEnabled = false;
        final List<Friend> friends = new ArrayList<>();

        JSONObject json() throws JSONException {
            JSONArray friendJson = new JSONArray();
            for (Friend f : friends) friendJson.put(f.json());
            return new JSONObject()
                    .put("id", id).put("name", name).put("username", username)
                    .put("email", email)
                    .put("phone", phone).put("description", description)
                    .put("descriptionEnabled", descriptionEnabled)
                    .put("friends", friendJson);
        }

        static Profile fromJson(JSONObject data) {
            Profile p = new Profile();
            p.id = data.optString("id", p.id);
            p.name = data.optString("name");
            p.username = data.optString("username");
            p.email = data.optString("email");
            // Migrate existing presets, preferring the former contact address.
            if (p.email.trim().isEmpty()) p.email = data.optString("contactEmail");
            if (p.email.trim().isEmpty()) p.email = data.optString("accountEmail");
            p.phone = data.optString("phone");
            p.description = data.optString("description");
            p.descriptionEnabled = data.optBoolean("descriptionEnabled", !p.description.isEmpty());
            JSONArray friends = data.optJSONArray("friends");
            if (friends != null) for (int i = 0; i < friends.length(); i++) {
                JSONObject item = friends.optJSONObject(i);
                if (item != null) p.friends.add(new Friend(
                        item.optString("name"), item.optString("username")));
            }
            return p;
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(252, 252, 252));
        getWindow().setNavigationBarColor(Color.rgb(252, 252, 252));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        restoreData();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);
        drawerLayout = new DrawerLayout(this);
        drawerLayout.setScrimColor(0x660F172A);
        drawerLayout.setDrawerElevation(dp(12));
        drawerLayout.addView(root, new DrawerLayout.LayoutParams(-1, -1));
        setContentView(drawerLayout);

        LinearLayout browserPanel = vertical();
        browserPanel.setBackgroundColor(Color.WHITE);
        root.addView(browserPanel, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout topBar = vertical();
        topBar.setPadding(dp(12), dp(8), dp(12), dp(8));
        topBar.setBackgroundColor(Color.rgb(246, 246, 246));
        LinearLayout actions = horizontal();
        Button back = button("← Presets", () -> showHome());
        Button refill = button("Re-remplir", () -> fillForm(0));
        actions.addView(back, new LinearLayout.LayoutParams(0, dp(48), 1));
        actions.addView(refill, new LinearLayout.LayoutParams(0, dp(48), 1));
        topBar.addView(actions);
        browserStatus = text("Le formulaire officiel reste sous le contrôle de l’utilisateur.", 12, false);
        topBar.addView(browserStatus);
        browserPanel.addView(topBar);

        browser = new WebView(this);
        browser.setBackgroundColor(Color.WHITE);
        WebSettings webSettings = browser.getSettings();
        webSettings.setJavaScriptEnabled(true); // The real help form needs JavaScript.
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(false);
        webSettings.setAllowContentAccess(false);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(browser, true);
        browser.setWebChromeClient(new WebChromeClient());
        browser.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                if (pendingProfile != null && url.startsWith("https://help.snapchat.com/hc/fr-fr/requests/new")) {
                    fillForm(0);
                }
            }

            @Override public void onReceivedError(WebView view, WebResourceRequest request,
                                                   WebResourceError error) {
                if (request.isForMainFrame()) {
                    browserStatus.setText("Chargement impossible. Vérifie Internet ou ouvre le site plus tard.");
                }
            }
        });
        browserPanel.addView(browser, new LinearLayout.LayoutParams(-1, 0, 1));

        homePanel = vertical();
        homePanel.setBackgroundColor(Color.rgb(250, 250, 250));
        root.addView(homePanel, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout toolbar = horizontal();
        toolbar.setPadding(dp(8), dp(8), dp(16), dp(8));
        toolbar.setBackgroundColor(Color.WHITE);
        menuButton = button("☰", () -> drawerLayout.openDrawer(drawerPanel));
        menuButton.setTextSize(24);
        menuButton.setContentDescription("Ouvrir le menu des profils");
        styleDrawerButton(menuButton, false);
        menuButton.setPadding(0, 0, 0, 0);
        toolbar.addView(menuButton, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = text("🔥 Streak keeper", 22, true);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        homePanel.addView(toolbar);

        home = new ScrollView(this);
        home.setFillViewport(true);
        home.setBackgroundColor(Color.rgb(250, 250, 250));
        homePanel.addView(home, new LinearLayout.LayoutParams(-1, 0, 1));
        createProfileDrawer();
        renderHome();
        if (state != null && state.getBoolean("profileDrawerOpen")) {
            drawerLayout.post(() -> drawerLayout.openDrawer(drawerPanel, false));
        }
        browser.loadUrl(FORM_URL); // Load beneath the presets, but never send a request.
    }

    private void showHome() {
        pendingProfile = null;
        pendingFriend = null;
        renderHome();
        homePanel.setVisibility(View.VISIBLE);
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
    }

    private void renderHome() {
        LinearLayout page = vertical();
        page.setPadding(dp(16), dp(20), dp(16), dp(28));
        renderDrawerProfiles();
        if (active == null) {
            gap(page, 25);
            page.addView(text("Ouvre le menu ☰ pour ajouter ton premier profil Snapchat.", 16, false));
            home.removeAllViews();
            home.addView(page);
            return;
        }

        gap(page, 12);
        page.addView(text("Profil actif : " + active.name, 18, true));
        page.addView(text("@" + active.username + " · " + active.email, 13, false));

        gap(page, 18);
        page.addView(text("Amis de " + active.name + " (" + active.friends.size() + ")", 19, true));
        page.addView(button("+ Ajouter un ami", this::addFriend));
        page.addView(button("Copier les amis d’un autre profil", this::copyFriends));
        gap(page, 8);
        if (active.friends.isEmpty()) {
            page.addView(text("Aucun ami enregistré. Ajoute-en un pour commencer.", 14, false));
        }

        for (Friend friend : new ArrayList<>(active.friends)) {
            LinearLayout friendRow = horizontal();
            friendRow.setPadding(dp(4), dp(4), dp(4), dp(4));
            LinearLayout labelBox = vertical();
            labelBox.addView(text(friend.name, 16, true));
            labelBox.addView(text("@" + friend.username, 12, false));
            friendRow.addView(labelBox, new LinearLayout.LayoutParams(0, -2, 1));
            friendRow.addView(button("Préparer", () -> prepare(friend)));
            friendRow.addView(button("×", () -> removeFriend(friend)));
            page.addView(friendRow);
            View line = new View(this);
            line.setBackgroundColor(Color.rgb(227, 227, 227));
            page.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
        }

        gap(page, 20);
        page.addView(text("Comment ça marche ?", 16, true));
        page.addView(text("Choisis un ami. L’application préremplit les champs connus du formulaire officiel, puis te laisse vérifier le CAPTCHA et cliquer toi-même sur Envoyer.", 13, false));
        gap(page, 10);
        page.addView(text("Projet personnel non affilié à Snapchat. Aucun mot de passe n’est demandé et aucune demande n’est envoyée automatiquement.", 12, false));
        home.removeAllViews();
        home.addView(page);
    }

    private void createProfileDrawer() {
        drawerPanel = vertical();
        drawerPanel.setBackgroundColor(Color.WHITE);
        drawerPanel.setPadding(dp(16), dp(16), dp(16), dp(16));
        int width = Math.min(dp(320), getResources().getDisplayMetrics().widthPixels - dp(56));
        DrawerLayout.LayoutParams params = new DrawerLayout.LayoutParams(width, -1);
        params.gravity = Gravity.LEFT;
        drawerLayout.addView(drawerPanel, params);
        drawerLayout.setDrawerTitle(Gravity.LEFT, "Profils Snapchat");

        LinearLayout heading = horizontal();
        heading.addView(text("Mes profils", 24, true), new LinearLayout.LayoutParams(0, -2, 1));
        Button close = button("×", () -> drawerLayout.closeDrawer(drawerPanel));
        close.setTextSize(26);
        close.setContentDescription("Fermer le menu des profils");
        styleDrawerButton(close, false);
        close.setPadding(0, 0, 0, 0);
        heading.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
        drawerPanel.addView(heading);
        TextView hint = text("Choisis le profil à utiliser", 13, false);
        hint.setTextColor(Color.rgb(100, 116, 139));
        drawerPanel.addView(hint);
        gap(drawerPanel, 20);

        ScrollView list = new ScrollView(this);
        list.setFillViewport(true);
        drawerProfiles = vertical();
        list.addView(drawerProfiles);
        drawerPanel.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        gap(drawerPanel, 12);
        Button add = button("+ Ajouter un profil", () -> {
            drawerLayout.closeDrawer(drawerPanel);
            editProfile(null);
        });
        styleDrawerButton(add, true);
        drawerPanel.addView(add, new LinearLayout.LayoutParams(-1, dp(56)));
        drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override public void onDrawerOpened(View drawerView) {
                close.requestFocus();
            }

            @Override public void onDrawerClosed(View drawerView) {
                menuButton.requestFocus();
            }
        });
    }

    private void renderDrawerProfiles() {
        drawerProfiles.removeAllViews();
        if (profiles.isEmpty()) {
            drawerProfiles.addView(text("Aucun profil enregistré.\nAjoute ton premier profil ci-dessous.", 15, false));
        }
        for (Profile profile : profiles) {
            boolean selected = profile == active;
            Button choice = button((selected ? "✓  " : "") + profile.name + "\n@" + profile.username, () -> {
                active = profile;
                persist();
                renderHome();
                drawerLayout.closeDrawer(drawerPanel);
            });
            choice.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
            choice.setMaxLines(2);
            choice.setEllipsize(TextUtils.TruncateAt.END);
            choice.setSelected(selected);
            choice.setContentDescription(profile.name + ", @" + profile.username
                    + (selected ? ", profil actif" : ""));
            styleDrawerButton(choice, selected);
            LinearLayout.LayoutParams row = new LinearLayout.LayoutParams(-1, -2);
            row.bottomMargin = dp(8);
            choice.setMinHeight(dp(68));
            LinearLayout profileRow = horizontal();
            profileRow.addView(choice, new LinearLayout.LayoutParams(0, -2, 1));
            Button options = new Button(this);
            options.setText("⋮");
            options.setTextSize(24);
            options.setContentDescription("Options du profil " + profile.name + ", @" + profile.username);
            options.setTooltipText("Options du profil");
            styleDrawerButton(options, false);
            options.setPadding(0, 0, 0, 0);
            options.setOnClickListener(anchor -> showProfileOptions(anchor, profile));
            profileRow.addView(options, new LinearLayout.LayoutParams(dp(48), dp(48)));
            drawerProfiles.addView(profileRow, row);
        }
    }

    private void showProfileOptions(View anchor, Profile profile) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("Modifier").setOnMenuItemClickListener(item -> {
            editProfile(profile);
            return true;
        });
        menu.getMenu().add("Supprimer…").setOnMenuItemClickListener(item -> {
            confirmDeleteProfile(profile);
            return true;
        });
        menu.show();
    }

    private void styleDrawerButton(Button button, boolean highlighted) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(highlighted ? Color.rgb(238, 242, 255) : Color.WHITE);
        background.setCornerRadius(dp(16));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x224F46E5), background, null));
        button.setTextColor(highlighted ? Color.rgb(67, 56, 202) : Color.rgb(30, 41, 59));
        button.setPadding(dp(16), dp(10), dp(16), dp(10));
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setElevation(0);
        button.setStateListAnimator(null);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("profileDrawerOpen", drawerLayout.isDrawerOpen(drawerPanel));
        super.onSaveInstanceState(state);
    }

    private void editProfile(Profile current) {
        boolean adding = current == null;
        Profile candidate = adding ? new Profile() : current;
        LinearLayout fields = vertical();
        fields.setPadding(dp(18), dp(8), dp(18), dp(8));
        EditText name = input(fields, "Nom du preset", candidate.name);
        EditText username = input(fields, "Pseudo Snapchat", candidate.username);
        EditText email = input(fields, "E-mail", candidate.email);
        email.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText phone = input(fields, "Téléphone avec indicatif (ex : +33...)", candidate.phone);
        CheckBox descriptionEnabled = new CheckBox(this);
        descriptionEnabled.setText("Ajouter une description à la demande");
        descriptionEnabled.setChecked(candidate.descriptionEnabled);
        fields.addView(descriptionEnabled);
        EditText description = input(fields, "Description de la demande (facultatif)", candidate.description);
        description.setVisibility(descriptionEnabled.isChecked() ? View.VISIBLE : View.GONE);
        descriptionEnabled.setOnCheckedChangeListener((buttonView, checked) ->
                description.setVisibility(checked ? View.VISIBLE : View.GONE));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(fields);
        new AlertDialog.Builder(this)
                .setTitle(adding ? "Nouveau preset" : "Modifier le preset")
                .setView(scroll)
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Enregistrer", (dialog, which) -> {
                    if (blank(name) || blank(username) || blank(email) || blank(phone)) {
                        Toast.makeText(this, "Tous les champs sauf la description sont nécessaires.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    candidate.name = value(name);
                    candidate.username = value(username);
                    candidate.email = value(email);
                    candidate.phone = value(phone);
                    candidate.description = value(description);
                    candidate.descriptionEnabled = descriptionEnabled.isChecked();
                    if (adding) {
                        profiles.add(candidate);
                        active = candidate;
                    }
                    persist();
                    renderHome();
                }).show();
    }

    private void addFriend() {
        if (active == null) return;
        LinearLayout fields = vertical();
        fields.setPadding(dp(18), dp(8), dp(18), dp(8));
        EditText alias = input(fields, "Nom à afficher", "");
        EditText username = input(fields, "Pseudo Snapchat de l’ami", "");
        new AlertDialog.Builder(this).setTitle("Ajouter un ami").setView(fields)
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Ajouter", (dialog, which) -> {
                    if (blank(username)) return;
                    String u = value(username);
                    if (hasFriend(active, u)) {
                        Toast.makeText(this, "Cet ami est déjà présent.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    active.friends.add(new Friend(blank(alias) ? u : value(alias), u));
                    persist();
                    renderHome();
                }).show();
    }

    private boolean hasFriend(Profile p, String username) {
        for (Friend f : p.friends) if (f.username.equalsIgnoreCase(username)) return true;
        return false;
    }

    private void copyFriends() {
        if (active == null) return;
        List<Profile> others = new ArrayList<>();
        for (Profile p : profiles) if (p != active) others.add(p);
        if (others.isEmpty()) {
            Toast.makeText(this, "Crée d’abord un autre profil.", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[others.size()];
        for (int i = 0; i < others.size(); i++) labels[i] = others.get(i).name;
        new AlertDialog.Builder(this).setTitle("Importer tous les amis de…")
                .setItems(labels, (dialog, which) -> {
                    int count = 0;
                    for (Friend f : others.get(which).friends) {
                        if (!hasFriend(active, f.username)) {
                            active.friends.add(new Friend(f.name, f.username));
                            count++;
                        }
                    }
                    persist();
                    renderHome();
                    Toast.makeText(this, count + " ami(s) importé(s).", Toast.LENGTH_SHORT).show();
                }).show();
    }

    private void removeFriend(Friend f) {
        new AlertDialog.Builder(this).setTitle("Supprimer " + f.name + " ?")
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Supprimer", (dialog, which) -> {
                    active.friends.remove(f);
                    persist();
                    renderHome();
                }).show();
    }

    private void confirmDeleteProfile(Profile toDelete) {
        new AlertDialog.Builder(this).setTitle("Supprimer le profil " + toDelete.name + " ?")
                .setMessage("@" + toDelete.username + "\nCela supprimera aussi sa liste locale d’amis. Cette action est définitive.")
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Supprimer", (dialog, which) -> {
                    profiles.remove(toDelete);
                    if (active == toDelete) active = profiles.isEmpty() ? null : profiles.get(0);
                    persist();
                    renderHome();
                }).show();
    }

    private void prepare(Friend friend) {
        pendingProfile = active;
        pendingFriend = friend;
        browserStatus.setText("Chargement pour @" + friend.username + "…");
        drawerLayout.closeDrawer(drawerPanel, false);
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        homePanel.setVisibility(View.GONE);
        browser.loadUrl(FORM_URL); // Fresh request; no programmatic submission.
    }

    /** Fill only known first-party page fields; never trigger the form submit button. */
    private void fillForm(int attempt) {
        if (pendingProfile == null || pendingFriend == null) return;
        String currentUrl = browser.getUrl();
        if (currentUrl == null || !currentUrl.startsWith("https://help.snapchat.com/hc/fr-fr/requests/new")) {
            browserStatus.setText("Le formulaire n’est plus affiché. Reviens aux presets pour recommencer.");
            return;
        }
        Profile p = pendingProfile;
        StringBuilder js = new StringBuilder("(function(){")
                .append("const fields=[")
                .append(field("request_anonymous_requester_email", p.email)).append(",")
                .append(field("request_custom_fields_24281229", p.username)).append(",")
                .append(field("request_custom_fields_24335325", p.email)).append(",")
                .append(field("request_custom_fields_24369716", p.phone)).append(",")
                .append(field("request_custom_fields_24369736", pendingFriend.username));
        if (p.descriptionEnabled && !p.description.isEmpty()) {
            js.append(",").append(field("request_description", p.description));
        }
        js.append("];let filled=0,missing=[];")
                .append("for(const [id,val] of fields){const el=document.getElementById(id);")
                .append("if(!el){missing.push(id);continue;}")
                .append("const proto=el.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;")
                .append("const setter=Object.getOwnPropertyDescriptor(proto,'value').set;")
                .append("setter.call(el,val);el.dispatchEvent(new Event('input',{bubbles:true}));")
                .append("el.dispatchEvent(new Event('change',{bubbles:true}));filled++;}")
                .append("return JSON.stringify({filled:filled,missing:missing});})()");
        browser.evaluateJavascript(js.toString(), response -> {
            try {
                String actual = response;
                if (actual != null && actual.startsWith("\"")) {
                    actual = new JSONArray("[" + actual + "]").getString(0);
                }
                JSONObject outcome = new JSONObject(actual);
                int filled = outcome.optInt("filled");
                if (filled < 5 && attempt < 3) {
                    browser.postDelayed(() -> fillForm(attempt + 1), 800);
                } else {
                    browserStatus.setText(filled + " champ(s) renseigné(s) pour @" + pendingFriend.username
                            + ". Vérifie les données, le CAPTCHA et clique sur Envoyer toi-même.");
                }
            } catch (Exception e) {
                browserStatus.setText("Préremplissage indisponible : remplis le formulaire manuellement.");
            }
        });
    }

    private static String field(String id, String value) {
        return "[" + JSONObject.quote(id) + "," + JSONObject.quote(value) + "]";
    }

    private void restoreData() {
        String raw = getPreferences(MODE_PRIVATE).getString(STORAGE_KEY, "");
        if (raw.isEmpty()) return;
        try {
            JSONObject data = new JSONObject(raw);
            JSONArray saved = data.optJSONArray("profiles");
            if (saved != null) for (int i = 0; i < saved.length(); i++) {
                JSONObject item = saved.optJSONObject(i);
                if (item != null) profiles.add(Profile.fromJson(item));
            }
            String activeId = data.optString("activeId");
            for (Profile p : profiles) if (p.id.equals(activeId)) active = p;
            if (active == null && !profiles.isEmpty()) active = profiles.get(0);
        } catch (JSONException ignored) {
            Toast.makeText(this, "Les presets enregistrés sont illisibles.", Toast.LENGTH_LONG).show();
        }
    }

    private void persist() {
        try {
            JSONArray items = new JSONArray();
            for (Profile p : profiles) items.put(p.json());
            JSONObject data = new JSONObject().put("profiles", items)
                    .put("activeId", active == null ? "" : active.id);
            getPreferences(MODE_PRIVATE).edit().putString(STORAGE_KEY, data.toString()).apply();
        } catch (JSONException e) {
            Toast.makeText(this, "Impossible d’enregistrer les presets.", Toast.LENGTH_LONG).show();
        }
    }

    private int dp(int n) { return (int) (n * getResources().getDisplayMetrics().density + .5f); }
    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }
    private LinearLayout horizontal() {
        LinearLayout l = new LinearLayout(this);
        l.setGravity(android.view.Gravity.CENTER_VERTICAL);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }
    private TextView text(String value, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(Color.rgb(28, 30, 34));
        t.setTextSize(sp);
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setPadding(dp(3), dp(3), dp(3), dp(5));
        return t;
    }
    private Button button(String label, Runnable action) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(v -> action.run());
        return b;
    }
    private EditText input(LinearLayout parent, String hint, String original) {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setText(original);
        e.setHint(hint);
        e.setTextSize(15);
        parent.addView(e, new LinearLayout.LayoutParams(-1, dp(54)));
        return e;
    }
    private void gap(LinearLayout container, int n) {
        container.addView(new View(this), new LinearLayout.LayoutParams(1, dp(n)));
    }
    private String value(EditText e) { return e.getText().toString().trim(); }
    private boolean blank(EditText e) { return value(e).isEmpty(); }

    @Override public void onBackPressed() {
        if (drawerLayout.isDrawerVisible(drawerPanel)) drawerLayout.closeDrawer(drawerPanel);
        else if (homePanel.getVisibility() == View.GONE) showHome();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (browser != null) browser.destroy();
        super.onDestroy();
    }
}

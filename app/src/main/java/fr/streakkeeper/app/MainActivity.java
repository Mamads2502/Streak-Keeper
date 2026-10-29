package fr.streakkeeper.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.content.SharedPreferences;
import android.graphics.Paint;
import android.net.Uri;
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
 * The user sees the official page and submits it manually, may change.
 */
public class MainActivity extends Activity {
    private static final String FORM_URL =
            "https://help.snapchat.com/hc/fr-fr/requests/new?co=true&ticket_form_id=149423";
    private static final String STORAGE_KEY = "streak_keeper_v1";
    private static final String LEGACY_STORAGE_KEY = "streak_presets_v1";
    // getPreferences() used this file before the Java package was renamed.
    private static final String PREFERENCES_FILE = "MainActivity";

    private AppTheme ui;
    private TextView browserContext;

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
        ui = new AppTheme(this);
        if (android.os.Build.VERSION.SDK_INT >= 27) {
            getWindow().setNavigationBarColor(ui.background);
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        restoreData();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(ui.background);
        drawerLayout = new DrawerLayout(this);
        drawerLayout.setScrimColor((ui.text & 0x00FFFFFF) | 0x66000000);
        drawerLayout.setDrawerElevation(dp(12));
        drawerLayout.addView(root, new DrawerLayout.LayoutParams(-1, -1));
        setContentView(drawerLayout);

        LinearLayout browserPanel = vertical();
        browserPanel.setBackgroundColor(ui.background);
        root.addView(browserPanel, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout topBar = vertical();
        topBar.setPadding(dp(20), dp(16), dp(20), dp(16));
        topBar.setBackgroundColor(ui.surface);
        topBar.addView(text("Assistance Snapchat", 22, true));
        browserContext = mutedText("Un ami, une demande à vérifier.", 13);
        topBar.addView(browserContext);
        gap(topBar, 12);
        LinearLayout actions = horizontal();
        Button back = button("← Retour", this::showHome);
        back.setContentDescription("Retour aux profils et amis");
        Button refill = primaryButton("Re-remplir", () -> fillForm(0));
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(0, -2, 1);
        backParams.setMarginEnd(dp(8));
        actions.addView(back, backParams);
        actions.addView(refill, new LinearLayout.LayoutParams(0, -2, 1));
        topBar.addView(actions);
        gap(topBar, 12);
        browserStatus = mutedText("Vérifie les informations avant l’envoi manuel.", 13);
        browserStatus.setBackground(ui.shape(ui.background, Color.TRANSPARENT));
        browserStatus.setPadding(dp(12), dp(10), dp(12), dp(10));
        browserStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        topBar.addView(browserStatus, new LinearLayout.LayoutParams(-1, -2));
        browserPanel.addView(topBar);

        browser = new WebView(this);
        browser.setBackgroundColor(ui.surface);
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
                if (isSupportForm(url)) {
                    applyFormTheme();
                    if (pendingProfile != null) fillForm(0);
                }
            }

            @Override public void onReceivedError(WebView view, WebResourceRequest request,
                                                   WebResourceError error) {
                if (request.isForMainFrame()) {
                    setBrowserStatus("Chargement impossible. Vérifie Internet ou ouvre le site plus tard.");
                }
            }
        });
        FrameLayout formCard = new FrameLayout(this);
        formCard.setBackground(ui.shape(ui.surface, ui.border));
        formCard.setClipToOutline(true);
        formCard.addView(browser, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams formParams = new LinearLayout.LayoutParams(-1, 0, 1);
        formParams.setMargins(dp(12), dp(12), dp(12), dp(12));
        browserPanel.addView(formCard, formParams);

        homePanel = vertical();
        homePanel.setBackgroundColor(ui.background);
        root.addView(homePanel, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout toolbar = horizontal();
        toolbar.setPadding(dp(8), dp(8), dp(16), dp(8));
        toolbar.setBackgroundColor(ui.surface);
        menuButton = button("☰", () -> drawerLayout.openDrawer(drawerPanel));
        menuButton.setTextSize(24);
        menuButton.setContentDescription("Ouvrir le menu des profils");
        styleDrawerButton(menuButton, false);
        menuButton.setPadding(0, 0, 0, 0);
        toolbar.addView(menuButton, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = text("Streak keeper", 22, true);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        homePanel.addView(toolbar);

        home = new ScrollView(this);
        home.setFillViewport(true);
        home.setBackgroundColor(ui.background);
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
        page.setPadding(dp(AppTheme.PAGE_PADDING), dp(20), dp(AppTheme.PAGE_PADDING), dp(28));
        renderDrawerProfiles();
        if (active == null) {
            LinearLayout welcome = card();
            welcome.addView(text("Bienvenue", 26, true));
            gap(welcome, 8);
            welcome.addView(mutedText("Tes profils et tes amis, au même endroit.", 15));
            gap(welcome, 16);
            welcome.addView(mutedText("Ouvre le menu ☰, puis ajoute ton premier profil pour commencer.", 15));
            page.addView(welcome);
        } else {
            LinearLayout summary = card();
            summary.addView(mutedText("PROFIL ACTIF", 11));
            gap(summary, 4);
            summary.addView(text(active.name, 24, true));
            summary.addView(mutedText("@" + active.username, 15));
            summary.addView(mutedText(active.email, 13));
            page.addView(summary);
            gap(page, 24);

            LinearLayout heading = horizontal();
            heading.addView(text("Mes amis", 22, true), new LinearLayout.LayoutParams(0, -2, 1));
            TextView count = text(Integer.toString(active.friends.size()), 14, true);
            count.setBackground(ui.shape(ui.accentSoft, Color.TRANSPARENT));
            count.setPadding(dp(12), dp(6), dp(12), dp(6));
            heading.addView(count);
            page.addView(heading);
            gap(page, 12);
            page.addView(primaryButton("+ Ajouter un ami", this::addFriend), new LinearLayout.LayoutParams(-1, -2));
            gap(page, 8);
            page.addView(button("Copier depuis un autre profil", this::copyFriends), new LinearLayout.LayoutParams(-1, -2));
            gap(page, 16);
            if (active.friends.isEmpty()) {
                LinearLayout empty = card();
                empty.addView(text("Ta liste commence ici", 17, true));
                gap(empty, 4);
                empty.addView(mutedText("Ajoute un ami ou copie ceux d’un autre profil.", 14));
                page.addView(empty);
            }
            for (Friend friend : new ArrayList<>(active.friends)) {
                LinearLayout friendCard = card();
                LinearLayout identity = horizontal();
                LinearLayout labels = vertical();
                labels.addView(text(friend.name, 17, true));
                labels.addView(mutedText("@" + friend.username, 13));
                identity.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
                Button remove = button("×", () -> removeFriend(friend));
                ui.button(remove, AppTheme.ButtonStyle.QUIET);
                remove.setTextSize(24);
                remove.setPadding(0, 0, 0, 0);
                remove.setContentDescription("Supprimer l’ami " + friend.name);
                identity.addView(remove, new LinearLayout.LayoutParams(dp(48), dp(48)));
                friendCard.addView(identity);
                gap(friendCard, 12);
                friendCard.addView(button("Préparer la demande →", () -> prepare(friend)), new LinearLayout.LayoutParams(-1, -2));
                LinearLayout.LayoutParams row = new LinearLayout.LayoutParams(-1, -2);
                row.bottomMargin = dp(12);
                page.addView(friendCard, row);
            }
        }
        gap(page, 20);
        Button guide = button("Comment ça marche ?", this::showGuide);
        ui.button(guide, AppTheme.ButtonStyle.QUIET);
        guide.setBackgroundColor(Color.TRANSPARENT);
        guide.setTextColor(ui.muted);
        guide.setPaintFlags(guide.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        page.addView(guide, new LinearLayout.LayoutParams(-1, -2));
        home.removeAllViews();
        home.addView(page);
    }

    private void showGuide() {
        LinearLayout content = vertical();
        content.setPadding(dp(24), dp(8), dp(24), dp(16));
        String[][] steps = {
                {"1. Choisis ton profil", "Ouvre ☰ pour créer ou sélectionner un profil. Le menu ⋮ permet de le modifier."},
                {"2. Prépare une demande", "Ajoute un ami ou copie une liste, puis touche « Préparer la demande »."},
                {"3. Vérifie et envoie", "Relis le formulaire Snapchat, complète le CAPTCHA si demandé, puis envoie toi-même."}
        };
        for (String[] step : steps) {
            content.addView(text(step[0], 16, true));
            gap(content, 4);
            content.addView(mutedText(step[1], 14));
            gap(content, 16);
        }
        content.addView(mutedText("Projet non affilié à Snapchat. Aucun mot de passe demandé, aucun envoi automatique.", 12));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        showDialog(new AlertDialog.Builder(this).setTitle("Comment ça marche ?")
                .setView(scroll).setPositiveButton("Compris", null));
    }

    private LinearLayout card() {
        LinearLayout card = vertical();
        card.setBackground(ui.shape(ui.surface, ui.border));
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        return card;
    }

    private void createProfileDrawer() {
        drawerPanel = vertical();
        drawerPanel.setBackgroundColor(ui.surface);
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
        hint.setTextColor(ui.muted);
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
        ui.button(add, AppTheme.ButtonStyle.PRIMARY);
        drawerPanel.addView(add, new LinearLayout.LayoutParams(-1, -2));
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
        float size = button.getTextSize() / getResources().getDisplayMetrics().scaledDensity;
        ui.button(button, highlighted ? AppTheme.ButtonStyle.SECONDARY : AppTheme.ButtonStyle.QUIET);
        button.setTextSize(size);
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
        ui.text(descriptionEnabled, 15, false);
        descriptionEnabled.setMinHeight(dp(48));
        descriptionEnabled.setChecked(candidate.descriptionEnabled);
        fields.addView(descriptionEnabled);
        EditText description = input(fields, "Description de la demande (facultatif)", candidate.description);
        description.setSingleLine(false);
        description.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        description.setMinLines(3);
        description.setGravity(Gravity.TOP | Gravity.START);
        View descriptionField = (View) description.getParent();
        descriptionField.setVisibility(descriptionEnabled.isChecked() ? View.VISIBLE : View.GONE);
        descriptionEnabled.setOnCheckedChangeListener((buttonView, checked) ->
                descriptionField.setVisibility(checked ? View.VISIBLE : View.GONE));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(fields);
        showDialog(new AlertDialog.Builder(this)
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
                }));
    }

    private void addFriend() {
        if (active == null) return;
        LinearLayout fields = vertical();
        fields.setPadding(dp(18), dp(8), dp(18), dp(8));
        EditText username = input(fields, "Pseudo Snapchat de l’ami", "");
        EditText alias = input(fields, "Nom à afficher (facultatif)", "");
        ScrollView scroll = new ScrollView(this);
        scroll.addView(fields);
        showDialog(new AlertDialog.Builder(this).setTitle("Ajouter un ami").setView(scroll)
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
                }));
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
        showDialog(new AlertDialog.Builder(this).setTitle("Importer tous les amis de…")
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
                }));
    }

    private void removeFriend(Friend f) {
        showDialog(new AlertDialog.Builder(this).setTitle("Supprimer " + f.name + " ?")
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Supprimer", (dialog, which) -> {
                    active.friends.remove(f);
                    persist();
                    renderHome();
                }));
    }

    private void confirmDeleteProfile(Profile toDelete) {
        showDialog(new AlertDialog.Builder(this).setTitle("Supprimer le profil " + toDelete.name + " ?")
                .setMessage("@" + toDelete.username + "\nCela supprimera aussi sa liste locale d’amis. Cette action est définitive.")
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Supprimer", (dialog, which) -> {
                    profiles.remove(toDelete);
                    if (active == toDelete) active = profiles.isEmpty() ? null : profiles.get(0);
                    persist();
                    renderHome();
                }));
    }

    private void setBrowserStatus(String message) {
        browserStatus.setText(message);
        browserStatus.setVisibility(message.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void prepare(Friend friend) {
        pendingProfile = active;
        pendingFriend = friend;
        browserContext.setText(active.name + " · @" + friend.username);
        setBrowserStatus("");
        drawerLayout.closeDrawer(drawerPanel, false);
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        homePanel.setVisibility(View.GONE);
        browser.loadUrl(FORM_URL); // Fresh request; no programmatic submission.
    }

    /** Fill only known first-party page fields; never trigger the form submit button. */
    private void fillForm(int attempt) {
        if (pendingProfile == null || pendingFriend == null) return;
        String currentUrl = browser.getUrl();
        if (!isSupportForm(currentUrl)) {
            setBrowserStatus("Le formulaire n’est plus affiché. Reviens aux presets pour recommencer.");
            return;
        }
        applyFormTheme();
        Profile p = pendingProfile;
        Friend friend = pendingFriend;
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
            if (pendingProfile != p || pendingFriend != friend) return;
            try {
                String actual = response;
                if (actual != null && actual.startsWith("\"")) {
                    actual = new JSONArray("[" + actual + "]").getString(0);
                }
                JSONObject outcome = new JSONObject(actual);
                int filled = outcome.optInt("filled");
                if (filled < 5 && attempt < 3) {
                    browser.postDelayed(() -> {
                        if (pendingProfile == p && pendingFriend == friend) fillForm(attempt + 1);
                    }, 800);
                } else {
                    setBrowserStatus("");
                }
            } catch (Exception e) {
                setBrowserStatus("Préremplissage indisponible : remplis le formulaire manuellement.");
            }
        });
    }

    private static String field(String id, String value) {
        return "[" + JSONObject.quote(id) + "," + JSONObject.quote(value) + "]";
    }

    private void restoreData() {
        SharedPreferences preferences = getSharedPreferences(PREFERENCES_FILE, MODE_PRIVATE);
        String raw = preferences.getString(STORAGE_KEY, null);
        if (raw == null) raw = preferences.getString(LEGACY_STORAGE_KEY, "");
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
            getSharedPreferences(PREFERENCES_FILE, MODE_PRIVATE).edit()
                    .putString(STORAGE_KEY, data.toString()).remove(LEGACY_STORAGE_KEY).apply();
        } catch (JSONException e) {
            Toast.makeText(this, "Impossible d’enregistrer les presets.", Toast.LENGTH_LONG).show();
        }
    }

    private static boolean isSupportForm(String url) {
        if (url == null) return false;
        Uri uri = Uri.parse(url);
        return "https".equals(uri.getScheme()) && "help.snapchat.com".equals(uri.getHost())
                && "/hc/fr-fr/requests/new".equals(uri.getPath());
    }

    private void applyFormTheme() {
        if (!isSupportForm(browser.getUrl())) return;
        browser.evaluateJavascript("(function(){let s=document.getElementById('streak-keeper-theme');"
                + "if(!s){s=document.createElement('style');s.id='streak-keeper-theme';document.head.appendChild(s);}"
                + "s.textContent=" + JSONObject.quote(ui.formCss()) + ";})()", null);
    }

    private TextView mutedText(String value, int size) {
        TextView view = text(value, size, false);
        view.setTextColor(ui.muted);
        return view;
    }

    private Button primaryButton(String label, Runnable action) {
        Button view = button(label, action);
        ui.button(view, AppTheme.ButtonStyle.PRIMARY);
        return view;
    }

    private AlertDialog showDialog(AlertDialog.Builder builder) {
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(ignored -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (positive != null) ui.button(positive, AppTheme.ButtonStyle.SECONDARY);
            if (negative != null) ui.button(negative, AppTheme.ButtonStyle.QUIET);
        });
        dialog.show();
        return dialog;
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
        ui.text(t, sp, bold);
        t.setPadding(0, dp(2), 0, dp(4));
        return t;
    }
    private Button button(String label, Runnable action) {
        Button b = new Button(this);
        b.setText(label);
        ui.button(b, AppTheme.ButtonStyle.SECONDARY);
        b.setOnClickListener(v -> action.run());
        return b;
    }
    private EditText input(LinearLayout parent, String hint, String original) {
        LinearLayout field = vertical();
        field.addView(mutedText(hint, 12));
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setText(original);
        e.setHint(hint);
        ui.input(e);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(12);
        field.addView(e, new LinearLayout.LayoutParams(-1, -2));
        parent.addView(field, params);
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

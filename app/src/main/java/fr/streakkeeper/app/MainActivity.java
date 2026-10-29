package fr.streakkeeper.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.content.SharedPreferences;
import android.graphics.Paint;
import android.net.Uri;
import android.text.TextUtils;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.inputmethod.InputMethodManager;
import android.view.Gravity;
import android.view.MotionEvent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
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
import android.widget.ImageButton;
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
    private boolean formTouched;
    private boolean formInteractionLocked;
    private String scrollDoneMarker;
    private int interactionLockGeneration;
    private int formGeneration;
    private FriendList.Order friendOrder = FriendList.Order.OLDEST;
    private String friendQuery = "";
    private String searchProfileId = "";
    private boolean searchOpen;
    private LinearLayout friendResults;
    private TextView friendCount;
    private EditText friendSearch;
    private ImageButton sortButton;
    private ImageButton searchToggle;

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
    private boolean selecting;
    private final List<Friend> selectedFriends = new ArrayList<>();
    private final List<Friend> requestQueue = new ArrayList<>();
    private int requestIndex;
    private int requestSession;
    private boolean submissionObserved;
    private boolean submissionTransitioned;
    private String submitMarker;
    private LinearLayout selectionBar;
    private Button selectionAction;
    private Button selectionClose;

    static final class Friend implements FriendList.Entry {
        String name, username;
        final long addedAt;

        Friend(String name, String username) {
            this(name, username, System.currentTimeMillis());
        }

        Friend(String name, String username, long addedAt) {
            this.name = name;
            this.username = username;
            this.addedAt = Math.max(0, addedAt);
        }

        @Override public String displayName() { return name; }
        @Override public String snapchatUsername() { return username; }
        @Override public long addedAt() { return addedAt; }

        JSONObject json() throws JSONException {
            return new JSONObject().put("name", name).put("username", username).put("addedAt", addedAt);
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
                        item.optString("name"), item.optString("username"), item.optLong("addedAt", 0)));
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
        friendOrder = FriendList.Order.restore(getSharedPreferences(PREFERENCES_FILE, MODE_PRIVATE)
                .getString("friendSort", FriendList.Order.OLDEST.name()));
        if (state != null) {
            friendQuery = state.getString("friendQuery", "");
            searchOpen = state.getBoolean("friendSearchOpen");
            searchProfileId = state.getString("searchProfileId", "");
        }

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
        Button refill = primaryButton("Re-remplir", () -> {
            if (submissionObserved) {
                setBrowserStatus("Le bouton d’envoi a déjà été activé pour cette demande.");
                return;
            }
            cancelFormScroll();
            formTouched = false;
            lockFormInteractions();
            fillForm(0);
        });
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
        browser.setOnTouchListener((view, event) -> {
            if (formInteractionLocked) return true;
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) formTouched = true;
            return false; // Keep normal WebView gestures and clicks.
        });
        browser.setOnGenericMotionListener((view, event) -> formInteractionLocked);
        browser.setOnKeyListener((view, keyCode, event) -> formInteractionLocked);
        WebSettings webSettings = browser.getSettings();
        webSettings.setJavaScriptEnabled(true); // The real help form needs JavaScript.
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(false);
        webSettings.setAllowContentAccess(false);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(browser, true);
        browser.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onConsoleMessage(android.webkit.ConsoleMessage message) {
                if (formInteractionLocked && scrollDoneMarker != null && scrollDoneMarker.equals(message.message())) {
                    unlockFormInteractions();
                    return true;
                }
                if (pendingFriend != null && !submissionObserved && submitMarker != null
                        && submitMarker.equals(message.message())) {
                    submissionObserved = true;
                    setBrowserStatus("Envoi en cours : attente de la réponse du site…");
                    int session = requestSession;
                    monitorSubmissionResult(session, 0);
                    return true;
                }
                return super.onConsoleMessage(message);
            }
        });
        browser.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                formGeneration++;
            }

            @Override public void onPageFinished(WebView view, String url) {
                if (isSupportForm(url)) {
                    applyFormTheme();
                    if (pendingProfile != null && !submissionObserved) {
                        installSubmitObserver();
                        fillForm(0);
                    }
                } else if (pendingFriend != null && formInteractionLocked) {
                    cancelFormScroll();
                }
            }

            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request,
                                                       android.webkit.WebResourceResponse response) {
                if (request.isForMainFrame() && pendingFriend != null) {
                    cancelFormScroll();
                    setBrowserStatus("Erreur du site. Vérifie si la demande a été reçue avant de réessayer.");
                }
            }

            @Override public void onReceivedError(WebView view, WebResourceRequest request,
                                                   WebResourceError error) {
                if (request.isForMainFrame()) {
                    cancelFormScroll();
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
        menuButton = button("", () -> drawerLayout.openDrawer(drawerPanel));
        menuButton.setTextSize(24);
        menuButton.setContentDescription("Ouvrir le menu des profils");
        styleDrawerButton(menuButton, false);
        menuButton.setPadding(0, 0, 0, 0);
        menuButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_menu, 0, 0, 0);
        toolbar.addView(menuButton, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = text("Streak keeper", 22, true);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        selectionClose = button("×", () -> { exitSelection(); renderHome(); });
        selectionClose.setTextSize(26);
        selectionClose.setContentDescription("Quitter la sélection");
        styleDrawerButton(selectionClose, false);
        toolbar.addView(selectionClose, new LinearLayout.LayoutParams(dp(48), dp(48)));
        selectionClose.setVisibility(View.GONE);
        homePanel.addView(toolbar);

        home = new ScrollView(this);
        home.setFillViewport(true);
        home.setBackgroundColor(ui.background);
        homePanel.addView(home, new LinearLayout.LayoutParams(-1, 0, 1));
        selectionBar = vertical();
        selectionBar.setPadding(dp(20), dp(8), dp(20), dp(16));
        selectionAction = primaryButton("🔥  Préparer (0)", this::startSelectedRequests);
        selectionBar.addView(selectionAction, new LinearLayout.LayoutParams(-1, -2));
        homePanel.addView(selectionBar);
        selectionBar.setVisibility(View.GONE);
        createProfileDrawer();
        renderHome();
        if (state != null && state.getBoolean("profileDrawerOpen")) {
            drawerLayout.post(() -> drawerLayout.openDrawer(drawerPanel, false));
        }
        browser.loadUrl(FORM_URL); // Load beneath the presets, but never send a request.
    }

    private void showHome() {
        cancelFormScroll();
        requestSession++;
        requestQueue.clear();
        exitSelection();
        pendingProfile = null;
        pendingFriend = null;
        renderHome();
        homePanel.setVisibility(View.VISIBLE);
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
    }

    private void renderHome() {
        String profileId = active == null ? "" : active.id;
        if (!profileId.equals(searchProfileId)) {
            friendQuery = "";
            searchOpen = false;
            searchProfileId = profileId;
        }
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
            friendCount = count;
            count.setBackground(ui.shape(ui.accentSoft, Color.TRANSPARENT));
            count.setPadding(dp(12), dp(6), dp(12), dp(6));
            heading.addView(count);
            Button select = button("Sélectionner", () -> {
                selecting = true;
                selectedFriends.clear();
                updateSelectionUi();
                renderFriendResults();
            });
            ui.button(select, AppTheme.ButtonStyle.QUIET);
            select.setVisibility(selecting || active.friends.isEmpty() ? View.GONE : View.VISIBLE);
            heading.addView(select);
            page.addView(heading);
            gap(page, 12);
            LinearLayout actions = horizontal();
            actions.addView(primaryButton("+ Ajouter un ami", this::addFriend),
                    new LinearLayout.LayoutParams(0, -2, 1));
            ImageButton copy = friendToolIcon(R.drawable.ic_copy, "Importer des amis depuis un autre profil");
            copy.setOnClickListener(view -> copyFriends());
            LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(dp(48), dp(48));
            copyParams.leftMargin = dp(8);
            actions.addView(copy, copyParams);
            page.addView(actions);
            gap(page, 16);
            addFriendTools(page);
            friendResults = vertical();
            page.addView(friendResults);
            renderFriendResults();
        }
        gap(page, 20);
        Button guide = button("Comment ça marche ?", this::showGuide);
        ui.button(guide, AppTheme.ButtonStyle.QUIET);
        guide.setBackgroundColor(Color.TRANSPARENT);
        guide.setTextColor(ui.muted);
        guide.setPaintFlags(guide.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        guide.setMinWidth(0);
        guide.setMinimumWidth(0);
        guide.setPadding(0, guide.getPaddingTop(), 0, guide.getPaddingBottom());
        LinearLayout.LayoutParams guideParams = new LinearLayout.LayoutParams(-2, -2);
        guideParams.gravity = Gravity.CENTER_HORIZONTAL;
        page.addView(guide, guideParams);
        home.removeAllViews();
        home.addView(page);
    }

    private void addFriendTools(LinearLayout page) {
        ImageButton search = friendToolIcon(R.drawable.ic_search, "Rechercher des amis");
        searchToggle = search;
        sortButton = friendToolIcon(R.drawable.ic_filter, "Trier les amis : " + friendOrder.label);
        sortButton.setOnClickListener(view -> showFriendSort());
        LinearLayout searchRow = horizontal();
        friendSearch = new EditText(this);
        friendSearch.setSingleLine(true);
        friendSearch.setHint("Surnom ou @pseudo");
        friendSearch.setContentDescription("Rechercher par surnom ou pseudo Snapchat");
        friendSearch.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        friendSearch.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        ui.input(friendSearch);
        friendSearch.setText(friendQuery);
        friendSearch.setSelection(friendQuery.length());
        searchRow.addView(friendSearch, new LinearLayout.LayoutParams(0, -2, 1));
        Button clear = button("×", () -> friendSearch.setText(""));
        ui.button(clear, AppTheme.ButtonStyle.QUIET);
        clear.setPadding(0, 0, 0, 0);
        clear.setContentDescription("Effacer la recherche");
        searchRow.addView(clear, new LinearLayout.LayoutParams(dp(48), dp(48)));
        clear.setVisibility(friendQuery.isEmpty() ? View.INVISIBLE : View.VISIBLE);
        page.addView(new FriendToolsLayout(this, search, sortButton, searchRow),
                new LinearLayout.LayoutParams(-1, -2));
        searchRow.setVisibility(searchOpen ? View.VISIBLE : View.GONE);
        search.setSelected(searchOpen);
        styleFriendTool(search, searchOpen);
        search.setOnClickListener(view -> {
            searchOpen = !searchOpen;
            searchRow.setVisibility(searchOpen ? View.VISIBLE : View.GONE);
            search.setSelected(searchOpen);
            styleFriendTool(search, searchOpen);
            InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (searchOpen) {
                friendSearch.requestFocus();
                friendSearch.post(() -> keyboard.showSoftInput(friendSearch, InputMethodManager.SHOW_IMPLICIT));
            } else {
                friendSearch.setText("");
                keyboard.hideSoftInputFromWindow(friendSearch.getWindowToken(), 0);
                friendSearch.clearFocus();
            }
        });
        friendSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                friendQuery = s.toString();
                clear.setVisibility(s.length() == 0 ? View.INVISIBLE : View.VISIBLE);
                renderFriendResults();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        gap(page, 12);
    }

    private ImageButton friendToolIcon(int drawable, String label) {
        ImageButton icon = new ImageButton(this);
        icon.setImageResource(drawable);
        icon.setContentDescription(label);
        icon.setTooltipText(label);
        icon.setPadding(dp(12), dp(12), dp(12), dp(12));
        styleFriendTool(icon, false);
        return icon;
    }

    private void styleFriendTool(ImageButton icon, boolean selected) {
        icon.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(ui.accentSoft),
                ui.shape(selected ? ui.accentSoft : ui.surface, Color.TRANSPARENT), null));
        icon.setImageTintList(android.content.res.ColorStateList.valueOf(ui.text));
    }

    private void showFriendSort() {
        PopupMenu menu = new PopupMenu(this, sortButton);
        for (FriendList.Order order : FriendList.Order.values()) {
            android.view.MenuItem item = menu.getMenu().add(0, order.ordinal(), order.ordinal(), order.label);
            item.setCheckable(true).setChecked(order == friendOrder);
            item.setOnMenuItemClickListener(selected -> {
                friendOrder = order;
                getSharedPreferences(PREFERENCES_FILE, MODE_PRIVATE).edit()
                        .putString("friendSort", order.name()).apply();
                sortButton.setTooltipText("Trier les amis : " + order.label);
                sortButton.setContentDescription("Trier les amis : " + order.label);
                renderFriendResults();
                return true;
            });
        }
        menu.getMenu().setGroupCheckable(0, true, true);
        menu.show();
    }

    private void renderFriendResults() {
        if (active == null || friendResults == null) return;
        friendResults.removeAllViews();
        java.util.List<Friend> matches = FriendList.select(active.friends, friendQuery, friendOrder);
        friendCount.setText(matches.size() == active.friends.size() ? Integer.toString(matches.size())
                : matches.size() + " / " + active.friends.size());
        friendCount.setContentDescription(matches.size() + " amis affichés sur " + active.friends.size());
        if (!active.friends.isEmpty() && matches.isEmpty()) {
            LinearLayout empty = card();
            empty.addView(text("Aucun ami trouvé", 17, true));
            empty.addView(mutedText("Essaie une partie du surnom ou du pseudo Snapchat.", 14));
            empty.addView(button("Effacer la recherche", () -> friendSearch.setText("")));
            friendResults.addView(empty);
        }
            if (active.friends.isEmpty()) {
                LinearLayout empty = card();
                empty.addView(text("Ta liste commence ici", 17, true));
                gap(empty, 4);
                empty.addView(mutedText("Ajoute un ami ou copie ceux d’un autre profil.", 14));
                friendResults.addView(empty);
            }
        for (Friend friend : matches) {
            Profile owner = active;
            LinearLayout row = horizontal();
            row.setBackground(ui.shape(ui.surface, ui.border));
            row.setPadding(dp(14), dp(10), dp(8), dp(10));
            LinearLayout labels = vertical();
            TextView name = text(friend.name, 16, true);
            name.setMaxLines(2);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setContentDescription(friend.name);
            TextView username = mutedText("@" + friend.username, 13);
            username.setSingleLine(true);
            username.setEllipsize(TextUtils.TruncateAt.END);
            labels.addView(name);
            labels.addView(username);
            labels.setPadding(0, 0, dp(8), 0);
            row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));

            if (selecting) {
                CheckBox check = new CheckBox(this);
                check.setButtonTintList(android.content.res.ColorStateList.valueOf(ui.accent));
                check.setContentDescription("Sélectionner " + friend.name + ", @" + friend.username);
                check.setChecked(selectedFriends.contains(friend));
                check.setOnCheckedChangeListener((v, checked) -> {
                    if (checked && !selectedFriends.contains(friend)) selectedFriends.add(friend);
                    else if (!checked) selectedFriends.remove(friend);
                    updateSelectionUi();
                });
                row.addView(check, new LinearLayout.LayoutParams(dp(48), dp(48)));
                row.setOnClickListener(v -> check.setChecked(!check.isChecked()));
            } else {
            ImageButton prepare = friendToolIcon(R.drawable.ic_flame,
                    "Préparer la demande pour " + friend.name + ", @" + friend.username);
            android.graphics.drawable.GradientDrawable circle = new android.graphics.drawable.GradientDrawable();
            circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            circle.setColor(ui.accentSoft);
            prepare.setBackground(new android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(ui.border), circle, null));
            prepare.setImageTintList(android.content.res.ColorStateList.valueOf(ui.accent));
            prepare.setOnClickListener(view -> prepare(friend));
            row.addView(prepare, new LinearLayout.LayoutParams(dp(48), dp(48)));
            ImageButton more = friendToolIcon(R.drawable.ic_more,
                    "Options pour " + friend.name + ", @" + friend.username);
            more.setOnClickListener(view -> showFriendOptions(view, owner, friend));
            LinearLayout.LayoutParams options = new LinearLayout.LayoutParams(dp(48), dp(48));
            options.setMarginStart(dp(4));
            row.addView(more, options);
            }
            LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2);
            layout.bottomMargin = dp(8);
            friendResults.addView(row, layout);
        }
    }

    private void showFriendOptions(View anchor, Profile owner, Friend friend) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("Modifier").setOnMenuItemClickListener(item -> {
            editFriend(owner, friend);
            return true;
        });
        menu.getMenu().add("Copier vers un autre profil").setOnMenuItemClickListener(item -> {
            copyFriend(owner, friend);
            return true;
        });
        menu.getMenu().add("Supprimer…").setOnMenuItemClickListener(item -> {
            removeFriend(owner, friend);
            return true;
        });
        menu.show();
    }

    private void editFriend(Profile owner, Friend friend) {
        LinearLayout fields = vertical();
        fields.setPadding(dp(18), dp(8), dp(18), dp(8));
        EditText username = input(fields, "Pseudo Snapchat de l’ami", friend.username);
        EditText alias = input(fields, "Nom à afficher (facultatif)", friend.name);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(fields);
        AlertDialog dialog = showDialog(new AlertDialog.Builder(this).setTitle("Modifier l’ami")
                .setView(scroll).setNegativeButton("Annuler", null).setPositiveButton("Enregistrer", null));
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            if (blank(username)) {
                username.setError("Renseigne le pseudo Snapchat.");
                username.requestFocus();
                return;
            }
            String value = value(username);
            if (FriendList.containsUsername(owner.friends, value, friend)) {
                username.setError("Ce pseudo est déjà présent dans ce profil.");
                username.requestFocus();
                return;
            }
            friend.username = value;
            friend.name = blank(alias) ? value : value(alias);
            persist();
            renderHome();
            dialog.dismiss();
        });
    }

    private void copyFriend(Profile owner, Friend friend) {
        List<Profile> destinations = new ArrayList<>();
        for (Profile profile : profiles) if (profile != owner) destinations.add(profile);
        if (destinations.isEmpty()) {
            showDialog(new AlertDialog.Builder(this).setTitle("Aucun autre profil")
                    .setMessage("Ajoute un autre profil depuis le menu ☰ pour y copier cet ami.")
                    .setPositiveButton("Compris", null));
            return;
        }
        String[] labels = new String[destinations.size()];
        for (int i = 0; i < destinations.size(); i++) {
            Profile destination = destinations.get(i);
            labels[i] = destination.name + " · @" + destination.username
                    + (hasFriend(destination, friend.username) ? " — déjà présent" : "");
        }
        showDialog(new AlertDialog.Builder(this).setTitle("Copier @" + friend.username + " vers…")
                .setNegativeButton("Annuler", null)
                .setItems(labels, (dialog, index) -> {
                    Profile destination = destinations.get(index);
                    if (!FriendList.copyTo(friend, destination.friends, Friend::new)) {
                        Toast.makeText(this, "Cet ami est déjà présent dans ce profil.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    persist();
                    Toast.makeText(this, "Ami copié vers " + destination.name, Toast.LENGTH_SHORT).show();
                }));
    }

    private void showGuide() {
        LinearLayout content = vertical();
        content.setPadding(dp(24), dp(8), dp(24), dp(16));
        String[][] steps = {
                {"1. Choisis ton profil", "Ouvre ☰ pour créer ou sélectionner un profil. Le menu ⋮ permet de le modifier."},
                {"2. Prépare une demande", "Ajoute un ami ou copie une liste, puis touche la flamme à droite de son nom."},
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
        state.putString("friendQuery", friendQuery);
        state.putBoolean("friendSearchOpen", searchOpen);
        state.putString("searchProfileId", searchProfileId);
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
        return FriendList.containsUsername(p.friends, username, null);
    }

    private void copyFriends() {
        if (active == null) return;
        List<Profile> others = new ArrayList<>();
        for (Profile p : profiles) if (p != active) others.add(p);
        if (others.isEmpty()) {
            Toast.makeText(this, "Crée d’abord un autre profil.", Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout content = vertical();
        content.setPadding(dp(20), dp(8), dp(20), dp(12));
        content.addView(mutedText("Choisis le profil contenant les amis à importer.", 14));
        gap(content, 12);
        ProfileBadgeLayout badges = new ProfileBadgeLayout();
        for (Profile source : others) {
            Button badge = button(source.name, () -> {});
            badge.setBackground(new android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(ui.accentSoft),
                    ui.shape(ui.accentSoft, ui.accent), null));
            badge.setTextColor(ui.accent);
            badge.setElevation(dp(2));
            badge.setOnClickListener(view -> {
                AlertDialog dialog = (AlertDialog) badge.getTag();
                dialog.dismiss();
                selectFriendsToImport(source, active);
            });
            badges.addView(badge);
        }
        content.addView(badges);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        AlertDialog dialog = showDialog(new AlertDialog.Builder(this).setTitle("Importer les amis de…")
                .setView(scroll).setNegativeButton("Annuler", null));
        for (int i = 0; i < badges.getChildCount(); i++) badges.getChildAt(i).setTag(dialog);
    }

    private void selectFriendsToImport(Profile source, Profile destination) {
        LinearLayout content = vertical();
        content.setPadding(dp(20), dp(8), dp(20), dp(12));
        content.addView(mutedText("Choisis les amis à importer vers " + destination.name + ".", 14));
        gap(content, 8);
        LinearLayout list = vertical();
        List<CheckBox> choices = new ArrayList<>();
        List<Friend> available = new ArrayList<>();
        if (source.friends.isEmpty()) list.addView(mutedText("Ce profil n’a pas encore d’amis.", 14));
        for (Friend friend : source.friends) {
            boolean duplicate = hasFriend(destination, friend.username);
            CheckBox choice = new CheckBox(this);
            choice.setText(friend.name + " · @" + friend.username + (duplicate ? " — déjà présent" : ""));
            choice.setTextColor(duplicate ? ui.muted : ui.text);
            choice.setButtonTintList(android.content.res.ColorStateList.valueOf(ui.accent));
            choice.setMinHeight(dp(48));
            choice.setEnabled(!duplicate);
            list.addView(choice);
            if (!duplicate) { choices.add(choice); available.add(friend); }
        }
        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);
        int listHeight = Math.min(dp(320), getResources().getDisplayMetrics().heightPixels / 3);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, listHeight));
        CheckBox selectAll = new CheckBox(this);
        selectAll.setText("Tout sélectionner");
        selectAll.setTextColor(ui.text);
        selectAll.setButtonTintList(android.content.res.ColorStateList.valueOf(ui.accent));
        selectAll.setEnabled(!choices.isEmpty());
        content.addView(selectAll);
        Button importButton = primaryButton("Importer (0)", () -> {});
        content.addView(importButton, new LinearLayout.LayoutParams(-1, -2));
        AlertDialog dialog = showDialog(new AlertDialog.Builder(this)
                .setTitle("Amis de " + source.name).setView(content)
                .setNegativeButton("Annuler", null));
        boolean[] updating = {false};
        Runnable update = () -> {
            int count = 0;
            for (CheckBox choice : choices) if (choice.isChecked()) count++;
            updating[0] = true;
            selectAll.setChecked(!choices.isEmpty() && count == choices.size());
            updating[0] = false;
            importButton.setText("Importer (" + count + ")");
            importButton.setEnabled(count > 0);
            importButton.setAlpha(count > 0 ? 1f : .5f);
        };
        for (CheckBox choice : choices) choice.setOnCheckedChangeListener((view, checked) -> update.run());
        selectAll.setOnCheckedChangeListener((view, checked) -> {
            if (updating[0]) return;
            for (CheckBox choice : choices) choice.setChecked(checked);
            update.run();
        });
        importButton.setOnClickListener(view -> {
            int count = 0;
            for (int i = 0; i < choices.size(); i++) {
                if (choices.get(i).isChecked() && FriendList.copyTo(available.get(i), destination.friends, Friend::new)) count++;
            }
            if (count > 0) { persist(); renderHome(); }
            dialog.dismiss();
            Toast.makeText(this, count + " ami(s) importé(s).", Toast.LENGTH_SHORT).show();
        });
        update.run();
    }

    /** Places profile badges on successive lines without constraining their text. */
    private final class ProfileBadgeLayout extends ViewGroup {
        ProfileBadgeLayout() { super(MainActivity.this); }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec);
            int x = 0, y = 0, rowHeight = 0, spacing = dp(8);
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                child.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                int childWidth = child.getMeasuredWidth();
                if (x > 0 && x + childWidth > width) { y += rowHeight + spacing; x = 0; rowHeight = 0; }
                x += childWidth + spacing;
                rowHeight = Math.max(rowHeight, child.getMeasuredHeight());
            }
            setMeasuredDimension(resolveSize(width, widthSpec), resolveSize(y + rowHeight, heightSpec));
        }

        @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int x = 0, y = 0, rowHeight = 0, spacing = dp(8), width = right - left;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                int w = child.getMeasuredWidth(), h = child.getMeasuredHeight();
                if (x > 0 && x + w > width) { y += rowHeight + spacing; x = 0; rowHeight = 0; }
                child.layout(x, y, x + w, y + h);
                x += w + spacing;
                rowHeight = Math.max(rowHeight, h);
            }
        }
    }

    private void removeFriend(Profile owner, Friend f) {
        showDialog(new AlertDialog.Builder(this).setTitle("Supprimer " + f.name + " ?")
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Supprimer", (dialog, which) -> {
                    owner.friends.remove(f);
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

    private void updateSelectionUi() {
        drawerLayout.setDrawerLockMode(selecting ? DrawerLayout.LOCK_MODE_LOCKED_CLOSED : DrawerLayout.LOCK_MODE_UNLOCKED);
        selectionClose.setVisibility(selecting ? View.VISIBLE : View.GONE);
        menuButton.setVisibility(selecting ? View.GONE : View.VISIBLE);
        selectionBar.setVisibility(selecting ? View.VISIBLE : View.GONE);
        selectionAction.setText("🔥  Préparer (" + selectedFriends.size() + ")");
        selectionAction.setEnabled(!selectedFriends.isEmpty());
        selectionAction.setAlpha(selectedFriends.isEmpty() ? .5f : 1f);
    }

    private void exitSelection() {
        selecting = false;
        selectedFriends.clear();
        if (selectionBar != null) updateSelectionUi();
    }

    private void startSelectedRequests() {
        if (selectedFriends.isEmpty()) return;
        requestQueue.clear();
        requestQueue.addAll(selectedFriends);
        requestIndex = 0;
        prepareCurrent(requestQueue.get(0));
    }

    private void prepare(Friend friend) {
        requestQueue.clear();
        requestQueue.add(friend);
        requestIndex = 0;
        prepareCurrent(friend);
    }

    private void prepareCurrent(Friend friend) {
        requestSession++;
        submissionObserved = false;
        submissionTransitioned = false;
        submitMarker = "streak-submit-" + UUID.randomUUID();
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(home.getWindowToken(), 0);
        cancelFormScroll();
        formTouched = false;
        lockFormInteractions();
        pendingProfile = active;
        pendingFriend = friend;
        browserContext.setText(active.name + " · @" + friend.username + " · " + (requestIndex + 1) + "/" + requestQueue.size());
        setBrowserStatus("");
        drawerLayout.closeDrawer(drawerPanel, false);
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        homePanel.setVisibility(View.GONE);
        browser.loadUrl(FORM_URL); // Fresh request; no programmatic submission.
    }

    private void installSubmitObserver() {
        String marker = JSONObject.quote(submitMarker);
        browser.evaluateJavascript("(function(){if(window.__streakSubmitObserver)return;"
                + "window.__streakSubmitObserver=true;let sent=false;"
                + "document.addEventListener('click',function(e){"
                + "if(sent || !e.isTrusted || !document.getElementById('request_custom_fields_24369736'))return;"
                + "let target=e.target instanceof Element ? e.target.closest('button, input[type=submit]') : null;"
                + "if(!target || !target.form || target.disabled)return;"
                + "if(target.tagName==='BUTTON' && target.type!=='submit')return;"
                + "sent=true;console.log(" + marker + ");},true);})()", null);
    }

    private void monitorSubmissionResult(int session, int attempt) {
        if (session != requestSession || pendingFriend == null || submissionTransitioned) return;
        if (attempt >= 60) {
            setBrowserStatus("Réponse du site non détectée. Vérifie la page et ton e-mail avant de réessayer ; la série est arrêtée.");
            return;
        }
        Uri uri = Uri.parse(browser.getUrl() == null ? "" : browser.getUrl());
        boolean official = "https".equals(uri.getScheme()) && "help.snapchat.com".equals(uri.getHost());
        if (official) {
            int generation = formGeneration;
            browser.evaluateJavascript("(function(){if(!document.body)return false;"
                    + "let t=(document.body.innerText||'').toLowerCase().normalize('NFD')"
                    + ".replace(/[\\u0300-\\u036f]/g,'').replace(/\\s+/g,' ');"
                    + "return /nous avons (?:bien )?(?:pris en compte|recu).{0,100}(?:demande|requete)/.test(t)"
                    + "|| /(?:votre|ta) (?:demande|requete).{0,100}(?:bien )?(?:prise en compte|recue)/.test(t);})()", result -> {
                if (session != requestSession || pendingFriend == null || submissionTransitioned) return;
                if (generation == formGeneration && "true".equals(result)) {
                    submissionTransitioned = true;
                    if (requestIndex + 1 < requestQueue.size()) {
                        requestIndex++;
                        prepareCurrent(requestQueue.get(requestIndex));
                    } else {
                        setBrowserStatus("Réponse du site détectée.");
                        LinearLayout finishActions = horizontal();
                        finishActions.setPadding(dp(20), dp(8), dp(20), dp(16));
                        TextView thanks = mutedText("merci Mamads <3", 10);
                        finishActions.addView(thanks, new LinearLayout.LayoutParams(0, -2, 1));
                        Button homeButton = primaryButton("Retour à l’accueil", () -> {});
                        finishActions.addView(homeButton);
                        AlertDialog finished = showDialog(new AlertDialog.Builder(this)
                                .setTitle("🔥 Les flammes sont entre de bonnes mains !")
                                .setMessage("Les demandes ont été envoyées. Retourne à l’accueil pour retrouver tes amis.")
                                .setView(finishActions));
                        homeButton.setOnClickListener(view -> { finished.dismiss(); showHome(); });
                    }
                } else {
                    browser.postDelayed(() -> monitorSubmissionResult(session, attempt + 1), 500);
                }
            });
        } else {
            browser.postDelayed(() -> monitorSubmissionResult(session, attempt + 1), 500);
        }
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
        int generation = formGeneration;
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
            if (generation != formGeneration || pendingProfile != p || pendingFriend != friend) return;
            try {
                String actual = response;
                if (actual != null && actual.startsWith("\"")) {
                    actual = new JSONArray("[" + actual + "]").getString(0);
                }
                JSONObject outcome = new JSONObject(actual);
                int filled = outcome.optInt("filled");
                if (filled < 5 && attempt < 3) {
                    browser.postDelayed(() -> {
                        if (generation == formGeneration && pendingProfile == p && pendingFriend == friend) fillForm(attempt + 1);
                    }, 800);
                } else {
                    setBrowserStatus("");
                    if (formInteractionLocked && !formTouched && isSupportForm(browser.getUrl())) {
                        browser.evaluateJavascript(FormNavigation.SCRIPT.replace(
                                "__STREAK_SCROLL_DONE__", JSONObject.quote(scrollDoneMarker)), null);
                    } else {
                        unlockFormInteractions();
                    }
                }
            } catch (Exception e) {
                unlockFormInteractions();
                setBrowserStatus("Préremplissage indisponible : remplis le formulaire manuellement.");
            }
        });
    }

    private void lockFormInteractions() {
        formInteractionLocked = true;
        scrollDoneMarker = "streak-scroll-done-" + UUID.randomUUID();
        int lockGeneration = ++interactionLockGeneration;
        browser.clearFocus();
        browser.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        browser.postDelayed(() -> {
            if (formInteractionLocked && interactionLockGeneration == lockGeneration) {
                cancelFormScroll();
            }
        }, 8000);
    }

    private void unlockFormInteractions() {
        formInteractionLocked = false;
        scrollDoneMarker = null;
        interactionLockGeneration++;
        browser.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
    }

    private void cancelFormScroll() {
        unlockFormInteractions();
        formGeneration++;
        if (browser != null) browser.evaluateJavascript(FormNavigation.CANCEL, null);
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
        else if (selecting) { exitSelection(); renderHome(); }
        else if (searchOpen && searchToggle != null) searchToggle.performClick();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (browser != null) browser.destroy();
        super.onDestroy();
    }
}

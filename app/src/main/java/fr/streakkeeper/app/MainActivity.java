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
import android.widget.Switch;
import android.content.res.ColorStateList;
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
    private SelectionScrollView home;
    private LinearLayout homePanel;
    private DrawerLayout drawerLayout;
    private LinearLayout drawerPanel;
    private LinearLayout drawerProfiles;
    private Button menuButton;
    private ImageButton settingsButton;
    private ImageButton selectionEdit, selectionCopy, selectionDelete;
    private TextView toolbarTitle;
    private Button procedureBack, procedureNext;
    private boolean automaticAdvance, procedureFinished, automaticFill, automaticCenter;
    private WebView browser;
    private TextView browserStatus;
    private boolean selecting;
    private final List<Friend> selectedFriends = new ArrayList<>();
    private final List<Friend> requestQueue = new ArrayList<>();
    private int requestIndex;
    private int requestSession;
    private boolean submissionObserved;
    private boolean submissionTransitioned;
    private boolean captchaWaiting;
    private String submitMarker;
    private LinearLayout selectionBar;
    private Button selectionAction;
    private ImageButton selectionClose;

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
        procedureBack = button("Retour au menu principal", this::showHome);
        procedureNext = primaryButton("Suivant", this::advanceManually);
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(0, -2, 1);
        backParams.setMarginEnd(dp(8));
        actions.addView(procedureBack, backParams);
        actions.addView(procedureNext, new LinearLayout.LayoutParams(0, -2, 1));
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
                        if (automaticFill) fillForm(0);
                        else finishFormPreparation();
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
        toolbarTitle = text("Streak keeper", 22, true);
        toolbarTitle.setSingleLine(true);
        toolbarTitle.setEllipsize(TextUtils.TruncateAt.END);
        toolbar.addView(toolbarTitle, new LinearLayout.LayoutParams(0, -2, 1));
        selectionEdit = friendToolIcon(R.drawable.ic_edit, "Modifier l’ami sélectionné");
        selectionEdit.setOnClickListener(view -> {
            if (active != null && selectedFriends.size() == 1) editFriend(active, selectedFriends.get(0));
        });
        toolbar.addView(selectionEdit, new LinearLayout.LayoutParams(dp(48), dp(48)));
        selectionCopy = friendToolIcon(R.drawable.ic_copy_to, "Copier les amis sélectionnés");
        selectionCopy.setOnClickListener(view -> copySelectedFriends());
        toolbar.addView(selectionCopy, new LinearLayout.LayoutParams(dp(48), dp(48)));
        selectionDelete = friendToolIcon(R.drawable.ic_delete, "Supprimer les amis sélectionnés");
        selectionDelete.setOnClickListener(view -> deleteSelectedFriends());
        toolbar.addView(selectionDelete, new LinearLayout.LayoutParams(dp(48), dp(48)));
        selectionEdit.setVisibility(View.GONE);
        selectionCopy.setVisibility(View.GONE);
        selectionDelete.setVisibility(View.GONE);
        settingsButton = friendToolIcon(R.drawable.ic_settings, "Paramètres");
        android.graphics.drawable.GradientDrawable settingsCircle = new android.graphics.drawable.GradientDrawable();
        settingsCircle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        settingsCircle.setColor(ui.surface);
        settingsButton.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(ui.accentSoft), settingsCircle, null));
        settingsButton.setOnClickListener(view -> showSettings());
        toolbar.addView(settingsButton, new LinearLayout.LayoutParams(dp(48), dp(48)));
        selectionClose = friendToolIcon(R.drawable.ic_close, "Quitter la sélection");
        selectionClose.setOnClickListener(view -> { exitSelection(); renderHome(); });
        toolbar.addView(selectionClose, new LinearLayout.LayoutParams(dp(48), dp(48)));
        selectionClose.setVisibility(View.GONE);
        homePanel.addView(toolbar);

        home = new SelectionScrollView();
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
            LinearLayout row = horizontal();
            row.setTag(friend);
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
                boolean selected = selectedFriends.contains(friend);
                TextView indicator = selectionIndicator();
                indicator.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
                FrameLayout slot = new FrameLayout(this);
                FrameLayout.LayoutParams badge = new FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER);
                slot.addView(indicator, badge);
                row.addView(slot, new LinearLayout.LayoutParams(dp(48), dp(48)));
                row.setContentDescription((selected ? "Sélectionné : " : "Non sélectionné : ")
                        + friend.name + ", @" + friend.username);
                row.setSelected(selected);
                row.setOnLongClickListener(view -> {
                    boolean target = !selectedFriends.contains(friend);
                    home.armSelectionDrag(target, friend);
                    row.performClick();
                    return true;
                });
                row.setOnClickListener(v -> {
                    boolean nowSelected = !selectedFriends.contains(friend);
                    if (nowSelected) selectedFriends.add(friend);
                    else selectedFriends.remove(friend);
                    indicator.setVisibility(nowSelected ? View.VISIBLE : View.INVISIBLE);
                    row.setSelected(nowSelected);
                    row.setContentDescription((nowSelected ? "Sélectionné : " : "Non sélectionné : ")
                            + friend.name + ", @" + friend.username);
                    updateSelectionUi();
                });
            } else {
                View.OnLongClickListener selectOnLongPress = view -> {
                    selecting = true;
                    selectedFriends.clear();
                    home.armSelectionDrag(true, friend);
                    selectedFriends.add(friend);
                    updateSelectionUi();
                    renderFriendResults();
                    return true;
                };
                row.setOnLongClickListener(selectOnLongPress);
                ImageButton prepare = friendToolIcon(R.drawable.ic_flame,
                        "Préparer la demande pour " + friend.name + ", @" + friend.username);
                android.graphics.drawable.GradientDrawable circle = new android.graphics.drawable.GradientDrawable();
                circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                circle.setColor(ui.accentSoft);
                prepare.setBackground(new android.graphics.drawable.RippleDrawable(
                        android.content.res.ColorStateList.valueOf(ui.border), circle, null));
                prepare.setImageTintList(android.content.res.ColorStateList.valueOf(ui.accent));
                prepare.setOnClickListener(view -> prepare(friend));
                prepare.setOnLongClickListener(selectOnLongPress);
                row.addView(prepare, new LinearLayout.LayoutParams(dp(48), dp(48)));

            }
            LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2);
            layout.bottomMargin = dp(8);
            friendResults.addView(row, layout);
        }
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

    private void copySelectedFriends() {
        if (active == null || selectedFriends.isEmpty()) return;
        List<Friend> selection = new ArrayList<>(selectedFriends);
        List<Profile> destinations = new ArrayList<>();
        for (Profile profile : profiles) if (profile != active) destinations.add(profile);
        if (destinations.isEmpty()) {
            showDialog(new AlertDialog.Builder(this).setTitle("Aucun autre profil")
                    .setMessage("Ajoute un autre profil depuis le menu pour y copier tes amis.")
                    .setPositiveButton("Compris", null));
            return;
        }
        LinearLayout content = vertical();
        content.setPadding(dp(20), dp(8), dp(20), dp(12));
        content.addView(mutedText("Choisis le profil vers lequel copier les amis sélectionnés.", 14));
        gap(content, 12);
        ProfileBadgeLayout badges = new ProfileBadgeLayout();
        for (Profile destination : destinations) {
            int duplicates = 0;
            for (Friend friend : selection) if (hasFriend(destination, friend.username)) duplicates++;
            String label = destination.name + (duplicates > 0 ? " · " + duplicates + " déjà présent(s)" : "");
            Button badge = profileBadge(label);
            badge.setContentDescription(destination.name + " · @" + destination.username
                    + (duplicates > 0 ? " · " + duplicates + " déjà présent(s)" : ""));
            badge.setOnClickListener(view -> {
                int copied = 0;
                for (Friend friend : selection) {
                    if (FriendList.copyTo(friend, destination.friends, Friend::new)) copied++;
                }
                if (copied > 0) persist();
                int skipped = selection.size() - copied;
                ((AlertDialog) badge.getTag()).dismiss();
                Toast.makeText(this, copied + " ami(s) copié(s) vers " + destination.name
                        + (skipped > 0 ? " · " + skipped + " déjà présent(s)" : ""), Toast.LENGTH_LONG).show();
            });
            badges.addView(badge);
        }
        content.addView(badges);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        AlertDialog dialog = showDialog(new AlertDialog.Builder(this).setTitle("Copier vers…")
                .setView(scroll).setNegativeButton("Annuler", null));
        for (int i = 0; i < badges.getChildCount(); i++) badges.getChildAt(i).setTag(dialog);
    }

    private void deleteSelectedFriends() {
        if (active == null || selectedFriends.isEmpty()) return;
        Profile owner = active;
        List<Friend> selection = new ArrayList<>(selectedFriends);
        String label = selection.size() == 1 ? "Supprimer " + selection.get(0).name + " ?"
                : "Supprimer " + selection.size() + " amis ?";
        showDialog(new AlertDialog.Builder(this).setTitle(label)
                .setMessage("Cette action est définitive.")
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Supprimer", (dialog, which) -> {
                    owner.friends.removeAll(selection);
                    persist();
                    exitSelection();
                    renderHome();
                }));
    }

    private void showGuide() {
        LinearLayout content = vertical();
        content.setPadding(dp(24), dp(8), dp(24), dp(16));
        String[][] steps = {
                {"1. Prépare ton profil", "Ouvre ☰ pour créer ou choisir un profil Snapchat. Ajoute tes amis ou importe-les depuis un autre profil."},
                {"2. Choisis les amis", "Pour une demande, touche la flamme d’un ami. Pour plusieurs, maintiens un ami appuyé, puis touche ou parcours les autres en gardant le doigt appuyé. La barre supérieure permet aussi de modifier, copier ou supprimer la sélection."},
                {"3. Vérifie chaque demande", "L’application prépare le formulaire officiel d’assistance pour chaque ami. Relis les informations, complète-les si nécessaire et appuie toi-même sur Envoyer."},
                {"4. Poursuis à ton rythme", "Dans les paramètres ⚙, choisis le passage automatique ou manuel au formulaire suivant, ainsi que le préremplissage et le centrage. En mode manuel, Suivant peut passer un formulaire sans l’envoyer."},
                {"Vérification CAPTCHA", "Snapchat peut demander une vérification : termine-la sur la page et appuie à nouveau sur Envoyer si nécessaire. Avant de réessayer, vérifie si la demande a été reçue. Aucun réglage ne garantit l’absence de CAPTCHA."}
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
            Button choice = profileBadge((selected ? "✓  " : "") + profile.name + "\n@" + profile.username);
            choice.setOnClickListener(view -> {
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
            if (selected) {
                choice.setBackground(new android.graphics.drawable.RippleDrawable(
                        android.content.res.ColorStateList.valueOf(ui.accentSoft),
                        ui.shape(ui.accent, ui.accent), null));
                choice.setTextColor(ui.onAccent);
            }
            LinearLayout.LayoutParams row = new LinearLayout.LayoutParams(-1, -2);
            row.bottomMargin = dp(8);
            choice.setMinHeight(dp(68));
            LinearLayout profileRow = horizontal();
            LinearLayout badgeContainer = horizontal();
            badgeContainer.addView(choice, new LinearLayout.LayoutParams(-2, -2));
            profileRow.addView(badgeContainer, new LinearLayout.LayoutParams(0, -2, 1));
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
        AlertDialog dialog = showDialog(new AlertDialog.Builder(this)
                .setTitle(adding ? "Nouveau preset" : "Modifier le preset")
                .setView(scroll)
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Enregistrer", null));
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            if (blank(name) || blank(username) || blank(email) || blank(phone)) {
                Toast.makeText(this, "Tous les champs sauf la description sont nécessaires.", Toast.LENGTH_LONG).show();
                return;
            }
            boolean test = adding && name.getText().toString().equals("test")
                    && username.getText().toString().equals("test")
                    && email.getText().toString().equals("test")
                    && phone.getText().toString().equals("test");
            int count = test ? TestProfiles.count(value(description)) : 0;
            if (test && count < 0) {
                descriptionEnabled.setChecked(true);
                description.setError("Indique un nombre entier entre 1 et " + TestProfiles.MAX_FRIENDS + ", ou laisse vide pour 10 amis.");
                description.requestFocus();
                return;
            }
            candidate.name = value(name);
            candidate.username = value(username);
            candidate.email = value(email);
            candidate.phone = value(phone);
            candidate.description = value(description);
            candidate.descriptionEnabled = descriptionEnabled.isChecked();
            if (test) {
                SharedPreferences preferences = getSharedPreferences(PREFERENCES_FILE, MODE_PRIVATE);
                long number = preferences.getLong("testProfileNumber", 0);
                for (Profile profile : profiles) {
                    String existing = profile.name.toLowerCase(java.util.Locale.ROOT);
                    if (existing.matches("test[0-9]+")) {
                        try { number = Math.max(number, Long.parseLong(existing.substring(4))); }
                        catch (NumberFormatException ignored) { /* Too large to be generated. */ }
                    }
                }
                boolean collision;
                do {
                    number++;
                    String proposed = "test" + number;
                    collision = false;
                    for (Profile profile : profiles) {
                        if (profile.name.equalsIgnoreCase(proposed)) { collision = true; break; }
                    }
                } while (collision);
                candidate.name = "test" + number;
                candidate.description = "";
                candidate.descriptionEnabled = false;
                for (String pseudo : TestProfiles.usernames(count, new java.util.Random())) {
                    candidate.friends.add(new Friend(pseudo, pseudo));
                }
                preferences.edit().putLong("testProfileNumber", number).apply();
            }
            if (adding) {
                profiles.add(candidate);
                active = candidate;
            }
            persist();
            dialog.dismiss();
            renderHome();
        });
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

    private Button profileBadge(String label) {
        Button badge = button(label, () -> {});
        badge.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(ui.accentSoft),
                ui.shape(ui.accentSoft, ui.accent), null));
        badge.setTextColor(ui.accent);
        badge.setElevation(dp(2));
        return badge;
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
            Button badge = profileBadge(source.name);
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
        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Rechercher un ami");
        search.setContentDescription("Rechercher un ami à importer");
        ui.input(search);
        content.addView(search, new LinearLayout.LayoutParams(-1, -2));
        gap(content, 12);
        LinearLayout list = vertical();
        List<Friend> available = new ArrayList<>();
        List<Friend> chosen = new ArrayList<>();
        SelectionScrollView scroll = new SelectionScrollView(list, chosen);
        Button selectAll = button("Tout sélectionner", () -> {});
        Button importButton = primaryButton("Importer (0)", () -> {});
        Runnable update = () -> {
            boolean all = !available.isEmpty() && chosen.size() == available.size();
            selectAll.setText(all ? "Tout désélectionner" : "Tout sélectionner");
            selectAll.setEnabled(!available.isEmpty());
            selectAll.setAlpha(available.isEmpty() ? .5f : 1f);
            importButton.setText("Importer (" + chosen.size() + ")");
            importButton.setEnabled(!chosen.isEmpty());
            importButton.setAlpha(chosen.isEmpty() ? .5f : 1f);
        };
        if (source.friends.isEmpty()) list.addView(mutedText("Ce profil n’a pas encore d’amis.", 14));
        for (Friend friend : source.friends) {
            boolean duplicate = hasFriend(destination, friend.username);
            if (!duplicate) available.add(friend);
            LinearLayout row = horizontal();
            row.setTag(friend);
            row.setBackground(ui.shape(ui.surface, ui.border));
            row.setPadding(dp(14), dp(10), dp(8), dp(10));
            LinearLayout labels = vertical();
            TextView name = text(friend.name, 16, true);
            name.setMaxLines(2);
            name.setEllipsize(TextUtils.TruncateAt.END);
            if (duplicate) name.setTextColor(ui.muted);
            labels.addView(name);
            TextView username = mutedText("@" + friend.username + (duplicate ? " · Déjà présent" : ""), 13);
            username.setMaxLines(2);
            username.setEllipsize(TextUtils.TruncateAt.END);
            labels.addView(username);
            row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
            TextView indicator = selectionIndicator();
            indicator.setVisibility(View.INVISIBLE);
            FrameLayout slot = new FrameLayout(this);
            slot.addView(indicator, new FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER));
            row.addView(slot, new LinearLayout.LayoutParams(dp(48), dp(48)));
            row.setEnabled(!duplicate);
            row.setContentDescription(friend.name + ", @" + friend.username
                    + (duplicate ? ", déjà présent" : ", non sélectionné"));
            if (!duplicate) {
                row.setOnClickListener(view -> {
                    boolean selected = !chosen.contains(friend);
                    if (selected) chosen.add(friend); else chosen.remove(friend);
                    indicator.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
                    row.setSelected(selected);
                    row.setContentDescription(friend.name + ", @" + friend.username
                            + (selected ? ", sélectionné" : ", non sélectionné"));
                    update.run();
                });
                row.setOnLongClickListener(view -> {
                    scroll.armSelectionDrag(!chosen.contains(friend), friend);
                    row.performClick();
                    return true;
                });
            }
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.bottomMargin = dp(8);
            list.addView(row, params);
        }
        TextView noResults = mutedText("Aucun ami trouvé.", 14);
        noResults.setVisibility(View.GONE);
        list.addView(noResults);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                scroll.stopSelectionDrag();
                List<Friend> matches = FriendList.select(source.friends, text.toString(), FriendList.Order.OLDEST);
                int shown = 0;
                for (int i = 0; i < list.getChildCount(); i++) {
                    View row = list.getChildAt(i);
                    if (!(row.getTag() instanceof Friend)) continue;
                    boolean visible = matches.contains((Friend) row.getTag());
                    row.setVisibility(visible ? View.VISIBLE : View.GONE);
                    if (visible) shown++;
                }
                noResults.setVisibility(shown == 0 && !source.friends.isEmpty() ? View.VISIBLE : View.GONE);
                scroll.scrollTo(0, 0);
            }
            @Override public void afterTextChanged(Editable text) { }
        });
        scroll.addView(list);
        int listHeight = Math.min(dp(320), getResources().getDisplayMetrics().heightPixels / 3);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, listHeight));
        gap(content, 12);
        content.addView(selectAll, new LinearLayout.LayoutParams(-1, -2));
        gap(content, 8);
        content.addView(importButton, new LinearLayout.LayoutParams(-1, -2));
        AlertDialog dialog = showDialog(new AlertDialog.Builder(this)
                .setTitle("Amis de " + source.name).setView(content)
                .setNegativeButton("Annuler", null));
        dialog.setOnDismissListener(ignored -> scroll.stopSelectionDrag());
        selectAll.setOnClickListener(view -> {
            boolean select = chosen.size() != available.size();
            for (int i = 0; i < list.getChildCount(); i++) {
                View row = list.getChildAt(i);
                if (row.isEnabled() && row.getTag() instanceof Friend
                        && chosen.contains((Friend) row.getTag()) != select) row.performClick();
            }
            update.run();
        });
        importButton.setOnClickListener(view -> {
            int count = 0;
            for (Friend friend : chosen) {
                if (FriendList.copyTo(friend, destination.friends, Friend::new)) count++;
            }
            if (count > 0) { persist(); renderHome(); }
            dialog.dismiss();
            Toast.makeText(this, count + " ami(s) importé(s).", Toast.LENGTH_SHORT).show();
        });
        update.run();
    }

    private TextView selectionIndicator() {
        TextView indicator = text("✓", 17, true);
        indicator.setTextColor(ui.onAccent);
        indicator.setGravity(Gravity.CENTER);
        indicator.setPadding(0, 0, 0, 0);
        android.graphics.drawable.GradientDrawable circle = new android.graphics.drawable.GradientDrawable();
        circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        circle.setColor(ui.accent);
        indicator.setBackground(circle);
        indicator.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return indicator;
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
        toolbarTitle.setText(selecting ? selectedFriends.size() + " sélectionné(s)" : "Streak keeper");
        toolbarTitle.setTextSize(selecting ? 16 : 22);
        selectionEdit.setVisibility(selecting && selectedFriends.size() == 1 ? View.VISIBLE : View.GONE);
        selectionCopy.setVisibility(selecting ? View.VISIBLE : View.GONE);
        selectionDelete.setVisibility(selecting ? View.VISIBLE : View.GONE);
        selectionCopy.setEnabled(!selectedFriends.isEmpty());
        selectionDelete.setEnabled(!selectedFriends.isEmpty());
        selectionCopy.setAlpha(selectedFriends.isEmpty() ? .4f : 1f);
        selectionDelete.setAlpha(selectedFriends.isEmpty() ? .4f : 1f);
        menuButton.setVisibility(selecting ? View.GONE : View.VISIBLE);
        settingsButton.setVisibility(selecting ? View.GONE : View.VISIBLE);
        selectionBar.setVisibility(selecting ? View.VISIBLE : View.GONE);
        selectionAction.setText("🔥  Préparer (" + selectedFriends.size() + ")");
        selectionAction.setEnabled(!selectedFriends.isEmpty());
        selectionAction.setAlpha(selectedFriends.isEmpty() ? .5f : 1f);
    }

    private void exitSelection() {
        selecting = false;
        if (home != null) home.stopSelectionDrag();
        selectedFriends.clear();
        if (selectionBar != null) updateSelectionUi();
    }

    private final class SelectionScrollView extends ScrollView {
        private final LinearLayout importRows;
        private final List<Friend> importSelection;
        private boolean armed, dragging, selectValue;
        private LinearLayout rows() { return importRows == null ? friendResults : importRows; }
        private List<Friend> selection() { return importRows == null ? selectedFriends : importSelection; }
        private float downX, downY, pointerX, pointerY, scrollRemainder;
        private long lastFrame;
        private final List<Friend> originalSelection = new ArrayList<>();
        private Friend anchor, endpoint;
        private final Runnable scrollSelection = new Runnable() {
            @Override public void run() {
                if (!dragging || (importRows == null && !selecting)) return;
                int[] position = new int[2];
                getLocationOnScreen(position);
                float y = pointerY - position[1];
                float edge = dp(96);
                float strength = y < edge ? -Math.min(1f, (edge - y) / edge)
                        : y > getHeight() - edge ? Math.min(1f, (y - (getHeight() - edge)) / edge) : 0;
                long now = android.os.SystemClock.uptimeMillis();
                float seconds = Math.min(0.05f, (now - lastFrame) / 1000f);
                lastFrame = now;
                scrollRemainder += strength * dp(1200) * seconds;
                int delta = (int) scrollRemainder;
                scrollRemainder -= delta;
                if (delta != 0) {
                    int before = getScrollY();
                    scrollBy(0, delta);
                    if (getScrollY() != before) selectUnderPointer();
                }
                postOnAnimation(this);
            }
        };

        SelectionScrollView() { this(null, null); }
        SelectionScrollView(LinearLayout rows, List<Friend> selection) {
            super(MainActivity.this);
            importRows = rows;
            importSelection = selection;
        }

        void armSelectionDrag(boolean select, Friend start) {
            armed = true;
            selectValue = select;
            anchor = start;
            endpoint = start;
            originalSelection.clear();
            originalSelection.addAll(selection());
        }

        void stopSelectionDrag() {
            armed = false;
            dragging = false;
            removeCallbacks(scrollSelection);
            scrollRemainder = 0;
            originalSelection.clear();
            anchor = null;
            endpoint = null;
        }

        private void selectUnderPointer() {
            if (rows() == null) return;
            int[] position = new int[2];
            getLocationOnScreen(position);
            if (pointerX < position[0] || pointerX >= position[0] + getWidth()) return;
            float hitY = Math.max(position[1], Math.min(pointerY, position[1] + getHeight() - 1));
            for (int i = 0; i < rows().getChildCount(); i++) {
                View row = rows().getChildAt(i);
                if (row.getVisibility() != View.VISIBLE || !row.isEnabled() || !(row.getTag() instanceof Friend)) continue;
                row.getLocationOnScreen(position);
                if (pointerX >= position[0] && pointerX < position[0] + row.getWidth()
                        && hitY >= position[1] && hitY < position[1] + row.getHeight()) {
                    Friend friend = (Friend) row.getTag();
                    updateRange(friend);
                    break;
                }
            }
        }

        private void updateRange(Friend current) {
            if (current == endpoint) return;
            int start = -1, end = -1, previousEnd = -1;
            for (int i = 0; i < rows().getChildCount(); i++) {
                Object friend = rows().getChildAt(i).getTag();
                if (friend == anchor) start = i;
                if (friend == current) end = i;
                if (friend == endpoint) previousEnd = i;
            }
            if (start < 0 || end < 0 || previousEnd < 0) return;
            endpoint = current;
            int first = Math.min(start, end), last = Math.max(start, end);
            int previousFirst = Math.min(start, previousEnd), previousLast = Math.max(start, previousEnd);
            for (int i = 0; i < rows().getChildCount(); i++) {
                View row = rows().getChildAt(i);
                if (row.getVisibility() != View.VISIBLE || !row.isEnabled() || !(row.getTag() instanceof Friend)) continue;
                Friend friend = (Friend) row.getTag();
                boolean inRange = i >= first && i <= last;
                boolean wasInRange = i >= previousFirst && i <= previousLast;
                if (!inRange && !wasInRange) continue;
                boolean selected = inRange ? selectValue : !selectValue;
                if (selection().contains(friend) != selected) row.performClick();
            }
            // Keep the original order for friends selected before this gesture.
            List<Friend> ordered = new ArrayList<>();
            for (Friend friend : originalSelection) if (selection().contains(friend)) ordered.add(friend);
            for (Friend friend : selection()) if (!ordered.contains(friend)) ordered.add(friend);
            selection().clear();
            selection().addAll(ordered);
        }

        @Override public boolean dispatchTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                stopSelectionDrag();
                downX = event.getRawX();
                downY = event.getRawY();
            } else if (action == MotionEvent.ACTION_MOVE && armed) {
                float previousX = dragging ? pointerX : downX;
                float previousY = dragging ? pointerY : downY;
                pointerX = event.getRawX();
                pointerY = event.getRawY();
                if (!dragging && Math.hypot(pointerX - downX, pointerY - downY)
                        > android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop()) {
                    dragging = true;
                    MotionEvent cancel = MotionEvent.obtain(event);
                    cancel.setAction(MotionEvent.ACTION_CANCEL);
                    super.dispatchTouchEvent(cancel);
                    cancel.recycle();
                    lastFrame = android.os.SystemClock.uptimeMillis();
                    postOnAnimation(scrollSelection);
                }
                if (dragging) {
                    float endX = pointerX, endY = pointerY;
                    int steps = Math.max(1, (int) Math.ceil(Math.hypot(endX - previousX, endY - previousY) / dp(12)));
                    for (int step = 1; step <= steps; step++) {
                        pointerX = previousX + (endX - previousX) * step / steps;
                        pointerY = previousY + (endY - previousY) * step / steps;
                        selectUnderPointer();
                    }
                    return true;
                }
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL
                    || action == MotionEvent.ACTION_POINTER_DOWN) {
                if (dragging) {
                    stopSelectionDrag();
                    return true;
                }
                stopSelectionDrag();
            }
            return super.dispatchTouchEvent(event);
        }
    }

    private void showSettings() {
        LinearLayout page = vertical();
        page.setPadding(dp(20), dp(12), dp(20), dp(24));
        page.setBackgroundColor(ui.surface);
        LinearLayout navigation = settingsSection(page, "Enchaînement", "Choisis comment passer d’un ami à l’autre.");
        addSetting(navigation, "Formulaire suivant automatique",
                "Passe au suivant après confirmation de réception.", "automaticAdvance",
                "Quand cette option est activée, le formulaire suivant s’ouvre après la réponse du site. "
                + "Sinon, utilise Suivant pour avancer à ton rythme, même sans attendre la confirmation. "
                + "L’envoi de chaque demande reste manuel.");

        gap(page, 20);
        LinearLayout form = settingsSection(page, "Formulaire", "Adapte la préparation à tes habitudes.");
        addSetting(form, "Préremplissage automatique",
                "Renseigne les informations enregistrées pour toi.", "automaticFill",
                "Les coordonnées du profil et le pseudo de l’ami sont renseignés dans le formulaire. "
                + "Désactive cette option si tu préfères saisir les informations toi-même. "
                + "Vérifie toujours le formulaire avant l’envoi.");
        View divider = new View(this);
        divider.setBackgroundColor(ui.border);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, dp(1));
        dividerParams.setMargins(dp(16), 0, dp(16), 0);
        form.addView(divider, dividerParams);
        addSetting(form, "Centrage automatique",
                "Affiche directement la partie utile du formulaire.", "automaticCenter",
                "Après le chargement, la page se place sur le bouton d’envoi ou sur un élément "
                + "qui demande ton attention, comme une erreur ou une vérification visible. "
                + "Tu peux désactiver cette option pour parcourir le formulaire toi-même.");

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(page);
        LinearLayout layout = vertical();
        layout.setBackgroundColor(ui.surface);
        LinearLayout heading = horizontal();
        heading.setPadding(dp(24), dp(16), dp(20), dp(8));
        heading.addView(text("Paramètres", 22, true), new LinearLayout.LayoutParams(0, -2, 1));
        ImageButton close = friendToolIcon(R.drawable.ic_close, "Fermer les paramètres");
        close.setBackground(new android.graphics.drawable.RippleDrawable(
                ColorStateList.valueOf(ui.border), ui.shape(ui.background, Color.TRANSPARENT), null));
        heading.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
        layout.addView(heading);
        layout.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        AlertDialog settings = showDialog(new AlertDialog.Builder(this).setView(layout));
        close.setOnClickListener(view -> settings.dismiss());
        android.view.Window window = settings.getWindow();
        if (window != null) {
            int height = (int) (getResources().getDisplayMetrics().heightPixels * .8f);
            window.setLayout(window.getAttributes().width, height);
        }
    }

    private LinearLayout settingsSection(LinearLayout page, String title, String description) {
        LinearLayout card = vertical();
        card.setBackground(ui.shape(ui.surface, ui.border));
        page.addView(card, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout header = horizontal();
        header.setPadding(dp(16), dp(14), dp(12), dp(14));
        header.setMinimumHeight(dp(64));
        header.setBackground(new android.graphics.drawable.RippleDrawable(
                ColorStateList.valueOf(ui.accentSoft), ui.shape(ui.surface, Color.TRANSPARENT), null));
        View accent = new View(this);
        android.graphics.drawable.GradientDrawable accentShape = new android.graphics.drawable.GradientDrawable();
        accentShape.setColor(ui.accent);
        accentShape.setCornerRadius(dp(3));
        accent.setBackground(accentShape);
        LinearLayout.LayoutParams accentParams = new LinearLayout.LayoutParams(dp(4), dp(36));
        accentParams.setMarginEnd(dp(12));
        header.addView(accent, accentParams);
        LinearLayout words = vertical();
        words.addView(text(title, 17, true));
        words.addView(mutedText(description, 13));
        header.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        TextView chevron = text("⌃", 24, true);
        chevron.setTextColor(ui.accent);
        chevron.setGravity(Gravity.CENTER);
        chevron.setPadding(0, 0, 0, 0);
        chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        header.addView(chevron, new LinearLayout.LayoutParams(dp(40), dp(48)));
        card.addView(header);
        LinearLayout section = vertical();
        card.addView(section, new LinearLayout.LayoutParams(-1, -2));
        header.setFocusable(true);
        header.setContentDescription(title + ", déplié. Appuyer pour replier.");
        final boolean[] expanded = {true};
        final android.animation.ValueAnimator[] animation = {null};
        header.setOnClickListener(view -> {
            expanded[0] = !expanded[0];
            if (animation[0] != null) animation[0].cancel();
            int start = section.getVisibility() == View.VISIBLE ? section.getHeight() : 0;
            section.setVisibility(View.VISIBLE);
            section.measure(View.MeasureSpec.makeMeasureSpec(card.getWidth(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int end = expanded[0] ? section.getMeasuredHeight() : 0;
            chevron.setText(expanded[0] ? "⌃" : "⌄");
            header.setContentDescription(title + (expanded[0] ? ", déplié. Appuyer pour replier."
                    : ", replié. Appuyer pour déplier."));
            header.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
            android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofInt(start, end);
            animation[0] = animator;
            animator.setDuration(220);
            animator.setInterpolator(new android.view.animation.DecelerateInterpolator());
            animator.addUpdateListener(value -> {
                ViewGroup.LayoutParams params = section.getLayoutParams();
                params.height = (int) value.getAnimatedValue();
                section.setLayoutParams(params);
            });
            animator.addListener(new android.animation.AnimatorListenerAdapter() {
                private boolean cancelled;
                @Override public void onAnimationCancel(android.animation.Animator value) { cancelled = true; }
                @Override public void onAnimationEnd(android.animation.Animator value) {
                    if (cancelled) return;
                    ViewGroup.LayoutParams params = section.getLayoutParams();
                    params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    section.setLayoutParams(params);
                    if (!expanded[0]) section.setVisibility(View.GONE);
                    animation[0] = null;
                }
            });
            animator.start();
        });
        return section;
    }

    private void addSetting(LinearLayout section, String label, String description, String key, String explanation) {
        LinearLayout row = horizontal();
        row.setPadding(dp(16), dp(12), dp(12), dp(12));
        LinearLayout words = vertical();
        words.addView(text(label, 15, true));
        words.addView(mutedText(description, 13));
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));

        Button info = button("ⓘ", () -> showDialog(new AlertDialog.Builder(this)
                .setTitle(label).setMessage(explanation).setPositiveButton("Compris", null)));
        ui.button(info, AppTheme.ButtonStyle.QUIET);
        info.setPadding(0, 0, 0, 0);
        info.setTextSize(20);
        info.setTextColor(ui.muted);
        info.setContentDescription("En savoir plus : " + label);
        info.setTooltipText("En savoir plus : " + label);
        row.addView(info, new LinearLayout.LayoutParams(dp(48), dp(48)));

        Switch toggle = new Switch(this);
        toggle.setContentDescription(label);
        toggle.setMinWidth(dp(48));
        toggle.setMinHeight(dp(48));
        int[][] states = {new int[]{android.R.attr.state_checked}, new int[]{}};
        toggle.setThumbTintList(new ColorStateList(states, new int[]{ui.accent, ui.surface}));
        toggle.setTrackTintList(new ColorStateList(states, new int[]{ui.accent, ui.muted}));
        toggle.setChecked(getSharedPreferences(PREFERENCES_FILE, MODE_PRIVATE).getBoolean(key, true));
        toggle.setOnCheckedChangeListener((view, checked) ->
                getSharedPreferences(PREFERENCES_FILE, MODE_PRIVATE).edit().putBoolean(key, checked).apply());
        row.addView(toggle, new LinearLayout.LayoutParams(-2, dp(48)));
        section.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private void updateProcedureActions() {
        procedureBack.setText(procedureFinished ? "✓  Retour au menu principal" : "Retour au menu principal");
        int color = Color.parseColor(procedureFinished ? "#18713B" : "#B3261E");
        procedureBack.setBackground(ui.shape(color, Color.TRANSPARENT));
        procedureBack.setTextColor(Color.WHITE);
        boolean showNext = !automaticAdvance && !procedureFinished && requestIndex + 1 < requestQueue.size();
        procedureNext.setVisibility(showNext ? View.VISIBLE : View.GONE);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) procedureBack.getLayoutParams();
        params.setMarginEnd(showNext ? dp(8) : 0);
        procedureBack.setLayoutParams(params);
    }

    private void advanceManually() {
        if (procedureFinished || automaticAdvance || requestIndex + 1 >= requestQueue.size()) return;
        requestIndex++;
        prepareCurrent(requestQueue.get(requestIndex));
    }

    private void startSelectedRequests() {
        if (selectedFriends.isEmpty()) return;
        automaticAdvance = getSharedPreferences(PREFERENCES_FILE, MODE_PRIVATE).getBoolean("automaticAdvance", true);
        procedureFinished = false;
        requestQueue.clear();
        requestQueue.addAll(selectedFriends);
        requestIndex = 0;
        prepareCurrent(requestQueue.get(0));
    }

    private void prepare(Friend friend) {
        automaticAdvance = getSharedPreferences(PREFERENCES_FILE, MODE_PRIVATE).getBoolean("automaticAdvance", true);
        procedureFinished = false;
        requestQueue.clear();
        requestQueue.add(friend);
        requestIndex = 0;
        prepareCurrent(friend);
    }

    private void prepareCurrent(Friend friend) {
        requestSession++;
        submissionObserved = false;
        submissionTransitioned = false;
        captchaWaiting = false;
        submitMarker = "streak-submit-" + UUID.randomUUID();
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(home.getWindowToken(), 0);
        cancelFormScroll();
        formTouched = false;
        SharedPreferences settings = getSharedPreferences(PREFERENCES_FILE, MODE_PRIVATE);
        automaticFill = settings.getBoolean("automaticFill", true);
        automaticCenter = settings.getBoolean("automaticCenter", true);
        if (automaticFill || automaticCenter) lockFormInteractions();
        pendingProfile = active;
        pendingFriend = friend;
        updateProcedureActions();
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
        Uri uri = Uri.parse(browser.getUrl() == null ? "" : browser.getUrl());
        boolean official = "https".equals(uri.getScheme()) && "help.snapchat.com".equals(uri.getHost());
        if (official) {
            int generation = formGeneration;
            browser.evaluateJavascript(SubmissionPage.SCRIPT, result -> {
                if (session != requestSession || pendingFriend == null || submissionTransitioned) return;
                if (generation == formGeneration && "2".equals(result)) {
                    submissionTransitioned = true;
                    if (automaticAdvance && requestIndex + 1 < requestQueue.size()) {
                        requestIndex++;
                        prepareCurrent(requestQueue.get(requestIndex));
                    } else if (requestIndex + 1 >= requestQueue.size()) {
                        procedureFinished = true;
                        setBrowserStatus("Réponse du site détectée pour la dernière demande.");
                        updateProcedureActions();
                    } else {
                        setBrowserStatus("Réponse du site détectée. Passe au formulaire suivant ou retourne au menu principal.");
                    }
                } else {
                    if (generation == formGeneration && "1".equals(result)) {
                        if (!captchaWaiting) {
                            captchaWaiting = true;
                            unlockFormInteractions();
                            setBrowserStatus("Vérification CAPTCHA : termine-la sur la page pour poursuivre.");
                        }
                    } else if (generation == formGeneration && captchaWaiting) {
                        captchaWaiting = false;
                        setBrowserStatus("Vérification terminée ou masquée. Si nécessaire, appuie à nouveau sur Envoyer.");
                    } else if (attempt == 60 && !captchaWaiting) {
                        setBrowserStatus("Réponse du site en attente. Vérifie le formulaire et termine toute vérification demandée.");
                    }
                    browser.postDelayed(() -> monitorSubmissionResult(session, attempt + 1), attempt < 60 ? 500 : 1000);
                }
            });
        } else {
            browser.postDelayed(() -> monitorSubmissionResult(session, attempt + 1), attempt < 60 ? 500 : 1000);
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
                    finishFormPreparation();
                }
            } catch (Exception e) {
                unlockFormInteractions();
                setBrowserStatus("Préremplissage indisponible : remplis le formulaire manuellement.");
            }
        });
    }

    private void finishFormPreparation() {
        if (automaticCenter && formInteractionLocked && !formTouched && isSupportForm(browser.getUrl())) {
            browser.evaluateJavascript(FormNavigation.SCRIPT.replace("__STREAK_CHECK_FIELDS__",
                    automaticFill ? "true" : "false").replace(
                    "__STREAK_SCROLL_DONE__", JSONObject.quote(scrollDoneMarker)), null);
        } else {
            unlockFormInteractions();
        }
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

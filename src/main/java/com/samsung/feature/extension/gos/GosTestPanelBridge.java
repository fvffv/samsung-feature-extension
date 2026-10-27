package com.samsung.feature.extension.gos;

/** Constants shared by the module UI and the code injected into GOS. */
public final class GosTestPanelBridge {
    public static final String MODULE_PACKAGE = "com.samsung.feature.extension";
    public static final String TARGET_PACKAGE = "com.samsung.android.game.gos";
    public static final String TARGET_SERVICE =
            "com.samsung.android.game.gos.endpoint.GosService";
    public static final String TEST_ACTIVITY =
            "com.samsung.android.game.gos.test.fragment.TestActivity";

    public static final String ACTION_OPEN_TEST_PANEL =
            MODULE_PACKAGE + ".OPEN_GOS_TEST_PANEL";
    public static final String ACTION_OPEN_TEST_PANEL_RESULT =
            MODULE_PACKAGE + ".OPEN_GOS_TEST_PANEL_RESULT";
    public static final String EXTRA_REQUEST_ID = "requestId";
    public static final String EXTRA_SUCCESS = "success";
    public static final String EXTRA_MESSAGE = "message";
    public static final String EXTRA_PENDING_INTENT = "pendingIntent";
    public static final String EXTRA_USE_ENGLISH = "useEnglish";

    private GosTestPanelBridge() {
    }
}

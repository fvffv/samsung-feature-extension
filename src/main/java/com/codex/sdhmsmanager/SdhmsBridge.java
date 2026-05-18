package com.codex.sdhmsmanager;

public final class SdhmsBridge {
    public static final String MODULE_PACKAGE = "com.codex.myfileswebdavpopup";
    public static final String TARGET_PACKAGE = "com.sec.android.sdhms";

    public static final String ACTION_REQUEST = MODULE_PACKAGE + ".SDHMS_REQUEST";
    public static final String ACTION_RESPONSE = MODULE_PACKAGE + ".SDHMS_RESPONSE";

    public static final String EXTRA_REQUEST_ID = "requestId";
    public static final String EXTRA_COMMAND = "command";
    public static final String EXTRA_DATA = "data";
    public static final String EXTRA_PACKAGE_NAME = "packageName";
    public static final String EXTRA_ENABLED = "enabled";

    public static final String CMD_REFRESH = "refresh";
    public static final String CMD_SET_THERMAL_MASTER = "setThermalMaster";
    public static final String CMD_SET_BRIGHTNESS_LIMIT_OFF = "setBrightnessLimitOff";
    public static final String CMD_SET_CP_TM_OFF = "setCpTmOff";
    public static final String CMD_SET_FAS_RESTRICTED = "setFasRestricted";

    private SdhmsBridge() {
    }
}

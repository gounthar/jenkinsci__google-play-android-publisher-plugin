package org.jenkinsci.plugins.googleplayandroidpublisher;

import java.text.DecimalFormat;
import java.util.regex.Pattern;

public class Constants {

    /** Special track name which, if used, will upload a file to Internal App Sharing, rather than a release track. */
    public static final String TRACK_NAME_INTERNAL_APP_SHARING = "internal-app-sharing";

    /** Deobfuscation file type: proguard */
    public static final String DEOBFUSCATION_FILE_TYPE_PROGUARD = "proguard";

    /** Deobfuscation file type: native debugging symbols */
    public static final String DEOBFUSCATION_FILE_TYPE_NATIVE_CODE = "nativeCode";

    /** File name pattern which expansion files must match. */
    static final Pattern OBB_FILE_REGEX =
            Pattern.compile("^(main|patch)\\.([0-9]+)\\.([._a-z0-9]+)\\.obb$", Pattern.CASE_INSENSITIVE);

    /** Expansion file type: main */
    static final String OBB_FILE_TYPE_MAIN = "main";

    /** Expansion file type: patch */
    static final String OBB_FILE_TYPE_PATCH = "patch";

    /** Formatter that only displays decimal places when necessary. */
    static final DecimalFormat PERCENTAGE_FORMATTER = new DecimalFormat("#.####");

}

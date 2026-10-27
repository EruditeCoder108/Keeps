package dev.keeps.library.indexing;

import android.os.Environment;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowEnvironment;

/** Explicit scoped-storage policy: API35's real no-arg method otherwise indexes an empty simulated volume list. */
@Implements(Environment.class)
public class ScopedStorageEnvironment extends ShadowEnvironment {
    @Implementation(minSdk = 30)
    protected static boolean isExternalStorageManager() { return false; }
}

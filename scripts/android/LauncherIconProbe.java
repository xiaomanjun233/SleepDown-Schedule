import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/** Runs under adb shell app_process; parses a built APK without installing or starting it. */
public final class LauncherIconProbe {
    public static void main(String[] args) {
        try {
            if (args.length != 1) throw new IllegalArgumentException("Expected an APK path on the device");
            Looper.prepareMainLooper();
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Object thread = activityThread.getMethod("systemMain").invoke(null);
            Context context = (Context) activityThread.getMethod("getSystemContext").invoke(thread);
            PackageManager pm = context.getPackageManager();
            PackageInfo pkg = pm.getPackageArchiveInfo(args[0], PackageManager.GET_ACTIVITIES
                | PackageManager.MATCH_DISABLED_COMPONENTS);
            if (pkg == null) throw new IllegalStateException("Cannot parse APK");
            ApplicationInfo app = pkg.applicationInfo;
            app.sourceDir = args[0];
            app.publicSourceDir = args[0];
            Resources resources = pm.getResourcesForApplication(app);
            Map<String, String[]> icons = new HashMap<>();
            for (ActivityInfo activity : pkg.activities) {
                String suffix = activity.name.substring(activity.name.lastIndexOf('.') + 1);
                if (!suffix.startsWith("Launcher")) continue;
                String day = imageHash(resources, activity.icon, false);
                String night = imageHash(resources, activity.icon, true);
                icons.put(suffix, new String[] { day, night });
                System.out.println(suffix + " parsedIcon=" + resources.getResourceName(activity.icon)
                    + " day=" + day + " night=" + night);
            }
            checkStyle(icons, "Launcher");
            checkStyle(icons, "LauncherKanban");
            require(imageHash(resources, app.icon, false).equals(icons.get("LauncherKanbanLight")[0]),
                "Application light icon differs from its fixed artwork");
            require(imageHash(resources, app.icon, true).equals(icons.get("LauncherKanbanDark")[1]),
                "Application icon cannot follow dark mode");
            System.out.println("PASS: both styles preserve fixed light/dark and follow mode; application icon follows too");
            System.exit(0);
        } catch (Exception error) {
            error.printStackTrace(System.out);
            System.exit(1);
        }
    }

    private static void checkStyle(Map<String, String[]> icons, String prefix) {
        String[] light = icons.get(prefix + "Light");
        String[] dark = icons.get(prefix + "Dark");
        String[] follow = icons.get(prefix + "Follow");
        require(light != null && dark != null && follow != null, "Missing launcher variants for " + prefix);
        require(light[0].equals(light[1]), prefix + " fixed light icon changes with the theme");
        require(dark[0].equals(dark[1]), prefix + " fixed dark icon changes with the theme");
        require(!light[0].equals(dark[0]), prefix + " light and dark artwork are identical");
        require(follow[0].equals(light[0]), prefix + " follow mode does not use the light artwork");
        require(follow[1].equals(dark[1]), prefix + " follow mode does not use the dark artwork");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }

    private static String imageHash(Resources base, int id, boolean night) throws Exception {
        Configuration config = new Configuration(base.getConfiguration());
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
            | (night ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        Resources resources = new Resources(base.getAssets(), base.getDisplayMetrics(), config);
        Drawable drawable = resources.getDrawable(id, null);
        Bitmap bitmap = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888);
        drawable.setBounds(0, 0, 192, 192);
        drawable.draw(new Canvas(bitmap));
        ByteBuffer pixels = ByteBuffer.allocate(bitmap.getByteCount());
        bitmap.copyPixelsToBuffer(pixels);
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(pixels.array());
        bitmap.recycle();
        StringBuilder result = new StringBuilder();
        for (byte value : hash) result.append(String.format("%02x", value));
        return result.toString();
    }
}

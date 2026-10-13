package uz.unnarsx.cherrygram.aiqiyi;

import java.io.File;
import java.io.FileWriter;

/** Writes debug lines to /sdcard/Download/aqydbg.txt so we can read them without logcat. */
public class AiqiyiDebug {
    private static final String PATH = "/storage/emulated/0/Download/aqydbg.txt";

    public static void log(String msg) {
        try {
            File f = new File(PATH);
            FileWriter w = new FileWriter(f, true);
            w.write(System.currentTimeMillis() + " " + msg + "\n");
            w.flush();
            w.close();
        } catch (Throwable ignored) {
        }
    }
}

/**
 * One-shot extractor for FLOW 8 (input_type, preset) → Mixing Station icon ids.
 *
 * Run on a device with Flow Mix installed:
 *   adb push Flow8IconTableExtractor.dex /data/local/tmp/
 *   adb shell CLASSPATH=/data/local/tmp/Flow8IconTableExtractor.dex:/data/app/.../base.apk \
 *     app_process / Flow8IconTableExtractor
 */
public final class Flow8IconTableExtractor {
    private Flow8IconTableExtractor() {}

    public static void main(String[] args) throws Exception {
        Class<?> clientClass = Class.forName("com.musicgroup.xairbt.NativeModels.XAIRClient");
        Object client = clientClass.getDeclaredConstructor().newInstance();
        java.lang.reflect.Method getCount =
                clientClass.getMethod("getNumberOfInputChannelPresets", int.class);
        java.lang.reflect.Method getIcon =
                clientClass.getMethod("getInputChannelPresetIconIdAtIndex", int.class, int.class);

        java.lang.reflect.Method getLabel =
                clientClass.getMethod("getInputChannelPresetLabelBytesAtIndex", int.class, int.class);

        System.out.println("# (input_type, preset) -> ms_icon_id, label");
        for (int type = 0; type <= 6; type++) {
            int count;
            try {
                count = (int) getCount.invoke(client, type);
            } catch (Exception e) {
                continue;
            }
            if (count <= 0) {
                continue;
            }
            System.out.println("# type " + type + " count " + count);
            for (int preset = 0; preset < count; preset++) {
                int icon = (int) getIcon.invoke(client, type, preset);
                String label = new String((byte[]) getLabel.invoke(client, type, preset), "UTF-8");
                System.out.println(type + "," + preset + "," + icon + "," + label);
            }
        }
    }
}

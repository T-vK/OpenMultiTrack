/** Dump JNI preset name + description for every FLOW input icon slot. */
public final class Flow8ApkLabelExtractor {
    private Flow8ApkLabelExtractor() {}

    public static void main(String[] args) throws Exception {
        Class<?> clientClass = Class.forName("com.musicgroup.xairbt.NativeModels.XAIRClient");
        Object client = clientClass.getDeclaredConstructor().newInstance();
        java.lang.reflect.Method getCount =
                clientClass.getMethod("getNumberOfInputChannelPresets", int.class);
        java.lang.reflect.Method getName =
                clientClass.getMethod("getInputChannelPresetNameAtIndex", int.class, int.class);
        java.lang.reflect.Method getDescription =
                clientClass.getMethod("getInputChannelPresetDescriptionAtIndex", int.class, int.class);

        System.out.println("# input_type\tpreset\tdrawable\tname\tdescription");
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
                String name = (String) getName.invoke(client, type, preset);
                String description = (String) getDescription.invoke(client, type, preset);
                if (name == null) {
                    name = "";
                }
                if (description == null) {
                    description = "";
                }
                String drawable = String.format("input_icon_%03d", type * 100 + preset);
                System.out.println(type + "\t" + preset + "\t" + drawable + "\t" + name + "\t" + description);
            }
        }
    }
}

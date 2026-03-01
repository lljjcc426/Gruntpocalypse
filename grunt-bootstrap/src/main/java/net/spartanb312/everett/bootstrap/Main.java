package net.spartanb312.everett.bootstrap;

@Module(name = "Launch Wrapper", version = Main.LAUNCH_WRAPPER_VERSION, description = "Bootstrap launch wrapper", author = "B_312")
public class Main {

    public static final String LAUNCH_WRAPPER_VERSION = "2.1.0b";
    public static String[] args;
    private static final String defaultEntry = "net.spartanb312.everett.bootstrap.DefaultEntry";

    public static void main(String[] args) throws Exception {
        Main.args = args;
        ExternalClassLoader.invokeKotlinObjectField(Class.forName(defaultEntry));
    }

}

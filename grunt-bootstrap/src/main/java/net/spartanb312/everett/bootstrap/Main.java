package net.spartanb312.everett.bootstrap;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class Main {

    private static final String clientEntry = "net.spartanb312.grunteon.obfuscator.ClientEntry";
    private static final String serverEntry = "net.spartanb312.grunteon.obfuscator.ServerEntry";
    public static String[] args;

    public static void main(String[] args) throws Exception {
        Main.args = args;
        var serverMode = Arrays.asList(args).contains("-server");
        var entry = serverMode ? serverEntry : clientEntry;
        if (serverMode) System.out.println("Running on server mode");
        ExternalClassLoader loader = new ExternalClassLoader("grunteon-bootstrap", Main.class.getClassLoader());
        scanExtensions(loader, "modules", "module");
        scanExtensions(loader, "plugins", "plugin");
        ExternalClassLoader.invokeKotlinObjectField(loader.loadClass(entry));
    }

    private static void scanExtensions(ExternalClassLoader loader, String directoryName, String kind) throws Exception {
        File directory = new File(directoryName);
        if (!directory.isDirectory()) return;
        File[] jars = directory.listFiles(file -> file.isFile() && file.getName().toLowerCase().endsWith(".jar"));
        if (jars == null || jars.length == 0) return;
        Arrays.sort(jars, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        System.out.println("Scanning " + kind + "s from " + directory.getAbsolutePath());
        for (File jar : jars) {
            loader.loadJar(jar);
            String entryPoint = readEntryPoint(jar);
            if (entryPoint == null || entryPoint.isBlank()) {
                System.out.println(" - Loaded " + kind + " jar without entry point: " + jar.getName());
                continue;
            }
            System.out.println(" - Initializing " + kind + ": " + entryPoint);
            ExternalClassLoader.invokeKotlinObjectField(loader.loadClass(entryPoint));
        }
    }

    private static String readEntryPoint(File jar) throws IOException {
        try (JarFile jarFile = new JarFile(jar)) {
            Manifest manifest = jarFile.getManifest();
            if (manifest == null) return null;
            String value = manifest.getMainAttributes().getValue("Entry-Point");
            if (value == null || value.isBlank()) {
                value = manifest.getMainAttributes().getValue("EntryPoint");
            }
            return value;
        }
    }

}

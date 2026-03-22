package ca.admin.delivermore.version;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class AdminVersionInfo {

    private static final String VERSION = buildVersion();

    private AdminVersionInfo() {
    }

    public static String getVersion() {
        return VERSION;
    }

    private static String buildVersion() {
        String major = readProperty("admin-version.properties", "version.major", "0");
        String minor = readProperty("admin-version.properties", "version.minor", "0");
        String micro = readProperty("admin-git.properties", "git.total.commit.count", "0");

        return major + "." + minor + "." + normalizeMicro(micro);
    }

    private static String normalizeMicro(String micro) {
        return micro != null && micro.matches("\\d+") ? micro : "0";
    }

    private static String readProperty(String resourceName, String key, String defaultValue) {
        Properties properties = new Properties();
        try (InputStream in = AdminVersionInfo.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (in == null) {
                return defaultValue;
            }
            properties.load(in);
            return properties.getProperty(key, defaultValue);
        } catch (IOException ex) {
            return defaultValue;
        }
    }
}

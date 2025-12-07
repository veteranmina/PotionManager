package me.veritasluxmea.PotionsManager.Utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class UpdateChecker
{
    private static String projectId = "";
    private URL checkURL;
    private static String newVersion = "";
    private JavaPlugin plugin;

    /**
     * Create an UpdateChecker for Modrinth
     * @param plugin Your JavaPlugin instance
     * @param modrinthProjectId The Modrinth project ID (slug or ID from project URL)
     *                          Example: "potionmanager" or "ABC123XY"
     */
    public UpdateChecker(JavaPlugin plugin, String modrinthProjectId) {
        this.plugin = plugin;
        newVersion = plugin.getDescription().getVersion();
        projectId = modrinthProjectId;
        try {
            // Modrinth API endpoint for project versions
            this.checkURL = new URL("https://api.modrinth.com/v2/project/" + modrinthProjectId + "/version");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to create update checker URL: " + e.getMessage());
        }
    }

    public String getProjectId() {
        return projectId;
    }

    public JavaPlugin getPlugin() {
        return this.plugin;
    }

    public static String getLatestVersion() {
        return newVersion;
    }

    public static String getResourceURL() {
        return "https://modrinth.com/plugin/" + projectId;
    }

    /**
     * Check if there's a newer version available on Modrinth
     * @return true if an update is available, false otherwise
     * @throws Exception if there's an error checking for updates
     */
    public boolean checkForUpdates() throws Exception {
        HttpURLConnection con = (HttpURLConnection) this.checkURL.openConnection();
        con.setRequestMethod("GET");
        con.setRequestProperty("User-Agent", "VeritasLuxMea/PotionManager/" + plugin.getDescription().getVersion());
        con.setConnectTimeout(5000);
        con.setReadTimeout(5000);

        int responseCode = con.getResponseCode();
        if (responseCode != 200) {
            throw new Exception("Failed to check for updates. HTTP response code: " + responseCode);
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();

        // Parse JSON response
        JSONParser parser = new JSONParser();
        JSONArray versions = (JSONArray) parser.parse(response.toString());

        if (versions.isEmpty()) {
            return false;
        }

        // Get the latest version (first in the array)
        JSONObject latestVersion = (JSONObject) versions.get(0);
        newVersion = (String) latestVersion.get("version_number");

        // Compare versions
        String currentVersion = this.plugin.getDescription().getVersion();
        return !currentVersion.equals(newVersion) && isNewerVersion(newVersion, currentVersion);
    }

    /**
     * Compare two version strings to determine if the first is newer
     * Handles versions like: 4.0.1, 4.0, 1.2.3-SNAPSHOT
     * @param latestVer The latest version from Modrinth
     * @param currentVer The current plugin version
     * @return true if latestVer is newer than currentVer
     */
    private boolean isNewerVersion(String latestVer, String currentVer) {
        try {
            // Remove any suffixes like -SNAPSHOT, -BETA, etc.
            String latest = latestVer.split("-")[0];
            String current = currentVer.split("-")[0];

            String[] latestParts = latest.split("\\.");
            String[] currentParts = current.split("\\.");

            int maxLength = Math.max(latestParts.length, currentParts.length);

            for (int i = 0; i < maxLength; i++) {
                int latestPart = i < latestParts.length ? Integer.parseInt(latestParts[i]) : 0;
                int currentPart = i < currentParts.length ? Integer.parseInt(currentParts[i]) : 0;

                if (latestPart > currentPart) {
                    return true;
                } else if (latestPart < currentPart) {
                    return false;
                }
            }
            return false; // Versions are equal
        } catch (NumberFormatException e) {
            // If parsing fails, fall back to string comparison
            return !latestVer.equals(currentVer);
        }
    }
}
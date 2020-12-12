import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class Util {

    private static final File directory = new File("persisted-data/");
    private static final String USER_AGENT = "Mozilla/5.0";

    public static JSONObject getAllOwnedGames() throws Exception {
        String url = "https://api.steampowered.com/IPlayerService/GetOwnedGames/v1/" +
                "?key=2321BD719D46F6E288C8ACDDA8C4AF02" +
                "&steamid=" + readUserData("steamID") +
                "&include_appinfo=1&include_played_free_games=1";
        String response = getRequest(url);
        return (JSONObject) new JSONParser().parse(response);
    }

    public static JSONObject getAchievementByAppID(long appID) throws Exception {
        String url = "https://api.steampowered.com/ISteamUserStats/GetPlayerAchievements/v1/" +
                "?key=2321BD719D46F6E288C8ACDDA8C4AF02" +
                "&steamid=" + readUserData("steamID") +
                "&appid=" + appID
                + "&l=" + readUserData("language");
        String response = getRequest(url);
        return (JSONObject) new JSONParser().parse(response);
    }

    public static JSONObject getGlobalAchievementPercentagesForApp(long appID) throws Exception {
        String url = "https://api.steampowered.com/ISteamUserStats/GetGlobalAchievementPercentagesForApp/v2/" +
                "?key=2321BD719D46F6E288C8ACDDA8C4AF02&gameid=" + appID;
        String response = getRequest(url);
        return (JSONObject) new JSONParser().parse(response);
    }

    private static String getRequest(String url) throws Exception {
        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();
        con.setRequestProperty("User-Agent", USER_AGENT);
        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuilder response = new StringBuilder();
        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();
        return response.toString();
    }

    public static void createDefaultUserData() {
        try {
            readJson("userData");
        } catch (Exception e) {
            JSONObject userData = new JSONObject();
            userData.put("language", "english");
            userData.put("lastSelectedGameIndex", 0);
            writeJson(userData, "userData");
        }
    }

    public static void persistUserData(String key, Object value) {
        try {
            JSONObject userData = readJson("userData");
            userData.put(key, value);
            writeJson(userData, "userData");
        } catch (Exception e) {
            System.out.println("Warning: Could not persist user data '" + key + "'.");
        }
    }

    public static Object readUserData(String key) {
        try {
            JSONObject userDataJSON = readJson("userData");
            return userDataJSON.get(key);
        } catch (Exception e) {
            System.out.println("Warning: Could not read '" + key + "' from user data.");
        }
        return null;
    }

    public static JSONObject readJson(String filename) throws Exception {
        File m_file = new File(directory + File.separator + filename);
        FileReader reader = new FileReader(m_file);
        JSONParser jsonParser = new JSONParser();
        return (JSONObject) jsonParser.parse(reader);
    }

    public static void writeJson(JSONObject mJSONObject, String fileName) {
        directory.mkdirs();
        File m_file = new File(directory + File.separator + fileName);
        try (FileWriter fileWriter = new FileWriter(m_file)) {
            fileWriter.write(mJSONObject.toJSONString());
            fileWriter.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}

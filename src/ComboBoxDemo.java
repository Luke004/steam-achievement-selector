import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.awt.*;
import java.awt.event.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import javax.swing.*;

public class ComboBoxDemo extends JPanel implements ActionListener {

    private static final String USER_AGENT = "Mozilla/5.0";
    JLabel picture, loadingText;

    public ComboBoxDemo() {
        super(new BorderLayout());

        try {
            JSONObject ownedGamesJSON = getAllOwnedGames();
            JSONObject responseJSON = (JSONObject) ownedGamesJSON.get("response");
            JSONArray gamesJSON = (JSONArray) responseJSON.get("games");
            Long gameCount = (Long) responseJSON.get("game_count");
            ArrayList<String> listOfGamesWithAchievements = new ArrayList<>();
            for (int i = 0; i < gameCount; ++i) {
                JSONObject game = ((JSONObject) gamesJSON.get(i));
                Boolean has_community_visible_stats = (Boolean) game.get("has_community_visible_stats");
                if (has_community_visible_stats != null && has_community_visible_stats) {
                    JSONObject achievements_root = getAchievementByAppID((Long) game.get("appid"));
                    JSONObject playerStats = (JSONObject) achievements_root.get("playerstats");
                    JSONArray achievements = (JSONArray) playerStats.get("achievements");
                    if (achievements != null) {
                        String gameInfo = (String) game.get("name");
                        int achieved_counter = 0;
                        for (Object achievement : achievements) {
                            Long achieved = (Long)((JSONObject) achievement).get("achieved");
                            if(achieved == 1){
                                achieved_counter++;
                            }
                        }
                        gameInfo += " (" + achieved_counter + "/" + achievements.size() + ")";

                        listOfGamesWithAchievements.add(gameInfo);
                        int debug = 0;
                    }
                }
            }
            JComboBox gameList = new JComboBox(listOfGamesWithAchievements.toArray());
            //petList.setSelectedIndex(0);
            gameList.addActionListener(this);
            //System.out.println(listOfGamesWithAchievements.size());
            add(gameList, BorderLayout.PAGE_START);
            setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        } catch (Exception e) {
            e.printStackTrace();
        }

        /*
        loadingText = new JLabel();
        loadingText.setText("Loading all Steam games that have achievements ...");
        loadingText.setFont(loadingText.getFont().deriveFont(Font.ITALIC));
        loadingText.setHorizontalAlignment(JLabel.CENTER);
        loadingText.setBorder(BorderFactory.createEmptyBorder(10,0,0,0));
        add(loadingText, BorderLayout.CENTER);

         */

        /*
        String[] petStrings = { "Bird", "Cat", "Dog", "Rabbit", "Pig" };

        //Create the combo box, select the item at index 4.
        //Indices start at 0, so 4 specifies the pig.
        JComboBox petList = new JComboBox(gameNames);
        petList.setSelectedIndex(4);
        petList.addActionListener(this);

        //Set up the picture.
        picture = new JLabel();
        picture.setFont(picture.getFont().deriveFont(Font.ITALIC));
        picture.setHorizontalAlignment(JLabel.CENTER);
        updateLabel(petStrings[petList.getSelectedIndex()]);
        picture.setBorder(BorderFactory.createEmptyBorder(10,0,0,0));

        //The preferred size is hard-coded to be the width of the
        //widest image and the height of the tallest image + the border.
        //A real program would compute this.
        picture.setPreferredSize(new Dimension(177, 122+10));

        //Lay out the demo.
        add(petList, BorderLayout.PAGE_START);
        add(picture, BorderLayout.PAGE_END);
        setBorder(BorderFactory.createEmptyBorder(20,20,20,20));
         */
    }

    /**
     * Listens to the combo box.
     */
    public void actionPerformed(ActionEvent e) {
        JComboBox cb = (JComboBox) e.getSource();
        String petName = (String) cb.getSelectedItem();
        updateLabel(petName);
    }

    protected void updateLabel(String name) {
        ImageIcon icon = createImageIcon("images/" + name + ".gif");
        picture.setIcon(icon);
        picture.setToolTipText("A drawing of a " + name.toLowerCase());
        if (icon != null) {
            picture.setText(null);
        } else {
            picture.setText("Image not found");
        }
    }

    /**
     * Returns an ImageIcon, or null if the path was invalid.
     */
    protected static ImageIcon createImageIcon(String path) {
        java.net.URL imgURL = ComboBoxDemo.class.getResource(path);
        if (imgURL != null) {
            return new ImageIcon(imgURL);
        } else {
            System.err.println("Couldn't find file: " + path);
            return null;
        }
    }

    /**
     * Create the GUI and show it.  For thread safety,
     * this method should be invoked from the
     * event-dispatching thread.
     */
    private static void createAndShowGUI() {
        //Create and set up the window.
        JFrame frame = new JFrame("Steam Achievement Selector");
        frame.setPreferredSize(new Dimension(400, 300));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        //Create and set up the content pane.
        JComponent newContentPane = new ComboBoxDemo();
        newContentPane.setOpaque(true); //content panes must be opaque
        frame.setContentPane(newContentPane);

        //Display the window.
        frame.pack();
        frame.setVisible(true);
    }

    private static JSONObject getAllOwnedGames() throws Exception {
        String url = "https://api.steampowered.com/IPlayerService/GetOwnedGames/v1/" +
                "?key=2321BD719D46F6E288C8ACDDA8C4AF02&steamid=76561198009775508&include_appinfo=1&include_played_free_games=1";
        String response = getRequest(url);
        return (JSONObject) new JSONParser().parse(response);
    }

    private static JSONObject getAchievementByAppID(long appID) throws Exception {
        String url = "https://api.steampowered.com/ISteamUserStats/GetPlayerAchievements/v1/" +
                "?key=2321BD719D46F6E288C8ACDDA8C4AF02&steamid=76561198009775508&appid=" + appID;
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

    public static void main(String[] args) {
        //Schedule a job for the event-dispatching thread:
        //creating and showing this application's GUI.
        javax.swing.SwingUtilities.invokeLater(ComboBoxDemo::createAndShowGUI);
    }
}
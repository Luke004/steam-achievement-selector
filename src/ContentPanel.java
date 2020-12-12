import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.FileReader;
import java.text.DecimalFormat;
import java.util.ArrayList;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;

public class ContentPanel extends JPanel implements ActionListener {

    private static final DecimalFormat df2 = new DecimalFormat("#.##");

    JSONArray achievementsList;
    JTable table;

    JLabel picture, loadingText;

    @SuppressWarnings("unchecked")
    public ContentPanel() throws Exception {
        super(new BorderLayout());
        Util.createUserDataJSON();
        try {
            Util.readJson("ownedGames");    // just attempt to read it, if it fails -> catch
            achievementsList = (JSONArray) (Util.readJson("achievementsList")).get("gameList");
            initComboBox();
        } catch (Exception e) {
            // could not load json from persisted files
            loadingText = new JLabel();
            loadingText.setFont(loadingText.getFont().deriveFont(Font.ITALIC));
            loadingText.setHorizontalAlignment(JLabel.CENTER);
            loadingText.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
            add(loadingText, BorderLayout.CENTER);
            // load all owned games
            Thread loadOwnedGames = new Thread(() -> {
                loadingText.setText("Loading all owned games with achievements ...");
                try {
                    Util.writeJson(Util.getAllOwnedGames(), "ownedGames");
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
            loadOwnedGames.start();
            loadOwnedGames.join();
            // when here, we have finished loading of all owned games
            // now load achievement info for each game:
            Thread loadAchievementInfo = new Thread(() -> {
                int loadPercentage;
                try {
                    JSONObject ownedGamesJSON = Util.readJson("ownedGames");
                    JSONObject responseJSON = (JSONObject) ownedGamesJSON.get("response");
                    JSONArray gamesJSON = (JSONArray) responseJSON.get("games");
                    Long gameCount = (Long) responseJSON.get("game_count");
                    JSONArray achievements_root_list = new JSONArray();
                    for (int i = 0; i < gameCount; ++i) {
                        JSONObject game = ((JSONObject) gamesJSON.get(i));
                        Boolean has_community_visible_stats = (Boolean) game.get("has_community_visible_stats");
                        if (has_community_visible_stats != null && has_community_visible_stats) {
                            JSONObject achievements_root = Util.getAchievementByAppID((Long) game.get("appid"));
                            JSONObject playerStats = (JSONObject) achievements_root.get("playerstats");
                            JSONArray achievements = (JSONArray) playerStats.get("achievements");
                            if (achievements != null) {
                                playerStats.put("appid", game.get("appid"));
                                achievements_root_list.add(playerStats);
                            }
                        }
                        loadPercentage = (int) ((float) i / gameCount * 100);
                        loadingText.setText("Loading achievement info for each game (" + loadPercentage + "%)");
                    }
                    // persist the achievement list
                    JSONObject achievementsListJSON = new JSONObject();
                    achievementsListJSON.put("gameList", achievements_root_list);
                    Util.writeJson(achievementsListJSON, "achievementsList");
                    // finally load the combo box using the newly created achievement list
                    achievementsList = (JSONArray) (Util.readJson("achievementsList")).get("gameList");
                    initComboBox();
                    loadingText.setVisible(false);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
            loadAchievementInfo.start();
        }
    }

    @SuppressWarnings("unchecked")
    private void initComboBox() {
        try {
            ArrayList<String> listOfGamesWithAchievements = new ArrayList<>();
            // for better performance: persist an 'achievedCounter' so we don't have to calculate it every run
            // check if achievement list has a achievedCounter
            // do this by checking for it in the first game in the achievement list
            JSONObject firstGamePlayerStats = (JSONObject) achievementsList.get(0);
            if (firstGamePlayerStats.get("achievedCounter") == null) {
                // has no achievement counter persisted in json
                // -> count all achieved achievements and persist the counter
                for (Object game : achievementsList) {
                    JSONObject gameJSON = (JSONObject) game;
                    JSONArray achievements = (JSONArray) gameJSON.get("achievements");
                    Long achieved_counter = 0L;
                    for (Object achievement : achievements) {
                        Long achieved = (Long) ((JSONObject) achievement).get("achieved");
                        if (achieved == 1) {
                            achieved_counter++;
                        }
                    }
                    gameJSON.put("achievedCounter", achieved_counter);
                }
                // finally persist the new achievement list with the achievedCounter (overrides old list)
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("gameList", achievementsList);
                Util.writeJson(jsonObject, "achievementsList");
            }
            for (Object game : achievementsList) {
                JSONObject gameJSON = (JSONObject) game;
                JSONArray achievements = (JSONArray) gameJSON.get("achievements");
                Long achieved_counter = (Long) gameJSON.get("achievedCounter");
                String gameInfo = (String) gameJSON.get("gameName");
                gameInfo += " (" + achieved_counter + "/" + achievements.size() + ")";
                listOfGamesWithAchievements.add(gameInfo);
            }
            // create and add the comboBox with the game list and set the idx of the last opened game
            JComboBox<?> gameList = new JComboBox<>(listOfGamesWithAchievements.toArray());
            int lastSelectedGameIndex = Util.getLastSelectedGameIndex();
            gameList.setSelectedIndex(lastSelectedGameIndex);
            gameList.addActionListener(this);
            add(gameList, BorderLayout.PAGE_START);
            setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
            createTable();
            fillTable(lastSelectedGameIndex);
        } catch (Exception e) {
            System.out.println("Failed to create game combo box!");
            e.printStackTrace();
        }
    }

    /**
     * Listens to the combo box.
     */
    public void actionPerformed(ActionEvent e) {
        JComboBox<?> cb = (JComboBox<?>) e.getSource();
        String gameName = (String) cb.getSelectedItem();
        Util.setLastSelectedGameIndex(cb.getSelectedIndex());
        fillTable(cb.getSelectedIndex());
        //listAchievements(gameName);
    }

    private void createTable() {
        table = new JTable();
        add(new JScrollPane(table));
    }

    private void fillTable(int gameIdx) {
        DefaultTableModel tableModel = new DefaultTableModel();
        tableModel.addColumn("Name");
        tableModel.addColumn("Global Percentage");

        Long currentGameID = (Long) ((JSONObject)achievementsList.get(gameIdx)).get("appid");
        JSONObject achievementPercentagesJSONObject = null;
        try {
            // try to load the achievementPercentagesJSONObject locally
            achievementPercentagesJSONObject = Util.readJson(currentGameID.toString());
        } catch (Exception ignored) {
            try {
                // local loading of achievementPercentagesJSONObject failed -> send request to steam api
                achievementPercentagesJSONObject = (JSONObject) Util.getGlobalAchievementPercentagesForApp(currentGameID)
                        .get("achievementpercentages");
                // persist the request
                Util.writeJson(achievementPercentagesJSONObject, currentGameID.toString());
            } catch (Exception ignored2) { }
        }
        if(achievementPercentagesJSONObject == null) {
            System.out.println("Error: Could not load global achievement percentages.");
            return;
        }
        JSONArray gameAchievementList = (JSONArray) ((JSONObject)achievementsList.get(gameIdx)).get("achievements");
        try {
            JSONArray achievementPercentagesJSONArray = (JSONArray) achievementPercentagesJSONObject.get("achievements");
            for (Object o : gameAchievementList) {
                JSONObject achievementJSON = (JSONObject) o;

                if((Long)achievementJSON.get("achieved") == 0){     // only show achievements that are not done yet
                    for(Object achievementWithPercentage : achievementPercentagesJSONArray){
                        JSONObject achievementWithPercentageJSON = (JSONObject) achievementWithPercentage;
                        if(achievementJSON.get("apiname").equals(achievementWithPercentageJSON.get("name"))){
                            Object globalPercentage = achievementWithPercentageJSON.get("percent");
                            tableModel.insertRow(0, new Object[] {
                                    achievementJSON.get("name"),
                                    df2.format(globalPercentage)
                            });
                            break;
                        }
                    }



                }







            }
            table.setModel(tableModel);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error: Could not load global achievement percentages.");
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
        frame.setPreferredSize(new Dimension(800, 600));
        frame.setResizable(false);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        try {
            // create and set up the content pane
            JComponent newContentPane = new ContentPanel();
            newContentPane.setOpaque(true); //content panes must be opaque
            frame.setContentPane(newContentPane);
            frame.pack();
            frame.setVisible(true);
        } catch (Exception e) {
            System.out.println("Failed to build content pane!");
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        //Schedule a job for the event-dispatching thread:
        //creating and showing this application's GUI.
        javax.swing.SwingUtilities.invokeLater(ContentPanel::createAndShowGUI);
    }
}
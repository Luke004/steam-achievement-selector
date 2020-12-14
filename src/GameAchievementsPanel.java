import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.awt.*;
import java.awt.event.*;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

public class GameAchievementsPanel extends JPanel implements ActionListener {

    private static final DecimalFormat df2 = new DecimalFormat("#.##");
    private Map<String, String> toolTipTexts;
    JSONArray achievementsList;
    JTable table;
    JLabel loadingText;

    @SuppressWarnings("unchecked")
    public GameAchievementsPanel() throws Exception {
        super(new BorderLayout());
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
                            JSONObject achievementInfo = getGameInfo((Long) game.get("appid"));
                            if (achievementInfo != null) {
                                achievements_root_list.add(achievementInfo);
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
            int lastSelectedGameIndex = ((Long) Objects.requireNonNull(Util.readUserData("lastSelectedGameIndex"))).intValue();
            gameList.setSelectedIndex(lastSelectedGameIndex);
            gameList.addActionListener(this);
            add(gameList, BorderLayout.PAGE_START);
            setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
            createTable();
            fillTable(lastSelectedGameIndex);
            JButton updateBtn = new JButton("Update");
            updateBtn.addActionListener(e -> {
                updateGameInfo(gameList.getSelectedIndex());
            });
            add(updateBtn, BorderLayout.PAGE_END);
        } catch (Exception e) {
            System.out.println("Failed to create game combo box!");
            e.printStackTrace();
        }
    }

    private void updateGameInfo(int gameIdx) {
        Long gameID = (Long) ((JSONObject) achievementsList.get(gameIdx)).get("appid");
        JSONObject gameInfoUpdated = getGameInfo(gameID);
        achievementsList.remove(gameIdx);   // delete old entry
        achievementsList.add(gameIdx, gameInfoUpdated);     // add new entry
        // persist the achievement list
        JSONObject achievementsListJSON = new JSONObject();
        achievementsListJSON.put("gameList", achievementsList);
        Util.writeJson(achievementsListJSON, "achievementsList");
        // update global achievement percentages as well
        JSONObject achievementPercentagesJSONObject;
        try {
            achievementPercentagesJSONObject = (JSONObject) Util.getGlobalAchievementPercentagesForApp(gameID)
                    .get("achievementpercentages");
            // persist the request
            Util.writeJson(achievementPercentagesJSONObject, gameID.toString());
        } catch (Exception ignored) {
        }
        // finally fill the table with the updated data
        fillTable(gameIdx);
    }

    /**
     * Listens to the combo box.
     */
    public void actionPerformed(ActionEvent e) {
        JComboBox<?> cb = (JComboBox<?>) e.getSource();
        //String gameName = (String) cb.getSelectedItem();
        Util.persistUserData("lastSelectedGameIndex", cb.getSelectedIndex());
        fillTable(cb.getSelectedIndex());
    }

    private void createTable() {
        table = new JTable();
        add(new JScrollPane(table));

        table.addMouseMotionListener(new MouseAdapter() {
            public void mouseMoved(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                int col = table.columnAtPoint(e.getPoint());
                if (row > -1 && col > -1) {
                    Object value = table.getValueAt(row, col);
                    if (null != value && !"".equals(value)) {
                        if (col == 0) {     // 'achievement name' col
                            table.setToolTipText(toolTipTexts.get(value.toString()));
                        } else {            // 'global percentage' col
                            table.setToolTipText(value.toString() + "% of all players have completed this achievement.");
                        }
                    } else {
                        table.setToolTipText(null);     // close prompt
                    }
                }
            }
        });

        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                int row = table.rowAtPoint(evt.getPoint());
                int col = table.columnAtPoint(evt.getPoint());
                if (row >= 0 && col >= 0) {
                    int dialogButton = JOptionPane.YES_NO_OPTION;
                    int dialogResult = JOptionPane.showConfirmDialog(table.getParent(),
                            "\"" + table.getValueAt(row, 0) +
                                    "\"\nHave you completed this achievement?",
                            "Mark as completed (Remove)", dialogButton);
                    if (dialogResult == 0) {
                        System.out.println("Yes");
                        ((DefaultTableModel) table.getModel()).removeRow(table.getSelectedRow());
                        // TODO: change JSON data 'achieved'
                    } else {
                        System.out.println("No");
                    }
                }
            }
        });
    }

    private void fillTable(int gameIdx) {
        DefaultTableModel tableModel = new DefaultTableModel();
        tableModel.addColumn("Name");
        tableModel.addColumn("Global Percentage");

        Long currentGameID = (Long) ((JSONObject) achievementsList.get(gameIdx)).get("appid");
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
            } catch (Exception ignored2) {
            }
        }
        if (achievementPercentagesJSONObject == null) {
            System.out.println("Error: Could not load global achievement percentages.");
            return;
        }
        // get the achievement list and display all achievements
        JSONArray gameAchievementList = (JSONArray) ((JSONObject) achievementsList.get(gameIdx)).get("achievements");
        try {
            // save the description for each achievement which will later be displayed in tables tool tip text
            if (toolTipTexts == null) {
                toolTipTexts = new HashMap<>();
            } else {
                toolTipTexts.clear();
            }
            JSONArray achievementPercentagesJSONArray = (JSONArray) achievementPercentagesJSONObject.get("achievements");
            for (Object o : gameAchievementList) {
                JSONObject achievementJSON = (JSONObject) o;
                if ((Long) achievementJSON.get("achieved") == 0) {     // only show achievements that are not done yet
                    for (Object achievementWithPercentage : achievementPercentagesJSONArray) {
                        JSONObject achievementWithPercentageJSON = (JSONObject) achievementWithPercentage;
                        if (achievementJSON.get("apiname").equals(achievementWithPercentageJSON.get("name"))) {
                            Object globalPercentage = achievementWithPercentageJSON.get("percent");
                            String achievementName = (String) achievementJSON.get("name");
                            String achievementDescription = (String) achievementJSON.get("description");
                            tableModel.insertRow(0, new Object[]{
                                    achievementJSON.get("name"),
                                    df2.format(globalPercentage)
                            });
                            toolTipTexts.put(
                                    achievementName,
                                    achievementDescription.isEmpty() ? "No description available." : achievementDescription
                            );
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

    private static JSONObject getGameInfo(long gameID) {
        JSONObject achievements_root;
        try {
            achievements_root = Util.getAchievementByAppID(gameID);
            JSONObject achievementInfo = (JSONObject) achievements_root.get("playerstats");
            JSONArray achievements = (JSONArray) achievementInfo.get("achievements");
            if (achievements != null) {
                achievementInfo.put("appid", gameID);
                achievementInfo = addAchievedCounterToGameJSON(achievementInfo);
                return achievementInfo;
            }
        } catch (Exception e) {
            System.out.println("Error: Could not load achievement info for game '" + gameID + "'");
        }
        return null;
    }

    private static JSONObject addAchievedCounterToGameJSON(JSONObject gameJSON) {
        JSONArray achievements = (JSONArray) gameJSON.get("achievements");
        Long achieved_counter = 0L;
        for (Object achievement : achievements) {
            Long achieved = (Long) ((JSONObject) achievement).get("achieved");
            if (achieved == 1) {
                achieved_counter++;
            }
        }
        gameJSON.put("achievedCounter", achieved_counter);
        return gameJSON;
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

        // first time opened -> prompt the user for his steam id
        if (Util.readUserData("steamID") == null) {
            // no steamID to read from exists yet -> first time opened or data deleted
            String steamID = JOptionPane.showInputDialog("What is your Steam ID?");
            Util.createDefaultUserData();
            Util.persistUserData("steamID", steamID);
        }

        try {
            // create and set up the content pane
            JComponent newContentPane = new GameAchievementsPanel();
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
        javax.swing.SwingUtilities.invokeLater(GameAchievementsPanel::createAndShowGUI);
    }
}
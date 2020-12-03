import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;

import javax.swing.*;

public class ContentPanel extends JPanel implements ActionListener {

    JLabel picture, loadingText;

    public ContentPanel() throws Exception {
        super(new BorderLayout());
        try {
            Util.readJson("ownedGames");    // just attempt to read it, if it fails -> catch
            JSONObject achievementsList = (JSONObject) Util.readJson("achievementsList");
            initComboBox(achievementsList);
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
                loadingText.setText("Loading achievement info for each game ...");
                try {
                    JSONObject ownedGamesJSON = (JSONObject) Util.readJson("ownedGames");
                    JSONObject responseJSON = (JSONObject) ownedGamesJSON.get("response");
                    JSONArray gamesJSON = (JSONArray) responseJSON.get("games");
                    Long gameCount = (Long) responseJSON.get("game_count");
                    JSONObject achievements_root_list = new JSONObject();
                    for (int i = 0; i < gameCount; ++i) {
                        JSONObject game = ((JSONObject) gamesJSON.get(i));
                        Boolean has_community_visible_stats = (Boolean) game.get("has_community_visible_stats");
                        if (has_community_visible_stats != null && has_community_visible_stats) {
                            JSONObject achievements_root = Util.getAchievementByAppID((Long) game.get("appid"));
                            JSONObject playerStats = (JSONObject) achievements_root.get("playerstats");
                            JSONArray achievements = (JSONArray) playerStats.get("achievements");
                            if (achievements != null) {
                                achievements_root_list.put(game.get("appid"), playerStats);
                            }
                        }
                    }
                    // persist the achievement list
                    Util.writeJson(achievements_root_list, "achievementsList");
                    // finally load the combo box using the newly created achievement list
                    JSONObject achievementsList = (JSONObject) Util.readJson("achievementsList");
                    initComboBox(achievementsList);
                    loadingText.setVisible(false);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
            loadAchievementInfo.start();
        }

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

    private void initComboBox(JSONObject achievementsList) {
        try {
            ArrayList<String> listOfGamesWithAchievements = new ArrayList<>();
            // for better performance: persist an 'achievedCounter' so we don't have to calculate it every run
            // check if achievement list has a achievedCounter
            // do this by checking for it in the first game in the achievement list
            Object firstKey = achievementsList.keySet().iterator().next();
            JSONObject firstGamePlayerStats = ((JSONObject) achievementsList.get(firstKey));
            if (firstGamePlayerStats.get("achievedCounter") == null) {
                // has no achievement counter persisted in json
                // -> count all achieved achievements and persist the counter
                for (Object key : achievementsList.keySet()) {
                    JSONObject playerStats = ((JSONObject) achievementsList.get(key));
                    JSONArray achievements = (JSONArray) playerStats.get("achievements");
                    int achieved_counter = 0;
                    for (Object achievement : achievements) {
                        Long achieved = (Long) ((JSONObject) achievement).get("achieved");
                        if (achieved == 1) {
                            achieved_counter++;
                        }
                    }
                    playerStats.put("achievedCounter", achieved_counter);
                    achievementsList.put(key, playerStats);
                }
                // finally persist the new achievement list with the achievedCounter (overrides old list)
                Util.writeJson(achievementsList, "achievementsList");
            }
            for (Object key : achievementsList.keySet()) {
                JSONObject playerStats = ((JSONObject) achievementsList.get(key));
                JSONArray achievements = (JSONArray) playerStats.get("achievements");
                Long achieved_counter = (Long) playerStats.get("achievedCounter");
                String gameInfo = (String) playerStats.get("gameName");
                gameInfo += " (" + achieved_counter + "/" + achievements.size() + ")";
                listOfGamesWithAchievements.add(gameInfo);
            }
            JComboBox gameList = new JComboBox(listOfGamesWithAchievements.toArray());
            //gameList.setSelectedIndex(0);
            gameList.addActionListener(this);
            add(gameList, BorderLayout.PAGE_START);
            setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        } catch (Exception e) {
            System.out.println("Failed to create game combo box!");
            e.printStackTrace();
        }
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
        java.net.URL imgURL = ContentPanel.class.getResource(path);
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
package main;

import main.gui.SettingsPanel;
import main.gui.StartMenuPanel;
import main.gui.WorldSelectPanel;

import javax.swing.*;
import java.awt.*;

public class Gameframe {

    private static final JFrame frame = new JFrame("game");

    private Gamepanel panel;
    private CardLayout cardLayout;
    private JPanel cardPanel;

    private boolean fromGame = false;

    public Gameframe() {
        frame.setSize(Screensize.getScreenx() / 2, Screensize.getScreeny() / 2);
        frame.setLocationRelativeTo(null);
        frame.setFocusable(false);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);
        frame.add(cardPanel);

        showStartMenu();
        frame.setVisible(true);
    }

    private void showStartMenu() {
        StartMenuPanel menuPanel = new StartMenuPanel(this::showWorldSelect, () -> {
            fromGame = false;
            showSettings();
        });
        cardPanel.add(menuPanel, "menu");
        cardLayout.show(cardPanel, "menu");
        frame.revalidate();
    }

    private void showSettings() {
        SettingsPanel settingsPanel = new SettingsPanel(() -> {
            if (fromGame && panel != null) {
                cardLayout.show(cardPanel, "game");
                panel.requestFocus();
            } else {
                fromGame = false;
                cardLayout.show(cardPanel, "menu");
            }
            cardPanel.revalidate();
        });
        cardPanel.add(settingsPanel, "settings");
        cardLayout.show(cardPanel, "settings");
        frame.revalidate();
        settingsPanel.requestFocusInWindow();
    }

    private void showWorldSelect() {
        WorldSelectPanel selectPanel = new WorldSelectPanel(
            (worldName, seed) -> startGame(worldName, seed),
            this::showStartMenu
        );
        cardPanel.add(selectPanel, "worldSelect");
        cardLayout.show(cardPanel, "worldSelect");
        frame.revalidate();
        selectPanel.requestFocusInWindow();
    }

    private void startGame(String worldName, long seed) {
        Gamepanel.seed = seed;
        panel = new Gamepanel(worldName);
        panel.onOpenSettings = () -> {
            fromGame = true;
            showSettings();
        };
        panel.onQuitGame = this::stopGame;
        cardPanel.add(panel, "game");
        cardLayout.show(cardPanel, "game");
        frame.revalidate();
        panel.requestFocus();
    }

    private void stopGame() {
        if (panel != null) {
            // 保存玩家数据
            panel.infiniteMap.savePlayerData(
                panel.player.currentX,
                panel.player.currentY,
                panel.inventoryPanel.getAllSlotData()
            );
            
            // 保存世界数据
            panel.infiniteMap.saveWorld();
            // 等待保存任务完成，最多等待3秒
            panel.infiniteMap.waitForSaveCompletion(3000);
            panel.infiniteMap.shutdown();
            panel.gamedrawthread = null;
            panel.paused = false;
        }
        fromGame = false;
        cardLayout.show(cardPanel, "menu");
        cardPanel.revalidate();
        panel = null;
    }

    public static int getsizeh() {
        return frame.getHeight();
    }

    public static int getsizew() {
        return frame.getWidth();
    }
}
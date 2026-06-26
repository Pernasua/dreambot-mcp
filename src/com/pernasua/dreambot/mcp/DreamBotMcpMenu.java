package com.pernasua.dreambot.mcp;

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;

final class DreamBotMcpMenu {
    private static final String MENU_NAME = "dreambot-mcp-menu";
    private static final int INSTALL_ATTEMPTS = 60;
    private static final long INSTALL_RETRY_MS = 500L;

    private final DreamBotMcpScript script;
    private JMenu menu;
    private JMenuItem statusItem;
    private JMenuItem urlItem;
    private boolean uninstalled;

    private DreamBotMcpMenu(DreamBotMcpScript script) {
        this.script = script;
    }

    static DreamBotMcpMenu attach(DreamBotMcpScript script) {
        DreamBotMcpMenu menu = new DreamBotMcpMenu(script);
        menu.ensureAttached();
        return menu;
    }

    void ensureAttached() {
        if (GraphicsEnvironment.isHeadless() || uninstalled) {
            return;
        }
        installLater(INSTALL_ATTEMPTS);
    }

    void refresh() {
        if (statusItem == null && urlItem == null) {
            return;
        }
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                refreshNow();
            }
        });
    }

    void uninstall() {
        uninstalled = true;
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (menu == null) {
                    return;
                }
                JMenuBar bar = findMenuBar();
                if (bar != null) {
                    bar.remove(menu);
                    bar.revalidate();
                    bar.repaint();
                }
                menu = null;
                statusItem = null;
                urlItem = null;
            }
        });
    }

    private void installLater(final int attemptsLeft) {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (uninstalled || isInstalled() || installNow()) {
                    return;
                }
                if (attemptsLeft <= 0) {
                    script.log("DreamBot MCP menu was not installed because no DreamBot menu bar was found");
                    return;
                }
                Thread retry = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(INSTALL_RETRY_MS);
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        installLater(attemptsLeft - 1);
                    }
                }, "dreambot-mcp-menu-install");
                retry.setDaemon(true);
                retry.start();
            }
        });
    }

    private boolean installNow() {
        JMenuBar bar = findMenuBar();
        if (bar == null) {
            return false;
        }
        removeExisting(bar);

        menu = new JMenu("DreamBot MCP");
        menu.setName(MENU_NAME);
        menu.addMenuListener(new MenuListener() {
            @Override
            public void menuSelected(MenuEvent event) {
                refreshNow();
            }

            @Override
            public void menuDeselected(MenuEvent event) {
            }

            @Override
            public void menuCanceled(MenuEvent event) {
            }
        });

        statusItem = new JMenuItem();
        statusItem.setEnabled(false);
        menu.add(statusItem);

        urlItem = new JMenuItem();
        urlItem.setEnabled(false);
        menu.add(urlItem);

        JMenuItem copyUrlItem = new JMenuItem("Copy MCP URL");
        copyUrlItem.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                copyMcpUrl();
            }
        });
        menu.add(copyUrlItem);

        JMenuItem statusLogItem = new JMenuItem("Log Status");
        statusLogItem.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                script.logRuntimeStatus();
            }
        });
        menu.add(statusLogItem);

        refreshNow();
        bar.add(menu);
        bar.revalidate();
        bar.repaint();
        script.log("DreamBot MCP toolbar installed");
        return true;
    }

    private boolean isInstalled() {
        return menu != null && menu.getParent() != null;
    }

    private void refreshNow() {
        if (statusItem != null) {
            statusItem.setText(script.runtimeMenuStatus());
        }
        if (urlItem != null) {
            urlItem.setText(script.runtimeMenuUrl());
        }
    }

    private void copyMcpUrl() {
        String url = script.runtimeMcpUrl();
        if (url == null || url.isEmpty()) {
            script.log("DreamBot MCP URL is not available yet");
            return;
        }
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(url), null);
            script.log("DreamBot MCP URL copied: " + url);
        } catch (Throwable t) {
            script.log("DreamBot MCP URL: " + url);
            script.log("DreamBot MCP clipboard copy failed: " + t);
        }
    }

    private static JMenuBar findMenuBar() {
        JFrame secondaryFrame = null;
        Frame[] frames = Frame.getFrames();
        for (int i = 0; i < frames.length; i++) {
            if (!(frames[i] instanceof JFrame)) {
                continue;
            }
            JFrame frame = (JFrame) frames[i];
            if (!frame.isDisplayable()) {
                continue;
            }
            JMenuBar bar = frame.getJMenuBar();
            if (bar != null) {
                if (frame.isVisible() && frame.isShowing()) {
                    return bar;
                }
                if (secondaryFrame == null) {
                    secondaryFrame = frame;
                }
            }
        }
        return secondaryFrame == null ? null : secondaryFrame.getJMenuBar();
    }

    private static void removeExisting(JMenuBar bar) {
        for (int i = bar.getMenuCount() - 1; i >= 0; i--) {
            JMenu existing = bar.getMenu(i);
            if (existing != null && (MENU_NAME.equals(existing.getName()) || "DreamBot MCP".equals(existing.getText()))) {
                bar.remove(existing);
            }
        }
    }
}

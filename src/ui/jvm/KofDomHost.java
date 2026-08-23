import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.undo.UndoManager;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KofDomHost {

    private static Color BG = new Color(0x28, 0x2A, 0x36);
    private static Color FG = new Color(0xF8, 0xF8, 0xF2);
    private static Color HEADER_BG = new Color(0x21, 0x22, 0x2C);
    private static Color CARET = new Color(0xF8, 0xF8, 0xF2);
    private static Color ERROR_FG = new Color(0xFF, 0x55, 0x55);
    private static Color TERM_BG = new Color(0x21, 0x22, 0x2C);
    private static Color TERM_FG = new Color(0x50, 0xFA, 0x7B);
    private static Color SELECTION = new Color(0x44, 0x47, 0x5A);
    private static Color CURRENT_LINE = new Color(0x44, 0x47, 0x5A);
    private static Color GUTTER_BG = new Color(0x21, 0x22, 0x2C);
    private static Color GUTTER_FG = new Color(0x62, 0x72, 0xA4);
    private static Color GUTTER_CURRENT = new Color(0xF8, 0xF8, 0xF2);
    private static int currentThemeIndex = 0;
    private static String currentThemeName = "Dracula";

    private JFrame frame;
    private JLabel header;
    private JLabel status;
    private JLabel statusLang;
    private JLabel statusTarget;
    private JLabel statusBranch;
    private JLabel statusErrors;
    private String currentTarget = "jvm";
    private JLabel breadcrumbs;
    private static final String SETTINGS_PATH = System.getProperty("user.home") + "/.config/kof-editor/settings.json";

    private void loadSettings() {
        try {
            java.io.File f = new java.io.File(SETTINGS_PATH);
            if (!f.exists()) return;
            String json = new String(java.nio.file.Files.readAllBytes(f.toPath()));
            String theme = jsonValue(json, "theme");
            String font = jsonValue(json, "fontSize");
            String wrap = jsonValue(json, "wrap");
            if (!theme.isEmpty()) {
                int idx = "escuro".equals(theme) || "0".equals(theme) ? 0 : 1;
                applyTheme(idx);
            }
            if (!font.isEmpty()) {
                try {
                    editorFontSize = Float.parseFloat(font);
                } catch (NumberFormatException ignored) {
                }
            }
            wrapEnabled = wrap.isEmpty() || wrap.equals("true");
        } catch (Exception ignored) {
        }
    }

    private void saveSettings() {
        try {
            java.io.File dir = new java.io.File(SETTINGS_PATH).getParentFile();
            dir.mkdirs();
            String json = "{\"theme\":" + (currentThemeIndex == 0 ? "\"escuro\"" : "\"claro\"")
                    + ",\"fontSize\":" + editorFontSize
                    + ",\"wrap\":" + wrapEnabled + "}";
            java.nio.file.Files.writeString(java.nio.file.Paths.get(SETTINGS_PATH), json);
            term.append("$ configurações salvas em " + SETTINGS_PATH + "\n");
        } catch (Exception ex) {
            term.append("$ erro ao salvar configurações: " + ex.getMessage() + "\n");
        }
    }
    private boolean applying = false;
    private String findTerm = "";
    private float editorFontSize = 14.0f;
    private JTabbedPane bottomPanel;
    private JPanel minimapPanel;
    private JList<String> outlineList;
    private java.util.Timer treeWatcher;
    private JPopupMenu completePopup;
    private JList<String> completeList;
    private static final String RECENT_PATH = System.getProperty("user.home") + "/.config/kof-editor/recent.json";
    private java.util.List<String> recentFiles = new ArrayList<>();
    private final java.util.Deque<String> closedTabs = new java.util.ArrayDeque<>();
    private int findPos = -1;
    private JList<String> diags;
    private Panel body;
    private LeafPanel activeLeaf;
    private Doc activeDoc;
    private JTextArea term;
    private JTextField termInput;
    private JPanel termPanel;
    private boolean termVisible = false;
    private JPanel rootContent;
    private JList<String> explorer;
    private JButton runBtn;
    private JButton clearBtn;
    private JButton openFolder;
    private JTextField explorerPath;
    private JPanel explorerPanel;
    private boolean explorerVisible = false;
    private JPanel westPanel;
    private JPanel activityBar;
    private JPanel sideBar;
    private CardLayout sideCards;
    private JPanel explorerView;
    private JPanel searchView;
    private JPanel gitView;
    private JTree fileTree;
    private JTextField searchInput;
    private JList<String> searchResults;
    private JTextField gitMsg;
    private JList<String> gitList;
    private JButton actExplorer;
    private JButton actSearch;
    private JButton actGit;
    private boolean sideVisible = false;
    private String activeView = "";
    private JTextField searchPath;
    private String currentSearchDir = "";

    private LanguageRegistry registry;

    abstract static class Panel {
        abstract Component comp();
    }

    static class LeafPanel extends Panel {
        final JTabbedPane tabs = new JTabbedPane();
        final List<Doc> docs = new ArrayList<>();
        Component comp() { return tabs; }
    }

    static class SplitPanel extends Panel {
        final int orientation;
        final JSplitPane split;
        Panel a;
        Panel b;
        SplitPanel(int orientation, Panel a, Panel b) {
            this.orientation = orientation;
            this.a = a;
            this.b = b;
            this.split = new JSplitPane(orientation);
            this.split.setLeftComponent(a.comp());
            this.split.setRightComponent(b.comp());
            this.split.setResizeWeight(0.5);
            this.split.setContinuousLayout(true);
            this.split.setDividerSize(4);
        }
        Component comp() { return split; }
    }

    static class Doc {
        final JTextPane pane = new JTextPane();
        Minimap minimap;
        java.util.Set<Integer> errorLines = new java.util.HashSet<>();
        String file = "";
        String lang = "kof";
        boolean dirty = false;
        final UndoManager undo = new UndoManager();
        final LeafPanel owner;
        Doc(LeafPanel owner) {
            this.owner = owner;
        }
        String name() {
            if (!file.isEmpty()) {
                String n = Paths.get(file).getFileName().toString();
                return dirty ? n + " ●" : n;
            }
            return dirty ? "novo.kf ●" : "novo.kf";
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new KofDomHost(args.length > 0 ? args[0] : null));
    }

    KofDomHost(String fileArg) {
        registry = new LanguageRegistry();
        buildUi();
        if (fileArg != null) {
            openPath(fileArg);
        } else {
            newDoc();
        }
    }

    private void buildUi() {
        try {
            UIManager.setLookAndFeel(new com.formdev.flatlaf.FlatDarkLaf());
        } catch (Exception ignored) {
        }
        applyTheme();
        darkUi();
        frame = new JFrame("Kof Editor");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        String icon = System.getProperty("kof.editor.icon", "kof.png");
        java.io.File ic = new java.io.File(icon);
        if (ic.exists()) {
            frame.setIconImage(Toolkit.getDefaultToolkit().getImage(ic.getAbsolutePath()));
        }

        header = new JLabel(" Kof Editor");
        header.setOpaque(true);
        header.setBackground(HEADER_BG);
        header.setForeground(FG);
        header.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        header.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));

        status = new JLabel(" ");
        status.setOpaque(true);
        status.setBackground(HEADER_BG);
        status.setForeground(FG);
        status.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        status.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));

        statusLang = new JLabel(" Kof ");
        statusLang.setOpaque(true);
        statusLang.setBackground(new Color(0x3A, 0x3D, 0x41));
        statusLang.setForeground(FG);
        statusLang.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        statusLang.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        statusLang.setToolTipText("Selecionar idioma");
        statusLang.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                JPopupMenu menu = new JPopupMenu();
                String[] langs = {"kof", "json", "markdown", "plain"};
                for (String l : langs) {
                    JMenuItem mi = new JMenuItem(l);
                    mi.addActionListener(ev -> { if (activeDoc != null) { activeDoc.lang = l; render(); } });
                    menu.add(mi);
                }
                menu.show(statusLang, 0, statusLang.getHeight());
            }
        });

        statusTarget = new JLabel(" jvm ");
        statusTarget.setOpaque(true);
        statusTarget.setBackground(new Color(0x3A, 0x3D, 0x41));
        statusTarget.setForeground(FG);
        statusTarget.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        statusTarget.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        statusTarget.setToolTipText("Target de build");
        statusTarget.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                JPopupMenu menu = new JPopupMenu();
                String[] targets = {"jvm", "native", "web"};
                for (String t : targets) {
                    JMenuItem mi = new JMenuItem(t);
                    mi.addActionListener(ev -> { currentTarget = t; updateStatus(); });
                    menu.add(mi);
                }
                menu.show(statusTarget, 0, statusTarget.getHeight());
            }
        });

        statusBranch = new JLabel("");
        statusBranch.setOpaque(true);
        statusBranch.setBackground(new Color(0x3A, 0x3D, 0x41));
        statusBranch.setForeground(new Color(0x8B, 0xE9, 0xFD));
        statusBranch.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        statusBranch.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));

        statusErrors = new JLabel("");
        statusErrors.setOpaque(true);
        statusErrors.setBackground(new Color(0x3A, 0x3D, 0x41));
        statusErrors.setForeground(ERROR_FG);
        statusErrors.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        statusErrors.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));

        JPanel statusRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 1));
        statusRight.setBackground(HEADER_BG);
        statusRight.add(statusErrors);
        statusRight.add(new JLabel(" Espaços:4 "));
        statusRight.add(new JLabel(" UTF-8 "));
        statusRight.add(statusLang);
        statusRight.add(statusTarget);

        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBackground(HEADER_BG);
        statusBar.add(statusBranch, BorderLayout.WEST);
        statusBar.add(status, BorderLayout.CENTER);
        statusBar.add(statusRight, BorderLayout.EAST);
        statusBar.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));

        diags = new JList<>();
        diags.setBackground(new Color(0x2A, 0x2A, 0x2A));
        diags.setForeground(ERROR_FG);
        diags.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        diags.addListSelectionListener(e -> {
            if (activeDoc == null || diags.getSelectedValue() == null) return;
            String sel = diags.getSelectedValue();
            try {
                int line = Integer.parseInt(sel.substring(sel.indexOf(':') + 1, sel.indexOf(',')).trim()) - 1;
                int offset = activeDoc.pane.getDocument().getDefaultRootElement().getElement(line).getStartOffset();
                activeDoc.pane.setCaretPosition(offset);
                activeDoc.pane.requestFocusInWindow();
            } catch (Exception ignored) {
            }
        });

        term = new JTextArea();
        term.setBackground(TERM_BG);
        term.setForeground(TERM_FG);
        term.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        term.setEditable(false);

        termInput = new JTextField();
        termInput.setBackground(TERM_BG);
        termInput.setForeground(TERM_FG);
        termInput.setCaretColor(TERM_FG);
        termInput.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        termInput.addActionListener(e -> runCommand(termInput.getText()));

        JPanel termBar = new JPanel(new BorderLayout());
        termBar.setBackground(TERM_BG);
        termBar.add(new JLabel(" $ "), BorderLayout.WEST);
        termBar.add(termInput, BorderLayout.CENTER);
        JPanel termTool = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        termTool.setBackground(TERM_BG);
        JButton runBtn = flatButton("Rodar");
        runBtn.addActionListener(e -> runDoc());
        JButton clearBtn = flatButton("Limpar");
        clearBtn.addActionListener(e -> term.setText(""));
        this.runBtn = runBtn;
        this.clearBtn = clearBtn;
        termTool.add(runBtn);
        termTool.add(clearBtn);
        termBar.add(termTool, BorderLayout.EAST);

        termPanel = new JPanel(new BorderLayout());
        termPanel.add(new JScrollPane(term), BorderLayout.CENTER);
        termPanel.add(termBar, BorderLayout.SOUTH);

        explorer = new JList<>();
        explorer.setBackground(new Color(0x25, 0x25, 0x25));
        explorer.setForeground(FG);
        explorer.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        explorer.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && explorer.getSelectedValue() != null) {
                    openPath(joinPath(explorerPath.getText(), explorer.getSelectedValue()));
                }
            }
        });

        explorerPath = new JTextField();
        explorerPath.setBackground(new Color(0x2A, 0x2A, 0x2A));
        explorerPath.setForeground(FG);
        explorerPath.setCaretColor(FG);
        explorerPath.addActionListener(e -> refreshExplorer());
        JButton openFolder = flatButton("Pasta...");
        openFolder.addActionListener(e -> chooseFolder());
        this.openFolder = openFolder;

        JPanel explorerBar = new JPanel(new BorderLayout(4, 0));
        explorerBar.setBackground(HEADER_BG);
        explorerBar.add(explorerPath, BorderLayout.CENTER);
        explorerBar.add(openFolder, BorderLayout.EAST);

        buildSideViews();

        westPanel = new JPanel(new BorderLayout());
        westPanel.setBackground(HEADER_BG);

        activityBar = new JPanel();
        activityBar.setLayout(new BoxLayout(activityBar, BoxLayout.Y_AXIS));
        activityBar.setBackground(HEADER_BG);
        activityBar.setBorder(BorderFactory.createEmptyBorder(4, 3, 4, 3));
        actExplorer = new IconButton("folder", "Explorer (Ctrl+Shift+E)", e -> toggleSide("explorer"));
        actSearch = new IconButton("search", "Buscar (Ctrl+Shift+F)", e -> toggleSide("search"));
        actGit = new IconButton("git", "Git (Ctrl+Shift+G)", e -> toggleSide("git"));
        JButton actOutline = new IconButton("outline", "Contorno", e -> toggleSide("outline"));
        JButton actProblems = new IconButton("problems", "Problemas", e -> toggleDiags());
        activityBar.add(actExplorer);
        activityBar.add(actSearch);
        activityBar.add(actGit);
        activityBar.add(actOutline);
        activityBar.add(actProblems);
        activityBar.add(Box.createVerticalGlue());

        sideCards = new CardLayout();
        sideBar = new JPanel(sideCards);
        sideBar.setBackground(new Color(0x21, 0x22, 0x2C));
        sideBar.setPreferredSize(new Dimension(240, 0));
        outlineList = new JList<>();
        outlineList.setBackground(new Color(0x21, 0x22, 0x2C));
        outlineList.setForeground(FG);
        outlineList.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        outlineList.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && outlineList.getSelectedValue() != null && activeDoc != null) {
                    try {
                        String sel = outlineList.getSelectedValue();
                        int line = Integer.parseInt(sel.substring(sel.lastIndexOf(':') + 1).trim()) - 1;
                        var root = activeDoc.pane.getDocument().getDefaultRootElement();
                        if (line >= 0 && line < root.getElementCount()) {
                            activeDoc.pane.setCaretPosition(root.getElement(line).getStartOffset());
                            activeDoc.pane.requestFocusInWindow();
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        });
        JPanel outlineView = new JPanel(new BorderLayout());
        outlineView.setBackground(HEADER_BG);
        outlineView.add(new JScrollPane(outlineList), BorderLayout.CENTER);
        sideBar.add(explorerView, "explorer");
        sideBar.add(searchView, "search");
        sideBar.add(gitView, "git");
        sideBar.add(outlineView, "outline");
        sideBar.setVisible(false);

        westPanel.add(activityBar, BorderLayout.WEST);
        westPanel.add(sideBar, BorderLayout.CENTER);

        JScrollPane diagScroll = new JScrollPane(diags);
        diagScroll.setPreferredSize(new Dimension(0, 120));

        bottomPanel = new JTabbedPane();
        bottomPanel.setPreferredSize(new Dimension(0, 150));
        bottomPanel.addTab("Problemas", diagScroll);
        bottomPanel.addTab("Terminal", termPanel);
        bottomPanel.setVisible(false);

        breadcrumbs = new JLabel(" ");
        breadcrumbs.setOpaque(true);
        breadcrumbs.setBackground(new Color(0x25, 0x25, 0x2B));
        breadcrumbs.setForeground(new Color(0x8B, 0xE9, 0xFD));
        breadcrumbs.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        breadcrumbs.setBorder(BorderFactory.createEmptyBorder(3, 12, 3, 12));

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(new Color(0x25, 0x25, 0x2B));
        topBar.add(header, BorderLayout.NORTH);
        topBar.add(breadcrumbs, BorderLayout.SOUTH);

        rootContent = new JPanel(new BorderLayout());
        rootContent.add(topBar, BorderLayout.NORTH);

        JMenuBar menuBar = buildMenu();
        menuBar.setBackground(HEADER_BG);
        menuBar.setForeground(FG);
        for (int i = 0; i < menuBar.getMenuCount(); i++) {
            JMenu menu = menuBar.getMenu(i);
            menu.setBackground(HEADER_BG);
            menu.setForeground(FG);
            for (int j = 0; j < menu.getItemCount(); j++) {
                JMenuItem mi = menu.getItem(j);
                if (mi != null) {
                    mi.setBackground(HEADER_BG);
                    mi.setForeground(FG);
                }
            }
        }
        frame.setJMenuBar(menuBar);
        frame.add(rootContent, BorderLayout.CENTER);
        frame.add(westPanel, BorderLayout.WEST);
        frame.add(bottomPanel, BorderLayout.SOUTH);
        frame.add(statusBar, BorderLayout.AFTER_LAST_LINE);
        frame.setSize(1280, 860);
        frame.setLocationRelativeTo(null);
        bindKeys();
        frame.setDropTarget(new java.awt.dnd.DropTarget(frame, new java.awt.dnd.DropTargetAdapter() {
            public void drop(java.awt.dnd.DropTargetDropEvent e) {
                e.acceptDrop(java.awt.dnd.DnDConstants.ACTION_COPY);
                try {
                    @SuppressWarnings("unchecked")
                    var files = (java.util.List<java.io.File>) e.getTransferable()
                            .getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor);
                    for (java.io.File f : files) {
                        openPath(f.getAbsolutePath());
                    }
                    e.dropComplete(true);
                } catch (java.awt.datatransfer.UnsupportedFlavorException | java.io.IOException ex) {
                    e.dropComplete(false);
                }
            }
        }));
        loadSettings();
        loadRecent();
        if (editorFontSize != 14.0f && body != null) setFontRec(body);
        if (!wrapEnabled && body != null) setWrapRec(body);
        refreshUiColors();
        frame.setVisible(true);
        SwingUtilities.invokeLater(() -> {
            toggleSide("explorer");
            startTreeWatcher();
        });
    }

    private static JButton flatButton(String label) {
        JButton b = new JButton(label);
        b.setFocusPainted(false);
        b.setForeground(FG);
        b.setBackground(HEADER_BG);
        b.setBorder(BorderFactory.createLineBorder(GUTTER_FG));
        return b;
    }

    private static void darkUi() {
        UIManager.put("Component.arc", 6);
        UIManager.put("Button.arc", 6);
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.thumbInsets", new java.awt.Insets(2, 2, 2, 2));
        UIManager.put("TabbedPane.tabType", "underline");
        UIManager.put("TabbedPane.tabHeight", 30);
        UIManager.put("TabbedPane.tabInsets", new java.awt.Insets(6, 10, 6, 10));
        UIManager.put("TabbedPane.selectedBackground", new Color(0x37, 0x37, 0x37));
        UIManager.put("TabbedPane.tabSeparatorColor", new Color(0x33, 0x33, 0x36));
        UIManager.put("TabbedPane.showTabSeparators", true);
        UIManager.put("TabbedPane.underlineColor", new Color(0x8B, 0xE9, 0xFD));
        UIManager.put("TabbedPane.underlineHeight", 2);
        UIManager.put("ToolTip.borderColor", new Color(0x4A, 0x4A, 0x4D));
        UIManager.put("Menu.selectionArc", 6);
        UIManager.put("PopupMenu.borderColor", new Color(0x4A, 0x4A, 0x4D));
        UIManager.put("PopupMenu.arc", 8);
        UIManager.put("Panel.background", BG);
        UIManager.put("Panel.foreground", FG);
        UIManager.put("MenuBar.background", HEADER_BG);
        UIManager.put("MenuBar.foreground", FG);
        UIManager.put("Menu.background", HEADER_BG);
        UIManager.put("Menu.foreground", FG);
        UIManager.put("Menu.selectionBackground", new Color(0x3A, 0x3D, 0x41));
        UIManager.put("MenuItem.background", HEADER_BG);
        UIManager.put("MenuItem.foreground", FG);
        UIManager.put("MenuItem.selectionBackground", new Color(0x3A, 0x3D, 0x41));
        UIManager.put("TabbedPane.background", BG);
        UIManager.put("TabbedPane.foreground", FG);
        UIManager.put("TabbedPane.selected", new Color(0x37, 0x37, 0x37));
        UIManager.put("TabbedPane.unselectedBackground", new Color(0x2D, 0x2D, 0x30));
        UIManager.put("TabbedPane.contentAreaColor", BG);
        UIManager.put("TabbedPane.tabAreaBackground", new Color(0x25, 0x25, 0x28));
        UIManager.put("TabbedPane.focus", new Color(0, 0, 0, 0));
        UIManager.put("Button.background", new Color(0x3A, 0x3D, 0x41));
        UIManager.put("Button.foreground", FG);
        UIManager.put("Button.select", new Color(0x4E, 0xC9, 0xB0));
        UIManager.put("Button.focus", new Color(0, 0, 0, 0));
        UIManager.put("ScrollBar.background", new Color(0x2D, 0x2D, 0x30));
        UIManager.put("ScrollBar.thumb", new Color(0x4A, 0x4A, 0x4D));
        UIManager.put("ScrollBar.thumbShadow", new Color(0x4A, 0x4A, 0x4D));
        UIManager.put("ScrollBar.thumbDarkShadow", new Color(0x33, 0x33, 0x36));
        UIManager.put("ScrollBar.track", new Color(0x25, 0x25, 0x28));
        UIManager.put("List.background", new Color(0x25, 0x25, 0x25));
        UIManager.put("List.foreground", FG);
        UIManager.put("List.selectionBackground", new Color(0x0E, 0x63, 0x9C));
        UIManager.put("TextField.background", new Color(0x2A, 0x2A, 0x2A));
        UIManager.put("TextField.foreground", FG);
        UIManager.put("TextField.caretForeground", CARET);
        UIManager.put("TextField.selectionBackground", new Color(0x0E, 0x63, 0x9C));
        UIManager.put("TextArea.background", TERM_BG);
        UIManager.put("TextArea.foreground", TERM_FG);
        UIManager.put("TextArea.caretForeground", TERM_FG);
        UIManager.put("EditorPane.background", BG);
        UIManager.put("EditorPane.foreground", FG);
        UIManager.put("EditorPane.caretForeground", CARET);
        UIManager.put("TextPane.background", BG);
        UIManager.put("TextPane.foreground", FG);
        UIManager.put("TextPane.caretForeground", CARET);
        UIManager.put("TabbedPane.unselectedForeground", new Color(0xA0, 0xA0, 0xA0));
        UIManager.put("TabbedPane.selectedForeground", FG);
        UIManager.put("Label.background", HEADER_BG);
        UIManager.put("Label.foreground", FG);
        UIManager.put("ToolTip.background", new Color(0x2A, 0x2A, 0x2A));
        UIManager.put("ToolTip.foreground", FG);
        UIManager.put("OptionPane.background", new Color(0x2A, 0x2A, 0x2A));
        UIManager.put("OptionPane.messageForeground", FG);
        UIManager.put("OptionPane.buttonBackground", new Color(0x3A, 0x3D, 0x41));
        UIManager.put("OptionPane.buttonForeground", FG);
    }

    private void bindKeys() {
        InputMap im = frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = frame.getRootPane().getActionMap();
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK), "new");
        am.put("new", new AbstractAction() { public void actionPerformed(ActionEvent e) { newDoc(); } });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "saveAs");
        am.put("saveAs", new AbstractAction() { public void actionPerformed(ActionEvent e) { save(true); } });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_SLASH, InputEvent.CTRL_DOWN_MASK), "comment");
        am.put("comment", new AbstractAction() { public void actionPerformed(ActionEvent e) { toggleLineComment(); } });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.CTRL_DOWN_MASK), "selectLine");
        am.put("selectLine", new AbstractAction() { public void actionPerformed(ActionEvent e) { selectLine(); } });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.ALT_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "dupLine");
        am.put("dupLine", new AbstractAction() { public void actionPerformed(ActionEvent e) { duplicateLine(); } });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F12, 0), "defn");
        am.put("defn", new AbstractAction() { public void actionPerformed(ActionEvent e) { goToDefinition(); } });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, InputEvent.CTRL_DOWN_MASK), "complete");
        am.put("complete", new AbstractAction() { public void actionPerformed(ActionEvent e) { showComplete(); } });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F2, InputEvent.CTRL_DOWN_MASK), "rename");
        am.put("rename", new AbstractAction() { public void actionPerformed(ActionEvent e) { renameSymbol(); } });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.ALT_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "format");
        am.put("format", new AbstractAction() { public void actionPerformed(ActionEvent e) { formatDocument(); } });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK), "recent");
        am.put("recent", new AbstractAction() { public void actionPerformed(ActionEvent e) { showRecent(); } });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "gotoSymbol");
        am.put("gotoSymbol", new AbstractAction() { public void actionPerformed(ActionEvent e) { showSymbolPicker(); } });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "reopen");
        am.put("reopen", new AbstractAction() { public void actionPerformed(ActionEvent e) { reopenClosedTab(); } });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_B, InputEvent.CTRL_DOWN_MASK), "toggleSide2");
        am.put("toggleSide2", new AbstractAction() { public void actionPerformed(ActionEvent e) { toggleSidebar(); } });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_J, InputEvent.CTRL_DOWN_MASK), "togglePanel");
        am.put("togglePanel", new AbstractAction() { public void actionPerformed(ActionEvent e) { bottomPanel.setVisible(!bottomPanel.isVisible()); frame.revalidate(); } });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_M, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "problems");
        am.put("problems", new AbstractAction() { public void actionPerformed(ActionEvent e) { toggleDiags(); } });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK), "open");
        am.put("open", new AbstractAction() { public void actionPerformed(ActionEvent e) { chooseAndOpen(); } });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK), "save");
        am.put("save", new AbstractAction() { public void actionPerformed(ActionEvent e) { save(false); } });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK), "close");
        am.put("close", new AbstractAction() { public void actionPerformed(ActionEvent e) { closeTab(); } });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), "undo");
        am.put("undo", new AbstractAction() { public void actionPerformed(ActionEvent e) {
            if (activeDoc != null && activeDoc.undo.canUndo()) activeDoc.undo.undo(); } });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK), "redo");
        am.put("redo", new AbstractAction() { public void actionPerformed(ActionEvent e) {
            if (activeDoc != null && activeDoc.undo.canRedo()) activeDoc.undo.redo(); } });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SLASH, InputEvent.CTRL_DOWN_MASK), "splitV");
        am.put("splitV", new AbstractAction() { public void actionPerformed(ActionEvent e) { split(JSplitPane.HORIZONTAL_SPLIT); } });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SLASH, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "splitH");
        am.put("splitH", new AbstractAction() { public void actionPerformed(ActionEvent e) { split(JSplitPane.VERTICAL_SPLIT); } });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_QUOTE, InputEvent.CTRL_DOWN_MASK), "term");
        am.put("term", new AbstractAction() { public void actionPerformed(ActionEvent e) { toggleTerminal(); } });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_B, InputEvent.CTRL_DOWN_MASK), "explorer");
        am.put("explorer", new AbstractAction() { public void actionPerformed(ActionEvent e) { toggleExplorer(); } });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "palette");
        am.put("palette", new AbstractAction() { public void actionPerformed(ActionEvent e) { showPalette(); } });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_E, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "sideExplorer");
        am.put("sideExplorer", new AbstractAction() { public void actionPerformed(ActionEvent e) { toggleSide("explorer"); } });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "sideSearch");
        am.put("sideSearch", new AbstractAction() { public void actionPerformed(ActionEvent e) { toggleSide("search"); } });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_G, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "sideGit");
        am.put("sideGit", new AbstractAction() { public void actionPerformed(ActionEvent e) { toggleSide("git"); } });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK), "quickOpen");
        am.put("quickOpen", new AbstractAction() { public void actionPerformed(ActionEvent e) { showQuickOpen(); } });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_G, InputEvent.CTRL_DOWN_MASK), "goLine");
        am.put("goLine", new AbstractAction() { public void actionPerformed(ActionEvent e) { goToLine(); } });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK), "findFile");
        am.put("findFile", new AbstractAction() { public void actionPerformed(ActionEvent e) { findInFile(); } });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F3, 0), "findNext");
        am.put("findNext", new AbstractAction() { public void actionPerformed(ActionEvent e) { findNext(); } });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.ALT_DOWN_MASK), "wrap");
        am.put("wrap", new AbstractAction() { public void actionPerformed(ActionEvent e) { toggleWrap(); } });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ADD, InputEvent.CTRL_DOWN_MASK), "zoomIn");
        am.put("zoomIn", new AbstractAction() { public void actionPerformed(ActionEvent e) { zoom(1.0f); } });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, InputEvent.CTRL_DOWN_MASK), "zoomIn2");
        am.put("zoomIn2", new AbstractAction() { public void actionPerformed(ActionEvent e) { zoom(1.0f); } });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, InputEvent.CTRL_DOWN_MASK), "zoomOut");
        am.put("zoomOut", new AbstractAction() { public void actionPerformed(ActionEvent e) { zoom(-1.0f); } });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.ALT_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "moveUp");
        am.put("moveUp", new AbstractAction() { public void actionPerformed(ActionEvent e) { moveLine(-1); } });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.ALT_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "moveDown");
        am.put("moveDown", new AbstractAction() { public void actionPerformed(ActionEvent e) { moveLine(1); } });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK), "selectNext");
        am.put("selectNext", new AbstractAction() { public void actionPerformed(ActionEvent e) { selectNextOccurrence(); } });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_K, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "delLine");
        am.put("delLine", new AbstractAction() { public void actionPerformed(ActionEvent e) { deleteLine(); } });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK), "nextTab");
        am.put("nextTab", new AbstractAction() { public void actionPerformed(ActionEvent e) { nextTab(1); } });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "prevTab");
        am.put("prevTab", new AbstractAction() { public void actionPerformed(ActionEvent e) { nextTab(-1); } });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.ALT_DOWN_MASK), "moveUpNoShift");
        am.put("moveUpNoShift", new AbstractAction() { public void actionPerformed(ActionEvent e) { moveLine(-1); } });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.ALT_DOWN_MASK), "moveDownNoShift");
        am.put("moveDownNoShift", new AbstractAction() { public void actionPerformed(ActionEvent e) { moveLine(1); } });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK), "insLineBelow");
        am.put("insLineBelow", new AbstractAction() { public void actionPerformed(ActionEvent e) { insertLineBelow(); } });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), "runF5");
        am.put("runF5", new AbstractAction() { public void actionPerformed(ActionEvent e) { runDoc(); } });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F5, InputEvent.CTRL_DOWN_MASK), "buildF5");
        am.put("buildF5", new AbstractAction() { public void actionPerformed(ActionEvent e) {
            runCommand("kof build /tmp/kof-editor-run.kf --target jvm --output /tmp/kof-editor-build"); } });
    }

    private JMenuBar buildMenu() {
        JMenuBar bar = new JMenuBar();

        JMenu file = new JMenu("Arquivo");
        file.add(item("Novo (Ctrl+N)", e -> newDoc()));
        file.add(item("Abrir... (Ctrl+O)", e -> chooseAndOpen()));
        file.add(item("Arquivos recentes (Ctrl+R)", e -> showRecent()));
        file.add(item("Salvar (Ctrl+S)", e -> save(false)));
        file.add(item("Salvar como...", e -> save(true)));
        file.addSeparator();
        file.add(item("Fechar aba (Ctrl+W)", e -> closeTab()));
        file.add(item("Fechar todos", e -> closeAllTabs()));
        file.add(item("Salvar tudo", e -> saveAll()));
        file.add(item("Fechar divisão", e -> closeSplit()));
        file.addSeparator();
        file.add(item("Sair", e -> frame.dispose()));
        bar.add(file);

        JMenu edit = new JMenu("Editar");
        edit.add(item("Desfazer (Ctrl+Z)", e -> { if (activeDoc != null && activeDoc.undo.canUndo()) activeDoc.undo.undo(); }));
        edit.add(item("Refazer (Ctrl+Y)", e -> { if (activeDoc != null && activeDoc.undo.canRedo()) activeDoc.undo.redo(); }));
        edit.addSeparator();
        edit.add(item("Selecionar linha (Ctrl+L)", e -> selectLine()));
        edit.add(item("Indentar seleção (Tab)", e -> indentSelection(1)));
        edit.add(item("Recuar seleção (Shift+Tab)", e -> indentSelection(-1)));
        edit.add(item("Comentar linha (Ctrl+/)", e -> toggleLineComment()));
        edit.add(item("Duplicar linha (Shift+Alt+↓)", e -> duplicateLine()));
        edit.addSeparator();
        edit.add(item("Ir para definição (F12)", e -> goToDefinition()));
        edit.add(item("Renomear símbolo (Ctrl+F2)", e -> renameSymbol()));
        edit.add(item("Autocomplete (Ctrl+Space)", e -> showComplete()));
        bar.add(edit);

        JMenu view = new JMenu("Exibir");
        view.add(item("Dividir à direita (Ctrl+\\\\)", e -> split(JSplitPane.HORIZONTAL_SPLIT)));
        view.add(item("Dividir abaixo (Ctrl+Shift+\\\\)", e -> split(JSplitPane.VERTICAL_SPLIT)));
        view.add(item("Fechar divisão", e -> closeSplit()));
        view.addSeparator();
        view.add(item("Alternar sidebar (Ctrl+B)", e -> toggleSidebar()));
        view.add(item("Alternar painel (Ctrl+J)", e -> bottomPanel.setVisible(!bottomPanel.isVisible())));
        view.add(item("Paleta de comandos (Ctrl+Shift+P)", e -> showPalette()));
        view.add(item("Terminal (Ctrl+`)", e -> toggleTerminal()));
        view.add(item("Problemas (Ctrl+Shift+M)", e -> toggleDiags()));
        bar.add(view);

        JMenu run = new JMenu("Executar");
        run.add(item("Rodar arquivo (kof run)", e -> runDoc()));
        run.add(item("Build (target jvm)", e -> runCommand("kof build /tmp/kof-editor-run.kf --target jvm --output /tmp/kof-editor-build")));
        bar.add(run);

        JMenu settings = new JMenu("Configurações");
        settings.add(item("Abrir configurações (settings.json)", e -> openSettings()));
        settings.add(item("Salvar configurações", e -> saveSettings()));
        bar.add(settings);

        JMenu theme = new JMenu("Tema");
        ButtonGroup group = new ButtonGroup();
        JRadioButtonMenuItem dark = new JRadioButtonMenuItem("Escuro (Dracula)", currentThemeIndex == 0);
        dark.addActionListener(e -> { applyTheme(0); refreshUiColors(); });
        JRadioButtonMenuItem light = new JRadioButtonMenuItem("Claro", currentThemeIndex == 1);
        light.addActionListener(e -> { applyTheme(1); refreshUiColors(); });
        group.add(dark);
        group.add(light);
        theme.add(dark);
        theme.add(light);
        theme.addSeparator();
        theme.add(item("Exportar tema (YAML)...", e -> exportTheme()));
        theme.add(item("Importar tema (YAML)...", e -> importTheme()));
        bar.add(theme);

        return bar;
    }

    private JMenuItem item(String label, java.awt.event.ActionListener a) {
        JMenuItem m = new JMenuItem(label);
        m.addActionListener(a);
        return m;
    }

    private Doc newDoc() {
        LeafPanel leaf = ensureLeaf();
        Doc doc = addDocTo(leaf, new Doc(leaf));
        doc.pane.setText("// novo arquivo Kof\n\nmain() {\n    println(\"ola, kof!\")\n}\n");
        return doc;
    }

    private void deleteLine() {
        if (activeDoc == null) return;
        try {
            var doc = activeDoc.pane.getDocument();
            int caret = activeDoc.pane.getCaretPosition();
            var root = doc.getDefaultRootElement();
            int line = root.getElementIndex(caret);
            int s = root.getElement(line).getStartOffset();
            int e = root.getElement(line).getEndOffset();
            boolean last = line == root.getElementCount() - 1;
            if (last) {
                int prevS = root.getElement(line - 1).getStartOffset();
                doc.remove(prevS, e - prevS);
            } else {
                doc.remove(s, e - s);
            }
        } catch (Exception ignored) {
        }
    }

    private LeafPanel ensureLeaf() {
        if (activeLeaf != null) return activeLeaf;
        LeafPanel leaf = new LeafPanel();
        leaf.tabs.setBackground(HEADER_BG);
        body = leaf;
        rootContent.add(leaf.comp(), BorderLayout.CENTER);
        activeLeaf = leaf;
        return leaf;
    }

    private Doc addDocTo(LeafPanel leaf, Doc doc) {
        doc.pane.setBackground(BG);
        doc.pane.setForeground(FG);
        doc.pane.setCaretColor(CARET);
        doc.pane.setSelectionColor(SELECTION);
        doc.pane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        doc.pane.putClientProperty("caretWidth", 2);
        doc.pane.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { onEdit(doc); }
            public void removeUpdate(DocumentEvent e) { onEdit(doc); }
            public void changedUpdate(DocumentEvent e) { onEdit(doc); }
        });
        doc.pane.getDocument().addUndoableEditListener(doc.undo::undoableEditHappened);
        doc.pane.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.isControlDown() && activeDoc == doc) {
                    goToDefinition();
                }
            }
        });
        doc.pane.getCaret().addChangeListener(e -> {
            applying = true;
            try {
                updateStatus();
                paintCurrentLine();
                paintOccurrences();
            } finally {
                applying = false;
            }
        });
        doc.pane.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyTyped(java.awt.event.KeyEvent e) {
                handleTyped(doc, e);
            }
        });
        doc.pane.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (activeDoc == doc && e.getKeyCode() == java.awt.event.KeyEvent.VK_TAB
                        && doc.pane.getSelectionStart() != doc.pane.getSelectionEnd()) {
                    e.consume();
                    indentSelection(e.isShiftDown() ? -1 : 1);
                }
            }
        });

        leaf.docs.add(doc);
        leaf.tabs.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (javax.swing.SwingUtilities.isMiddleMouseButton(e)) {
                    int idx = leaf.tabs.indexAtLocation(e.getX(), e.getY());
                    if (idx >= 0 && idx < leaf.docs.size()) {
                        activate(leaf, leaf.docs.get(idx));
                        closeTab();
                    }
                }
            }
        });
        LineGutter gutter = new LineGutter(doc);
        Minimap minimap = new Minimap(doc.pane);
        doc.minimap = minimap;
        JScrollPane sp = new JScrollPane(doc.pane);
        sp.setRowHeaderView(gutter);
        sp.setCorner(JScrollPane.UPPER_RIGHT_CORNER, new JLabel(" "));
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().addChangeListener(e -> gutter.repaint());
        JPanel editorArea = new JPanel(new BorderLayout());
        editorArea.add(sp, BorderLayout.CENTER);
        editorArea.add(minimap, BorderLayout.EAST);
        leaf.tabs.addTab(doc.name(), editorArea);
        leaf.tabs.setSelectedIndex(leaf.tabs.getTabCount() - 1);
        leaf.tabs.addChangeListener(e -> activate(leaf, leaf.docs.get(Math.max(0, leaf.tabs.getSelectedIndex()))));
        activate(leaf, doc);
        return doc;
    }

    private void activate(LeafPanel leaf, Doc doc) {
        activeLeaf = leaf;
        activeDoc = doc;
        render();
    }

    private void onEdit(Doc doc) {
        if (applying || doc != activeDoc) return;
        doc.dirty = true;
        doc.owner.tabs.setTitleAt(doc.owner.docs.indexOf(doc), doc.name());
        render();
    }

    private void split(int orientation) {
        if (activeLeaf == null) return;
        LeafPanel oldLeaf = activeLeaf;
        Doc oldDoc = activeDoc;
        LeafPanel newLeaf = new LeafPanel();
        newLeaf.tabs.setBackground(HEADER_BG);
        Doc copy = new Doc(newLeaf);
        copy.file = oldDoc.file;
        copy.lang = oldDoc.lang;
        copy.dirty = oldDoc.dirty;
        copy.pane.setText(oldDoc.pane.getText());

        SplitPanel sp = new SplitPanel(orientation, oldLeaf, newLeaf);
        replacePanel(oldLeaf, sp);
        activeLeaf = newLeaf;
        addDocTo(newLeaf, copy);
    }

    private void replacePanel(Panel oldP, Panel newP) {
        if (body == oldP) {
            body = newP;
            rootContent.add(body.comp(), BorderLayout.CENTER);
            rootContent.revalidate();
            rootContent.repaint();
            return;
        }
        replaceIn(rootContent, null, oldP, newP);
        rootContent.revalidate();
        rootContent.repaint();
    }

    private boolean replaceIn(Container c, SplitPanel owner, Panel target, Panel replacement) {
        for (Component child : c.getComponents()) {
            if (owner != null && owner.split != child) continue;
            if (owner != null) {
                if (owner.a == target) { owner.a = replacement; owner.split.setLeftComponent(replacement.comp()); return true; }
                if (owner.b == target) { owner.b = replacement; owner.split.setRightComponent(replacement.comp()); return true; }
            }
            if (child instanceof JSplitPane jsp) {
                SplitPanel sp = findSplitPanel(owner != null ? owner : rootBodyHolder(), jsp);
                if (sp != null && (sp.a == target || sp.b == target)) {
                    if (sp.a == target) { sp.a = replacement; sp.split.setLeftComponent(replacement.comp()); }
                    else { sp.b = replacement; sp.split.setRightComponent(replacement.comp()); }
                    return true;
                }
            }
        }
        return false;
    }

    private SplitPanel rootBodyHolder() { return body instanceof SplitPanel sp ? sp : null; }

    private SplitPanel findSplitPanel(SplitPanel root, JSplitPane jsp) {
        if (root == null || root.split == jsp) return root;
        if (root.a instanceof SplitPanel sa) {
            SplitPanel r = findSplitPanel(sa, jsp);
            if (r != null) return r;
        }
        if (root.b instanceof SplitPanel sb) {
            SplitPanel r = findSplitPanel(sb, jsp);
            if (r != null) return r;
        }
        return null;
    }

    private void closeSplit() {
        if (activeLeaf == null) return;
        closeSplitAt(rootBodyHolder(), activeLeaf);
    }

    private void closeSplitAt(SplitPanel root, LeafPanel target) {
        if (root == null) return;
        Panel other = null;
        if (root.a == target) other = root.b;
        else if (root.b == target) other = root.a;
        if (other != null) {
            replacePanel(root, other);
            if (other instanceof LeafPanel ol) {
                activeLeaf = ol;
                if (!ol.docs.isEmpty()) activate(ol, ol.docs.get(Math.max(0, ol.tabs.getSelectedIndex())));
            } else if (other instanceof SplitPanel os) {
                // focus a leaf dentro
            }
            return;
        }
        if (root.a instanceof SplitPanel sa) closeSplitAt(sa, target);
        if (root.b instanceof SplitPanel sb) closeSplitAt(sb, target);
    }

    private void toggleSidebar() {
        if (sideVisible) {
            sideBar.setVisible(false);
            sideVisible = false;
            activeView = "";
            styleActivity();
        } else {
            toggleSide(activeView.isEmpty() ? "explorer" : activeView);
        }
    }

    private void reopenClosedTab() {
        if (closedTabs.isEmpty()) {
            term.append("$ nenhuma aba fechada\n");
            return;
        }
        String path = closedTabs.pop();
        if (!path.isEmpty()) {
            openPath(path);
        }
    }

    private void closeTab() {
        if (activeDoc == null) return;
        if (activeDoc.dirty && !activeDoc.file.isEmpty()) {
            int r = JOptionPane.showConfirmDialog(frame,
                    "Salvar as alterações em '" + activeDoc.name() + "'?", "Kof Editor",
                    JOptionPane.YES_NO_CANCEL_OPTION);
            if (r == JOptionPane.CANCEL_OPTION) return;
            if (r == JOptionPane.YES_OPTION) save(false);
        }
        LeafPanel leaf = activeDoc.owner;
        int idx = leaf.docs.indexOf(activeDoc);
        closedTabs.push(activeDoc.file);
        leaf.docs.remove(idx);
        leaf.tabs.removeTabAt(idx);
        if (leaf.docs.isEmpty()) {
            closeSplitAt(rootBodyHolder(), leaf);
            if (body == leaf) {
                body = null;
                rootContent.removeAll();
                rootContent.add(header, BorderLayout.NORTH);
                activeLeaf = null;
                activeDoc = null;
                newDoc();
            }
        } else {
            int next = Math.min(idx, leaf.docs.size() - 1);
            leaf.tabs.setSelectedIndex(next);
            activate(leaf, leaf.docs.get(next));
        }
    }

    private void toggleTerminal() {
        termVisible = !termVisible;
        if (termVisible) {
            bottomPanel.setVisible(true);
            bottomPanel.setSelectedComponent(termPanel);
            frame.revalidate();
        }
    }

    private void toggleDiags() {
        if (bottomPanel.isVisible() && bottomPanel.getSelectedComponent() == diags.getParent().getParent()) {
            bottomPanel.setVisible(false);
        } else {
            bottomPanel.setVisible(true);
            bottomPanel.setSelectedIndex(0);
        }
        frame.revalidate();
    }

    private void chooseAndOpen() {
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            openPath(fc.getSelectedFile().getAbsolutePath());
        }
    }

    private void loadRecent() {
        try {
            java.io.File f = new java.io.File(RECENT_PATH);
            if (!f.exists()) return;
            String json = invokeKofObject("readDoc", RECENT_PATH).toString();
            Matcher m = Pattern.compile("\"((?:[^\"\\\\]|\\.)*)\"").matcher(json);
            while (m.find()) recentFiles.add(unescape(m.group(1)));
        } catch (Exception ignored) {
        }
    }

    private void saveRecent() {
        try {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < recentFiles.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(recentFiles.get(i).replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
            }
            sb.append("]");
            invokeKofObject("writeDoc", RECENT_PATH, sb.toString());
        } catch (Exception ignored) {
        }
    }

    private void rememberRecent(String path) {
        recentFiles.remove(path);
        recentFiles.add(0, path);
        while (recentFiles.size() > 10) recentFiles.remove(recentFiles.size() - 1);
        saveRecent();
    }

    private static String toAbsolute(String path) {
        Path p = Paths.get(path);
        if (!p.isAbsolute()) {
            p = Paths.get(System.getProperty("user.dir")).resolve(p).normalize();
        }
        return p.toString();
    }

    private void openPath(String path) {
        try {
            path = toAbsolute(path);
            rememberRecent(path);
            Object raw = invokeKofObject("readDoc", path);
            if (raw == null) {
                term.append("$ não foi possível ler: " + path + "\n");
                JOptionPane.showMessageDialog(frame, "Não foi possível abrir: " + path,
                        "Kof Editor", JOptionPane.ERROR_MESSAGE);
                return;
            }
            String doc = raw.toString();
            Doc d = newDoc();
            d.file = path;
            d.lang = detectLanguage(Paths.get(path).getFileName().toString());
            d.dirty = false;
            d.pane.setText(doc);
            d.undo.discardAllEdits();
            term.append("$ abriu " + Paths.get(path).getFileName() + " (" + d.lang + ")\n");
            render();
        } catch (Exception ex) {
            term.append("$ erro ao abrir " + path + ": " + ex + "\n");
            JOptionPane.showMessageDialog(frame, "Não foi possível abrir: " + ex.getMessage(),
                    "Kof Editor", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void save(boolean as) {
        if (activeDoc == null) return;
        String path = activeDoc.file;
        if (as || path.isEmpty()) {
            JFileChooser fc = new JFileChooser();
            if (fc.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) return;
            path = fc.getSelectedFile().getAbsolutePath();
        }
        try {
            invokeKofString("writeDoc", path, activeDoc.pane.getText());
            activeDoc.file = path;
            activeDoc.lang = detectLanguage(Paths.get(path).getFileName().toString());
            activeDoc.dirty = false;
            activeDoc.owner.tabs.setTitleAt(activeDoc.owner.docs.indexOf(activeDoc), activeDoc.name());
            term.append("$ salvou " + Paths.get(path).getFileName() + "\n");
            render();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "Não foi possível salvar: " + ex.getMessage(),
                    "Kof Editor", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String detectLanguage(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0) return "plain";
        String kind = registry.find(name.substring(dot + 1));
        return kind.isEmpty() ? "plain" : kind;
    }

    private void updateStatus() {
        if (activeDoc == null) return;
        int caret = activeDoc.pane.getCaretPosition();
        int line = 1, col = 1;
        try {
            var root = activeDoc.pane.getDocument().getDefaultRootElement();
            line = root.getElementIndex(caret) + 1;
            col = caret - root.getElement(line - 1).getStartOffset() + 1;
        } catch (Exception ignored) {
        }
        String base = status.getText();
        int at = base.indexOf("—  linha");
        String prefix = at >= 0 ? base.substring(0, at) : base;
        status.setText(prefix.trim() + "  —  linha " + line + ", coluna " + col);
        if (activeDoc != null) statusLang.setText(" " + activeDoc.lang + " ");
        statusTarget.setText(" " + currentTarget + " ");
    }

    private void updateGitBranch() {
        try {
            String out = captureCommand("git branch --show-current 2>/dev/null", gitDir()).trim();
            statusBranch.setText(out.isEmpty() ? "" : "  " + out + "  ");
        } catch (Exception ignored) {
        }
    }

    private void render() {
        if (activeDoc == null || applying) return;
        applying = true;
        try {
            renderInner();
        } finally {
            applying = false;
        }
    }

    private void renderInner() {
        String app = invokeKofString("renderApp", activeDoc.pane.getText(), activeDoc.lang, activeDoc.file);
        String title = jsonString(app, "title");
        String lang = jsonString(app, "lang");
        String file = jsonString(app, "file");
        String statusText = jsonString(app, "status");

        int caret = activeDoc.pane.getCaretPosition();
        int line = 1, col = 1;
        try {
            var root = activeDoc.pane.getDocument().getDefaultRootElement();
            line = root.getElementIndex(caret) + 1;
            col = caret - root.getElement(line - 1).getStartOffset() + 1;
        } catch (Exception ignored) {
        }

        String fileName = file.isEmpty() ? "novo.kf" : Paths.get(file).getFileName().toString();
        header.setText(" " + title + "  —  " + fileName + "  —  " + lang + (activeDoc.dirty ? "  ●" : ""));
        if (file.isEmpty()) {
            breadcrumbs.setText(" novo.kf");
        } else {
            String full = Paths.get(file).getParent() == null ? file
                    : Paths.get(file).getParent().toString() + "  ▸  " + Paths.get(file).getFileName();
            breadcrumbs.setText(" " + full);
        }
        status.setText(" " + statusText + "  —  linha " + line + ", coluna " + col);

        paintEditor();
        paintDiags(app);
        paintOutline();
    }

    private void paintOutline() {
        if (activeDoc == null) return;
        try {
            String json = invokeKofString("outlineKf", activeDoc.pane.getText());
            List<String> items = new ArrayList<>();
            Matcher m = Pattern.compile("\\{\"kind\":\"([^\"]+)\",\"name\":\"((?:[^\"\\\\]|\\\\.)*)\",\"line\":(\\d+)\\}").matcher(json);
            while (m.find()) {
                String kind = m.group(1);
                String name = unescape(m.group(2));
                String line = m.group(3);
                items.add((kind.equals("type") ? "C " : "f ") + name + "  :" + line);
            }
            outlineList.setListData(items.toArray(new String[0]));
        } catch (Exception ignored) {
        }
    }

    private int lastPaintedLine = -1;
    private java.util.List<int[]> lastOccurrenceRanges = new ArrayList<>();

    private void paintEditor() {
        if (activeDoc == null || activeDoc.minimap == null) return;
        String text = activeDoc.pane.getText();
        StyledDocument sd = activeDoc.pane.getStyledDocument();
        try {
            SimpleAttributeSet base = new SimpleAttributeSet();
            StyleConstants.setForeground(base, FG);
            StyleConstants.setBackground(base, BG);
            sd.setCharacterAttributes(0, Math.max(1, text.length()), base, true);
            if (activeDoc.lang.equals("plain") || text.isEmpty()) return;
            String app = invokeKofString("renderApp", text, activeDoc.lang, activeDoc.file);
            Matcher m = Pattern.compile("\\{\"kind\":\"([^\"]+)\",\"text\":\"((?:[^\"\\\\]|\\\\.)*)\",\"start\":(\\d+),\"end\":(\\d+)\\}").matcher(app);
            List<TokenRow> rows = new ArrayList<>();
            int row = 0;
            int lastLine = -1;
            while (m.find()) {
                String kind = m.group(1);
                int start = Integer.parseInt(m.group(3));
                int end = Integer.parseInt(m.group(4));
                SimpleAttributeSet a = new SimpleAttributeSet();
                StyleConstants.setForeground(a, colorFor(kind));
                if (kind.equals("error")) StyleConstants.setUnderline(a, true);
                sd.setCharacterAttributes(start, Math.max(1, end - start), a, true);
                var root = activeDoc.pane.getDocument().getDefaultRootElement();
                int line = root.getElementIndex(start);
                if (line != lastLine) {
                    lastLine = line;
                    rows.add(new TokenRow(kind, start, end));
                }
            }
            activeDoc.minimap.update(rows);
        } catch (RuntimeException ignored) {
        }
        paintCurrentLine();
    }

    private void paintCurrentLine() {
        if (activeDoc == null) return;
        StyledDocument sd = activeDoc.pane.getStyledDocument();
        try {
            int caret = activeDoc.pane.getCaretPosition();
            var root = activeDoc.pane.getDocument().getDefaultRootElement();
            int line = root.getElementIndex(caret);
            int start = root.getElement(line).getStartOffset();
            int end = root.getElement(line).getEndOffset();
            if (lastPaintedLine >= 0 && lastPaintedLine != line && lastPaintedLine < root.getElementCount()) {
                int ps = root.getElement(lastPaintedLine).getStartOffset();
                int pe = root.getElement(lastPaintedLine).getEndOffset();
                SimpleAttributeSet clear = new SimpleAttributeSet();
                StyleConstants.setBackground(clear, BG);
                sd.setCharacterAttributes(ps, Math.max(1, pe - ps), clear, true);
            }
            SimpleAttributeSet hl = new SimpleAttributeSet();
            StyleConstants.setBackground(hl, CURRENT_LINE);
            sd.setCharacterAttributes(start, Math.max(1, end - start), hl, true);
            lastPaintedLine = line;
        } catch (Exception ignored) {
        }
    }

    private void paintOccurrences() {
        if (activeDoc == null || lastOccurrenceRanges == null) return;
        StyledDocument sd = activeDoc.pane.getStyledDocument();
        try {
            for (int[] r : lastOccurrenceRanges) {
                SimpleAttributeSet clear = new SimpleAttributeSet();
                StyleConstants.setBackground(clear, BG);
                sd.setCharacterAttributes(r[0], Math.max(1, r[1] - r[0]), clear, true);
            }
            lastOccurrenceRanges.clear();
            String word = wordAtCaret();
            if (word.length() < 2) return;
            String text = activeDoc.pane.getText();
            int from = 0;
            while (true) {
                int idx = text.indexOf(word, from);
                if (idx < 0) break;
                SimpleAttributeSet hl = new SimpleAttributeSet();
                StyleConstants.setBackground(hl, new Color(0x3A, 0x3D, 0x41));
                sd.setCharacterAttributes(idx, word.length(), hl, true);
                lastOccurrenceRanges.add(new int[]{idx, idx + word.length()});
                from = idx + word.length();
            }
        } catch (Exception ignored) {
        }
    }

    private void paintDiags(String app) {
        activeDoc.errorLines.clear();
        List<String> items = new ArrayList<>();
        Matcher m = Pattern.compile("\\{\"line\":(\\d+),\"col\":(\\d+),\"text\":\"((?:[^\"\\\\]|\\\\.)*)\"\\}")
                .matcher(app);
        while (m.find()) {
            items.add("linha " + m.group(1) + ", coluna " + m.group(2) + "  —  '" + m.group(3) + "'");
            activeDoc.errorLines.add(Integer.parseInt(m.group(1)));
        }
        diags.setListData(items.toArray(new String[0]));
        diags.setForeground(items.isEmpty() ? FG : ERROR_FG);
        statusErrors.setText(items.isEmpty() ? " 0 erros " : " " + items.size() + " erros ");
    }

    private void toggleExplorer() {
        explorerVisible = !explorerVisible;
        if (explorerVisible) {
            if (explorerPath.getText().isEmpty()) {
                String start = activeDoc != null && !activeDoc.file.isEmpty()
                        ? Paths.get(activeDoc.file).getParent().toString() : System.getProperty("user.home");
                explorerPath.setText(start);
            }
            refreshExplorer();
            rootContent.add(explorerPanel, BorderLayout.WEST);
        } else {
            rootContent.remove(explorerPanel);
        }
        rootContent.revalidate();
        rootContent.repaint();
    }

    private void chooseFolder() {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fc.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            String dir = fc.getSelectedFile().getAbsolutePath();
            explorerPath.setText(dir);
            if (body != null) {
                toggleSide("explorer");
                reloadTree(dir);
            }
        }
    }

    private void refreshExplorer() {
        String dir = explorerPath.getText();
        if (dir.isEmpty()) return;
        try {
            String json = invokeKofString("listDir", dir);
            List<String> items = new ArrayList<>();
            Matcher m = Pattern.compile("\"([^\"]+)\"?").matcher(json);
            while (m.find()) items.add(m.group(1));
            explorer.setListData(items.toArray(new String[0]));
        } catch (Exception ex) {
            explorer.setListData(new String[]{"(erro: " + ex.getMessage() + ")"});
        }
    }

    private static String joinPath(String dir, String name) {
        if (dir.endsWith("/")) return dir + name;
        return dir + "/" + name;
    }

    private String themeToYaml() {
        StringBuilder yaml = new StringBuilder();
        yaml.append("name: ").append(currentThemeName).append("\n");
        yaml.append("bg: ").append(toHex(BG)).append("\n");
        yaml.append("fg: ").append(toHex(FG)).append("\n");
        yaml.append("header: ").append(toHex(HEADER_BG)).append("\n");
        yaml.append("selection: ").append(toHex(SELECTION)).append("\n");
        yaml.append("currentLine: ").append(toHex(CURRENT_LINE)).append("\n");
        yaml.append("gutter: ").append(toHex(GUTTER_FG)).append("\n");
        yaml.append("terminal: ").append(toHex(TERM_BG)).append("\n");
        yaml.append("terminalFg: ").append(toHex(TERM_FG)).append("\n");
        String[] keys = {"keyword", "type", "string", "char", "number", "comment",
                "operator", "punctuation", "identifier", "error"};
        for (String k : keys) {
            yaml.append(k).append(": ").append(toHex(KINDS.getOrDefault(k, FG))).append("\n");
        }
        return yaml.toString();
    }

    private static String toHex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static Color fromHex(String hex) {
        try {
            String h = hex.replace("#", "").trim();
            return new Color(Integer.parseInt(h.substring(0, 2), 16),
                    Integer.parseInt(h.substring(2, 4), 16),
                    Integer.parseInt(h.substring(4, 6), 16));
        } catch (Exception e) {
            return null;
        }
    }

    private void exportTheme() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new java.io.File(currentThemeName.replace(" ", "_").toLowerCase() + "-theme.yaml"));
        if (fc.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) return;
        try {
            java.nio.file.Files.writeString(fc.getSelectedFile().toPath(), themeToYaml());
            term.append("$ tema exportado: " + fc.getSelectedFile().getName() + "\n");
        } catch (java.io.IOException ex) {
            JOptionPane.showMessageDialog(frame, "Falha ao exportar: " + ex.getMessage(),
                    "Kof Editor", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void importTheme() {
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return;
        try {
            List<String> lines = java.nio.file.Files.readAllLines(fc.getSelectedFile().toPath());
            Map<String, Color> colors = new java.util.HashMap<>();
            for (String line : lines) {
                int idx = line.indexOf(':');
                if (idx <= 0) continue;
                String key = line.substring(0, idx).trim();
                String value = line.substring(idx + 1).trim();
                Color c = fromHex(value);
                if (c == null) continue;
                colors.put(key, c);
                if (key.equals("name")) {
                    currentThemeName = value;
                }
            }
            if (colors.containsKey("bg")) BG = colors.get("bg");
            if (colors.containsKey("fg")) FG = colors.get("fg");
            if (colors.containsKey("header")) HEADER_BG = colors.get("header");
            if (colors.containsKey("selection")) SELECTION = colors.get("selection");
            if (colors.containsKey("currentLine")) CURRENT_LINE = colors.get("currentLine");
            if (colors.containsKey("gutter")) GUTTER_FG = colors.get("gutter");
            if (colors.containsKey("terminal")) TERM_BG = colors.get("terminal");
            if (colors.containsKey("terminalFg")) TERM_FG = colors.get("terminalFg");
            CARET = FG;
            GUTTER_BG = HEADER_BG;
            GUTTER_CURRENT = FG;
            ERROR_FG = colors.getOrDefault("error", ERROR_FG);
            String[] keys = {"keyword", "type", "string", "char", "number", "comment",
                    "operator", "punctuation", "identifier", "error"};
            for (String k : keys) {
                if (colors.containsKey(k)) KINDS.put(k, colors.get(k));
            }
            term.append("$ tema importado: " + currentThemeName + "\n");
            refreshUiColors();
        } catch (java.io.IOException ex) {
            JOptionPane.showMessageDialog(frame, "Falha ao importar: " + ex.getMessage(),
                    "Kof Editor", JOptionPane.ERROR_MESSAGE);
        }
    }

    private JButton actButton(String tip, String label, java.awt.event.ActionListener a) {
        JButton b = flatButton(label);
        b.setToolTipText(tip);
        b.setAlignmentX(Component.CENTER_ALIGNMENT);
        b.setMaximumSize(new Dimension(44, 34));
        b.addActionListener(a);
        return b;
    }

    private void buildSideViews() {
        // Explorer (árvore de diretórios)
        fileTree = new JTree(new DefaultMutableTreeNode(new FileNode("raiz", "", true)));
        fileTree.setBackground(new Color(0x21, 0x22, 0x2C));
        fileTree.setForeground(FG);
        fileTree.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        fileTree.setCellRenderer(new FileTreeRenderer());
        fileTree.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        fileTree.addTreeWillExpandListener(new javax.swing.event.TreeWillExpandListener() {
            public void treeWillExpand(javax.swing.event.TreeExpansionEvent e) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) e.getPath().getLastPathComponent();
                if (node.getUserObject() instanceof FileNode fn && fn.isDir && node.getChildCount() == 1
                        && ((DefaultMutableTreeNode) node.getFirstChild()).getUserObject() == null) {
                    node.removeAllChildren();
                    loadDirChildren(node, fn);
                    ((DefaultTreeModel) fileTree.getModel()).reload(node);
                }
            }
            public void treeWillCollapse(javax.swing.event.TreeExpansionEvent e) {
            }
        });
        fileTree.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && fileTree.getPathForLocation(e.getX(), e.getY()) != null) {
                    DefaultMutableTreeNode node = (DefaultMutableTreeNode) fileTree.getPathForLocation(e.getX(), e.getY()).getLastPathComponent();
                    if (node.getUserObject() instanceof FileNode fn && !fn.isDir) {
                        openPath(fn.path);
                    }
                }
            }
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (e.isPopupTrigger()) showTreePopup(e);
            }
            public void mouseReleased(java.awt.event.MouseEvent e) {
                if (e.isPopupTrigger()) showTreePopup(e);
            }
        });

        JTextField treePath = new JTextField();
        treePath.setBackground(new Color(0x2A, 0x2A, 0x2A));
        treePath.setForeground(FG);
        treePath.addActionListener(e -> reloadTree(treePath.getText()));
        JButton treeRefresh = flatButton("Atualizar");
        treeRefresh.addActionListener(e -> {
            if (fileTree.getModel().getRoot() instanceof DefaultMutableTreeNode r
                    && r.getUserObject() instanceof FileNode fn) {
                reloadTree(fn.path);
            } else {
                reloadTree(treePath.getText());
            }
        });
        JPanel treeBar = new JPanel(new BorderLayout(4, 0));
        treeBar.setBackground(HEADER_BG);
        treeBar.add(treePath, BorderLayout.CENTER);
        treeBar.add(treeRefresh, BorderLayout.EAST);

        explorerView = new JPanel(new BorderLayout());
        explorerView.setBackground(HEADER_BG);
        explorerView.add(treeBar, BorderLayout.NORTH);
        explorerView.add(new JScrollPane(fileTree), BorderLayout.CENTER);

        // Buscar
        searchInput = new JTextField();
        searchInput.setBackground(new Color(0x2A, 0x2A, 0x2A));
        searchInput.setForeground(FG);
        searchInput.setCaretColor(FG);
        searchInput.addActionListener(e -> runSearch());
        searchResults = new JList<>();
        searchResults.setBackground(new Color(0x21, 0x22, 0x2C));
        searchResults.setForeground(FG);
        searchResults.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        searchResults.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && searchResults.getSelectedValue() != null) {
                    String sel = searchResults.getSelectedValue();
                    try {
                        int pipe = sel.lastIndexOf('|');
                        String file = sel.substring(0, sel.indexOf(':', sel.indexOf(':') + 1)).trim();
                        openPath(joinPath(currentSearchDir, file));
                        int line = Integer.parseInt(sel.substring(sel.indexOf(':') + 1, sel.indexOf(',')).trim());
                        int offset = activeDoc.pane.getDocument().getDefaultRootElement().getElement(line - 1).getStartOffset();
                        activeDoc.pane.setCaretPosition(offset);
                        activeDoc.pane.requestFocusInWindow();
                    } catch (Exception ignored) {
                    }
                }
            }
        });
        JTextField searchPath = new JTextField();
        searchPath.setBackground(new Color(0x2A, 0x2A, 0x2A));
        searchPath.setForeground(FG);
        searchView = new JPanel(new BorderLayout());
        searchView.setBackground(HEADER_BG);
        JPanel searchBar = new JPanel(new BorderLayout(4, 0));
        searchBar.setBackground(HEADER_BG);
        searchBar.add(searchInput, BorderLayout.CENTER);
        JPanel searchBar2 = new JPanel(new BorderLayout(4, 0));
        searchBar2.setBackground(HEADER_BG);
        searchBar2.add(new JLabel(" pasta: "), BorderLayout.WEST);
        searchBar2.add(searchPath, BorderLayout.CENTER);
        JPanel searchTop = new JPanel();
        searchTop.setLayout(new BoxLayout(searchTop, BoxLayout.Y_AXIS));
        searchTop.setBackground(HEADER_BG);
        searchTop.add(searchBar);
        searchTop.add(searchBar2);
        searchView.add(searchTop, BorderLayout.NORTH);
        searchView.add(new JScrollPane(searchResults), BorderLayout.CENTER);
        searchPath.addActionListener(e -> runSearch());
        this.searchPath = searchPath;

        // Git
        gitList = new JList<>();
        gitList.setBackground(new Color(0x21, 0x22, 0x2C));
        gitList.setForeground(FG);
        gitList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        gitList.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && gitList.getSelectedValue() != null) {
                    String sel = gitList.getSelectedValue();
                    String file = sel.length() > 3 ? sel.substring(3).trim() : "";
                    runCommand("git diff -- " + file);
                }
            }
        });
        gitMsg = new JTextField();
        gitMsg.setBackground(new Color(0x2A, 0x2A, 0x2A));
        gitMsg.setForeground(FG);
        gitMsg.setCaretColor(FG);
        JButton gitRefresh = flatButton("Atualizar");
        gitRefresh.addActionListener(e -> refreshGit());
        JButton gitCommit = flatButton("Commit");
        gitCommit.addActionListener(e -> gitCommit());
        JPanel gitBar = new JPanel(new BorderLayout(4, 0));
        gitBar.setBackground(HEADER_BG);
        gitBar.add(gitMsg, BorderLayout.CENTER);
        gitBar.add(gitCommit, BorderLayout.EAST);
        JPanel gitTool = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        gitTool.setBackground(HEADER_BG);
        gitTool.add(gitRefresh);
        gitView = new JPanel(new BorderLayout());
        gitView.setBackground(HEADER_BG);
        gitView.add(gitBar, BorderLayout.NORTH);
        gitView.add(new JScrollPane(gitList), BorderLayout.CENTER);
        gitView.add(gitTool, BorderLayout.SOUTH);
    }

    private static class FileNode {
        final String name;
        final String path;
        final boolean isDir;
        FileNode(String name, String path, boolean isDir) {
            this.name = name;
            this.path = path;
            this.isDir = isDir;
        }
        public String toString() { return name; }
    }

    private static class FileTreeRenderer extends DefaultTreeCellRenderer {
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded,
                boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            if (value instanceof DefaultMutableTreeNode node && node.getUserObject() instanceof FileNode fn) {
                setText(fn.name);
                setForeground(fn.isDir ? new Color(0x8B, 0xE9, 0xFD) : FG);
                if (fn.isDir) {
                    setFont(getFont().deriveFont(Font.BOLD));
                }
            }
            setBackgroundNonSelectionColor(new Color(0x21, 0x22, 0x2C));
            setBackgroundSelectionColor(new Color(0x44, 0x47, 0x5A));
            return this;
        }
    }

    private void showTreePopup(java.awt.event.MouseEvent e) {
        if (fileTree.getPathForLocation(e.getX(), e.getY()) == null) return;
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) fileTree.getPathForLocation(e.getX(), e.getY()).getLastPathComponent();
        if (!(node.getUserObject() instanceof FileNode fn)) return;
        JPopupMenu menu = new JPopupMenu();
        String dir = fn.isDir ? fn.path : Paths.get(fn.path).getParent().toString();
        String base = fn.isDir ? fn.name : fn.name.replaceFirst("\\.[^.]+$", "");
        JMenuItem novoArq = new JMenuItem("Novo arquivo");
        novoArq.addActionListener(ev -> {
            String name = JOptionPane.showInputDialog(frame, "Nome do arquivo:", "novo.kf");
            if (name != null) {
                Object ok = invokeKofObject("createFile", joinPath(dir, name));
                refreshCurrentTree();
            }
        });
        JMenuItem novaPasta = new JMenuItem("Nova pasta");
        novaPasta.addActionListener(ev -> {
            String name = JOptionPane.showInputDialog(frame, "Nome da pasta:", "nova");
            if (name != null) {
                invokeKofObject("createDir", joinPath(dir, name));
                refreshCurrentTree();
            }
        });
        JMenuItem renomear = new JMenuItem("Renomear");
        renomear.addActionListener(ev -> {
            String name = JOptionPane.showInputDialog(frame, "Novo nome:", fn.name);
            if (name != null && !name.equals(fn.name)) {
                String parent = Paths.get(fn.path).getParent().toString();
                try {
                    java.nio.file.Files.move(Paths.get(fn.path), Paths.get(joinPath(parent, name)));
                    refreshCurrentTree();
                } catch (java.io.IOException ex) {
                    JOptionPane.showMessageDialog(frame, ex.getMessage(), "Kof Editor", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        JMenuItem deletar = new JMenuItem("Deletar");
        deletar.addActionListener(ev -> {
            if (JOptionPane.showConfirmDialog(frame, "Deletar '" + fn.name + "'?", "Kof Editor",
                    JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                Object ok = invokeKofObject("deleteEntry", fn.path);
                refreshCurrentTree();
            }
        });
        menu.add(novoArq);
        menu.add(novaPasta);
        menu.addSeparator();
        if (fn.isDir) {
            JMenuItem abrirAqui = new JMenuItem("Abrir na pasta");
            abrirAqui.addActionListener(ev -> {
                explorerPath.setText(fn.path);
                reloadTree(fn.path);
            });
            menu.add(abrirAqui);
            menu.addSeparator();
        }
        menu.add(renomear);
        menu.add(deletar);
        menu.show(fileTree, e.getX(), e.getY());
    }

    private void refreshCurrentTree() {
        Object root = fileTree.getModel().getRoot();
        if (root instanceof DefaultMutableTreeNode r && r.getUserObject() instanceof FileNode fn) {
            reloadTree(fn.path);
        }
    }

    private void refreshTreePreserving() {
        if (fileTree.getModel().getRoot() instanceof DefaultMutableTreeNode root
                && root.getUserObject() instanceof FileNode fn) {
            List<String> expandedPaths = new ArrayList<>();
            java.util.Enumeration<?> expanded = fileTree.getExpandedDescendants(new TreePath(root));
            if (expanded != null) {
                while (expanded.hasMoreElements()) {
                    TreePath tp = (TreePath) expanded.nextElement();
                    if (tp.getLastPathComponent() instanceof DefaultMutableTreeNode n
                            && n.getUserObject() instanceof FileNode f) {
                        expandedPaths.add(f.path);
                    }
                }
            }
            DefaultMutableTreeNode newRoot = new DefaultMutableTreeNode(new FileNode(fn.name, fn.path, true));
            loadDirChildren(newRoot, new FileNode(fn.path, fn.path, true));
            fileTree.setModel(new DefaultTreeModel(newRoot));
            fileTree.expandRow(0);
            for (String p : expandedPaths) {
                expandNodeByPath(fileTree, newRoot, p);
            }
        }
    }

    private void expandNodeByPath(JTree tree, DefaultMutableTreeNode node, String path) {
        if (node.getUserObject() instanceof FileNode fn && fn.path.equals(path)) {
            tree.expandPath(new TreePath(node.getPath()));
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (node.getChildAt(i) instanceof DefaultMutableTreeNode child) {
                expandNodeByPath(tree, child, path);
            }
        }
    }

    private void reloadTree(String dir) {
        if (dir == null || dir.isEmpty()) return;
        dir = toAbsolute(dir);
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(new FileNode(dir.substring(dir.lastIndexOf('/') + 1), dir, true));
        loadDirChildren(root, new FileNode(dir, dir, true));
        fileTree.setModel(new DefaultTreeModel(root));
        fileTree.expandRow(0);
        sideBar.revalidate();
        sideBar.repaint();
    }

    private void loadDirChildren(DefaultMutableTreeNode parent, FileNode fn) {
        try {
            String json = invokeKofString("dirList", fn.path);
            Matcher m = Pattern.compile("\\{\"name\":\"((?:[^\"\\\\]|\\.)*)\",\"dir\":(true|false)\\}").matcher(json);
            boolean any = false;
            while (m.find()) {
                any = true;
                String name = unescape(m.group(1));
                boolean dir = m.group(2).equals("true");
                FileNode child = new FileNode(name, joinPath(fn.path, name), dir);
                DefaultMutableTreeNode node = new DefaultMutableTreeNode(child);
                if (dir) {
                    node.add(new DefaultMutableTreeNode((Object) null));
                }
                parent.add(node);
            }
        } catch (Exception ignored) {
        }
    }

    private void toggleSide(String view) {
        if (sideVisible && activeView.equals(view)) {
            sideBar.setVisible(false);
            sideVisible = false;
            activeView = "";
            styleActivity();
            return;
        }
        if (view.equals("explorer")) {
            String dir = activeDoc != null && !activeDoc.file.isEmpty()
                    ? toAbsolute(Paths.get(activeDoc.file).getParent().toString()) : System.getProperty("user.home");
            if (fileTree.getModel().getRoot() == null
                    || !(fileTree.getModel().getRoot() instanceof DefaultMutableTreeNode r)
                    || !(r.getUserObject() instanceof FileNode fn0) || !fn0.path.equals(dir)) {
                reloadTree(dir);
            }
        } else if (view.equals("git")) {
            refreshGit();
        }
        sideCards.show(sideBar, view);
        sideBar.setVisible(true);
        sideVisible = true;
        activeView = view;
        styleActivity();
    }

    private void styleActivity() {
        java.awt.event.ActionListener[] ignore = null;
        actExplorer.setBackground(activeView.equals("explorer") ? new Color(0x3A, 0x3D, 0x41) : HEADER_BG);
        actSearch.setBackground(activeView.equals("search") ? new Color(0x3A, 0x3D, 0x41) : HEADER_BG);
        actGit.setBackground(activeView.equals("git") ? new Color(0x3A, 0x3D, 0x41) : HEADER_BG);
    }

    private void runSearch() {
        String term = searchInput.getText();
        String dir = searchPath.getText();
        if (dir.isEmpty()) {
            dir = activeDoc != null && !activeDoc.file.isEmpty()
                    ? Paths.get(activeDoc.file).getParent().toString() : System.getProperty("user.home");
            searchPath.setText(dir);
        }
        currentSearchDir = dir;
        try {
            String json = invokeKofString("searchDir", dir, term);
            List<String> items = new ArrayList<>();
            Matcher m = Pattern.compile("\\{\"file\":\"((?:[^\"\\\\]|\\.)*)\",\"matches\":\\[((?:[^\\]]*))\\]\\}").matcher(json);
            while (m.find()) {
                String file = unescape(m.group(1));
                Matcher mm = Pattern.compile("\\\"line\\\":(\\d+),\\\"col\\\":(\\d+)\\}").matcher(m.group(2));
                while (mm.find()) {
                    items.add(file + ":" + mm.group(1) + "," + mm.group(2) + " | " + term);
                }
            }
            searchResults.setListData(items.toArray(new String[0]));
        } catch (Exception ex) {
            searchResults.setListData(new String[]{"(erro: " + ex.getMessage() + ")"});
        }
    }

    private String gitDir() {
        if (activeDoc != null && !activeDoc.file.isEmpty()) {
            Path parent = Paths.get(activeDoc.file).getParent();
            if (parent != null) return parent.toString();
        }
        if (searchPath != null && !searchPath.getText().isEmpty()) return searchPath.getText();
        return System.getProperty("user.dir");
    }

    private void refreshGit() {
        try {
            String out = captureCommand("git status --short 2>&1", gitDir());
            List<String> items = new ArrayList<>();
            for (String line : out.split("\n")) {
                if (!line.trim().isEmpty()) items.add(line);
            }
            gitList.setListData(items.toArray(new String[0]));
            term.append("$ git status: " + items.size() + " mudança(s)\n");
        } catch (Exception ex) {
            gitList.setListData(new String[]{"(git: " + ex.getMessage() + ")"});
        }
    }

    private void gitCommit() {
        String msg = gitMsg.getText().trim();
        if (msg.isEmpty()) {
            term.append("$ mensagem de commit vazia\n");
            return;
        }
        captureCommand("git add -A && git commit -m " + shellQuote(msg), gitDir());
        gitMsg.setText("");
        refreshGit();
    }

    private static String shellQuote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    private String captureCommand(String cmd) {
        return captureCommand(cmd, System.getProperty("user.dir"));
    }

    private String captureCommand(String cmd, String dir) {
        try {
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", cmd);
            pb.directory(new java.io.File(dir));
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes());
            p.waitFor();
            return out;
        } catch (Exception e) {
            return "(erro: " + e.getMessage() + ")";
        }
    }

    private boolean wrapEnabled = true;

    private void toggleWrap() {
        wrapEnabled = !wrapEnabled;
        if (body == null) return;
        setWrapRec(body);
    }

    private void setWrapRec(Panel p) {
        if (p instanceof LeafPanel lp) {
            for (Doc d : lp.docs) {
                if (wrapEnabled) {
                    d.pane.setPreferredSize(null);
                } else {
                    d.pane.setPreferredSize(new Dimension(2400, d.pane.getPreferredSize().height));
                }
                d.pane.revalidate();
            }
        } else if (p instanceof SplitPanel sp) {
            setWrapRec(sp.a);
            setWrapRec(sp.b);
        }
    }

    private void zoom(float delta) {
        editorFontSize = Math.max(8, Math.min(28, editorFontSize + delta));
        if (body == null) return;
        setFontRec(body);
        refreshAllGutters();
    }

    private void setFontRec(Panel p) {
        if (p instanceof LeafPanel lp) {
            for (Doc d : lp.docs) {
                d.pane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, Math.round(editorFontSize)));
            }
        } else if (p instanceof SplitPanel sp) {
            setFontRec(sp.a);
            setFontRec(sp.b);
        }
    }

    private void moveLine(int delta) {
        if (activeDoc == null) return;
        try {
            var doc = activeDoc.pane.getDocument();
            int caret = activeDoc.pane.getCaretPosition();
            int line = doc.getDefaultRootElement().getElementIndex(caret);
            int target = line + delta;
            var root = doc.getDefaultRootElement();
            if (target < 0 || target >= root.getElementCount()) return;
            int s1 = root.getElement(line).getStartOffset();
            int e1 = root.getElement(line).getEndOffset();
            int s2 = root.getElement(target).getStartOffset();
            int e2 = root.getElement(target).getEndOffset();
            String l1 = doc.getText(s1, e1 - s1);
            doc.remove(s1, e1 - s1);
            int insertAt = target > line ? s2 - (e1 - s1) : s2;
            doc.insertString(insertAt, l1, null);
            int caretLine = Math.min(Math.max(0, target), doc.getDefaultRootElement().getElementCount() - 1);
            activeDoc.pane.setCaretPosition(doc.getDefaultRootElement().getElement(caretLine).getStartOffset());
        } catch (Exception ignored) {
        }
    }

    private void selectNextOccurrence() {
        if (activeDoc == null) return;
        String sel = activeDoc.pane.getSelectedText();
        if (sel == null || sel.isEmpty()) {
            int pos = activeDoc.pane.getCaretPosition();
            try {
                int end = Math.min(pos + 1, activeDoc.pane.getDocument().getLength());
                sel = activeDoc.pane.getDocument().getText(pos, end - pos);
            } catch (Exception ignored) {
                return;
            }
        }
        if (sel.isEmpty()) return;
        String text = activeDoc.pane.getText();
        int from = activeDoc.pane.getCaretPosition() + sel.length();
        int idx = text.indexOf(sel, from);
        if (idx < 0) idx = text.indexOf(sel);
        if (idx >= 0) {
            activeDoc.pane.setCaretPosition(idx);
            activeDoc.pane.select(idx, idx + sel.length());
            activeDoc.pane.requestFocusInWindow();
        }
    }

    private void startTreeWatcher() {
        treeWatcher = new java.util.Timer("kof-tree-watcher", true);
        treeWatcher.scheduleAtFixedRate(new java.util.TimerTask() {
            public void run() {
                SwingUtilities.invokeLater(() -> {
                    if (sideVisible && activeView.equals("explorer")) {
                        refreshTreePreserving();
                    }
                });
            }
        }, 5000, 5000);
    }

    private void handleTyped(Doc doc, java.awt.event.KeyEvent e) {
        if (activeDoc != doc) return;
        char c = e.getKeyChar();
        try {
            int caret = doc.pane.getCaretPosition();
            var d = doc.pane.getDocument();
            if (c == '(' || c == '[' || c == '{') {
                String close = c == '(' ? ")" : c == '[' ? "]" : "}";
                d.insertString(caret, close, null);
                doc.pane.setCaretPosition(caret);
                e.consume();
            } else if (c == '"' || c == '\'') {
                String close = String.valueOf(c);
                d.insertString(caret, close, null);
                doc.pane.setCaretPosition(caret);
                e.consume();
            } else if (c == '\n') {
                e.consume();
                autoIndent();
            }
        } catch (Exception ignored) {
        }
    }

    private void autoIndent() {
        if (activeDoc == null) return;
        try {
            var doc = activeDoc.pane.getDocument();
            int caret = activeDoc.pane.getCaretPosition();
            var root = doc.getDefaultRootElement();
            int line = root.getElementIndex(caret);
            int s = root.getElement(line).getStartOffset();
            int e = root.getElement(line).getEndOffset();
            String lineText = doc.getText(s, e - s);
            int indent = 0;
            while (indent < lineText.length() && (lineText.charAt(indent) == ' ' || lineText.charAt(indent) == '\t')) {
                indent++;
            }
            String ind = lineText.substring(0, indent);
            boolean extra = lineText.trim().endsWith("{");
            doc.insertString(caret, "\n" + ind + (extra ? "    " : ""), null);
        } catch (Exception ignored) {
        }
    }

    private void toggleLineComment() {
        if (activeDoc == null) return;
        try {
            var doc = activeDoc.pane.getDocument();
            var root = doc.getDefaultRootElement();
            int selStart = activeDoc.pane.getSelectionStart();
            int selEnd = activeDoc.pane.getSelectionEnd();
            if (selStart == selEnd) {
                selStart = root.getElement(root.getElementIndex(selStart)).getStartOffset();
                selEnd = root.getElement(root.getElementIndex(selStart)).getEndOffset();
            }
            int lineStart = root.getElementIndex(selStart);
            int lineEnd = root.getElementIndex(Math.max(selStart, selEnd - 1));
            boolean allCommented = true;
            for (int i = lineStart; i <= lineEnd; i++) {
                String t = doc.getText(root.getElement(i).getStartOffset(),
                        root.getElement(i).getEndOffset() - root.getElement(i).getStartOffset());
                if (!t.trim().startsWith("//")) allCommented = false;
            }
            int anchor = -1;
            for (int i = lineEnd; i >= lineStart; i--) {
                int s = root.getElement(i).getStartOffset();
                String t = doc.getText(s, root.getElement(i).getEndOffset() - s);
                int ws = 0;
                while (ws < t.length() && (t.charAt(ws) == ' ' || t.charAt(ws) == '\t')) ws++;
                if (allCommented) {
                    doc.remove(s + ws, 3);
                } else {
                    doc.insertString(s + ws, "// ", null);
                }
                if (anchor < 0) anchor = s;
            }
            if (anchor >= 0) {
                activeDoc.pane.setCaretPosition(anchor);
            }
        } catch (Exception ignored) {
        }
    }

    private void selectLine() {
        if (activeDoc == null) return;
        try {
            var root = activeDoc.pane.getDocument().getDefaultRootElement();
            int line = root.getElementIndex(activeDoc.pane.getCaretPosition());
            int s = root.getElement(line).getStartOffset();
            int e = root.getElement(line).getEndOffset();
            activeDoc.pane.select(s, e);
        } catch (Exception ignored) {
        }
    }

    private void duplicateLine() {
        if (activeDoc == null) return;
        try {
            var doc = activeDoc.pane.getDocument();
            var root = doc.getDefaultRootElement();
            int line = root.getElementIndex(activeDoc.pane.getCaretPosition());
            int s = root.getElement(line).getStartOffset();
            int e = root.getElement(line).getEndOffset();
            String text = doc.getText(s, e - s);
            doc.insertString(e, text, null);
        } catch (Exception ignored) {
        }
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private String wordAtCaret() {
        if (activeDoc == null) return "";
        try {
            String text = activeDoc.pane.getText();
            int caret = activeDoc.pane.getCaretPosition();
            int s = caret;
            int e = caret;
            while (s > 0 && isWordChar(text.charAt(s - 1))) s--;
            while (e < text.length() && isWordChar(text.charAt(e))) e++;
            return text.substring(s, e);
        } catch (Exception ex) {
            return "";
        }
    }

    private void goToDefinition() {
        if (activeDoc == null) return;
        String word = wordAtCaret();
        if (word.isEmpty()) return;
        try {
            String outline = invokeKofString("outlineKf", activeDoc.pane.getText());
            Matcher m = Pattern.compile("\\{\"kind\":\"[^\"]+\",\"name\":\"" + Pattern.quote(word) + "\",\"line\":(\\d+)\\}").matcher(outline);
            if (m.find()) {
                int line = Integer.parseInt(m.group(1)) - 1;
                var root = activeDoc.pane.getDocument().getDefaultRootElement();
                if (line >= 0 && line < root.getElementCount()) {
                    activeDoc.pane.setCaretPosition(root.getElement(line).getStartOffset());
                    activeDoc.pane.requestFocusInWindow();
                }
                return;
            }
            String symbols = invokeKofString("symbolsJson", activeDoc.pane.getText());
            Matcher sm = Pattern.compile("\\{\"name\":\"" + Pattern.quote(word) + "\",\"offset\":(\\d+)\\}").matcher(symbols);
            if (sm.find()) {
                activeDoc.pane.setCaretPosition(Integer.parseInt(sm.group(1)));
                activeDoc.pane.requestFocusInWindow();
            }
        } catch (Exception ignored) {
        }
    }

    private void renameSymbol() {
        if (activeDoc == null) return;
        String word = wordAtCaret();
        if (word.isEmpty()) return;
        String newName = JOptionPane.showInputDialog(frame, "Novo nome para '" + word + "':", word);
        if (newName == null || newName.isEmpty() || newName.equals(word)) return;
        try {
            String json = invokeKofString("occurrencesJson", activeDoc.pane.getText(), word);
            List<Integer> offsets = new ArrayList<>();
            Matcher m = Pattern.compile("(\\d+)").matcher(json);
            while (m.find()) offsets.add(Integer.parseInt(m.group(1)));
            var doc = activeDoc.pane.getDocument();
            for (int i = offsets.size() - 1; i >= 0; i--) {
                doc.remove(offsets.get(i), word.length());
                doc.insertString(offsets.get(i), newName, null);
            }
        } catch (Exception ignored) {
        }
    }

    private static final String[][] SNIPPETS = {
            {"main", "main() {\n    \n}"},
            {"if", "if (condicao) {\n    \n}"},
            {"for", "for (var i = 0; i < n; i++) {\n    \n}"},
            {"while", "while (condicao) {\n    \n}"},
            {"class", "class Novo {\n    \n}"},
            {"record", "record Novo(Int x) {\n    \n}"},
            {"fn", "novaFuncao(): Int {\n    return 0\n}"},
    };

    private void showComplete() {
        if (activeDoc == null) return;
        String word = wordAtCaret();
        List<String> sugs = new ArrayList<>();
        for (String[] sn : SNIPPETS) {
            if (sn[0].startsWith(word) && word.length() >= 2) {
                sugs.add("snip " + sn[0]);
            }
        }
        try {
            String kj = invokeKofString("keywordsJson");
            Matcher km = Pattern.compile("\"([^\"]+)\"?").matcher(kj);
            while (km.find()) {
                String k = km.group(1);
                if (k.startsWith(word)) sugs.add(k);
            }
            String sj = invokeKofString("symbolsJson", activeDoc.pane.getText());
            Matcher sm = Pattern.compile("\"name\":\"((?:[^\"\\\\]|\\\\.)*)\"?").matcher(sj);
            while (sm.find()) {
                String n = sm.group(1);
                if (n.startsWith(word) && !sugs.contains(n)) sugs.add(n);
            }
        } catch (Exception ignored) {
        }
        if (sugs.isEmpty()) return;
        if (completePopup == null) {
            completeList = new JList<>();
            completeList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
            completeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            completeList.addMouseListener(new java.awt.event.MouseAdapter() {
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    if (e.getClickCount() == 2) applyComplete();
                }
            });
            completePopup = new JPopupMenu();
            completePopup.setFocusable(false);
            completePopup.add(new JScrollPane(completeList));
            completePopup.setPreferredSize(new Dimension(220, 200));
        }
        completeList.setListData(sugs.toArray(new String[0]));
        completeList.setSelectedIndex(0);
        try {
            Rectangle r = activeDoc.pane.modelToView2D(activeDoc.pane.getCaretPosition()).getBounds();
            completePopup.show(activeDoc.pane, r.x, r.y + activeDoc.pane.getFont().getSize() + 4);
        } catch (Exception ignored) {
            completePopup.show(activeDoc.pane, 60, 60);
        }
        activeDoc.pane.requestFocusInWindow();
    }

    private void applyComplete() {
        if (completePopup == null || completeList.getSelectedValue() == null) return;
        String sel = completeList.getSelectedValue();
        String word = wordAtCaret();
        try {
            int caret = activeDoc.pane.getCaretPosition();
            var doc = activeDoc.pane.getDocument();
            doc.remove(caret - word.length(), word.length());
            if (sel.startsWith("snip ")) {
                String name = sel.substring(5);
                for (String[] sn : SNIPPETS) {
                    if (sn[0].equals(name)) {
                        doc.insertString(caret - word.length(), sn[1], null);
                        break;
                    }
                }
            } else {
                doc.insertString(caret - word.length(), sel, null);
            }
        } catch (Exception ignored) {
        }
        completePopup.setVisible(false);
        activeDoc.pane.requestFocusInWindow();
    }

    private void nextTab(int delta) {
        if (activeLeaf == null || activeLeaf.docs.isEmpty()) return;
        int count = activeLeaf.docs.size();
        int idx = activeLeaf.docs.indexOf(activeDoc);
        int next = ((idx + delta) % count + count) % count;
        activate(activeLeaf, activeLeaf.docs.get(next));
        activeLeaf.tabs.setSelectedIndex(next);
    }

    private void insertLineBelow() {
        if (activeDoc == null) return;
        try {
            var doc = activeDoc.pane.getDocument();
            int caret = activeDoc.pane.getCaretPosition();
            var root = doc.getDefaultRootElement();
            int line = root.getElementIndex(caret);
            int s = root.getElement(line).getStartOffset();
            String lineText = doc.getText(s, root.getElement(line).getEndOffset() - s);
            int indent = 0;
            while (indent < lineText.length() && (lineText.charAt(indent) == ' ' || lineText.charAt(indent) == '\t')) {
                indent++;
            }
            String ind = lineText.substring(0, indent);
            int end = root.getElement(line).getEndOffset();
            doc.insertString(end, ind, null);
            activeDoc.pane.setCaretPosition(end + ind.length());
        } catch (Exception ignored) {
        }
    }

    private void indentSelection(int amount) {
        if (activeDoc == null) return;
        try {
            var doc = activeDoc.pane.getDocument();
            int selStart = activeDoc.pane.getSelectionStart();
            int selEnd = activeDoc.pane.getSelectionEnd();
            var root = doc.getDefaultRootElement();
            int lineStart = root.getElementIndex(selStart);
            int lineEnd = root.getElementIndex(Math.max(selStart, selEnd - 1));
            for (int i = lineStart; i <= lineEnd; i++) {
                int s = root.getElement(i).getStartOffset();
                if (amount > 0) {
                    doc.insertString(s, "    ", null);
                } else {
                    String t = doc.getText(s, Math.min(4, root.getElement(i).getEndOffset() - s));
                    int rm = 0;
                    while (rm < t.length() && rm < 4 && t.charAt(rm) == ' ') rm++;
                    if (rm > 0) doc.remove(s, rm);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void closeAllTabs() {
        if (activeLeaf == null) return;
        while (!activeLeaf.docs.isEmpty()) {
            activeDoc = activeLeaf.docs.get(0);
            activeLeaf.tabs.setSelectedIndex(0);
            closeTab();
        }
    }

    private void saveAll() {
        if (body == null) return;
        saveAllRec(body);
    }

    private void saveAllRec(Panel p) {
        if (p instanceof LeafPanel lp) {
            for (Doc d : lp.docs) {
                if (d.dirty) {
                    Doc prev = activeDoc;
                    activeDoc = d;
                    save(false);
                    activeDoc = prev;
                }
            }
        } else if (p instanceof SplitPanel sp) {
            saveAllRec(sp.a);
            saveAllRec(sp.b);
        }
    }

    private void showSymbolPicker() {
        if (activeDoc == null) return;
        try {
            String json = invokeKofString("outlineKf", activeDoc.pane.getText());
            List<String> symbols = new ArrayList<>();
            Matcher m = Pattern.compile("\\{\"kind\":\"([^\"]+)\",\"name\":\"((?:[^\"\\\\]|\\\\.)*)\",\"line\":(\\d+)\\}").matcher(json);
            while (m.find()) {
                symbols.add((m.group(1).equals("type") ? "C " : "f ") + unescape(m.group(2)) + "  :" + m.group(3));
            }
            if (symbols.isEmpty()) {
                term.append("$ nenhum símbolo encontrado\n");
                return;
            }
            JDialog dlg = new JDialog(frame, "Ir para símbolo", true);
            JTextField input = new JTextField();
            input.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            JList<String> list = new JList<>();
            list.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
            java.util.function.Consumer<String> update = q -> {
                List<String> shown = new ArrayList<>();
                for (String s : symbols) {
                    if (s.toLowerCase().contains(q.toLowerCase())) shown.add(s);
                }
                list.setListData(shown.toArray(new String[0]));
                if (!shown.isEmpty()) list.setSelectedIndex(0);
            };
            update.accept("");
            input.getDocument().addDocumentListener(new DocumentListener() {
                public void insertUpdate(DocumentEvent e) { update.accept(input.getText()); }
                public void removeUpdate(DocumentEvent e) { update.accept(input.getText()); }
                public void changedUpdate(DocumentEvent e) { update.accept(input.getText()); }
            });
            java.awt.event.ActionListener go = e -> {
                String sel = list.getSelectedValue();
                if (sel == null) return;
                try {
                    int line = Integer.parseInt(sel.substring(sel.lastIndexOf(':') + 1).trim()) - 1;
                    var root = activeDoc.pane.getDocument().getDefaultRootElement();
                    if (line >= 0 && line < root.getElementCount()) {
                        dlg.dispose();
                        activeDoc.pane.setCaretPosition(root.getElement(line).getStartOffset());
                        activeDoc.pane.requestFocusInWindow();
                    }
                } catch (NumberFormatException ignored) {
                }
            };
            input.addActionListener(go);
            list.addMouseListener(new java.awt.event.MouseAdapter() {
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    if (e.getClickCount() == 2) input.postActionEvent();
                }
            });
            dlg.setLayout(new BorderLayout(0, 4));
            dlg.add(input, BorderLayout.NORTH);
            dlg.add(new JScrollPane(list), BorderLayout.CENTER);
            dlg.setSize(460, 340);
            dlg.setLocationRelativeTo(frame);
            dlg.setVisible(true);
            input.requestFocusInWindow();
        } catch (Exception ignored) {
        }
    }

    private void showRecent() {
        if (recentFiles.isEmpty()) {
            term.append("$ sem arquivos recentes\n");
            return;
        }
        JDialog dlg = new JDialog(frame, "Arquivos recentes", true);
        JList<String> list = new JList<>(recentFiles.toArray(new String[0]));
        list.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && list.getSelectedValue() != null) {
                    dlg.dispose();
                    openPath(list.getSelectedValue());
                }
            }
        });
        list.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER && list.getSelectedValue() != null) {
                    dlg.dispose();
                    openPath(list.getSelectedValue());
                }
            }
        });
        dlg.setLayout(new BorderLayout());
        dlg.add(new JScrollPane(list), BorderLayout.CENTER);
        dlg.setSize(480, 320);
        dlg.setLocationRelativeTo(frame);
        dlg.setVisible(true);
    }

    private void formatDocument() {
        if (activeDoc == null) return;
        try {
            String formatted = invokeKofString("formatKf", activeDoc.pane.getText());
            int caret = activeDoc.pane.getCaretPosition();
            var doc = activeDoc.pane.getDocument();
            doc.remove(0, doc.getLength());
            doc.insertString(0, formatted, null);
            activeDoc.pane.setCaretPosition(Math.min(caret, doc.getLength()));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "Formatação falhou: " + ex.getMessage(),
                    "Kof Editor", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showQuickOpen() {
        if (activeDoc == null) return;
        String dir = !activeDoc.file.isEmpty() ? Paths.get(activeDoc.file).getParent().toString()
                : System.getProperty("user.dir");
        try {
            String json = invokeKofString("listAll", dir);
            List<String> all = new ArrayList<>();
            Matcher m = Pattern.compile("\"((?:[^\"\\\\]|\\.)*)\"").matcher(json);
            while (m.find()) all.add(unescape(m.group(1)));

            JDialog dlg = new JDialog(frame, "Abrir arquivo", true);
            JTextField input = new JTextField();
            input.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            JList<String> list = new JList<>();
            list.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
            java.util.function.Consumer<String> update = q -> {
                List<String> shown = new ArrayList<>();
                String query = q.toLowerCase();
                for (String f : all) {
                    if (f.toLowerCase().contains(query)) shown.add(f);
                }
                list.setListData(shown.toArray(new String[0]));
                if (!shown.isEmpty()) list.setSelectedIndex(0);
            };
            update.accept("");
            input.getDocument().addDocumentListener(new DocumentListener() {
                public void insertUpdate(DocumentEvent e) { update.accept(input.getText()); }
                public void removeUpdate(DocumentEvent e) { update.accept(input.getText()); }
                public void changedUpdate(DocumentEvent e) { update.accept(input.getText()); }
            });
            input.addActionListener(e -> {
                String sel = list.getSelectedValue();
                if (sel != null) {
                    dlg.dispose();
                    openPath(joinPath(dir, sel));
                }
            });
            dlg.setLayout(new BorderLayout(0, 4));
            dlg.add(input, BorderLayout.NORTH);
            dlg.add(new JScrollPane(list), BorderLayout.CENTER);
            dlg.setSize(520, 360);
            dlg.setLocationRelativeTo(frame);
            dlg.setVisible(true);
            input.requestFocusInWindow();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, ex.getMessage(), "Kof Editor", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void goToLine() {
        if (activeDoc == null) return;
        String input = JOptionPane.showInputDialog(frame, "Número da linha:", "Ir para linha", JOptionPane.PLAIN_MESSAGE);
        if (input == null) return;
        try {
            int line = Integer.parseInt(input.trim()) - 1;
            var root = activeDoc.pane.getDocument().getDefaultRootElement();
            if (line >= 0 && line < root.getElementCount()) {
                activeDoc.pane.setCaretPosition(root.getElement(line).getStartOffset());
                activeDoc.pane.requestFocusInWindow();
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private void findInFile() {
        if (activeDoc == null) return;
        String input = JOptionPane.showInputDialog(frame, "Buscar:", "Buscar no arquivo", JOptionPane.PLAIN_MESSAGE);
        if (input == null || input.isEmpty()) return;
        findTerm = input;
        findPos = -1;
        applyFindHighlight();
        findNext();
    }

    private void applyFindHighlight() {
        if (activeDoc == null || findTerm.isEmpty()) return;
        String text = activeDoc.pane.getText();
        StyledDocument sd = activeDoc.pane.getStyledDocument();
        int from = 0;
        while (true) {
            int idx = text.indexOf(findTerm, from);
            if (idx < 0) break;
            SimpleAttributeSet hl = new SimpleAttributeSet();
            StyleConstants.setBackground(hl, new Color(0xFF, 0xB8, 0x6C));
            StyleConstants.setForeground(hl, new Color(0x28, 0x2A, 0x36));
            sd.setCharacterAttributes(idx, findTerm.length(), hl, true);
            from = idx + findTerm.length();
        }
    }

    private void findNext() {
        if (activeDoc == null || findTerm.isEmpty()) return;
        String text = activeDoc.pane.getText();
        int start = findPos + 1;
        if (start >= text.length()) start = 0;
        int idx = text.indexOf(findTerm, start);
        if (idx < 0) {
            idx = text.indexOf(findTerm);
        }
        if (idx >= 0) {
            findPos = idx;
            activeDoc.pane.setCaretPosition(idx);
            activeDoc.pane.select(idx, idx + findTerm.length());
            activeDoc.pane.requestFocusInWindow();
        }
    }

    private void openSettings() {
        try {
            java.io.File f = new java.io.File(SETTINGS_PATH);
            if (!f.exists()) {
                f.getParentFile().mkdirs();
                java.nio.file.Files.writeString(f.toPath(),
                        "{\"theme\":\"escuro\",\"fontSize\":14,\"wrap\":true}");
            }
            openPath(f.getAbsolutePath());
        } catch (java.io.IOException ex) {
            JOptionPane.showMessageDialog(frame, ex.getMessage(), "Kof Editor", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showPalette() {
        record Cmd(String label, Runnable run) {}
        List<Cmd> all = List.of(
                new Cmd("Novo arquivo", this::newDoc),
                new Cmd("Abrir arquivo...", this::chooseAndOpen),
                new Cmd("Abrir pasta...", this::chooseFolder),
                new Cmd("Salvar", () -> save(false)),
                new Cmd("Salvar como...", () -> save(true)),
                new Cmd("Fechar aba", this::closeTab),
                new Cmd("Fechar divisão", this::closeSplit),
                new Cmd("Dividir à direita", () -> split(JSplitPane.HORIZONTAL_SPLIT)),
                new Cmd("Dividir abaixo", () -> split(JSplitPane.VERTICAL_SPLIT)),
                new Cmd("Explorer", () -> toggleSide("explorer")),
                new Cmd("Buscar na pasta", () -> toggleSide("search")),
                new Cmd("Git (mudanças)", () -> toggleSide("git")),
                new Cmd("Commit git...", this::gitCommit),
                new Cmd("Apagar linha (Ctrl+Shift+K)", this::deleteLine),
                new Cmd("Abrir configurações", this::openSettings),
                new Cmd("Salvar configurações", this::saveSettings),
                new Cmd("Terminal", this::toggleTerminal),
                new Cmd("Problemas", this::toggleDiags),
                new Cmd("Tema escuro (Dracula)", () -> { applyTheme(0); refreshUiColors(); }),
                new Cmd("Tema claro", () -> { applyTheme(1); refreshUiColors(); }),
                new Cmd("Exportar tema (YAML)...", this::exportTheme),
                new Cmd("Importar tema (YAML)...", this::importTheme),
                new Cmd("Abrir arquivo rápido (Ctrl+P)", this::showQuickOpen),
                new Cmd("Arquivos recentes (Ctrl+R)", this::showRecent),
                new Cmd("Ir para símbolo (Ctrl+Shift+O)", this::showSymbolPicker),
                new Cmd("Fechar todos", this::closeAllTabs),
                new Cmd("Reabrir aba fechada (Ctrl+Shift+T)", this::reopenClosedTab),
                new Cmd("Salvar tudo", this::saveAll),
                new Cmd("Ir para linha (Ctrl+G)", this::goToLine),
                new Cmd("Buscar no arquivo (Ctrl+F)", this::findInFile),
                new Cmd("Ir para definição (F12)", this::goToDefinition),
                new Cmd("Renomear símbolo (Ctrl+F2)", this::renameSymbol),
                new Cmd("Autocomplete (Ctrl+Space)", this::showComplete),
                new Cmd("Comentar linha (Ctrl+/)", this::toggleLineComment),
                new Cmd("Formatar documento (Shift+Alt+F)", this::formatDocument),
                new Cmd("Rodar arquivo (kof run)", this::runDoc),
                new Cmd("Build (target jvm)", () -> runCommand("kof build /tmp/kof-editor-run.kf --target jvm --output /tmp/kof-editor-build")),
                new Cmd("Sair", () -> frame.dispose()));

        JDialog dlg = new JDialog(frame, "Paleta de comandos", true);
        JTextField input = new JTextField();
        input.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        JList<String> list = new JList<>();
        list.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        java.util.function.Consumer<String> update = q -> {
            List<String> shown = new ArrayList<>();
            String query = q.toLowerCase();
            for (Cmd c : all) {
                if (c.label().toLowerCase().contains(query)) shown.add(c.label());
            }
            list.setListData(shown.toArray(new String[0]));
            if (!shown.isEmpty()) list.setSelectedIndex(0);
        };
        update.accept("");
        input.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { update.accept(input.getText()); }
            public void removeUpdate(DocumentEvent e) { update.accept(input.getText()); }
            public void changedUpdate(DocumentEvent e) { update.accept(input.getText()); }
        });
        input.addActionListener(e -> {
            String sel = list.getSelectedValue();
            if (sel == null) return;
            for (Cmd c : all) {
                if (c.label().equals(sel)) {
                    dlg.dispose();
                    c.run().run();
                    return;
                }
            }
        });
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) input.postActionEvent();
            }
        });
        dlg.setLayout(new BorderLayout(0, 4));
        dlg.add(input, BorderLayout.NORTH);
        dlg.add(new JScrollPane(list), BorderLayout.CENTER);
        dlg.setSize(480, 320);
        dlg.setLocationRelativeTo(frame);
        dlg.setVisible(true);
        input.requestFocusInWindow();
    }

    private void runDoc() {
        if (activeDoc == null) return;
        try {
            Path p = Files.createTempFile("kof-editor-", ".kf");
            Files.write(p, activeDoc.pane.getText().getBytes());
            runCommand("kof run " + p.toString());
        } catch (java.io.IOException ex) {
            term.append("$ erro: " + ex.getMessage() + "\n");
        }
    }

    private void runCommand(String cmd) {
        term.append("$ " + cmd + "\n");
        termInput.setText("");
        if (!termVisible) toggleTerminal();
        new Thread(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder("bash", "-c", cmd);
                String dir = gitDir();
                pb.directory(new java.io.File(dir));
                pb.redirectErrorStream(true);
                Process proc = pb.start();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        String l = line;
                        SwingUtilities.invokeLater(() -> term.append(l + "\n"));
                    }
                }
                int code = proc.waitFor();
                SwingUtilities.invokeLater(() -> term.append("(exit " + code + ")\n"));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> term.append("(erro: " + ex.getMessage() + ")\n"));
            }
        }).start();
    }

    private static Object invokeKofObject(String method, String... args) {
        try {
            Class<?> main = Class.forName("Default.Main");
            Class<?>[] types = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) types[i] = String.class;
            var m = main.getMethod(method, types);
            return m.invoke(null, (Object[]) args);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static String invokeKofString(String method, String... args) {
        try {
            Class<?> main = Class.forName("Default.Main");
            Class<?>[] types = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) types[i] = String.class;
            var m = main.getMethod(method, types);
            return (String) m.invoke(null, (Object[]) args);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static final Map<String, Color> KINDS = new java.util.HashMap<>();
    static {
        KINDS.put("keyword", new Color(0xC5, 0x86, 0xC0));
        KINDS.put("type", new Color(0x4E, 0xC9, 0xB0));
        KINDS.put("string", new Color(0xCE, 0x91, 0x78));
        KINDS.put("char", new Color(0xCE, 0x91, 0x78));
        KINDS.put("number", new Color(0xB5, 0xCE, 0xA8));
        KINDS.put("comment", new Color(0x6A, 0x99, 0x55));
        KINDS.put("error", new Color(0xF1, 0x4C, 0x4C));
    }

    private static Color colorFor(String kind) {
        return KINDS.getOrDefault(kind, FG);
    }

    private void applyTheme() {
        applyTheme(currentThemeIndex);
    }

    private void applyTheme(int index) {
        currentThemeIndex = index;
        try {
            String json = invokeKofString("themeAt", String.valueOf(index));
            currentThemeName = jsonValue(json, "name");
            BG = colorFrom(json, "bg");
            FG = colorFrom(json, "fg");
            HEADER_BG = colorFrom(json, "header");
            SELECTION = colorFrom(json, "selection");
            CURRENT_LINE = colorFrom(json, "currentLine");
            GUTTER_BG = HEADER_BG;
            GUTTER_FG = colorFrom(json, "gutter");
            CARET = FG;
            ERROR_FG = colorFrom(json, "error");
            TERM_BG = colorFrom(json, "terminal");
            TERM_FG = colorFrom(json, "terminalFg");
            GUTTER_CURRENT = FG;
            String[] keys = {"keyword", "type", "string", "char", "number", "comment",
                    "operator", "punctuation", "identifier", "error"};
            for (String k : keys) {
                KINDS.put(k, colorFrom(json, k));
            }
        } catch (Exception ignored) {
        }
    }

    private static Color colorFrom(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\":\"([^\"]+)\"?").matcher(json);
        if (m.find()) {
            Matcher x = Pattern.compile("38;2;(\\d+);(\\d+);(\\d+)").matcher(m.group(1));
            if (x.find()) {
                return new Color(Integer.parseInt(x.group(1)),
                        Integer.parseInt(x.group(2)), Integer.parseInt(x.group(3)));
            }
        }
        return FG;
    }

    private static String jsonValue(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\":\"([^\"]+)\"?").matcher(json);
        return m.find() ? m.group(1) : "";
    }

    private void refreshUiColors() {
        darkUi();
        header.setBackground(HEADER_BG);
        header.setForeground(FG);
        status.setBackground(HEADER_BG);
        status.setForeground(FG);
        explorer.setBackground(HEADER_BG);
        explorer.setForeground(FG);
        explorerPath.setBackground(new Color(0x2A, 0x2A, 0x2A));
        explorerPath.setForeground(FG);
        explorerPath.setCaretColor(FG);
        diags.setBackground(HEADER_BG);
        diags.setForeground(FG);
        term.setBackground(TERM_BG);
        term.setForeground(TERM_FG);
        termInput.setBackground(TERM_BG);
        termInput.setForeground(TERM_FG);
        termInput.setCaretColor(TERM_FG);
        if (runBtn != null) { runBtn.setBackground(HEADER_BG); runBtn.setForeground(FG); }
        if (clearBtn != null) { clearBtn.setBackground(HEADER_BG); clearBtn.setForeground(FG); }
        if (openFolder != null) { openFolder.setBackground(HEADER_BG); openFolder.setForeground(FG); }
        rootContent.setBackground(BG);
        if (frame != null) frame.getContentPane().setBackground(BG);
        for (Component c : frame.getRootPane().getComponents()) {
            if (c instanceof JMenuBar) styleMenuBar((JMenuBar) c);
        }
        syncThemeMenu();
        refreshAllDocs();
        refreshAllGutters();
        SwingUtilities.invokeLater(this::render);
    }

    private void styleMenuBar(JMenuBar menuBar) {
        menuBar.setBackground(HEADER_BG);
        menuBar.setForeground(FG);
        for (int i = 0; i < menuBar.getMenuCount(); i++) {
            JMenu menu = menuBar.getMenu(i);
            menu.setBackground(HEADER_BG);
            menu.setForeground(FG);
            for (int j = 0; j < menu.getItemCount(); j++) {
                JMenuItem mi = menu.getItem(j);
                if (mi != null) {
                    mi.setBackground(HEADER_BG);
                    mi.setForeground(FG);
                }
            }
        }
    }

    private void syncThemeMenu() {
        JMenuBar bar = frame.getJMenuBar();
        if (bar == null) return;
        for (int i = 0; i < bar.getMenuCount(); i++) {
            JMenu menu = bar.getMenu(i);
            if (menu != null && menu.getText().equals("Tema")) {
                for (int j = 0; j < menu.getItemCount(); j++) {
                    JMenuItem mi = menu.getItem(j);
                    if (mi instanceof JRadioButtonMenuItem rb) {
                        rb.setSelected(rb.getText().contains(currentThemeName)
                                || (rb.getText().startsWith("Escuro") && currentThemeIndex == 0)
                                || (rb.getText().startsWith("Claro") && currentThemeIndex == 1));
                    }
                }
            }
        }
    }

    private void refreshAllDocs() {
        if (body == null) return;
        refreshDocsIn(body);
    }

    private void refreshDocsIn(Panel p) {
        if (p instanceof LeafPanel lp) {
            for (Doc d : lp.docs) {
                d.pane.setBackground(BG);
                d.pane.setForeground(FG);
                d.pane.setCaretColor(CARET);
                d.pane.setSelectionColor(SELECTION);
                d.pane.repaint();
            }
        } else if (p instanceof SplitPanel sp) {
            refreshDocsIn(sp.a);
            refreshDocsIn(sp.b);
        }
    }

    private void refreshAllGutters() {
        if (body == null) return;
        guttersIn(body);
    }

    private void guttersIn(Panel p) {
        if (p instanceof LeafPanel lp) {
            for (int i = 0; i < lp.tabs.getTabCount(); i++) {
                Component comp = lp.tabs.getComponentAt(i);
                if (comp instanceof JScrollPane sp && sp.getRowHeader() != null) {
                    sp.getRowHeader().getView().repaint();
                }
            }
        } else if (p instanceof SplitPanel sp) {
            guttersIn(sp.a);
            guttersIn(sp.b);
        }
    }

    private static Color xterm256(int n) {
        if (n < 16) {
            int[] r = {0,0x80,0,0x80,0,0x80,0,0xc0,0x80,0xff,0,0xff,0,0xff,0,0xff};
            int[] g = {0,0,0x80,0x80,0,0,0x80,0xc0,0x80,0,0xff,0xff,0,0,0xff,0xff};
            int[] b = {0,0,0,0,0x80,0x80,0x80,0xc0,0x80,0,0,0,0xff,0xff,0xff,0xff};
            return new Color(r[n], g[n], b[n]);
        }
        if (n < 232) {
            int v = n - 16;
            int r = 55 + (v / 36) * 40;
            int g = 55 + ((v % 36) / 6) * 40;
            int bb = 55 + (v % 6) * 40;
            return new Color(r, g, bb);
        }
        int gray = 8 + (n - 232) * 10;
        return new Color(gray, gray, gray);
    }

    private static String jsonString(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\":\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        return m.find() ? unescape(m.group(1)) : "";
    }

    private static String unescape(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(i + 1);
                switch (n) {
                    case 'n': out.append('\n'); i++; break;
                    case 't': out.append('\t'); i++; break;
                    case 'r': out.append('\r'); i++; break;
                    case '"': out.append('"'); i++; break;
                    case '\\': out.append('\\'); i++; break;
                    default: out.append(c);
                }
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static class IconButton extends JButton {
        private final String icon;
        IconButton(String icon, String tip, java.awt.event.ActionListener a) {
            super();
            this.icon = icon;
            setToolTipText(tip);
            setFocusPainted(false);
            setPreferredSize(new Dimension(46, 36));
            setMaximumSize(new Dimension(46, 36));
            setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            setBackground(HEADER_BG);
            setForeground(FG);
            addActionListener(a);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getForeground());
            int cx = getWidth() / 2;
            int cy = getHeight() / 2;
            switch (icon) {
                case "folder": {
                    g2.drawRect(cx - 8, cy - 4, 16, 12);
                    g2.drawLine(cx - 8, cy - 4, cx - 8, cy - 6);
                    g2.drawLine(cx - 8, cy - 6, cx - 3, cy - 6);
                    g2.drawLine(cx - 3, cy - 6, cx - 1, cy - 4);
                    break;
                }
                case "search": {
                    g2.drawOval(cx - 6, cy - 6, 10, 10);
                    g2.drawLine(cx + 2, cy + 2, cx + 7, cy + 7);
                    break;
                }
                case "git": {
                    g2.drawOval(cx - 7, cy - 6, 6, 6);
                    g2.drawOval(cx + 1, cy + 1, 6, 6);
                    g2.drawLine(cx - 4, cy - 1, cx - 4, cy + 2);
                    g2.drawLine(cx - 4, cy + 2, cx + 1, cy + 2);
                    break;
                }
                case "outline": {
                    for (int i = 0; i < 4; i++) {
                        g2.drawLine(cx - 8, cy - 7 + i * 5, cx - 1, cy - 7 + i * 5);
                        g2.drawLine(cx - 8, cy - 7 + i * 5, cx - 11, cy - 7 + i * 5);
                    }
                    break;
                }
                case "problems": {
                    g2.drawPolygon(new int[]{cx, cx - 6, cx + 6}, new int[]{cy - 7, cy + 7, cy + 7}, 3);
                    g2.setColor(ERROR_FG);
                    g2.drawLine(cx, cy - 2, cx, cy + 2);
                    g2.drawLine(cx, cy + 4, cx, cy + 4);
                    break;
                }
            }
            g2.dispose();
        }
    }

    private static class Minimap extends JPanel {
        private final JTextPane pane;
        private List<int[]> lines = new ArrayList<>();
        private Map<String, Color> colors = KINDS;

        Minimap(JTextPane pane) {
            this.pane = pane;
            setPreferredSize(new Dimension(48, 0));
            setBackground(new Color(0x21, 0x22, 0x2C));
            addMouseListener(new java.awt.event.MouseAdapter() {
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    if (!lines.isEmpty()) {
                        int line = Math.min(lines.size() - 1, e.getY() / Math.max(1, getHeight() / lines.size()));
                        try {
                            var root = pane.getDocument().getDefaultRootElement();
                            if (line < root.getElementCount()) {
                                pane.setCaretPosition(root.getElement(line).getStartOffset());
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
            });
        }

        void update(List<TokenRow> rows) {
            lines = new ArrayList<>();
            for (TokenRow r : rows) {
                Color c = colors.getOrDefault(r.kind, FG);
                lines.add(new int[]{c.getRed(), c.getGreen(), c.getBlue()});
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (lines.isEmpty()) return;
            int w = getWidth();
            int h = getHeight();
            int lineH = Math.max(1, h / Math.max(1, lines.size()));
            for (int i = 0; i < lines.size() && i < h / lineH + 1; i++) {
                int[] c = lines.get(i);
                g.setColor(new Color(c[0], c[1], c[2]));
                g.fillRect(1, i * lineH, w - 2, Math.max(1, lineH - 1));
            }
        }
    }

    private record TokenRow(String kind, int start, int end) {}

    private static class LineGutter extends JPanel {
        private final JTextPane pane;
        private final Doc doc;

        LineGutter(Doc doc) {
            this.doc = doc;
            this.pane = doc.pane;
            setBackground(GUTTER_BG);
            setPreferredSize(new Dimension(52, 10));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            FontMetrics fm = g.getFontMetrics(pane.getFont());
            int lineHeight = fm.getHeight();
            JViewport vp = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, pane);
            int y0 = vp != null ? vp.getViewPosition().y : 0;
            int first = Math.max(1, y0 / lineHeight + 1);
            int visible = vp != null ? vp.getExtentSize().height / lineHeight + 2 : 80;
            int total = pane.getDocument().getDefaultRootElement().getElementCount();
            int caretLine = -1;
            try {
                var root = pane.getDocument().getDefaultRootElement();
                caretLine = root.getElementIndex(pane.getCaretPosition());
            } catch (Exception ignored) {
            }
            setBackground(GUTTER_BG);
            for (int i = first; i <= Math.min(total, first + visible); i++) {
                g.setColor(i - 1 == caretLine ? GUTTER_CURRENT : GUTTER_FG);
                int y = (i - 1) * lineHeight - y0 + 4;
                String num = String.valueOf(i);
                g.drawString(num, getWidth() - fm.stringWidth(num) - 10, y + fm.getAscent());
            }
        }
    }
}

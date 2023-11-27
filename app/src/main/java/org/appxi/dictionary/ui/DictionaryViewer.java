package org.appxi.dictionary.ui;

import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import org.appxi.dictionary.Dictionaries;
import org.appxi.dictionary.Dictionary;
import org.appxi.dictionary.DictionaryHelper;
import org.appxi.dictionary.MatchType;
import org.appxi.event.EventHandler;
import org.appxi.javafx.app.web.WebToolPrinter;
import org.appxi.javafx.app.web.WebViewer;
import org.appxi.javafx.helper.FxHelper;
import org.appxi.javafx.visual.MaterialIcon;
import org.appxi.javafx.web.WebPane;
import org.appxi.javafx.workbench.WorkbenchApp;
import org.appxi.javafx.workbench.WorkbenchPane;
import org.appxi.prefs.UserPrefs;
import org.appxi.util.FileHelper;
import org.appxi.util.StringHelper;
import org.appxi.util.ext.HanLang;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class DictionaryViewer extends WebViewer {
    public static final Object AK_BY_SELECTION = new Object();

    final Dictionary.Entry entry;

    private boolean _searchAllDictionaries;
    private Button searchAll;
    private final EventHandler<HanLang.Event> _handleHanLangChanged = event -> navigate(null);

    DictionaryViewer(WorkbenchPane workbench, Dictionary.Entry entry) {
        super(workbench);
        this.entry = entry;
    }

    @Override
    protected Dictionary.Entry location() {
        return entry;
    }

    @Override
    protected String locationId() {
        return "dict@" + entry.dictionary.id + "@" + entry.id;
    }

    @Override
    protected void saveUserData() {
        //
    }

    @Override
    public void initialize() {
        super.initialize();
        //
        addTool_searchAllDictionaries();
        new WebToolPrinter(this);
        //
        app.eventBus.addEventHandler(HanLang.Event.CHANGED, _handleHanLangChanged);
        //
        WebViewer.addShortcutKeys(this);
        DictionaryViewer.addShortcutKeys(this);
        WebViewer.addShortcutMenu(this);
        DictionaryViewer.addShortcutMenu(this);
        DictionaryViewer.addShortcutMenu_(this);
        DictionaryViewer.addSelectionEvent(this);
    }

    @Override
    public void deinitialize() {
        app.eventBus.removeEventHandler(HanLang.Event.CHANGED, _handleHanLangChanged);
        super.deinitialize();
    }

    protected void addTool_searchAllDictionaries() {
        searchAll = MaterialIcon.TRAVEL_EXPLORE.flatButton();
        searchAll.setText("查全部词典");
        searchAll.setTooltip(new Tooltip("从所有词典中精确查词“" + entry.title() + "”"));
        searchAll.setOnAction(this::onSearchAllDictionaries);
        //
        this.webPane.getTopBar().addLeft(searchAll);
    }

    void onSearchAllDictionaries(ActionEvent event) {
        _searchAllDictionaries = true;
        navigate(null);
        // 已从全部词典查询，此时禁用掉此按钮
        ((Button) event.getSource()).setDisable(true);
    }

    @Override
    protected Object createWebContent() {
        final StringBuilder buff = new StringBuilder();
        //
        buff.append("<!DOCTYPE html><html lang=\"zh\"><head><meta charset=\"UTF-8\">");
        if (null != DictionaryContext.webIncludesSupplier) {
            StringHelper.buildWebIncludes(buff, DictionaryContext.webIncludesSupplier.get());
        }
        buff.append("""
                <script type="text/javascript">
                function onJavaReady(args) {
                    setWebStyleTheme(args.theme);
                }
                </script>
                """);
        //
        buff.append("</head><body><article style=\"padding: 0 1rem;\">");
        //
        StringBuilder htmlDoc = new StringBuilder();
        if (_searchAllDictionaries) {
            List<Dictionary.Entry> list = new ArrayList<>();
            Dictionaries.def.search(entry.title(), MatchType.TitleEquals)
                    .forEachRemaining(entry -> list.add(entry.dictionary == this.entry.dictionary ? 0 : list.size(), entry));
            list.forEach(entry -> htmlDoc.append(DictionaryHelper.toHtmlDocument(entry)));
        } else {
            htmlDoc.append(DictionaryHelper.toHtmlDocument(entry));
        }
        if (null != DictionaryContext.htmlDocumentWrapper) {
            buff.append(DictionaryContext.htmlDocumentWrapper.apply(htmlDoc.toString()));
        } else {
            buff.append(htmlDoc);
        }
        //
        buff.append("</article></body></html>");
        // 由于词条内容可能涉及特殊字符，此处使用本地文件以保证正常显示
        String tempInfo = entry.dictionary.id + "." + entry.id + (_searchAllDictionaries ? ".all" : "");
        Path tempFile = UserPrefs.cacheDir().resolve(FileHelper.makeEncodedPath(tempInfo, ".html"));
        FileHelper.writeString(buff.toString(), tempFile);
        return tempFile;
    }

    @Override
    protected void onWebEngineLoadSucceeded() {
        super.onWebEngineLoadSucceeded();
        //
        if (null != searchAll && UserPrefs.prefs.getBoolean("dictionary.viewerLoadAll", false)) {
            FxHelper.runThread(150, () -> searchAll.fire());
        }
    }

    @Override
    protected WebJavaBridgeImpl createWebJavaBridge() {
        return new WebJavaBridgeImpl();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public class WebJavaBridgeImpl extends WebViewer.WebJavaBridgeImpl {
        public void seeAlso(String dictId, String keyword) {
            app.eventBus.fireEvent(DictionaryEvent.ofSearchExact(dictId, keyword));
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static void addShortcutKeys(WebViewer webViewer) {
        final WebPane webPane = webViewer.webPane;
        final WorkbenchApp app = webViewer.app;
        // Ctrl + D
        webPane.shortcutKeys.put(new KeyCodeCombination(KeyCode.D, KeyCombination.SHORTCUT_DOWN), event -> {
            // 如果有选中文字，则按选中文字处理
            String origText = webPane.executeScript("getValidSelectionText()");
            String trimText = null == origText ? null : origText.strip().replace('\n', ' ');
            final String availText = StringHelper.isBlank(trimText) ? null : trimText;

            final String str = null == availText ? null : StringHelper.trimChars(availText, 20, "");
            app.eventBus.fireEvent(DictionaryEvent.ofSearch(str));
            event.consume();
        });
    }

    public static void addShortcutMenu(WebViewer webViewer) {
        final WebPane webPane = webViewer.webPane;
        final WorkbenchApp app = webViewer.app;
        //
        webPane.shortcutMenu.add(selection -> {
            MenuItem menuItem = new MenuItem();
            menuItem.getProperties().put(WebPane.GRP_MENU, "search2");
            if (selection.hasTrims) {
                menuItem.setText("查词典：" + StringHelper.trimChars(selection.trims, 10));
            } else {
                menuItem.setText("查词典");
            }
            menuItem.setOnAction(event -> app.eventBus.fireEvent(DictionaryEvent.ofSearch(
                    selection.hasTrims ? StringHelper.trimChars(selection.trims, 20, "") : null)));
            return List.of(menuItem);
        });
    }

    private static void addShortcutMenu_(WebViewer webViewer) {
        final WebPane webPane = webViewer.webPane;
        final WorkbenchApp app = webViewer.app;
        //
        webPane.shortcutMenu.add(selection -> {
            MenuItem menuItem = new MenuItem("复制引用");
            menuItem.getProperties().put(WebPane.GRP_MENU, "copy");
            menuItem.setDisable(true);
            return List.of(menuItem);
        });
    }

    public static void addSelectionEvent(WebViewer webViewer) {
        final WebPane webPane = webViewer.webPane;
        final WorkbenchApp app = webViewer.app;
        //
        webPane.selectionListeners.add((shortcutDown, selection) -> {
            Object act = webPane.getProperties().get(DictionaryViewer.AK_BY_SELECTION);
            if (null == act) {
                act = UserPrefs.prefs.getString("dictionary.bySelection", "selection&shortcut1");
            }
            if ("selection&shortcut1".equals(act) && shortcutDown || "selection&shortcut0".equals(act) && !shortcutDown) {
                String text = StringHelper.trimChars(selection.trims, 20, "");
                if (!text.isBlank()) {
                    app.eventBus.fireEvent(DictionaryEvent.ofSearch(text));
                }
            }
        });
    }
}

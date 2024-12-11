package org.appxi.dictionary.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.appxi.dictionary.Dictionaries;
import org.appxi.dictionary.Dictionary;
import org.appxi.dictionary.DictionaryHelper;
import org.appxi.dictionary.MatchType;
import org.appxi.event.Event;
import org.appxi.event.EventHandler;
import org.appxi.javafx.app.BaseApp;
import org.appxi.javafx.app.web.WebAction_Goto;
import org.appxi.javafx.app.web.WebApp;
import org.appxi.javafx.app.web.WebViewer;
import org.appxi.javafx.control.TextInput;
import org.appxi.javafx.visual.MaterialIcon;
import org.appxi.javafx.web.WebPane;
import org.appxi.javafx.web.WebSelection;
import org.appxi.prefs.UserPrefs;
import org.appxi.property.RawProperty;
import org.appxi.util.FileHelper;
import org.appxi.util.StringHelper;
import org.appxi.util.ext.FiConsumerX3;
import org.appxi.util.ext.HanLang;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class EntryViewer extends WebViewer {
    private final EventHandler<Event> _handleHanLangChanged = event -> navigate(null);

    private Supplier<List<String>> webIncludesSupplier;
    private Function<String, String> htmlDocumentWrapper;

    final TextInput textInput = new TextInput();
    final Hyperlink sourceInfo;
    final RawProperty<Predicate<Dictionary>> source = new RawProperty<>();
    private WebAction_Goto actionGoto;

    EntryViewer(BaseApp app, String text, final Predicate<Dictionary> sourceFilter) {
        super(app);
        textInput.input.setText(text);
        textInput.input.setAlignment(Pos.CENTER);
        HBox.setHgrow(textInput, Priority.ALWAYS);

        sourceInfo = new Hyperlink();
        sourceInfo.getStyleClass().add("never-visited");
        textInput.leftArea.getChildren().add(sourceInfo);

        sourceInfo.setGraphic(MaterialIcon.MANAGE_SEARCH.graphic());
        sourceInfo.setTooltip(new Tooltip("查词义范围设置"));
        //
        source.set(sourceFilter);
        source.addListener((ov, nv) -> navigate(null));
    }

    @Override
    protected Dictionary.Entry location() {
        return null;
    }

    @Override
    protected String locationId() {
        return null;
    }

    @Override
    public void initialize() {
        super.initialize();
        //
        actionGoto = new WebAction_Goto(this, "div[data-dict] > :first-child", "div");
        Button button = MaterialIcon.NEAR_ME.flatButton();
        button.setText("转到");
        button.setTooltip(new Tooltip("转到词典 (Ctrl+T)"));
        button.setOnAction(event -> actionGoto.action());
        webPane.getTopBox().addLeft(button);
        // Ctrl + T
        webPane.shortcutKeys.put(new KeyCodeCombination(KeyCode.T, KeyCombination.SHORTCUT_DOWN), (event, selection) -> button.fire());

        //
        webPane.getTopBox().addLeft(textInput);
        textInput.input.setOnAction(e -> {
            String text = textInput.input.getText().strip();
            if (text.isEmpty()) {
                return;
            }
            navigate(null);
        });
        sourceInfo.setOnAction(actionEvent -> DictionaryContext.openScopesDialog(app, DictionaryContext.DEF_SCOPES2, source));
        //
        app.eventBus.addEventHandler(HanLang.CHANGED, _handleHanLangChanged);
        //
        WebViewer.addShortcutKeys(this);
        EntryViewer.addShortcutKeys(this);
        WebViewer.addShortcutMenu(this);
        EntryViewer.addShortcutMenu(this);
        EntryViewer.addShortcutMenu2(this);
        EntryViewer.addSelectionEvent(this);
        //
        if (app instanceof WebApp webApp) {
            this.webIncludesSupplier = webApp.webIncludesSupplier();
            this.htmlDocumentWrapper = webApp.htmlDocumentWrapper();
        }
    }

    @Override
    public void deinitialize() {
        app.eventBus.removeEventHandler(HanLang.CHANGED, _handleHanLangChanged);
        super.deinitialize();
    }

    @Override
    protected Object createWebContent() {
        final StringBuilder buff = new StringBuilder();
        //
        buff.append("<!DOCTYPE html><html lang=\"zh\"><head><meta charset=\"UTF-8\">");

        if (null != webIncludesSupplier) {
            StringHelper.buildWebIncludes(buff, webIncludesSupplier.get());
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
        final Set<String> dictList = new HashSet<>();
        final StringBuilder htmlDoc = new StringBuilder();

        Dictionaries dictionaries = Dictionaries.def.filtered(source.get());
        dictionaries = dictionaries.size() > 0 ? dictionaries : Dictionaries.def;
        dictionaries
                .search(textInput.input.getText().strip(), MatchType.TitleEquals)
                .forEachRemaining(entry -> {
                    dictList.add(entry.dictionary.id);
                    htmlDoc.append(DictionaryHelper.toHtmlDocument(entry));
                });

        if (null != htmlDocumentWrapper) {
            buff.append(htmlDocumentWrapper.apply(htmlDoc.toString()));
        } else {
            buff.append(htmlDoc);
        }
        //
        buff.append("</article></body></html>");
        //

        // 不同词或不同范围重新查词后实际词典位置不同，需要重新计算定位目录
        sourceInfo.setText("在 %d/%d 词典中查到:".formatted(dictList.size(), dictionaries.size()));
        actionGoto.reset();

        // 由于词条内容可能涉及特殊字符，此处使用本地文件以保证正常显示
        String tempInfo = String.valueOf(System.currentTimeMillis());
        Path tempFile = UserPrefs.cacheDir().resolve(FileHelper.makeEncodedPath(tempInfo, ".html"));
        FileHelper.writeString(buff.toString(), tempFile);
        tempFile.toFile().deleteOnExit();
        return tempFile;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected WebJavaBridgeImpl createWebJavaBridge() {
        return new WebJavaBridgeImpl();
    }

    public class WebJavaBridgeImpl extends WebViewer.WebJavaBridgeImpl {
        public void seeAlso(String dictId, String keyword) {
            app.eventBus.fireEvent(new EntryEvent(EntryEvent.SEARCH_EXACT, keyword, dictId));
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static void addShortcutKeys(WebViewer webViewer) {
        final WebPane webPane = webViewer.webPane;
        final BaseApp app = webViewer.app;
        //
        final FiConsumerX3<KeyEvent, WebSelection, Boolean> BC = (event, selection, exact) -> {
            final String str = StringHelper.trimChars(selection.trims, 20, "");
            if (!exact) {
                event.consume();
                app.eventBus.fireEvent(new EntryEvent(EntryEvent.SEARCH, str, null));
            } else if (StringHelper.isNotBlank(str)) {
                event.consume();
                app.eventBus.fireEvent(new EntryEvent(EntryEvent.SEARCH_EXACT, str, null));
            }
        };
        // Ctrl + D , 查词
        webPane.shortcutKeys.put(new KeyCodeCombination(KeyCode.D, KeyCombination.SHORTCUT_DOWN), (e, s) -> BC.accept(e, s, false));
        // Ctrl + Shift + D , 查词义
        webPane.shortcutKeys.put(new KeyCodeCombination(KeyCode.D, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN), (e, s) -> BC.accept(e, s, true));
    }

    public static void addShortcutMenu(WebViewer webViewer) {
        final WebPane webPane = webViewer.webPane;
        final BaseApp app = webViewer.app;
        //
        webPane.shortcutMenu.add(selection -> {
            final MenuItem menuItem = new MenuItem();
            menuItem.getProperties().put(WebPane.GRP_MENU, "search2");
            if (selection.hasTrims) {
                menuItem.setText("查词条：" + StringHelper.trimChars(selection.trims, 10));
            } else {
                menuItem.setText("查词条");
            }
            menuItem.setOnAction(event -> app.eventBus.fireEvent(new EntryEvent(EntryEvent.SEARCH,
                    selection.hasTrims ? StringHelper.trimChars(selection.trims, 20, "") : null, null)));
            return List.of(menuItem);
        });
        //
        webPane.shortcutMenu.add(selection -> {
            if (selection.hasTrims) {
                final MenuItem menuItem = new MenuItem();
                menuItem.getProperties().put(WebPane.GRP_MENU, "search2");
                menuItem.setText("查词义：" + StringHelper.trimChars(selection.trims, 10));
                menuItem.setOnAction(event -> app.eventBus.fireEvent(new EntryEvent(EntryEvent.SEARCH_EXACT,
                        StringHelper.trimChars(selection.trims, 20, ""), null)));
                return List.of(menuItem);
            }
            return List.of();
        });
    }

    private static void addShortcutMenu2(WebViewer webViewer) {
        final WebPane webPane = webViewer.webPane;
        final BaseApp app = webViewer.app;
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
        final BaseApp app = webViewer.app;
        //
        webPane.selectionListeners.add((e, selection) -> {
            if (e.isAltDown() || e.isShiftDown() || !selection.hasTrims) {
                return;
            }
            String act = app.config.getString("dictionary.bySelection", "selection&shortcut1");
            if ("selection&shortcut1".equals(act) && e.isShortcutDown() || "selection&shortcut0".equals(act) && !e.isShortcutDown()) {
                String text = StringHelper.trimChars(selection.trims, 20, "");
                app.eventBus.fireEvent(new EntryEvent(EntryEvent.SEARCH_EXACT, text, null));
            }
        });
    }
}

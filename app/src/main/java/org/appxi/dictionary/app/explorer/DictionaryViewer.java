package org.appxi.dictionary.app.explorer;

import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import org.appxi.dictionary.Dictionaries;
import org.appxi.dictionary.Dictionary;
import org.appxi.dictionary.DictionaryHelper;
import org.appxi.dictionary.SearchType;
import org.appxi.javafx.app.search.DictionaryEvent;
import org.appxi.javafx.app.web.WebToolPrinter;
import org.appxi.javafx.app.web.WebViewer;
import org.appxi.javafx.helper.FxHelper;
import org.appxi.javafx.visual.MaterialIcon;
import org.appxi.javafx.web.WebSelection;
import org.appxi.javafx.workbench.WorkbenchPane;
import org.appxi.prefs.UserPrefs;
import org.appxi.util.FileHelper;
import org.appxi.util.StringHelper;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

class DictionaryViewer extends WebViewer {
    final Dictionary.Entry entry;

    private boolean _searchAllDictionaries;
    private Button searchAll;

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
            Dictionaries.search(entry.title(), SearchType.TitleEquals, null, null)
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

    protected void onWebViewContextMenuRequest(List<MenuItem> model, WebSelection selection) {
        super.onWebViewContextMenuRequest(model, selection);
        //
        String textTip = selection.hasTrims ? "：" + StringHelper.trimChars(selection.trims, 8) : "";
        String textForSearch = selection.hasTrims ? selection.trims : null;
        //
        MenuItem copyRef = new MenuItem("复制引用");
        copyRef.setDisable(true);

        //
        model.add(createMenu_copy(selection));
        model.add(copyRef);
        model.add(new SeparatorMenuItem());
        model.add(createMenu_search(textTip, textForSearch));
        model.add(createMenu_searchExact(textTip, textForSearch));
        model.add(createMenu_lookup(textTip, textForSearch));
        model.add(createMenu_finder(textTip, selection));
        model.add(new SeparatorMenuItem());
        model.add(createMenu_dict(selection));
        model.add(createMenu_pinyin(selection));
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public class WebJavaBridgeImpl extends WebViewer.WebJavaBridgeImpl {
        public void seeAlso(String dictId, String keyword) {
            app.eventBus.fireEvent(DictionaryEvent.ofSearchExact(dictId, keyword));
        }
    }
}

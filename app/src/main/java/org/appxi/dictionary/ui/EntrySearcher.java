package org.appxi.dictionary.ui;

import javafx.event.ActionEvent;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.Tooltip;
import javafx.scene.input.InputEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.appxi.dictionary.Dictionaries;
import org.appxi.dictionary.Dictionary;
import org.appxi.dictionary.MatchType;
import org.appxi.javafx.app.BaseApp;
import org.appxi.javafx.control.LookupLayer;
import org.appxi.javafx.helper.FxHelper;
import org.appxi.javafx.visual.MaterialIcon;
import org.appxi.property.RawProperty;
import org.appxi.smartcn.convert.ChineseConvertors;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

class EntrySearcher extends LookupLayer<Dictionary.Entry> {
    final BaseApp app;
    String inputQuery, finalQuery;
    final RawProperty<Predicate<Dictionary>> source = new RawProperty<>();

    EntrySearcher(BaseApp app, Predicate<Dictionary> sourceFilter) {
        super(app.getPrimaryGlass());
        this.app = app;
        //
        sourceInfo.setGraphic(MaterialIcon.MANAGE_SEARCH.graphic());
        sourceInfo.setTooltip(new Tooltip("查词条范围设置"));
        sourceInfo.setOnAction(actionEvent -> DictionaryContext.openScopesDialog(app, DictionaryContext.DEF_SCOPES, source));

        //
        source.addListener((ov, nv) -> sourceInfo.setText("在 %d 词条中查:".formatted(getSearchScopes().sizeEntries())));
        source.set(sourceFilter);
        source.addListener((ov, nv) -> {
            String searchedText = getSearchedText();
            reset();
            search(searchedText);
        });
    }

    private Dictionaries getSearchScopes() {
        final Dictionaries dictionaries = Dictionaries.def.filtered(source.get());
        return dictionaries.size() > 0 ? dictionaries : Dictionaries.def;
    }

    @Override
    protected int getPaddingSizeOfParent() {
        return 200;
    }

    @Override
    protected String getHeaderText() {
        return "查词条";
    }

    @Override
    protected void helpButtonAction(ActionEvent actionEvent) {
        FxHelper.showTextViewerWindow(app, "dictionaryLookup.helpWindow", "查词功能使用说明",
                """
                        查词：
                        >> 当前支持自动简繁汉字和英文词！
                                                
                        >> 匹配规则（默认1）：
                        1）以词开始：输入 或 输入*；
                        2）以词结尾：*输入；
                        3）以词存在：*输入*；
                        4）以双引号包含精确查词："输入"；
                                                
                        >> 快捷键：Ctrl+D 开启；ESC 或 点击透明区 退出此界面；上/下方向键选择列表项；回车键打开；
                                                
                                                
                        查词范围：
                        规则：在设定默认查词范围时，所有【未选中】的词库均被记录，在新增更多词库后将排除这些未选中的词库，而新增的词库将被用于查询。
                        """);
    }

    @Override
    protected LookupResult<Dictionary.Entry> lookupByKeywords(String lookupText, int resultLimit) {
        inputQuery = lookupText;

        // detect
        final StringBuilder keywords = new StringBuilder(lookupText);
        final MatchType matchType = MatchType.detect(keywords);
        lookupText = keywords.toString();

        lookupText = lookupText.isBlank() ? "" : ChineseConvertors.toHans(lookupText);
        finalQuery = lookupText;

        final boolean finalQueryIsBlank = null == finalQuery || finalQuery.isBlank();
        List<Dictionary.Entry> result = new ArrayList<>(1024);

        final Dictionaries dictionaries = getSearchScopes();
        final long total = dictionaries.sizeEntries();

        if (finalQueryIsBlank) {
            final Iterator<Dictionary.Entry> searcher = dictionaries.shuffled().search(finalQuery, matchType);
            while (searcher.hasNext()) {
                result.add(searcher.next());
                if (result.size() >= resultLimit) {
                    break;
                }
            }
            return new LookupResult<>(total, total, result);
        }

        final Iterator<Dictionary.Entry> searcher = dictionaries.search(finalQuery, matchType);
        while (searcher.hasNext()) {
            result.add(searcher.next());
        }
        result.sort(Dictionary.Entry::compareTo);

        final long count = result.size();
        final LookupResult<Dictionary.Entry> lookupResult;
        if (count > resultLimit) {
            lookupResult = new LookupResult<>(total, count, new ArrayList<>(result.subList(0, resultLimit)));
            result.clear();
        } else {
            lookupResult = new LookupResult<>(total, count, result);
        }
        System.gc();
        return lookupResult;
    }

    protected void lookupByCommands(String searchTerm, Collection<Dictionary.Entry> result) {
    }

    @Override
    protected void updateItemLabel(Labeled labeled, Dictionary.Entry data) {
        //
        Label title = new Label(data.title());
        title.getStyleClass().addAll("primary", "plaintext");

        Label detail = new Label(data.dictionary.name, MaterialIcon.LOCATION_ON.graphic());
        detail.getStyleClass().add("secondary");

        HBox.setHgrow(title, Priority.ALWAYS);
        HBox pane = new HBox(5, title, detail);

        labeled.setText(title.getText());
        labeled.setGraphic(pane);
        labeled.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);

        if (!finalQuery.isBlank()) {
            FxHelper.highlight(title, Set.of(finalQuery));
        }
    }

    @Override
    protected void handleEnterOrDoubleClickActionOnSearchResultList(InputEvent event, Dictionary.Entry item) {
        app.eventBus.fireEvent(new EntryEvent(EntryEvent.SEARCH_EXACT, item.title(), item.dictionary.id));
    }
}

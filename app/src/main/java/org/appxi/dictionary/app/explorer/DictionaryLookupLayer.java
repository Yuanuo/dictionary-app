package org.appxi.dictionary.app.explorer;

import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.Tooltip;
import javafx.scene.input.InputEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.appxi.dictionary.Dictionaries;
import org.appxi.dictionary.Dictionary;
import org.appxi.dictionary.SearchType;
import org.appxi.dictionary.app.App;
import org.appxi.holder.RawHolder;
import org.appxi.javafx.control.LookupLayer;
import org.appxi.javafx.helper.FxHelper;
import org.appxi.javafx.visual.MaterialIcon;
import org.appxi.javafx.workbench.WorkbenchApp;
import org.appxi.prefs.UserPrefs;
import org.appxi.smartcn.convert.ChineseConvertors;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

class DictionaryLookupLayer extends LookupLayer<Dictionary.Entry> {
    final WorkbenchApp app;
    String inputQuery, finalQuery;
    private Predicate<Dictionary> dictionaryPredicate;

    DictionaryLookupLayer(WorkbenchApp app) {
        super(app.getPrimaryGlass());
        this.app = app;

        Button mgrButton = MaterialIcon.BUILD_CIRCLE.iconButton(e -> DictionaryContext.openPreferencesDialog(app));
        mgrButton.setTooltip(new Tooltip(App.NAME + "设置"));
        textInput.rightArea.getChildren().add(mgrButton);

        //
        this.setDictionaryPredicate(null);
    }

    private void setDictionaryPredicate(Predicate<Dictionary> dictionaryPredicate) {
        if (null == dictionaryPredicate) {
            List<String> excludedList = Stream.of(UserPrefs.prefs.getString("dictionary.defaultScopes", "").split("\\|\\|"))
                    .filter(s -> !s.isBlank())
                    .toList();
            dictionaryPredicate = dictionary -> excludedList.isEmpty() || !excludedList.contains(dictionary.name);
            sourceInfo.setGraphic(MaterialIcon.MANAGE_SEARCH.graphic());
            sourceInfo.setOnAction(actionEvent -> {
                RawHolder<Predicate<Dictionary>> currentPredicate = new RawHolder<>(this.dictionaryPredicate);
                DictionaryContext.openSearchScopesDialog(app, currentPredicate);
                if (this.dictionaryPredicate != currentPredicate.value) {
                    setDictionaryPredicate(currentPredicate.value);
                    String searchedText = getSearchedText();
                    reset();
                    search(searchedText);
                }
            });
        }
        this.dictionaryPredicate = dictionaryPredicate;

        Dictionaries dictionaries = Dictionaries.getDictionaries(dictionaryPredicate);
        if (dictionaries.totalDictionaries() == 0) {
            this.dictionaryPredicate = null;
            dictionaries = Dictionaries.getDictionaries();
        }
        sourceInfo.setText("在 %d 词条中查找:".formatted(dictionaries.totalEntries()));
    }

    @Override
    protected int getPaddingSizeOfParent() {
        return 200;
    }

    @Override
    protected String getHeaderText() {
        return "查词典";
    }

    @Override
    protected void helpButtonAction(ActionEvent actionEvent) {
        FxHelper.showTextViewerWindow(app, "dictionaryLookup.helpWindow", "查词使用方法",
                """
                        >> 当前支持自动简繁汉字和英文词！
                        >> 匹配规则：默认1）以词开始：输入 或 输入*；2）以词结尾：*输入；3）以词存在：*输入*；4）以双引号包含精确查词："输入"；
                        >> 快捷键：Ctrl+D 开启；ESC 或 点击透明区 退出此界面；上/下方向键选择列表项；回车键打开；
                        """);
    }

    @Override
    protected LookupResult<Dictionary.Entry> lookupByKeywords(String lookupText, int resultLimit) {
        inputQuery = lookupText;

        // detect
        final StringBuilder keywords = new StringBuilder(lookupText);
        final SearchType searchType = SearchType.detect(keywords);
        lookupText = keywords.toString();

        lookupText = lookupText.isBlank() ? "" : ChineseConvertors.toHans(lookupText);
        finalQuery = lookupText;

        final boolean finalQueryIsBlank = null == finalQuery || finalQuery.isBlank();
        List<Dictionary.Entry> result = new ArrayList<>(1024);

        final Dictionaries dictionaries = Dictionaries.getDictionaries(dictionaryPredicate);
        final long total = dictionaries.totalEntries();

        if (finalQueryIsBlank) {
            Collections.shuffle(dictionaries.list);
            final Iterator<Dictionary.Entry> searcher = dictionaries.search(finalQuery, searchType, null);
            while (searcher.hasNext()) {
                result.add(searcher.next());
                if (result.size() >= resultLimit) {
                    break;
                }
            }
            return new LookupResult<>(total, total, result);
        }

        final Iterator<Dictionary.Entry> searcher = dictionaries.search(finalQuery, searchType, null);
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
            lookupResult = new LookupResult(total, count, result);
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
        DictionaryContext.openViewer(app, item);
    }
}

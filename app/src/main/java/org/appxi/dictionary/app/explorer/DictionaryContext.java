package org.appxi.dictionary.app.explorer;

import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Window;
import org.appxi.dictionary.Dictionaries;
import org.appxi.dictionary.Dictionary;
import org.appxi.dictionary.SearchType;
import org.appxi.dictionary.app.App;
import org.appxi.holder.RawHolder;
import org.appxi.javafx.app.AppEvent;
import org.appxi.javafx.app.BaseApp;
import org.appxi.javafx.app.DesktopApp;
import org.appxi.javafx.app.search.DictionaryEvent;
import org.appxi.javafx.control.HBoxEx;
import org.appxi.javafx.control.OpaqueLayer;
import org.appxi.javafx.helper.FxHelper;
import org.appxi.javafx.workbench.WorkbenchApp;
import org.appxi.javafx.workbench.WorkbenchPane;
import org.appxi.prefs.UserPrefs;
import org.appxi.util.DigestHelper;
import org.appxi.util.FileHelper;
import org.appxi.util.OSVersions;
import org.appxi.util.StringHelper;
import org.appxi.util.ext.RawVal;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DictionaryContext {
    static Supplier<List<String>> webIncludesSupplier;
    static Function<String, String> htmlDocumentWrapper;

    public static void setupInitialize(WorkbenchApp app,
                                       Supplier<List<String>> webIncludesSupplier,
                                       Function<String, String> htmlDocumentWrapper) {
        DictionaryContext.webIncludesSupplier = webIncludesSupplier;
        DictionaryContext.htmlDocumentWrapper = htmlDocumentWrapper;
        //
        app.eventBus.addEventHandler(AppEvent.STARTED, event -> {
            DictionaryContext.rescanDictionaries(app);
        });
        //
        app.eventBus.addEventHandler(DictionaryEvent.SEARCH, event -> openSearcher(app, null != event.text ? event.text.strip() : null));
        //
        app.eventBus.addEventHandler(DictionaryEvent.SEARCH_EXACT, event -> {
            Dictionary dictionary = Dictionaries.getDictionary(event.dictionary);
            Iterator<Dictionary.Entry> iterator = dictionary.search(event.text, SearchType.TitleEquals, null);
            if (iterator.hasNext()) {
                openViewer(app, iterator.next());
            }
        });
    }

    public static List<Path> getManagedPaths() {
        final List<Path> list = new ArrayList<>();
        if (DesktopApp.productionMode) {
            list.add(DesktopApp.appDir().resolve("template/dict"));
        } else {
            final Path dictRepo;
            Path tmp = Path.of("../appxi-smart-dictionary/repo");
            if (FileHelper.notExists(tmp)) {
                tmp = Path.of("../../appxi-smart-dictionary/repo");
            }
            try {
                tmp = tmp.toFile().getCanonicalFile().toPath();
            } catch (Exception ignore) {
            }
            dictRepo = tmp;
            list.add(dictRepo);
        }
        list.add(Path.of(System.getProperty("user.home")).resolve(".smartDictionary/dict"));

        list.addAll(Stream.of(UserPrefs.prefs.getString("dictionary.paths", "").strip().split("\\|\\|"))
                .filter(s -> !s.isBlank())
                .map(Path::of)
                .filter(p -> !list.contains(p))
                .distinct()
                .toList());
        return list;
    }

    private static void rescanDictionaries(WorkbenchApp app) {
        Dictionaries.clear();
        Dictionaries.discover(getManagedPaths().toArray(new Path[0]));
        FxHelper.runThread(2000, () -> app.toast("已扫描并成功加载 " + Dictionaries.getDictionaries().totalDictionaries() + " 个词库！"));
    }

    public static void openPreferencesDialog(BaseApp app) {
        final VBox settings = new VBox(15, configSearcherTypes(app), configSearcherAll(app), configSourcePaths(app));

        final DialogPane dialogPane = new DialogPane() {
            @Override
            protected Node createButtonBar() {
                return null;
            }
        };
        if (OSVersions.isLinux) {
            dialogPane.setPrefSize(720, 540);
        }
        dialogPane.setContent(settings);
        dialogPane.getButtonTypes().addAll(ButtonType.OK);
        //
        Dialog<?> dialog = new Dialog<>();
        dialog.setTitle(App.NAME + "设置");
        dialog.setDialogPane(dialogPane);
        dialog.getDialogPane().setPrefWidth(800);
        dialog.setResizable(true);
        dialog.initOwner(app.getPrimaryStage());
        dialog.setOnShown(evt -> FxHelper.runThread(100, () -> {
            dialog.setHeight(600);
            dialogPane.setMinWidth(800);
            dialog.setY(dialog.getOwner().getY() + (dialog.getOwner().getHeight() - dialog.getHeight()) / 2);
            if (dialog.getX() < 0) dialog.setX(0);
            if (dialog.getY() < 0) dialog.setY(0);
        }));
        dialog.showAndWait();
    }

    private static Node configSearcherTypes(BaseApp app) {
        ChoiceBox<RawVal<String>> choiceBox = new ChoiceBox<>();
        choiceBox.getItems().addAll(
                RawVal.vk("embed", "主窗口标签页"),
                RawVal.vk("layer", "主窗口遮罩层"),
                RawVal.vk("popup", "新窗口")
        );
        final String defSearcherType = UserPrefs.prefs.getString("dictionary.searcherType", "embed");
        choiceBox.getSelectionModel().select(
                choiceBox.getItems().stream().filter(rv -> defSearcherType.equalsIgnoreCase(rv.value())).findFirst()
                        .orElse(choiceBox.getItems().get(0))
        );
        choiceBox.getSelectionModel().selectedItemProperty()
                .addListener((o, ov, nv) -> UserPrefs.prefs.setProperty("dictionary.searcherType", nv.value()));

        HBox contentBox = new HBox(5, new Label("默认查词位置：当根据选择文字查词时，在"), choiceBox, new Label("中打开。"));
        contentBox.setAlignment(Pos.BOTTOM_LEFT);
        contentBox.setPadding(new Insets(5));
        return contentBox;
    }

    private static Node configSearcherAll(BaseApp app) {
        ChoiceBox<RawVal<String>> choiceBox = new ChoiceBox<>();
        choiceBox.getItems().addAll(
                RawVal.vk("true", "全部词典"),
                RawVal.vk("false", "单一词典")
        );
        final String defSearchAll = UserPrefs.prefs.getString("dictionary.viewerLoadAll", "false");
        choiceBox.getSelectionModel().select(
                choiceBox.getItems().stream().filter(rv -> defSearchAll.equalsIgnoreCase(rv.value())).findFirst()
                        .orElse(choiceBox.getItems().get(1))
        );
        choiceBox.getSelectionModel().selectedItemProperty()
                .addListener((o, ov, nv) -> UserPrefs.prefs.setProperty("dictionary.viewerLoadAll", nv.value()));

        HBox contentBox = new HBox(5, new Label("打开词条查看器时，默认显示的词条内容来自"), choiceBox);
        contentBox.setAlignment(Pos.BOTTOM_LEFT);
        contentBox.setPadding(new Insets(5));
        return contentBox;
    }

    private static Node configSourcePaths(BaseApp app) {
        Label label = new Label("词典库列表，支持自动从文件夹扫描，或指定实际的词典库文件！");

        ListView<Path> listView = new ListView<>();
        VBox.setVgrow(listView, Priority.ALWAYS);
        listView.setMinHeight(200);
        listView.setStyle("-fx-border-width: 1px; -fx-border-color: gray;");
        listView.getItems().setAll(getManagedPaths());
        listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        listView.getItems().addListener((ListChangeListener<? super Path>) c -> {
            List<Path> paths = listView.getItems();
            UserPrefs.prefs.setProperty("dictionary.paths", paths.stream()
                    .filter(path -> paths.indexOf(path) > 1)
                    .map(Path::toString)
                    .collect(Collectors.joining("||")));
        });
        //
        Button addFolder = new Button("添加文件夹");
        addFolder.setOnAction(actionEvent -> {
            DirectoryChooser chooser = new DirectoryChooser();
            File selectedFile = chooser.showDialog(app.getPrimaryStage());
            if (null == selectedFile) {
                return;
            }
            Path selectedPath = selectedFile.toPath();
            List<Path> paths = listView.getItems();
            if (!paths.contains(selectedPath)) {
                paths.add(selectedPath);
            }
        });
        //
        Button addFile = new Button("添加文件（*.tomed）");
        addFile.setOnAction(actionEvent -> {
            FileChooser chooser = new FileChooser();
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(app.getAppName() + "词库", "*.tomed"));
            List<File> selectedFiles = chooser.showOpenMultipleDialog(app.getPrimaryStage());
            if (null == selectedFiles || selectedFiles.isEmpty()) {
                return;
            }
            List<Path> paths = listView.getItems();
            paths.addAll(selectedFiles.stream()
                    .map(File::toPath)
                    .filter(path -> !paths.contains(path))
                    .toList());
        });
        //
        Button del = new Button("移除所选");
        del.setOnAction(actionEvent -> listView.getItems().removeAll(listView.getSelectionModel().getSelectedItems()));
        del.disableProperty().bind(Bindings.createBooleanBinding(() -> {
            List<Integer> indices = listView.getSelectionModel().getSelectedIndices();
            return indices.isEmpty() || indices.contains(0) || indices.contains(1);
        }, listView.getSelectionModel().getSelectedIndices()));
        //
        Button addOthers = new Button("导入三方词库");
        addOthers.setOnAction(actionEvent -> {
            FileChooser chooser = new FileChooser();
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("MDX", "*.mdx"));
        });
        //
        HBoxEx toolbar = new HBoxEx();
        toolbar.setSpacing(5);
        toolbar.addLeft(label);
        toolbar.addRight(addFolder, addFile, del);
        //
        VBox contentBox = new VBox(15, toolbar, listView);
        contentBox.setPadding(new Insets(5));
        VBox.setVgrow(contentBox, Priority.ALWAYS);
        return contentBox;
    }

    public static void openSearchScopesDialog(BaseApp app, RawHolder<Predicate<Dictionary>> currentPredicate) {
        final TabPane settings = new TabPane();
        configSearchScopes(settings, currentPredicate);

        final DialogPane dialogPane = new DialogPane() {
            @Override
            protected Node createButtonBar() {
                return null;
            }
        };
        if (OSVersions.isLinux) {
            dialogPane.setPrefSize(540, 720);
        }
        dialogPane.setContent(settings);
        dialogPane.getButtonTypes().addAll(ButtonType.OK);
        //
        Dialog<?> dialog = new Dialog<>();
        dialog.setTitle("设定搜索范围");
        dialog.setDialogPane(dialogPane);
        dialog.getDialogPane().setPrefWidth(600);
        dialog.setResizable(true);
        dialog.initOwner(app.getPrimaryStage());
        dialog.setOnShown(evt -> FxHelper.runThread(100, () -> {
            dialog.setHeight(800);
            dialog.setY(dialog.getOwner().getY() + (dialog.getOwner().getHeight() - dialog.getHeight()) / 2);
            if (dialog.getX() < 0) dialog.setX(0);
            if (dialog.getY() < 0) dialog.setY(0);
        }));
        dialog.showAndWait();
    }

    private static void configSearchScopes(TabPane tabPane, RawHolder<Predicate<Dictionary>> currentPredicate) {
        ListView<RawVal<SimpleBooleanProperty>> listView = new ListView<>();
        VBox.setVgrow(listView, Priority.ALWAYS);
        listView.setMinHeight(200);
        listView.setStyle("-fx-border-width: 1px; -fx-border-color: gray;");
        listView.getItems().setAll(
                Dictionaries.getDictionaries().list.stream()
                        .map(v -> RawVal.kv(v.name, new SimpleBooleanProperty(currentPredicate.value == null || currentPredicate.value.test(v))))
                        .toList()
        );
        listView.setCellFactory(stringListView -> new CheckBoxListCell<>(RawVal::value));
        //
        final Consumer<String> applyAction = saveKey -> {
            List<String> excludedList = new ArrayList<>(
                    listView.getItems().stream()
                            .filter(rv -> !rv.value().get())
                            .map(RawVal::key)
                            .toList()
            );
            // 如果是全选则使用空列表规则
            if (excludedList.size() == listView.getItems().size()) {
                excludedList.clear();
            }
            currentPredicate.value = d -> excludedList.isEmpty() || !excludedList.contains(d.name);
            //
            if (null != saveKey) {
                UserPrefs.prefs.setProperty(saveKey, String.join("||", excludedList));
            }
            //
            tabPane.getScene().getWindow().hide();
        };
        //
        Button applyCurrent = new Button("应用");
        applyCurrent.setTooltip(new Tooltip("仅应用到当前搜索范围"));
        applyCurrent.setOnAction(actionEvent -> applyAction.accept(null));
        //
        Button applyDefault = new Button("应用并设为默认");
        applyDefault.setTooltip(new Tooltip("应用到当前搜索范围，并设为新开查词的默认搜索范围"));
        applyDefault.setOnAction(actionEvent -> applyAction.accept("dictionary.defaultScopes"));
        //
        Button cancelButton = new Button("取消");
        cancelButton.setTooltip(new Tooltip("不修改当前搜索范围"));
        cancelButton.setOnAction(actionEvent -> tabPane.getScene().getWindow().hide());

        //
        Button selAll = new Button("全选");
        selAll.setOnAction(actionEvent -> listView.getItems().forEach(rv -> rv.value().set(true)));
        //
        Button selNone = new Button("全不选");
        selNone.setOnAction(actionEvent -> listView.getItems().forEach(rv -> rv.value().set(false)));
        //
        Button selInvert = new Button("反选");
        selInvert.setOnAction(actionEvent -> listView.getItems().forEach(rv -> rv.value().set(!rv.value().get())));
        //

        HBoxEx toolbar = new HBoxEx();
        toolbar.setSpacing(5);
        toolbar.addLeft(selAll, selNone, selInvert);
        toolbar.addRight(applyCurrent, applyDefault, cancelButton);

        Tab tab = new Tab("当前搜索范围", new VBox(5, listView, toolbar));
        tab.setClosable(false);
        tabPane.getTabs().add(tab);
    }

    public static void openSearcherInEmbed(WorkbenchApp app, String text) {
        // 有从外部打开的全文搜索，此时需要隐藏透明层
        OpaqueLayer.hideOpaqueLayer(app.getPrimaryGlass());

        final WorkbenchPane workbench = app.workbench();

        // 优先查找可用的搜索视图，以避免打开太多未使用的搜索视图
        DictionaryEmbedSearcher searcher = workbench.mainViews.getTabs().stream()
                .map(tab -> (tab.getUserData() instanceof DictionaryEmbedSearcher view && view.isNeverSearched()) ? view : null)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseGet(() -> new DictionaryEmbedSearcher("SEARCHER-".concat(DigestHelper.uid()), workbench));
        FxHelper.runLater(() -> {
            if (!workbench.existsMainView(searcher.id.get())) {
                workbench.addWorkbenchPartAsMainView(searcher, false);
            }
            workbench.selectMainView(searcher.id.get());
            searcher.search(text);
        });
    }

    public static void openSearcherInLayer(WorkbenchApp app, String text) {
        // 隐藏透明层
        OpaqueLayer.hideOpaqueLayer(app.getPrimaryGlass());

        final WorkbenchPane workbench = app.workbench();

        DictionaryLookupLayer lookupLayer = (DictionaryLookupLayer) workbench.getProperties().get(DictionaryLookupLayer.class);
        if (null == lookupLayer) {
            workbench.getProperties().put(DictionaryLookupLayer.class, lookupLayer = new DictionaryLookupLayer(app));
        }
        lookupLayer.show(text != null ? text : lookupLayer.inputQuery);
    }

    public static void openSearcherInPopup(WorkbenchApp app, String text) {
        final Dialog<?> dialog = new Dialog<>();
        final DialogPane dialogPane = new DialogPane() {
            @Override
            protected Node createButtonBar() {
                return null;
            }
        };

        final DictionaryLookupLayer lookupPane = new DictionaryLookupLayer(app);
        lookupPane.setPadding(new Insets(10));
        lookupPane.setSpacing(10);
        lookupPane.textInput.input.textProperty().addListener((o, ov, nv) -> {
            String title = "查词";
            if (null != nv)
                title = title + "：" + (nv.isBlank() ? "*" : StringHelper.trimChars(nv, 16));
            dialog.setTitle(title);
        });
        //
        dialogPane.setContent(lookupPane);
        dialogPane.getButtonTypes().add(ButtonType.OK);
        //
        dialog.setDialogPane(dialogPane);
        dialog.getDialogPane().setPrefWidth(800);
        dialog.setResizable(true);
        dialog.initModality(Modality.NONE);
        dialog.initOwner(app.getPrimaryStage());
        dialog.setOnShown(evt -> FxHelper.runThread(30, () -> {
            dialog.setHeight(600);
            dialog.setY(dialog.getOwner().getY() + (dialog.getOwner().getHeight() - dialog.getHeight()) / 2);
            if (dialog.getX() < 0) dialog.setX(0);
            if (dialog.getY() < 0) dialog.setY(0);
            //
            lookupPane.search(text);
            FxHelper.runThread(50, lookupPane.textInput.input::requestFocus);
        }));
        dialog.show();
    }

    public static void openSearcher(WorkbenchApp app, String text) {
        String searcherType = UserPrefs.prefs.getString("dictionary.searcherType", "embed");
        if ("layer".equalsIgnoreCase(searcherType)) {
            openSearcherInLayer(app, text);
        } else if ("popup".equalsIgnoreCase(searcherType)) {
            openSearcherInPopup(app, text);
        } else {
            openSearcherInEmbed(app, text);
        }
    }

    public static void openViewer(WorkbenchApp app, Dictionary.Entry entry) {
        final String windowId = entry.dictionary.id + " /" + entry.id;

        //
        Window window = Window.getWindows().stream().filter(w -> windowId.equals(w.getScene().getUserData())).findFirst().orElse(null);
        if (null != window) {
            window.requestFocus();
            return;
        }
        //
        final String windowTitle = entry.title() + " -- " + entry.dictionary.name + "  -  " + app.getAppName();
        FxHelper.showHtmlViewerWindow(app, windowId, windowTitle, dialog -> new DictionaryViewer(app.workbench(), entry) {
            @Override
            void onSearchAllDictionaries(ActionEvent event) {
                dialog.setTitle(entry.title() + " -- 全部词典  -  " + app.getAppName());
                super.onSearchAllDictionaries(event);
            }
        });
    }
}

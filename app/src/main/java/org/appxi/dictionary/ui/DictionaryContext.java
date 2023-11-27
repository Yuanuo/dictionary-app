package org.appxi.dictionary.ui;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Window;
import org.appxi.dictionary.Dictionaries;
import org.appxi.dictionary.Dictionary;
import org.appxi.dictionary.DictionaryBuilder;
import org.appxi.dictionary.MatchType;
import org.appxi.dictionary.app.App;
import org.appxi.dictionary.io.DictFileMdx;
import org.appxi.dictionary.io.DictFileTxt;
import org.appxi.javafx.app.AppEvent;
import org.appxi.javafx.app.BaseApp;
import org.appxi.javafx.app.DesktopApp;
import org.appxi.javafx.control.HBoxEx;
import org.appxi.javafx.control.OpaqueLayer;
import org.appxi.javafx.helper.FxHelper;
import org.appxi.javafx.settings.DefaultOption;
import org.appxi.javafx.settings.DefaultOptions;
import org.appxi.javafx.settings.Option;
import org.appxi.javafx.settings.OptionEditorBase;
import org.appxi.javafx.settings.SettingsList;
import org.appxi.javafx.workbench.WorkbenchApp;
import org.appxi.javafx.workbench.WorkbenchPane;
import org.appxi.prefs.UserPrefs;
import org.appxi.property.RawProperty;
import org.appxi.util.DigestHelper;
import org.appxi.util.FileHelper;
import org.appxi.util.OSVersions;
import org.appxi.util.StringHelper;
import org.appxi.util.ext.Compression;
import org.appxi.util.ext.RawVal;
import org.appxi.util.ext.RawVal2;

import java.io.File;
import java.nio.charset.StandardCharsets;
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
            DictionaryContext.reloadDictionaries(app);
        });
        //
        app.eventBus.addEventHandler(DictionaryEvent.SEARCH, event -> openSearcher(app, null != event.text ? event.text.strip() : null));
        //
        app.eventBus.addEventHandler(DictionaryEvent.SEARCH_EXACT, event -> {
            Dictionary dictionary = Dictionaries.find(event.dictionary);
            Iterator<Dictionary.Entry> iterator = dictionary.search(event.text, MatchType.TitleEquals, null);
            if (iterator.hasNext()) {
                openViewer(app, iterator.next());
            }
        });
        //
        SettingsList.add(DictionaryContext::optionForSelectionEvent);
        SettingsList.add(DictionaryContext::optionForSearcherPlaces);
        SettingsList.add(DictionaryContext::optionForViewerLoadAll);
        SettingsList.add(() -> optionForSourcePaths(app));
    }

    public static Path getDefaultRepo() {
        return Path.of(System.getProperty("user.home")).resolve("." + App.ID + "/dict");
    }

    public static List<Path> getManagedPaths() {
        final List<Path> list = new ArrayList<>();
        if (DesktopApp.productionMode) {
            list.add(DesktopApp.appDir().resolve("dict"));
        } else {
            final Path dictRepo;
            Path tmp = Path.of("../appxi-dictionary.dd");
            if (FileHelper.notExists(tmp)) {
                tmp = Path.of("../../appxi-dictionary.dd");
            }
            try {
                tmp = tmp.toFile().getCanonicalFile().toPath();
            } catch (Exception ignore) {
            }
            dictRepo = tmp;
            list.add(dictRepo);
        }
        list.add(getDefaultRepo());

        list.addAll(Stream.of(UserPrefs.prefs.getString("dictionary.paths", "").strip().split("\\|\\|"))
                .filter(s -> !s.isBlank())
                .map(Path::of)
                .filter(p -> !list.contains(p))
                .distinct()
                .toList());
        return list;
    }

    private static void reloadDictionaries(WorkbenchApp app) {
        Dictionaries.def.clear();
        Dictionaries.def.add(getManagedPaths().toArray(new Path[0]));
        FxHelper.runThread(2000, () -> app.toast("已发现并成功加载 " + Dictionaries.def.size() + " 个词库！"));
    }

    public static Predicate<Dictionary> getDefaultScopesFilter() {
        List<String> excludedList = Stream.of(UserPrefs.prefs.getString("dictionary.defaultScopes", "").split("\\|\\|"))
                .filter(s -> !s.isBlank())
                .toList();
        return dictionary -> excludedList.isEmpty() || !excludedList.contains(dictionary.name);
    }

    private static Option<RawVal<String>> optionForSelectionEvent() {
        final List<RawVal<String>> values = List.of(
                RawVal.vk("selection&shortcut1", "仅在按下 Ctrl或Cmd 并选中文字后，立即查词"),
                RawVal.vk("selection&shortcut0", "仅在选中文字后，立即查词（同时按下Ctrl或Cmd将失效）"),
                RawVal.vk("none", "禁用选字查词功能")
        );
        final ObjectProperty<RawVal<String>> valueProperty = new SimpleObjectProperty<>(
                values.stream()
                        .filter(rv -> rv.value().equals(UserPrefs.prefs.getString("dictionary.bySelection", "selection&shortcut1")))
                        .findFirst().orElse(values.get(0))
        );
        valueProperty.addListener((o, ov, nv) -> {
            if (null == ov || Objects.equals(ov, nv)) return;
            UserPrefs.prefs.setProperty("dictionary.bySelection", nv.value());
        });
        return new DefaultOptions<RawVal<String>>("选字查词", "若同时按下Alt或Shift键时将失效", "查词", true)
                .setValues(values)
                .setValueProperty(valueProperty);
    }

    private static Option<RawVal<String>> optionForSearcherPlaces() {
        final List<RawVal<String>> values = List.of(
                RawVal.vk("popup", "新窗口"),
                RawVal.vk("embed", "主窗口标签页"),
                RawVal.vk("layer", "主窗口遮罩层")
        );
        final ObjectProperty<RawVal<String>> valueProperty = new SimpleObjectProperty<>(
                values.stream()
                        .filter(rv -> rv.value().equals(UserPrefs.prefs.getString("dictionary.searcherPlace", "popup")))
                        .findFirst().orElse(values.get(0))
        );
        valueProperty.addListener((o, ov, nv) -> {
            if (null == ov || Objects.equals(ov, nv)) return;
            UserPrefs.prefs.setProperty("dictionary.searcherPlace", nv.value());
        });
        return new DefaultOptions<RawVal<String>>("查词界面位置", "显示查词输入界面位置", "查词", true)
                .setValues(values)
                .setValueProperty(valueProperty);
    }

    private static Option<RawVal<String>> optionForViewerLoadAll() {
        final List<RawVal<String>> values = List.of(
                RawVal.vk("true", "全部词典"),
                RawVal.vk("false", "单一词典")
        );
        final ObjectProperty<RawVal<String>> valueProperty = new SimpleObjectProperty<>(
                values.stream()
                        .filter(rv -> rv.value().equals(UserPrefs.prefs.getString("dictionary.viewerLoadAll", "false")))
                        .findFirst().orElse(values.get(1))
        );
        valueProperty.addListener((o, ov, nv) -> {
            if (null == ov || Objects.equals(ov, nv)) return;
            UserPrefs.prefs.setProperty("dictionary.viewerLoadAll", nv.value());
        });
        return new DefaultOptions<RawVal<String>>("词条内容来源", "打开词条查看器时，默认显示的词条内容来自", "查词", true)
                .setValues(values)
                .setValueProperty(valueProperty);
    }

    private static Option<String> optionForSourcePaths(WorkbenchApp app) {
        return new DefaultOption<String>("词库列表", null, "查词", true,
                option -> new OptionEditorBase<>(option, new Button()) {
                    private ObjectProperty<String> valueProperty;

                    @Override
                    public Property<String> valueProperty() {
                        if (this.valueProperty == null) {
                            this.valueProperty = new SimpleObjectProperty<>();
                            getEditor().setText("管理");
                            getEditor().setOnAction(evt -> openSearchSourcesDialog(app));
                        }
                        return this.valueProperty;
                    }

                    @Override
                    public void setValue(String value) {
                    }
                })
                .setValue("");
    }

    private static void openSearchSourcesDialog(BaseApp app) {
        final VBox settings = new VBox(3, configSearchSources(app));

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
        dialog.setTitle("词库管理");
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

    private static Node configSearchSources(BaseApp app) {
        Label label = new Label("词库列表。使用指定的词库文件或从目录（及子级）中扫描。");

        ListView<Path> listView = new ListView<>();
        VBox.setVgrow(listView, Priority.ALWAYS);
        listView.setMinHeight(200);
        listView.setStyle("-fx-border-width: 1px; -fx-border-color: gray;");
        listView.getItems().setAll(getManagedPaths());
        listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        listView.getItems().addListener((ListChangeListener<? super Path>) c -> {
            if (c.wasAdded()) {
                Dictionaries.def.add(c.getAddedSubList().toArray(new Path[0]));
            }
            if (c.wasRemoved()) {
                Dictionaries.def.remove(c.getRemoved().toArray(new Path[0]));
            }

            List<Path> paths = listView.getItems();
            UserPrefs.prefs.setProperty("dictionary.paths", paths.stream()
                    .filter(path -> paths.indexOf(path) > 1)
                    .map(Path::toString)
                    .collect(Collectors.joining("||")));
        });
        //
        Button addFolder = new Button("添加目录");
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
        Button addFile = new Button("添加文件（*" + Dictionary.FILE_SUFFIX + "）");
        addFile.setOnAction(actionEvent -> {
            FileChooser chooser = new FileChooser();
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(app.getAppName() + "词库", "*" + Dictionary.FILE_SUFFIX));
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
        del.setOnAction(actionEvent -> {
            listView.getItems().removeAll(listView.getSelectionModel().getSelectedItems());
        });
        del.disableProperty().bind(Bindings.createBooleanBinding(() -> {
            List<Integer> indices = listView.getSelectionModel().getSelectedIndices();
            return indices.isEmpty() || indices.contains(0) || indices.contains(1);
        }, listView.getSelectionModel().getSelectedIndices()));
        //
        Button addTxt = new Button("导入词库（*.txt）");
        addTxt.setOnAction(actionEvent -> {
            FileChooser chooser = new FileChooser();
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Lines（【WORD】TEXT）", "*.txt"));
            List<File> selectedFiles = chooser.showOpenMultipleDialog(app.getPrimaryStage());
            if (null == selectedFiles || selectedFiles.isEmpty()) {
                return;
            }
            List<File> importedFiles = new ArrayList<>();

            final Path saveDir = getDefaultRepo();
            for (File selectedFile : selectedFiles) {
                org.appxi.util.ext.Node<Dictionary.Entry> dictTree = DictFileTxt.read(selectedFile);
                String dictName = dictTree.value.title();

                try {
                    final File dictFile = DictionaryBuilder.build(dictTree, StandardCharsets.UTF_8, Compression.zip, "", dictName, saveDir);
                    Dictionaries.def.add(dictFile.toPath());
                    importedFiles.add(dictFile);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (app instanceof DesktopApp desktopApp) {
                desktopApp.toast("导入了" + importedFiles.size() + "个Txt词库文件到目录：" + saveDir);
            }
        });
        //
        Button addMdx = new Button("导入词库（*.mdx）");
        addMdx.setOnAction(actionEvent -> {
            FileChooser chooser = new FileChooser();
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("MDict file", "*.mdx"));
            List<File> selectedFiles = chooser.showOpenMultipleDialog(app.getPrimaryStage());
            if (null == selectedFiles || selectedFiles.isEmpty()) {
                return;
            }
            List<File> importedFiles = new ArrayList<>();

            final Path saveDir = getDefaultRepo();
            for (File selectedFile : selectedFiles) {
                org.appxi.util.ext.Node<Dictionary.Entry> dictTree = DictFileMdx.read(selectedFile);
                String dictName = dictTree.value.title();

                try {
                    final File dictFile = DictionaryBuilder.build(dictTree, StandardCharsets.UTF_8, Compression.zip, "", dictName, saveDir);
                    Dictionaries.def.add(dictFile.toPath());
                    importedFiles.add(dictFile);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (app instanceof DesktopApp desktopApp) {
                desktopApp.toast("导入了" + importedFiles.size() + "个Mdx词库文件到目录：" + saveDir);
            }
        });
        //
        Button mdx2Txt = new Button("转换（mdx->txt）");
        mdx2Txt.setOnAction(actionEvent -> {
            FileChooser chooser = new FileChooser();
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("MDict file", "*.mdx"));
            List<File> selectedFiles = chooser.showOpenMultipleDialog(app.getPrimaryStage());
            if (null == selectedFiles || selectedFiles.isEmpty()) {
                return;
            }
            List<File> importedFiles = new ArrayList<>();

            for (File selectedFile : selectedFiles) {
                org.appxi.util.ext.Node<Dictionary.Entry> dictTree = DictFileMdx.read(selectedFile);
                String dictName = dictTree.value.title();

                try {
                    DictFileTxt.save(selectedFile.getParentFile().toPath(), dictName, dictTree);
                    importedFiles.add(selectedFile);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (app instanceof DesktopApp desktopApp) {
                desktopApp.toast("转换了" + importedFiles.size() + "个Mdx词库文件到原目录。");
            }
        });
        //
        HBoxEx toolbar = new HBoxEx();
        toolbar.setSpacing(5);
        toolbar.addLeft(addFolder, addFile, addTxt, addMdx, mdx2Txt, del);
        //
        VBox contentBox = new VBox(5, label, toolbar, listView);
        contentBox.setPadding(new Insets(5));
        VBox.setVgrow(contentBox, Priority.ALWAYS);
        return contentBox;
    }

    public static void openSearchScopesDialog(BaseApp app, RawProperty<Predicate<Dictionary>> filterProperty) {
        final TabPane settings = new TabPane();

        configCurrentScopes(settings, filterProperty);
        configDefaultScopes(settings, filterProperty);

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
        dialog.setTitle("查词范围设置");
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

    private static void configCurrentScopes(TabPane tabPane, RawProperty<Predicate<Dictionary>> filterProperty) {
        final Predicate<Dictionary> defaultScopesFilter = getDefaultScopesFilter();

        ListView<RawVal2<Dictionary, SimpleBooleanProperty>> listView = new ListView<>();
        VBox.setVgrow(listView, Priority.ALWAYS);
        listView.setMinHeight(200);
        listView.setStyle("-fx-border-width: 1px; -fx-border-color: gray;");
        listView.getItems().setAll(
                Dictionaries.def.list().stream()
                        .map(v -> RawVal2.kv(v, new SimpleBooleanProperty(filterProperty.get() == null || filterProperty.get().test(v))))
                        .toList()
        );
        listView.setCellFactory(stringListView -> new CheckBoxListCell<>(RawVal2::value));
        //
        final Consumer<Boolean> applyAction = updateDefault -> {
            List<String> excludedList = new ArrayList<>(
                    listView.getItems().stream()
                            .filter(rv -> !rv.value().get())
                            .map(rv -> rv.key().name)
                            .toList()
            );
            // 如果是全选则使用空列表规则
            if (excludedList.size() == listView.getItems().size()) {
                excludedList.clear();
            }
            if (updateDefault) {
                UserPrefs.prefs.setProperty("dictionary.defaultScopes", String.join("||", excludedList));
            }
            //
            filterProperty.set(d -> excludedList.isEmpty() || !excludedList.contains(d.name));
            //
            tabPane.getScene().getWindow().hide();
        };
        //
        Button applyCurrent = new Button("应用");
        applyCurrent.setTooltip(new Tooltip("仅应用到当前查词范围"));
        applyCurrent.setOnAction(actionEvent -> applyAction.accept(false));
        //
        Button applyDefault = new Button("应用&设为默认");
        applyDefault.setTooltip(new Tooltip("应用到当前查词范围，并设为新开查词的默认查词范围"));
        applyDefault.setOnAction(actionEvent -> applyAction.accept(true));
        //
        Button cancelButton = new Button("取消");
        cancelButton.setTooltip(new Tooltip("不修改当前查词范围"));
        cancelButton.setOnAction(actionEvent -> tabPane.getScene().getWindow().hide());

        //
        Button selAll = new Button("全选");
        selAll.setOnAction(actionEvent -> listView.getItems().forEach(rv -> rv.value().set(true)));
        //
        Button selNone = new Button("清空");
        selNone.setOnAction(actionEvent -> listView.getItems().forEach(rv -> rv.value().set(false)));
        //
        Button selInvert = new Button("反选");
        selInvert.setOnAction(actionEvent -> listView.getItems().forEach(rv -> rv.value().set(!rv.value().get())));
        //
        Button selDef = new Button("默认");
        selDef.setTooltip(new Tooltip("重置为默认查词范围"));
        selDef.setOnAction(actionEvent -> listView.getItems().forEach(rv -> rv.value().set(defaultScopesFilter.test(rv.key()))));
        //

        HBoxEx toolbar = new HBoxEx();
        toolbar.setSpacing(5);
        toolbar.addLeft(selAll, selNone, selInvert, selDef);
        toolbar.addRight(applyCurrent, applyDefault, cancelButton);

        Tab tab = new Tab("当前查词范围", new VBox(5, listView, toolbar));
        tab.setClosable(false);
        tabPane.getTabs().add(tab);
    }

    private static void configDefaultScopes(TabPane tabPane, RawProperty<Predicate<Dictionary>> filterProperty) {
        final Predicate<Dictionary> defaultScopesFilter = getDefaultScopesFilter();

        ListView<RawVal2<Dictionary, SimpleBooleanProperty>> listView = new ListView<>();
        VBox.setVgrow(listView, Priority.ALWAYS);
        listView.setMinHeight(200);
        listView.setStyle("-fx-border-width: 1px; -fx-border-color: gray;");
        listView.getItems().setAll(
                Dictionaries.def.list().stream()
                        .map(v -> RawVal2.kv(v, new SimpleBooleanProperty(defaultScopesFilter.test(v))))
                        .toList()
        );
        listView.setCellFactory(stringListView -> new CheckBoxListCell<>(RawVal2::value));
        //
        final Consumer<Boolean> applyAction = updateCurrent -> {
            List<String> excludedList = new ArrayList<>(
                    listView.getItems().stream()
                            .filter(rv -> !rv.value().get())
                            .map(rv -> rv.key().name)
                            .toList()
            );
            // 如果是全选则使用空列表规则
            if (excludedList.size() == listView.getItems().size()) {
                excludedList.clear();
            }
            UserPrefs.prefs.setProperty("dictionary.defaultScopes", String.join("||", excludedList));
            //
            if (updateCurrent) {
                filterProperty.set(d -> excludedList.isEmpty() || !excludedList.contains(d.name));
            }
            //
            tabPane.getScene().getWindow().hide();
        };
        //
        Button applyDefault = new Button("保存");
        applyDefault.setTooltip(new Tooltip("保存为默认查词范围，在新开查词中生效"));
        applyDefault.setOnAction(actionEvent -> applyAction.accept(false));
        //
        Button applyCurrent = new Button("保存&设为当前");
        applyCurrent.setTooltip(new Tooltip("保存为默认查词范围，并设为当前查词范围"));
        applyCurrent.setOnAction(actionEvent -> applyAction.accept(true));
        //
        Button cancelButton = new Button("取消");
        cancelButton.setTooltip(new Tooltip("不修改查词范围"));
        cancelButton.setOnAction(actionEvent -> tabPane.getScene().getWindow().hide());

        //
        Button selAll = new Button("全选");
        selAll.setOnAction(actionEvent -> listView.getItems().forEach(rv -> rv.value().set(true)));
        //
        Button selNone = new Button("清空");
        selNone.setOnAction(actionEvent -> listView.getItems().forEach(rv -> rv.value().set(false)));
        //
        Button selInvert = new Button("反选");
        selInvert.setOnAction(actionEvent -> listView.getItems().forEach(rv -> rv.value().set(!rv.value().get())));
        //
        Button selDef = new Button("默认");
        selDef.setTooltip(new Tooltip("重置为默认查词范围"));
        selDef.setOnAction(actionEvent -> listView.getItems().forEach(rv -> rv.value().set(defaultScopesFilter.test(rv.key()))));
        //

        HBoxEx toolbar = new HBoxEx();
        toolbar.setSpacing(5);
        toolbar.addLeft(selAll, selNone, selInvert, selDef);
        toolbar.addRight(applyDefault, applyCurrent, cancelButton);

        Tab tab = new Tab("默认查词范围", new VBox(5, listView, toolbar));
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
        String searcherPlace = UserPrefs.prefs.getString("dictionary.searcherPlace", "popup");
        if ("layer".equalsIgnoreCase(searcherPlace)) {
            openSearcherInLayer(app, text);
        } else if ("popup".equalsIgnoreCase(searcherPlace)) {
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

package org.appxi.dictionary.ui;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import org.appxi.dictionary.Dictionaries;
import org.appxi.dictionary.Dictionary;
import org.appxi.dictionary.DictionaryBuilder;
import org.appxi.dictionary.app.App;
import org.appxi.dictionary.io.DictFileMdx;
import org.appxi.dictionary.io.DictFileTxt;
import org.appxi.javafx.app.AppEvent;
import org.appxi.javafx.app.BaseApp;
import org.appxi.javafx.control.HBoxEx;
import org.appxi.javafx.helper.FxHelper;
import org.appxi.javafx.settings.DefaultOption;
import org.appxi.javafx.settings.DefaultOptions;
import org.appxi.javafx.settings.Option;
import org.appxi.javafx.settings.OptionEditorBase;
import org.appxi.property.RawProperty;
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
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DictionaryContext {
    public static final Path SHARED_PATH = Path.of(System.getProperty("user.home")).resolve("." + App.ID + "/dict");
    static final String DEF_SCOPES = "dictionary.defaultScopes";
    static final String DEF_SCOPES2 = "dictionary.defaultScopes2";

    public static void setupDirectories(BaseApp app) {
        app.eventBus.addEventHandler(AppEvent.STARTED, event -> {
            Dictionaries.def.clear();
            Dictionaries.def.add(getDataPaths(app).toArray(new Path[0]));
            FxHelper.runThread(2000, () -> app.toast("已发现并成功加载 " + Dictionaries.def.size() + " 个词库！"));
        });
    }

    public static void setupApplication(BaseApp app) {
        app.eventBus.addEventHandler(EntryEvent.SEARCH, event -> {
            final String text = null != event.text ? event.text.strip() : null;

            final List<String> dictList = Stream.of(app.config.getString(DEF_SCOPES, "").split("\\|\\|"))
                    .filter(s -> !s.isBlank())
                    .toList();
            final Predicate<Dictionary> sourceFilter = dictionary -> dictList.isEmpty() || !dictList.contains(dictionary.name);

            final Dialog<?> dialog = new Dialog<>();
            final DialogPane dialogPane = new DialogPane() {
                @Override
                protected Node createButtonBar() {
                    return null;
                }
            };

            final EntrySearcher lookupPane = new EntrySearcher(app, sourceFilter);
            lookupPane.setPadding(new Insets(10));
            lookupPane.setSpacing(10);
            lookupPane.textInput.input.textProperty().addListener((o, ov, nv) -> {
                String title = "查词条";
                if (null != nv)
                    title = title + "：" + (nv.isBlank() ? "*" : StringHelper.trimChars(nv, 16));
                dialog.setTitle(title + "  -  " + app.getAppName());
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
        });
        //
        app.eventBus.addEventHandler(EntryEvent.SEARCH_EXACT, event -> {
            String eventDict = event.dictionary;
            if (null == eventDict) {
                eventDict = app.config.getString(DEF_SCOPES2, "");
            }
            final List<String> dictList = Stream.of(eventDict.split("\\|\\|"))
                    .filter(s -> !s.isBlank())
                    .toList();
            final Predicate<Dictionary> sourceFilter = dictionary -> dictList.isEmpty() || dictList.contains(dictionary.name) || dictList.contains(dictionary.id);

            final String windowId = String.valueOf(System.currentTimeMillis());
            final String windowTitle = "查词义：" + event.text + "  -  " + app.getAppName();
            //
            FxHelper.showHtmlViewerWindow(app, windowId, windowTitle, dialog -> new EntryViewer(app, event.text, sourceFilter) {
                @Override
                protected void onWebEngineLoadSucceeded() {
                    super.onWebEngineLoadSucceeded();
                    //
                    dialog.setTitle("查词义：" + textInput.input.getText().strip() + "  -  " + app.getAppName());
                }
            });
        });
        //
        app.options.add(() -> optionForSelectionEvent(app));
        app.options.add(() -> optionForDataPaths(app));
    }

    private static List<Path> getDataPaths(BaseApp app) {
        final List<Path> list = new ArrayList<>();
        if (!FxHelper.isDevMode) {
            list.add(FxHelper.appDir().resolve("dict"));
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
        list.add(SHARED_PATH);

        list.addAll(Stream.of(app.config.getString("dictionary.paths", "").strip().split("\\|\\|"))
                .filter(s -> !s.isBlank())
                .map(Path::of)
                .filter(p -> !list.contains(p))
                .distinct()
                .toList());
        return list;
    }

    private static Option<RawVal<String>> optionForSelectionEvent(BaseApp app) {
        final List<RawVal<String>> values = List.of(
                RawVal.vk("selection&shortcut1", "仅在按下 Ctrl或Cmd 并选中文字后，立即查词义"),
                RawVal.vk("selection&shortcut0", "仅在选中文字后，立即查词义（同时按下Ctrl或Cmd将失效）"),
                RawVal.vk("none", "禁用选字查词义功能")
        );
        final ObjectProperty<RawVal<String>> valueProperty = new SimpleObjectProperty<>(
                values.stream()
                        .filter(rv -> rv.value().equals(app.config.getString("dictionary.bySelection", "selection&shortcut1")))
                        .findFirst().orElse(values.get(0))
        );
        valueProperty.addListener((o, ov, nv) -> {
            if (null == ov || Objects.equals(ov, nv)) return;
            app.config.setProperty("dictionary.bySelection", nv.value());
        });
        return new DefaultOptions<RawVal<String>>("选字查词义", "若同时按下Alt或Shift键时将失效", "查词", true)
                .setValues(values)
                .setValueProperty(valueProperty);
    }

    private static Option<String> optionForDataPaths(BaseApp app) {
        return new DefaultOption<String>("词库列表", null, "查词", true,
                option -> new OptionEditorBase<>(option, new Button()) {
                    private ObjectProperty<String> valueProperty;

                    @Override
                    public Property<String> valueProperty() {
                        if (this.valueProperty == null) {
                            this.valueProperty = new SimpleObjectProperty<>();
                            getEditor().setText("管理");
                            getEditor().setOnAction(evt -> openDataPathsDialog(app));
                        }
                        return this.valueProperty;
                    }

                    @Override
                    public void setValue(String value) {
                    }
                })
                .setValue("");
    }

    private static void openDataPathsDialog(BaseApp app) {
        final VBox settings = new VBox(3, configDataPaths(app));

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

    private static Node configDataPaths(BaseApp app) {
        Label label = new Label("词库列表。使用指定的词库文件或从目录（及子级）中扫描。");

        ListView<Path> listView = new ListView<>();
        VBox.setVgrow(listView, Priority.ALWAYS);
        listView.setMinHeight(200);
        listView.setStyle("-fx-border-width: 1px; -fx-border-color: gray;");
        listView.getItems().setAll(getDataPaths(app));
        listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        listView.getItems().addListener((ListChangeListener<? super Path>) c -> {
            if (c.wasAdded()) {
                Dictionaries.def.add(c.getAddedSubList().toArray(new Path[0]));
            }
            if (c.wasRemoved()) {
                Dictionaries.def.remove(c.getRemoved().toArray(new Path[0]));
            }

            List<Path> paths = listView.getItems();
            app.config.setProperty("dictionary.paths", paths.stream()
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

            final Path saveDir = SHARED_PATH;
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
            app.toast("导入了" + importedFiles.size() + "个Txt词库文件到目录：" + saveDir);
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

            final Path saveDir = SHARED_PATH;
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
            app.toast("导入了" + importedFiles.size() + "个Mdx词库文件到目录：" + saveDir);
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
            app.toast("转换了" + importedFiles.size() + "个Mdx词库文件到原目录。");
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

    static void openScopesDialog(BaseApp app, String confKey, RawProperty<Predicate<Dictionary>> source) {
        final Dialog<?> dialog = new Dialog<>();
        final DialogPane dialogPane = new DialogPane() {
            @Override
            protected Node createButtonBar() {
                return null;
            }
        };
        //
        final String confType = DEF_SCOPES.equals(confKey) ? "查词条" : "查词义";
        final Node dialogNode = configScopes(app, dialog, confKey, source);
        //
        if (OSVersions.isLinux) {
            dialogPane.setPrefSize(540, 720);
        }
        dialogPane.setContent(dialogNode);
        dialogPane.getButtonTypes().addAll(ButtonType.OK);
        //
        dialog.setTitle(confType + "范围设置");
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

    private static Node configScopes(BaseApp app, Dialog<?> dialog, String confKey, RawProperty<Predicate<Dictionary>> source) {
        final List<String> dictList = Stream.of(app.config.getString(confKey, "").split("\\|\\|"))
                .filter(s -> !s.isBlank())
                .toList();
        final boolean excludeMode = DEF_SCOPES.equals(confKey);
        //
        final Predicate<Dictionary> defaultFilter;
        final Consumer<Boolean> applyAction;
        final ListView<RawVal2<Dictionary, SimpleBooleanProperty>> listView = new ListView<>();
        if (excludeMode) {
            defaultFilter = dict -> dictList.isEmpty() || !dictList.contains(dict.name);
            applyAction = updateDefault -> {
                List<String> excludeList = new ArrayList<>(
                        listView.getItems().stream()
                                .filter(rv -> !rv.value().get())
                                .map(rv -> rv.key().name)
                                .toList()
                );
                // 如果是全选则使用空列表规则
                if (excludeList.size() == listView.getItems().size()) {
                    excludeList.clear();
                }
                if (updateDefault) {
                    app.config.setProperty(confKey, String.join("||", excludeList));
                }
                //
                source.set(d -> excludeList.isEmpty() || !excludeList.contains(d.name));
                //
                dialog.hide();
            };
        } else {
            defaultFilter = dict -> dictList.isEmpty() || dictList.contains(dict.name);
            applyAction = updateDefault -> {
                List<String> includeList = new ArrayList<>(
                        listView.getItems().stream()
                                .filter(rv -> rv.value().get())
                                .map(rv -> rv.key().name)
                                .toList()
                );
                if (updateDefault) {
                    app.config.setProperty(confKey, String.join("||", includeList));
                }
                //
                source.set(d -> includeList.isEmpty() || includeList.contains(d.name));
                //
                dialog.hide();
            };
        }
        //
        VBox.setVgrow(listView, Priority.ALWAYS);
        listView.setMinHeight(200);
        listView.setStyle("-fx-border-width: 1px; -fx-border-color: gray;");
        listView.getItems().setAll(
                Dictionaries.def.list().stream()
                        .map(v -> RawVal2.kv(v, new SimpleBooleanProperty(source.get() == null || source.get().test(v))))
                        .toList()
        );
        listView.setCellFactory(stringListView -> new CheckBoxListCell<>(RawVal2::value));
        //
        Button applyCurrent = new Button("应用");
        applyCurrent.setTooltip(new Tooltip("应用到当前查词范围"));
        applyCurrent.setOnAction(actionEvent -> applyAction.accept(false));
        //
        Button applyDefault = new Button("应用&设为默认");
        applyDefault.setTooltip(new Tooltip("应用到当前查词范围，并设为默认范围"));
        applyDefault.setOnAction(actionEvent -> applyAction.accept(true));
        //
        Button cancelButton = new Button("取消");
        cancelButton.setTooltip(new Tooltip("不修改当前查词范围"));
        cancelButton.setOnAction(actionEvent -> dialog.hide());

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
        selDef.setTooltip(new Tooltip("重置为默认范围"));
        selDef.setOnAction(actionEvent -> listView.getItems().forEach(rv -> rv.value().set(defaultFilter.test(rv.key()))));
        //

        HBoxEx toolbar = new HBoxEx();
        toolbar.setSpacing(5);
        toolbar.addLeft(selAll, selNone, selInvert, selDef);
        toolbar.addRight(applyCurrent, applyDefault, cancelButton);

        return new VBox(5, listView, toolbar);
    }
}

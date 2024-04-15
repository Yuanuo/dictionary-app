package org.appxi.dictionary.app;

import javafx.scene.Scene;
import javafx.stage.Stage;
import org.appxi.dictionary.pref.AboutController;
import org.appxi.dictionary.pref.PreferencesController;
import org.appxi.dictionary.ui.DictionaryContext;
import org.appxi.dictionary.ui.DictionaryController;
import org.appxi.dictionary.ui.EntryEvent;
import org.appxi.file.FileWatcher;
import org.appxi.javafx.app.AppEvent;
import org.appxi.javafx.app.web.WebApp;
import org.appxi.javafx.app.web.WebViewer;
import org.appxi.javafx.helper.FxHelper;
import org.appxi.javafx.visual.VisualEvent;
import org.appxi.javafx.visual.VisualProvider;
import org.appxi.javafx.web.WebPane;
import org.appxi.javafx.workbench.WorkbenchApp;
import org.appxi.javafx.workbench.WorkbenchPane;
import org.appxi.javafx.workbench.WorkbenchPart;
import org.appxi.prefs.UserPrefs;
import org.appxi.smartcn.convert.ChineseConvertors;
import org.appxi.util.ext.HanLang;

import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class App extends WorkbenchApp implements WebApp {
    public static final String ID = "smartWords";
    public static final String NAME = "Smart Dictionary";
    public static final String VERSION = "24.04.15";

    private final VisualProvider visualProvider = new VisualProvider(this);

    public final HanLang.Provider hanTextProvider;

    public App() {
        super(UserPrefs.dataDir());
        this.hanTextProvider = new HanLang.Provider(config, eventBus);
    }

    @Override
    public VisualProvider visualProvider() {
        return visualProvider;
    }

    @Override
    public void init() {
        super.init();
        //
        new Thread(WebPane::preloadLibrary).start();

        //
        options.add(() -> FxHelper.optionForHanLang(hanTextProvider, "以 简体/繁体 显示阅读视图中文字符"));
        //
        eventBus.addEventHandler(AppEvent.STARTED, e -> FxHelper.runThread(30, () -> eventBus.fireEvent(new EntryEvent(EntryEvent.SEARCH, null, null))));

        //
        DictionaryContext.setupDirectories(this);
        DictionaryContext.setupApplication(this);
    }

    @Override
    protected void showing(Stage primaryStage) {
        super.showing(primaryStage);
        if (!FxHelper.isDevMode) {
            Optional.ofNullable(App.class.getResource("app_desktop.css"))
                    .ifPresent(v -> primaryStage.getScene().getStylesheets().add(v.toExternalForm()));
        } else {
            Scene scene = primaryStage.getScene();
            visualProvider().visual().unAssign(scene);
            watchCss(scene, Path.of("../../appxi-javafx/src/main/resources/org/appxi/javafx/visual/visual_desktop.css"));
            watchCss(scene, Path.of("src/main/resources/org/appxi/dictionary/app/app_desktop.css"));
            scene.getStylesheets().forEach(System.out::println);
        }

        AppPreloader.hide();
    }

    @Override
    protected void stopped() {
        super.stopped();
        System.exit(0);
    }

    private void watchCss(Scene scene, Path file) {
        try {
            final String filePath = file.toRealPath().toUri().toString().replace("///", "/");
            System.out.println("watch css: " + filePath);
            scene.getStylesheets().add(filePath);
        } catch (Exception e) {
            e.printStackTrace();
        }

        FileWatcher watcher = new FileWatcher(file.getParent());
        watcher.watching();
        watcher.addListener(event -> {
            if (event.type != FileWatcher.WatchType.MODIFY) return;
            if (event.getSource().getFileName().toString().endsWith("~")) return;
            String css = null;
            try {
                css = event.getSource().toRealPath().toUri().toString().replace("///", "/");
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (null == css) return;
            System.out.println("CSS < " + css);
            if (css.endsWith("web.css")) {
                eventBus.fireEvent(new VisualEvent(VisualEvent.SET_STYLE, null));
            } else if (scene.getStylesheets().contains(css)) {
                final int idx = scene.getStylesheets().indexOf(css);
                String finalCss = css;
                javafx.application.Platform.runLater(() -> {
                    scene.getStylesheets().remove(finalCss);
                    if (idx != -1) scene.getStylesheets().add(idx, finalCss);
                    else scene.getStylesheets().add(finalCss);
                });
            }
        });
    }

    @Override
    public String getAppName() {
        return NAME;
    }

    @Override
    public List<URL> getAppIcons() {
        final String[] iconSizes = new String[]{"32", "64", "128", "256"};
        final List<URL> result = new ArrayList<>(iconSizes.length);
        for (String iconSize : iconSizes) {
            result.add(App.class.getResource("icon-".concat(iconSize).concat(".png")));
        }
        return result;
    }

    @Override
    protected List<WorkbenchPart> createWorkbenchParts(WorkbenchPane workbench) {
        final List<WorkbenchPart> result = new ArrayList<>();

        result.add(new DictionaryController(workbench));
//        result.add(new Welcome(workbench));

        result.add(new PreferencesController(workbench));
        result.add(new AboutController(workbench));
        return result;
    }

    @Override
    public Supplier<List<String>> webIncludesSupplier() {
        return () -> {
            List<String> result = WebViewer.getWebIncludeURIs();
            final Path dir = FxHelper.appDir().resolve("template/web-incl");
            result.addAll(Stream.of("html-viewer.css", "html-viewer.js")
                    .map(s -> dir.resolve(s).toUri().toString())
                    .toList()
            );
            result.add("<link id=\"CSS\" rel=\"stylesheet\" type=\"text/css\" href=\"" + visualProvider().getWebStyleSheetURI() + "\">");
            return result;
        };
    }

    @Override
    public Function<String, String> htmlDocumentWrapper() {
        return text -> ChineseConvertors.convert(text, null, hanTextProvider.get());
    }
}

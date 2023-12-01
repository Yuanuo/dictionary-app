package org.appxi.dictionary.app;

import org.appxi.javafx.visual.VisualProvider;
import org.appxi.javafx.workbench.WorkbenchApp;

import java.nio.file.Path;

public abstract class WorkbenchApp1 extends WorkbenchApp {
    private final VisualProvider visualProvider = new VisualProvider(this);

    public WorkbenchApp1(Path workspace) {
        super(workspace);
        Thread.setDefaultUncaughtExceptionHandler(this::handleUncaughtException);
    }

    protected void handleUncaughtException(Thread thread, Throwable throwable) {
        logger.error("<UNCAUGHT>", throwable);
        toastError(throwable.getClass().getName().concat(": ").concat(throwable.getMessage()));
    }

    @Override
    public VisualProvider visualProvider() {
        return visualProvider;
    }

    @Override
    protected void stopped() {
        super.stopped();
        System.exit(0);
    }
}

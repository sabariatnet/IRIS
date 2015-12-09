package com.temenos.interaction.loader.detector;

/*
 * #%L
 * interaction-dynamic-loader
 * %%
 * Copyright (C) 2012 - 2015 Temenos Holdings N.V.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.temenos.interaction.core.loader.Action;
import com.temenos.interaction.core.loader.FileEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executes actions every time a change (creation, modification or
 * deletion) in a collection of directories is detected.
 *
 * The implementation sets a scheduled task whenever setResources or
 * setListeners is called, which executes a command (in this case
 * ListenerNotificationTask) every 10 seconds. ListenerNotificationTask watches
 * the directories for changes and is responsible of calling the execute method
 * of all listener's action with the directory that changed as parameter.
 *
 * @author andres
 * @author trojanbug
 * @author cmclopes
 */
public class DirectoryChangeActionNotifier implements DirectoryChangeDetector<Action<FileEvent<File>>> {

    private static final Logger logger = LoggerFactory.getLogger(DirectoryChangeActionNotifier.class);
    private Collection<? extends File> resources = new ArrayList();
    private Collection<? extends Action<FileEvent<File>>> listeners = new ArrayList();
    private WatchService watchService;
    private ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> scheduledTask = null;
    // make it a parameter
    private long interval_seconds = 10;


    @Override
    public void setResources(Collection<? extends File> resources) {
        if (resources == null) {
            this.resources = new ArrayList<File>();
        }
        this.resources = new ArrayList<File>(resources);
        initWatchers(resources);
    }

    @Override
    public void setListeners(Collection<? extends Action<FileEvent<File>>> listeners) {
        if (listeners == null) {
            this.listeners = new ArrayList<Action<FileEvent<File>>>();
            return;
        }
        this.listeners = new ArrayList<Action<FileEvent<File>>>(listeners);
        initWatchers(getResources());
    }

    public Collection<? extends File> getResources() {
        return resources;
    }

    public Collection<? extends Action<FileEvent<File>>> getListeners() {
        return listeners;
    }

    protected void initWatchers(Collection<? extends File> resources) {
        if (scheduledTask != null) {
            scheduledTask.cancel(true);
        }
        if (resources == null || resources.isEmpty() || getListeners() == null || getListeners().isEmpty()) {
            return;
        }
        try {
            WatchService ws = FileSystems.getDefault().newWatchService();
            for (File file : resources) {
                Path filePath = Paths.get(file.toURI());
                filePath.register(ws, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
            }

            watchService = ws;
            scheduledTask = executorService.scheduleWithFixedDelay(new ListenerNotificationTask(watchService, getListeners(), interval_seconds * 1000), 5, interval_seconds, TimeUnit.SECONDS);
        } catch (IOException ex) {
            throw new RuntimeException("Error configuring directory change listener - unexpected IOException", ex);
        }
    }

    /**
     * Runnable class that uses a provided WatchService on files and directories
     * to execute all listener's actions for detected events.
     * 
     * It currently ignores all events in a user-specified time interval after the
     * first accepted event.
     *
     * @author andres
     * @author trojanbug
     * @author cmclopes
     */
    protected static class ListenerNotificationTask implements Runnable {

        private WatchService watchService;
        private Collection<? extends Action<FileEvent<File>>> listeners;
        private long lastRun = 0;
        private long interval = 0;

        public ListenerNotificationTask(WatchService watchService, Collection<? extends Action<FileEvent<File>>> listeners, long interval) {
            this.watchService = watchService;
            this.listeners = listeners;
            this.interval = interval;
        }

        @Override
        public void run() {
            try {
                WatchKey key = watchService.take();
                for (WatchEvent<?> e : key.pollEvents()) {
                    // TODO change this for a schedule run in the future
                    if (System.currentTimeMillis() - lastRun > interval) {
                        WatchEvent.Kind<?> kind = e.kind();
                        logger.warn(kind.name());
                        if (kind != StandardWatchEventKinds.OVERFLOW) {
                            Path dir = (Path) key.watchable();
                            Path fullPath = dir.resolve((Path) e.context());
                            FileEvent<File> newEvent = new DirectoryChangeEvent(fullPath.toFile());
                            for (Action<FileEvent<File>> action : listeners) {
                                action.execute(newEvent);
                            }
                        }
                        lastRun = System.currentTimeMillis();
                    }
                }
                key.reset();
            } catch (InterruptedException ex) {

            }
        }

    }

    /**
     * Helper class for getting a directory from a File instance.
     *
     * @author andres
     * @author trojanbug
     * @author cmclopes
     */
    public static class DirectoryChangeEvent implements FileEvent<File> {

        private File directory;

        public DirectoryChangeEvent(File file) {
            if (!file.isDirectory()) {
                directory = file.getAbsoluteFile().getParentFile();
            } else {
                directory = file;
            }
        }

        @Override
        public File getResource() {
            return directory;
        }

    }

}

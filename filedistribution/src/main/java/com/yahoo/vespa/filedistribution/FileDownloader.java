//  Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.filedistribution;

import com.google.common.util.concurrent.SettableFuture;
import com.yahoo.config.FileReference;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.config.ConnectionPool;
import com.yahoo.vespa.defaults.Defaults;

import java.io.File;
import java.time.Duration;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

/**
 * Handles downloads of files (file references only for now)
 *
 * @author hmusum
 */
public class FileDownloader {

    private final static Logger log = Logger.getLogger(FileDownloader.class.getName());

    private final File downloadDirectory;
    private final Duration timeout;
    private final FileReferenceDownloader fileReferenceDownloader;

    public FileDownloader(ConnectionPool connectionPool) {
        this(connectionPool,
                new File(Defaults.getDefaults().underVespaHome("var/db/vespa/filedistribution")),
                Duration.ofMinutes(15));
    }

    FileDownloader(ConnectionPool connectionPool, File downloadDirectory, Duration timeout) {
        this.downloadDirectory = downloadDirectory;
        this.timeout = timeout;
        this.fileReferenceDownloader = new FileReferenceDownloader(downloadDirectory, connectionPool, timeout);
    }

    public Optional<File> getFile(FileReference fileReference) {
        try {
            return getFutureFile(fileReference).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            return Optional.empty();
        }
    }

    public Future<Optional<File>> getFutureFile(FileReference fileReference) {
        Objects.requireNonNull(fileReference, "file reference cannot be null");
        File directory = new File(downloadDirectory, fileReference.value());
        log.log(LogLevel.DEBUG, "Checking if there is a file in '" + directory.getAbsolutePath() + "' ");

        Optional<File> file = getFileFromFileSystem(fileReference, directory);
        if (file.isPresent()) {
            SettableFuture<Optional<File>> future = SettableFuture.create();
            future.set(file);
            return future;
        } else {
            log.log(LogLevel.INFO, "File reference '" + fileReference.value() + "' not found in " +
                    directory.getAbsolutePath() + ", starting download");
            return queueForDownload(fileReference, timeout);

        }
    }

    public void queueForDownload(List<FileReference> fileReferences) {
        fileReferences.forEach(this::queueForDownload);
    }

    void receiveFile(FileReferenceData fileReferenceData) {
        fileReferenceDownloader.receiveFile(fileReferenceData);
    }

    double downloadStatus(FileReference fileReference) {
        return fileReferenceDownloader.downloadStatus(fileReference.value());
    }

    public Map<FileReference, Double> downloadStatus() {
        return fileReferenceDownloader.downloadStatus();
    }

    File downloadDirectory() {
        return downloadDirectory;
    }

    private Optional<File> getFileFromFileSystem(FileReference fileReference, File directory) {
        File[] files = directory.listFiles();
        if (directory.exists() && directory.isDirectory() && files != null && files.length > 0) {
            File file = files[0];
            if (!file.exists()) {
                throw new RuntimeException("File with reference '" + fileReference.value() + "' does not exist");
            } else if (!file.canRead()) {
                throw new RuntimeException("File with reference '" + fileReference.value() + "'exists, but unable to read it");
            } else {
                fileReferenceDownloader.setDownloadStatus(fileReference.value(), 100.0);
                return Optional.of(file);
            }
        }
        return Optional.empty();
    }

    private synchronized Future<Optional<File>> queueForDownload(FileReference fileReference, Duration timeout) {
        Future<Optional<File>> inProgress = fileReferenceDownloader.addDownloadListener(fileReference, () -> getFile(fileReference));
        if (inProgress != null) {
            log.log(LogLevel.INFO, "Already downloading '" + fileReference.value() + "'");
            return inProgress;
        }

        Future<Optional<File>> future = queueForDownload(fileReference);
        log.log(LogLevel.INFO, "Queued '" + fileReference.value() + "' for download with timeout " + timeout);
        return future;
    }

    // We don't care about the future in this call
    private Future<Optional<File>> queueForDownload(FileReference fileReference) {
        return queueForDownload(new FileReferenceDownload(fileReference, SettableFuture.create()));
    }

    private Future<Optional<File>> queueForDownload(FileReferenceDownload fileReferenceDownload) {
        fileReferenceDownloader.addToDownloadQueue(fileReferenceDownload);
        return fileReferenceDownload.future();
    }

    public FileReferenceDownloader fileReferenceDownloader() {
        return fileReferenceDownloader;
    }
}

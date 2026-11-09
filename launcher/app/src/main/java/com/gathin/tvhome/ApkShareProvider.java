package com.gathin.tvhome;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * A minimal self-written {@link ContentProvider} that exposes the downloaded APK to the system
 * package installer, so no {@code androidx.core} FileProvider is needed. It serves only files
 * inside {@link ApkUpdater#updateDir} and only for read, rejecting any path that escapes it.
 */
public final class ApkShareProvider extends ContentProvider {

    static final String AUTHORITY_SUFFIX = ".apkshare";

    /** Builds the content URI for one downloaded file: content://<package>.apkshare/<file>. */
    static Uri uriFor(Context context, File file) {
        return new Uri.Builder()
                .scheme("content")
                .authority(context.getPackageName() + AUTHORITY_SUFFIX)
                .appendPath(file.getName())
                .build();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        return "application/vnd.android.package-archive";
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (mode != null && !"r".equals(mode)) {
            throw new FileNotFoundException("only read access is allowed");
        }
        Context context = getContext();
        if (context == null) throw new FileNotFoundException("provider not ready");
        try {
            File dir = ApkUpdater.updateDir(context).getCanonicalFile();
            String name = uri.getLastPathSegment();
            if (name == null) throw new FileNotFoundException("missing file name");
            File file = new File(dir, name).getCanonicalFile();
            if (!file.getPath().startsWith(dir.getPath() + File.separator) || !file.isFile()) {
                throw new FileNotFoundException("file not found");
            }
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
        } catch (FileNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new FileNotFoundException(e.getMessage());
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
        return 0;
    }
}

package com.attackmode.planner;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

import androidx.core.content.ContextCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@CapacitorPlugin(
    name = "Backup",
    permissions = {
        @Permission(
            alias = "storage",
            strings = {Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}
        )
    }
)
public class BackupPlugin extends Plugin {

    private static final String FOLDER = "Attack Mode";
    private static final String FILE_NAME = "attack-mode-data.json";

    private File backupFile() {
        File documents = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        return new File(new File(documents, FOLDER), FILE_NAME);
    }

    private boolean hasAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return ContextCompat.checkSelfPermission(getContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
            == PackageManager.PERMISSION_GRANTED;
    }

    private JSObject status() {
        File file = backupFile();
        JSObject result = new JSObject();
        boolean granted = hasAccess();
        result.put("granted", granted);
        result.put("exists", granted && file.isFile());
        result.put("path", "Documents/" + FOLDER + "/" + FILE_NAME);
        return result;
    }

    @PluginMethod
    public void status(PluginCall call) {
        call.resolve(status());
    }

    @PluginMethod
    public void requestAccess(PluginCall call) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent intent = new Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:" + getContext().getPackageName())
            );
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                getContext().startActivity(intent);
            } catch (RuntimeException error) {
                Intent fallback = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(fallback);
            }
            call.resolve(status());
            return;
        }
        if (getPermissionState("storage") != com.getcapacitor.PermissionState.GRANTED) {
            requestPermissionForAlias("storage", call, "afterStorage");
            return;
        }
        call.resolve(status());
    }

    @PermissionCallback
    private void afterStorage(PluginCall call) {
        call.resolve(status());
    }

    @PluginMethod
    public void write(PluginCall call) {
        String json = call.getString("json");
        if (json == null || !hasAccess()) {
            call.reject("Backup is not available.");
            return;
        }

        File file = backupFile();
        File folder = file.getParentFile();
        if (folder != null && !folder.isDirectory() && !folder.mkdirs()) {
            call.reject("Could not create the backup folder.");
            return;
        }

        File temp = new File(folder, FILE_NAME + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temp)) {
            out.write(json.getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        } catch (IOException error) {
            call.reject("Could not write the backup.");
            return;
        }

        if (!temp.renameTo(file)) {
            file.delete();
            if (!temp.renameTo(file)) {
                call.reject("Could not save the backup.");
                return;
            }
        }
        call.resolve();
    }

    @PluginMethod
    public void read(PluginCall call) {
        JSObject result = new JSObject();
        File file = backupFile();
        if (!hasAccess() || !file.isFile()) {
            result.put("exists", false);
            result.put("json", "");
            call.resolve(result);
            return;
        }

        try (FileInputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int count;
            while ((count = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, count);
            }
            result.put("exists", true);
            result.put("json", buffer.toString("UTF-8"));
            call.resolve(result);
        } catch (IOException error) {
            call.reject("Could not read the backup.");
        }
    }
}

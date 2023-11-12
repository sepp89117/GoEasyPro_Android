package com.sepp89117.goeasypro_android;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.sepp89117.goeasypro_android.helpers.ShutterLog;

import java.util.ArrayList;
import java.util.List;

public class LogActivity extends AppCompatActivity {
    private ListView logListView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log);

        logListView = findViewById(R.id.logListView);
        Button clearLogButton = findViewById(R.id.clearLogButton);

        displayShutterLog();

        clearLogButton.setOnClickListener(view -> {
            final AlertDialog alert = new AlertDialog.Builder(LogActivity.this)
                    .setTitle("Clear Log")
                    .setMessage("Are you sure you want to clear the log?")
                    .setPositiveButton(getResources().getString(R.string.str_Yes), (dialog, which) -> {
                        clearShutterLog();
                        displayShutterLog();
                    })
                    .setNegativeButton(getResources().getString(R.string.str_Cancel), (dialog, which) -> dialog.dismiss())
                    .create();

            alert.show();
        });
    }

    private void displayShutterLog() {
        List<ShutterLog.ShutterLogEntry> logEntries = ShutterLog.getShutterLog(this);
        List<String> formattedLogEntries = formatLogEntries(logEntries);
        ArrayAdapter<String> logAdapter = new ArrayAdapter<>(this, R.layout.loglist_item, formattedLogEntries);
        logListView.setAdapter(logAdapter);

        // Füge einen OnItemLongClickListener zur ListView hinzu
        logListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                showDeleteConfirmationDialog(position);
                return true;
            }
        });
    }

    private void showDeleteConfirmationDialog(final int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Would you like to delete this entry?")
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Lösche das ausgewählte Shutter-Log-Eintrag
                        ShutterLog.deleteShutterLogEntry(LogActivity.this, position);

                        // Aktualisiere die ListView
                        displayShutterLog();
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Benutzer hat "No" ausgewählt, tue hier nichts
                    }
                });

        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    private void clearShutterLog() {
        SharedPreferences prefs = getSharedPreferences(ShutterLog.PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(ShutterLog.LOG_KEY);
        editor.apply();
    }

    private List<String> formatLogEntries(List<ShutterLog.ShutterLogEntry> logEntries) {
        List<String> formattedLogEntries = new ArrayList<>();
        for (ShutterLog.ShutterLogEntry entry : logEntries) {
            String formattedEntry = entry.getDateTime() + ": " + entry.getCameraName() + " - " + entry.getShutterAction();
            formattedLogEntries.add(formattedEntry);
        }
        return formattedLogEntries;
    }
}

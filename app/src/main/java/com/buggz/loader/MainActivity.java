package com.buggz.loader;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.DataOutputStream;

public class MainActivity extends Activity {

    private static final int PICK_FILE_REQUEST = 1;
    private static final String PREFS_NAME = "BuggzPrefs";
    private static final String PREF_LAST_SCRIPT = "last_script_path";
    
    private TextView tvSelectedFile;
    private TextView tvLog;
    private Button btnRunScript;
    private EditText etInput;
    private Button btnSendInput;
    private String selectedScriptPath = null;
    private DataOutputStream processInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnSelectScript = findViewById(R.id.btnSelectScript);
        btnRunScript = findViewById(R.id.btnRunScript);
        tvSelectedFile = findViewById(R.id.tvSelectedFile);
        tvLog = findViewById(R.id.tvLog);
        etInput = findViewById(R.id.etInput);
        btnSendInput = findViewById(R.id.btnSendInput);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String lastScript = prefs.getString(PREF_LAST_SCRIPT, null);
        
        if (lastScript != null && new File(lastScript).exists()) {
            selectedScriptPath = lastScript;
            tvSelectedFile.setText("Loaded previous script: payload.sh");
            btnRunScript.setEnabled(true);
            log("Ready to execute previous payload.");
        }

        btnSelectScript.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            startActivityForResult(intent, PICK_FILE_REQUEST);
        });

        btnRunScript.setOnClickListener(v -> {
            if (selectedScriptPath != null) {
                executeRootScript(selectedScriptPath);
            }
        });

        btnSendInput.setOnClickListener(v -> {
            if (processInput != null) {
                try {
                    String input = etInput.getText().toString() + "\n";
                    processInput.writeBytes(input);
                    processInput.flush();
                    log("-> " + input.trim());
                    etInput.setText("");
                } catch (Exception e) {
                    log("Failed to send input: " + e.getMessage());
                }
            } else {
                log("No active process to send input to.");
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FILE_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                try {
                    InputStream inputStream = getContentResolver().openInputStream(uri);
                    File tempFile = new File(getFilesDir(), "payload.sh");
                    OutputStream outputStream = new FileOutputStream(tempFile);
                    
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = inputStream.read(buffer)) > 0) {
                        outputStream.write(buffer, 0, length);
                    }
                    outputStream.close();
                    inputStream.close();

                    selectedScriptPath = tempFile.getAbsolutePath();
                    
                    SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
                    editor.putString(PREF_LAST_SCRIPT, selectedScriptPath);
                    editor.apply();

                    tvSelectedFile.setText("Selected: " + uri.getLastPathSegment());
                    btnRunScript.setEnabled(true);
                    log("Script copied and saved to: " + selectedScriptPath);

                } catch (Exception e) {
                    log("Error copying file: " + e.getMessage());
                }
            }
        }
    }

    private void executeRootScript(String scriptPath) {
        log("Requesting root access...");
        new Thread(() -> {
            try {
                Process process = Runtime.getRuntime().exec("su");
                processInput = new DataOutputStream(process.getOutputStream());
                
                // Start the script
                processInput.writeBytes("sh " + scriptPath + "\n");
                processInput.flush();
                
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                
                // Read stdout in a separate thread so we don't block stderr
                new Thread(() -> {
                    try {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            final String logLine = line;
                            runOnUiThread(() -> log(logLine));
                        }
                    } catch (Exception ignored) {}
                }).start();
                
                String line;
                while ((line = errorReader.readLine()) != null) {
                    final String logLine = "ERROR: " + line;
                    runOnUiThread(() -> log(logLine));
                }
                
                int exitCode = process.waitFor();
                processInput = null;
                runOnUiThread(() -> log("Process exited with code: " + exitCode));
                
            } catch (Exception e) {
                processInput = null;
                runOnUiThread(() -> log("Execution failed: " + e.getMessage()));
            }
        }).start();
    }

    private void log(String message) {
        tvLog.append("\n" + message);
    }
}

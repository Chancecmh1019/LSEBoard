package com.lseboard.app;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.widget.Toast;
import android.content.ClipboardManager;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.DynamicColors;

import java.util.List;

import com.lseboard.app.Util.SharedPrefHelper;

public class SetupActivity extends AppCompatActivity {

    private int currentStep = 0;
    
    private TextView tvTitle;
    private TextView tvMessage;
    private MaterialButton btnPrimary;
    private MaterialButton btnSecondary;
    private View textInputLayout;
    private com.google.android.material.textfield.TextInputEditText etUrls;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            DynamicColors.applyToActivityIfAvailable(this);
        }
        super.onCreate(savedInstanceState);
        
        // 如果已經同意條款且鍵盤已啟用，直接進入主畫面
        if (SharedPrefHelper.getDisclaimerStatus(this) && isKeyboardEnabled()) {
            launchMain();
            return;
        }

        setContentView(R.layout.activity_setup);

        tvTitle = findViewById(R.id.tvSetupTitle);
        tvMessage = findViewById(R.id.tvSetupMessage);
        btnPrimary = findViewById(R.id.btnPrimary);
        btnSecondary = findViewById(R.id.btnSecondary);
        textInputLayout = findViewById(R.id.textInputLayout);
        etUrls = findViewById(R.id.etUrls);

        updateUIForStep();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        if (currentStep == 1 && isKeyboardEnabled()) {
            currentStep = 2;
            updateUIForStep();
        }
    }

    private void updateUIForStep() {
        if (currentStep == 0) {
            // 第一步：同意條款
            if (SharedPrefHelper.getDisclaimerStatus(this)) {
                currentStep = 1;
                updateUIForStep();
                return;
            }
            tvTitle.setText(R.string.app_name);
            tvMessage.setText(R.string.setup_guide_message);
            btnPrimary.setText(R.string.accept_and_continue);
            btnPrimary.setOnClickListener(v -> {
                SharedPrefHelper.saveDisclaimerStatus(this, true);
                currentStep = 1;
                updateUIForStep();
            });
            btnSecondary.setVisibility(View.GONE);
        } else if (currentStep == 1) {
            // 第二步：啟用鍵盤
            if (isKeyboardEnabled()) {
                currentStep = 2;
                updateUIForStep();
                return;
            }
            tvTitle.setText(R.string.setup_guide_title);
            tvMessage.setText(R.string.enable_keyboard_msg);
            btnPrimary.setText(R.string.go_to_settings);
            btnPrimary.setOnClickListener(v -> {
                Intent intent = new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS);
                startActivity(intent);
            });
            btnSecondary.setVisibility(View.VISIBLE);
            btnSecondary.setText(R.string.skip);
            btnSecondary.setOnClickListener(v -> {
                currentStep = 2;
                updateUIForStep();
            });
        } else if (currentStep == 2) {
            // 第三步：下載第一個貼圖包
            tvTitle.setText(R.string.import_sticker_title);
            tvMessage.setText("您可以手動貼上多個網址（每行一個），或是點擊按鈕直接從剪貼簿讀取並匯入。");
            textInputLayout.setVisibility(View.VISIBLE);
            
            btnPrimary.setText("匯入網址");
            btnPrimary.setOnClickListener(v -> {
                String input = "";
                if (etUrls.getText() != null && !etUrls.getText().toString().trim().isEmpty()) {
                    input = etUrls.getText().toString().trim();
                } else {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null && clipboard.hasPrimaryClip() && clipboard.getPrimaryClip().getItemCount() > 0) {
                        CharSequence text = clipboard.getPrimaryClip().getItemAt(0).getText();
                        if (text != null) {
                            input = text.toString();
                        }
                    }
                }
                
                if (input.isEmpty()) {
                    Toast.makeText(this, "未輸入任何網址且剪貼簿為空", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                String[] lines = input.split("\\r?\\n");
                boolean validFound = false;
                for (String line : lines) {
                    if (line.contains("emojishop") || line.contains("emoji/?id=") || line.contains("/S/emoji/")) {
                        EmojiFetchService.startActionFetchEmoji(this, line.trim());
                        validFound = true;
                    } else if (line.contains("line.me")) {
                        FetchService.startActionFetch(this, line.trim());
                        validFound = true;
                    }
                }
                
                if (validFound) {
                    Toast.makeText(this, R.string.downloading_sticker, Toast.LENGTH_SHORT).show();
                    launchMain();
                } else {
                    Toast.makeText(this, R.string.invalid_url, Toast.LENGTH_SHORT).show();
                }
            });
            btnSecondary.setVisibility(View.VISIBLE);
            btnSecondary.setText(R.string.skip);
            btnSecondary.setOnClickListener(v -> launchMain());
        }
    }

    private boolean isKeyboardEnabled() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            List<InputMethodInfo> imes = imm.getEnabledInputMethodList();
            for (InputMethodInfo ime : imes) {
                if (ime.getPackageName().equals(getPackageName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private void launchMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}


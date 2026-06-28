package com.example.togglegrayscale;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

public class QuickMenuActivity extends Activity {

    private static final String TAG = "QuickMenuActivity";
    private static final String PREFS_ORDER = "menu_order_prefs";
    private static final String KEY_ORDER = "item_order";
    private static final String[] ALL_ITEM_IDS = {
        "manage_apps", "timer", "blue_light", "clock", "dimmer", "grayscale",
        "cine_mode", "screen_off", "audio_output",
        "google_home", "bluetooth", "system_info", "reboot",
        "config_mute", "config_youtube_190", "config_youtube_189"
    };

    private static final String[] ACTION_NAMES = {
        "Ninguna",
        "Silenciar Audio",
        "Escala de Grises (B/N)",
        "Apagar Pantalla (Negro)",
        "Google Home Panel",
        "YouTube",
        "Netflix",
        "Auriculares Bluetooth",
        "Menú de Acciones",
        "Filtro Luz Azul",
        "Reloj en Pantalla",
        "Dimmer de Pantalla",
        "Modo Cine",
        "Info del Sistema",
        "Reiniciar Chromecast",
        "Salida de Audio",
        "Pantalla Espejo",
        "Recientes",
        "Pausar y Apagar Pantalla"
    };

    private LinearLayout menuContainer;
    private LinearLayout panelTimer;
    private LinearLayout panelCine;
    private LinearLayout panelBlueLight;
    private LinearLayout panelButtonConfig;
    
    private TextView btnCancelTimer;
    private TextView btnCineBlueLightConfig;
    private TextView btnCineDimmerConfig;
    private TextView btnCineTimerConfig;
    private TextView btnApplyCine;

    // Button Config Panel Fields
    private TextView txtConfigTitle;
    private TextView btnConfigClick1;
    private TextView btnConfigClick2;
    private TextView btnConfigClick3;
    private TextView btnConfigLong;
    private TextView btnConfigDurationDec;
    private TextView btnConfigDurationInc;
    private TextView txtConfigDuration;
    private String configuringButton = null;

    // Clock Config Panel Fields
    private LinearLayout panelClockConfig;
    private TextView btnClockTextColor;
    private TextView btnClockBgColor;
    private TextView btnClockAlpha;
    private TextView btnClockPosition;
    private TextView btnClockSizeDec;
    private TextView btnClockSizeInc;
    private TextView txtClockSize;
    private TextView btnClockPadDec;
    private TextView btnClockPadInc;
    private TextView txtClockPad;
    private TextView btnClockXDec;
    private TextView btnClockXInc;
    private TextView txtClockX;
    private TextView btnClockYDec;
    private TextView btnClockYInc;
    private TextView txtClockY;
    private TextView btnApplyClock;

    // Custom Timer fields
    private TextView txtCustomHours;
    private TextView txtCustomMins;
    private int customHours = 0;
    private int customMins = 0;

    // SeekBar fields
    private SeekBar sliderBlueLight;
    private TextView txtBlueLightPct;

    private boolean isReorderMode = false;
    private int reorderSelectedIndex = -1;
    private String openSubPanel = null;
    private String lastFocusedId = null; // Guardamos el ID en lugar del View

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quick_menu);
        menuContainer          = findViewById(R.id.menu_container);
        panelTimer             = findViewById(R.id.panel_timer);
        panelCine              = findViewById(R.id.panel_cine);
        panelBlueLight         = findViewById(R.id.panel_blue_light);
        
        btnCancelTimer         = findViewById(R.id.btn_cancel_timer);
        btnCineBlueLightConfig = findViewById(R.id.btn_cine_blue_light_config);
        btnCineDimmerConfig    = findViewById(R.id.btn_cine_dimmer_config);
        btnCineTimerConfig     = findViewById(R.id.btn_cine_timer_config);
        btnApplyCine           = findViewById(R.id.btn_apply_cine);

        // Custom timer UI
        txtCustomHours         = findViewById(R.id.txt_custom_hours);
        txtCustomMins          = findViewById(R.id.txt_custom_mins);

        // SeekBar UI
        sliderBlueLight        = findViewById(R.id.slider_blue_light);
        txtBlueLightPct        = findViewById(R.id.txt_blue_light_pct);

        // Button Config UI
        panelButtonConfig    = findViewById(R.id.panel_button_config);
        txtConfigTitle       = findViewById(R.id.txt_config_title);
        btnConfigClick1      = findViewById(R.id.btn_config_click_1);
        btnConfigClick2      = findViewById(R.id.btn_config_click_2);
        btnConfigClick3      = findViewById(R.id.btn_config_click_3);
        btnConfigLong        = findViewById(R.id.btn_config_long);
        btnConfigDurationDec = findViewById(R.id.btn_config_duration_dec);
        btnConfigDurationInc = findViewById(R.id.btn_config_duration_inc);
        txtConfigDuration    = findViewById(R.id.txt_config_duration);

        // Clock Config UI
        panelClockConfig     = findViewById(R.id.panel_clock_config);
        btnClockTextColor    = findViewById(R.id.btn_clock_text_color);
        btnClockBgColor      = findViewById(R.id.btn_clock_bg_color);
        btnClockAlpha        = findViewById(R.id.btn_clock_alpha);
        btnClockPosition     = findViewById(R.id.btn_clock_position);
        btnClockSizeDec      = findViewById(R.id.btn_clock_size_dec);
        btnClockSizeInc      = findViewById(R.id.btn_clock_size_inc);
        txtClockSize         = findViewById(R.id.txt_clock_size);
        btnClockPadDec       = findViewById(R.id.btn_clock_pad_dec);
        btnClockPadInc       = findViewById(R.id.btn_clock_pad_inc);
        txtClockPad          = findViewById(R.id.txt_clock_pad);
        btnClockXDec         = findViewById(R.id.btn_clock_x_dec);
        btnClockXInc         = findViewById(R.id.btn_clock_x_inc);
        txtClockX            = findViewById(R.id.txt_clock_x);
        btnClockYDec         = findViewById(R.id.btn_clock_y_dec);
        btnClockYInc         = findViewById(R.id.btn_clock_y_inc);
        txtClockY            = findViewById(R.id.txt_clock_y);
        btnApplyClock        = findViewById(R.id.btn_apply_clock);

        setupSubPanelListeners();
        buildMenu();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isReorderMode) buildMenu();
    }

    private void setupSubPanelListeners() {
        // Preset Timers
        int[] timerIds  = {R.id.btn_5m, R.id.btn_15m, R.id.btn_30m, R.id.btn_45m, R.id.btn_1h, R.id.btn_2h, R.id.btn_2h30m, R.id.btn_3h};
        int[] timerMins = {5, 15, 30, 45, 60, 120, 150, 180};
        for (int i = 0; i < timerIds.length; i++) {
            final int mins = timerMins[i];
            View v = findViewById(timerIds[i]);
            if (v != null) v.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) { startTimer(mins); }
            });
        }

        // Custom Timer controls
        View btnHDec = findViewById(R.id.btn_hours_dec);
        View btnHInc = findViewById(R.id.btn_hours_inc);
        View btnMDec = findViewById(R.id.btn_mins_dec);
        View btnMInc = findViewById(R.id.btn_mins_inc);
        View btnStartCustom = findViewById(R.id.btn_start_custom_timer);

        if (btnHDec != null) btnHDec.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (customHours > 0) { customHours--; updateCustomTimerUI(); } }
        });
        if (btnHInc != null) btnHInc.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (customHours < 24) { customHours++; updateCustomTimerUI(); } }
        });
        if (btnMDec != null) btnMDec.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (customMins >= 1) { customMins--; updateCustomTimerUI(); } }
        });
        if (btnMInc != null) btnMInc.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (customMins <= 58) { customMins++; updateCustomTimerUI(); } }
        });
        if (btnStartCustom != null) btnStartCustom.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                int totalMins = (customHours * 60) + customMins;
                if (totalMins > 0) startTimer(totalMins);
            }
        });

        if (btnCancelTimer != null) {
            btnCancelTimer.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    Intent i = new Intent(QuickMenuActivity.this, SleepTimerService.class);
                    i.setAction("ACTION_CANCEL_TIMER");
                    startService(i);
                    closeSubPanels();
                    buildMenu();
                }
            });
        }

        // SeekBar (Blue light filter)
        if (sliderBlueLight != null) {
            SharedPreferences op = getOverlayPrefs();
            int currentPct = op.getInt("blue_light_pct", 0);
            sliderBlueLight.setProgress(currentPct);
            if (txtBlueLightPct != null) txtBlueLightPct.setText("Nivel: " + currentPct + "%");

            sliderBlueLight.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (txtBlueLightPct != null) txtBlueLightPct.setText("Nivel: " + progress + "%");
                    android.os.Bundle b = new android.os.Bundle();
                    b.putInt("pct", progress);
                    sendServiceAction("ACTION_SET_BLUE_LIGHT_PCT", b);
                    if (progress > 0) {
                        getOverlayPrefs().edit().putInt("blue_light_pct", progress).apply();
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });

            // Capturar la tecla Back directamente en el slider
            sliderBlueLight.setOnKeyListener(new View.OnKeyListener() {
                @Override
                public boolean onKey(View v, int keyCode, KeyEvent event) {
                    if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN) {
                        closeSubPanels();
                        buildMenu();
                        return true;
                    }
                    return false;
                }
            });
        }

        // Cine Config panel
        if (btnCineBlueLightConfig != null) {
            btnCineBlueLightConfig.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    SharedPreferences cp = getSharedPreferences("cine_prefs", MODE_PRIVATE);
                    cp.edit().putBoolean("cine_blue_light", !cp.getBoolean("cine_blue_light", true)).apply();
                    updateCineConfigPanel();
                }
            });
        }
        if (btnCineDimmerConfig != null) {
            btnCineDimmerConfig.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    SharedPreferences cp = getSharedPreferences("cine_prefs", MODE_PRIVATE);
                    cp.edit().putBoolean("cine_dimmer", !cp.getBoolean("cine_dimmer", false)).apply();
                    updateCineConfigPanel();
                }
            });
        }
        if (btnCineTimerConfig != null) {
            btnCineTimerConfig.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    SharedPreferences cp = getSharedPreferences("cine_prefs", MODE_PRIVATE);
                    int cur  = cp.getInt("cine_timer", 0);
                    int next = cur == 0 ? 30 : cur == 30 ? 60 : cur == 60 ? 90 : cur == 90 ? 120 : cur == 120 ? 150 : cur == 150 ? 180 : 0;
                    cp.edit().putInt("cine_timer", next).apply();
                    updateCineConfigPanel();
                }
            });
        }
        if (btnApplyCine != null) {
            btnApplyCine.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    SharedPreferences op = getOverlayPrefs();
                    boolean cur = op.getBoolean(ButtonMappingService.KEY_CINE_MODE, false);
                    op.edit().putBoolean(ButtonMappingService.KEY_CINE_MODE, !cur).apply();
                    sendServiceAction("ACTION_TOGGLE_CINE_MODE");
                    closeSubPanels();
                    buildMenu();
                }
            });
        }
        
        // Button Config Panel Listeners
        if (btnConfigClick1 != null) {
            btnConfigClick1.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (configuringButton == null) return;
                    String key = "btn_" + configuringButton + "_click_1_action";
                    int def = "mute".equals(configuringButton) ? 1 : 5;
                    int cur = getOverlayPrefs().getInt(key, def);
                    showActionPickerDialog(key, cur);
                }
            });
        }
        if (btnConfigClick2 != null) {
            btnConfigClick2.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (configuringButton == null) return;
                    String key = "btn_" + configuringButton + "_click_2_action";
                    int def = "mute".equals(configuringButton) ? 7 : ("youtube_190".equals(configuringButton) ? 4 : 0);
                    int cur = getOverlayPrefs().getInt(key, def);
                    showActionPickerDialog(key, cur);
                }
            });
        }
        if (btnConfigClick3 != null) {
            btnConfigClick3.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (configuringButton == null) return;
                    String key = "btn_" + configuringButton + "_click_3_action";
                    int def = "mute".equals(configuringButton) ? 8 : 0;
                    int cur = getOverlayPrefs().getInt(key, def);
                    showActionPickerDialog(key, cur);
                }
            });
        }
        if (btnConfigLong != null) {
            btnConfigLong.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (configuringButton == null) return;
                    String key = "btn_" + configuringButton + "_long_action";
                    int def = "mute".equals(configuringButton) ? 2 : ("youtube_190".equals(configuringButton) ? 3 : 4);
                    int cur = getOverlayPrefs().getInt(key, def);
                    showActionPickerDialog(key, cur);
                }
            });
        }
        if (btnConfigDurationDec != null) {
            btnConfigDurationDec.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    adjustDuration(-500);
                }
            });
        }
        if (btnConfigDurationInc != null) {
            btnConfigDurationInc.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    adjustDuration(500);
                }
            });
        }

        // Clock Config Panel Listeners
        if (btnClockSizeDec != null) {
            btnClockSizeDec.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { adjustClockIntPref("clock_size_sp", 16, -1, 8, 72); }
            });
        }
        if (btnClockSizeInc != null) {
            btnClockSizeInc.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { adjustClockIntPref("clock_size_sp", 16, 1, 8, 72); }
            });
        }
        if (btnClockPadDec != null) {
            btnClockPadDec.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { adjustClockIntPref("clock_padding_dp", 12, -1, 0, 100); }
            });
        }
        if (btnClockPadInc != null) {
            btnClockPadInc.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { adjustClockIntPref("clock_padding_dp", 12, 1, 0, 100); }
            });
        }
        if (btnClockXDec != null) {
            btnClockXDec.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { adjustClockIntPref("clock_pos_x_pct", 5, -1, 0, 100); }
            });
        }
        if (btnClockXInc != null) {
            btnClockXInc.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { adjustClockIntPref("clock_pos_x_pct", 5, 1, 0, 100); }
            });
        }
        if (btnClockYDec != null) {
            btnClockYDec.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { adjustClockIntPref("clock_pos_y_pct", 5, -1, 0, 100); }
            });
        }
        if (btnClockYInc != null) {
            btnClockYInc.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { adjustClockIntPref("clock_pos_y_pct", 5, 1, 0, 100); }
            });
        }
        if (btnClockTextColor != null) {
            btnClockTextColor.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int cur = getOverlayPrefs().getInt("clock_text_color_idx", 0);
                    showClockPickerDialog("clock_text_color_idx", CLOCK_COLOR_NAMES, cur);
                }
            });
        }
        if (btnClockBgColor != null) {
            btnClockBgColor.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int cur = getOverlayPrefs().getInt("clock_bg_color_idx", 0);
                    showClockPickerDialog("clock_bg_color_idx", CLOCK_BG_NAMES, cur);
                }
            });
        }
        if (btnClockAlpha != null) {
            btnClockAlpha.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int cur = getOverlayPrefs().getInt("clock_bg_alpha_pct", 2);
                    showClockPickerDialog("clock_bg_alpha_pct", CLOCK_ALPHA_NAMES, cur);
                }
            });
        }
        if (btnClockPosition != null) {
            btnClockPosition.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int cur = getOverlayPrefs().getInt("clock_position_idx", 0);
                    showClockPickerDialog("clock_position_idx", CLOCK_POSITION_NAMES, cur);
                }
            });
        }
        if (btnApplyClock != null) {
            btnApplyClock.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleOverlay(ButtonMappingService.KEY_CLOCK, "ACTION_TOGGLE_CLOCK");
                    updateClockConfigPanel();
                    buildMenu();
                }
            });
        }
    }

    private void updateCustomTimerUI() {
        if (txtCustomHours != null) txtCustomHours.setText(customHours + "h");
        if (txtCustomMins != null) txtCustomMins.setText(String.format("%02dm", customMins));
    }

    private void buildMenu() {
        menuContainer.removeAllViews();
        float d = getResources().getDisplayMetrics().density;
        String[] order = getMenuOrder();
        SharedPreferences op = getOverlayPrefs();
        SharedPreferences tp = getSharedPreferences(SleepTimerService.PREFS_NAME, MODE_PRIVATE);

        int prevGroup = -1;
        for (int i = 0; i < order.length; i++) {
            String id = order[i];
            int group = getGroupForId(id);
            if (prevGroup != -1 && prevGroup != group) addDivider(d);
            prevGroup = group;

            String label = getLabelForId(id, op, tp);
            int color    = getColorForId(id);
            TextView btn = createBtn(label, color, d);

            if (isReorderMode && reorderSelectedIndex == i) {
                btn.setBackgroundColor(Color.argb(210, 180, 100, 0));
            }
            final int idx = i; final String iid = id;
            btn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (isReorderMode) handleReorderClick(idx);
                    else {
                        lastFocusedId = iid;
                        handleItemClick(iid);
                    }
                }
            });
            // Le ponemos un tag para encontrarlo al restaurar foco
            btn.setTag(id);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) btn.getLayoutParams();
            lp.bottomMargin = Math.round(8 * d);
            btn.setLayoutParams(lp);
            menuContainer.addView(btn);
        }

        addDivider(d);
        String reorderLabel = isReorderMode ? "OK  Terminar Reordenamiento" : "=  Reordenar Menu";
        int reorderColor    = isReorderMode ? 0xFF4CAF50 : 0xFFAAAAAA;
        TextView reorderBtn = createBtn(reorderLabel, reorderColor, d);
        reorderBtn.setGravity(Gravity.CENTER);
        reorderBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                isReorderMode = !isReorderMode;
                if (!isReorderMode) reorderSelectedIndex = -1;
                if (isReorderMode) closeSubPanels();
                buildMenu();
            }
        });
        LinearLayout.LayoutParams rlp = (LinearLayout.LayoutParams) reorderBtn.getLayoutParams();
        rlp.bottomMargin = 0;
        reorderBtn.setLayoutParams(rlp);
        menuContainer.addView(reorderBtn);

        if (isReorderMode) {
            TextView hint = new TextView(this);
            hint.setText(reorderSelectedIndex == -1 ? "Selecciona un item para moverlo" : "Ahora selecciona donde colocarlo");
            hint.setTextColor(Color.argb(200, 255, 200, 80));
            hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            hint.setGravity(Gravity.CENTER);
            hint.setPadding(0, Math.round(6 * d), 0, 0);
            menuContainer.addView(hint);
        }

        if (menuContainer.getChildCount() > 0) {
            if (lastFocusedId != null) {
                View target = menuContainer.findViewWithTag(lastFocusedId);
                if (target != null) {
                    target.requestFocus();
                } else {
                    menuContainer.getChildAt(0).requestFocus();
                }
            } else {
                menuContainer.getChildAt(0).requestFocus();
            }
        }
    }

    private void closeSubPanels() {
        panelTimer.setVisibility(View.GONE);
        panelCine.setVisibility(View.GONE);
        panelBlueLight.setVisibility(View.GONE);
        if (panelButtonConfig != null) panelButtonConfig.setVisibility(View.GONE);
        if (panelClockConfig != null) panelClockConfig.setVisibility(View.GONE);
        openSubPanel = null;
        configuringButton = null;
        menuContainer.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
    }

    private void handleReorderClick(int index) {
        String[] order = getMenuOrder();
        if (reorderSelectedIndex == -1) {
            reorderSelectedIndex = index;
        } else if (reorderSelectedIndex == index) {
            reorderSelectedIndex = -1;
        } else {
            String temp = order[reorderSelectedIndex];
            order[reorderSelectedIndex] = order[index];
            order[index] = temp;
            saveMenuOrder(order);
            reorderSelectedIndex = -1;
        }
        buildMenu();
    }

    private void handleItemClick(String id) {
        switch (id) {
            case "manage_apps":
                try { startActivity(new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
                catch (Exception e) { Log.e(TAG, "manage_apps", e); }
                finish();
                break;
            case "timer":
                if ("timer".equals(openSubPanel)) {
                    closeSubPanels();
                } else {
                    closeSubPanels();
                    panelTimer.setVisibility(View.VISIBLE);
                    openSubPanel = "timer";
                    menuContainer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
                    updateTimerCancelButton();
                    View f = panelTimer.findViewById(R.id.btn_5m);
                    if (f != null) f.requestFocus();
                }
                break;
            case "blue_light":
                SharedPreferences bPrefs = getOverlayPrefs();
                boolean isLightOn = bPrefs.getBoolean(ButtonMappingService.KEY_BLUE_LIGHT, false);
                if (isLightOn) {
                    // Si está encendido, lo apagamos rápido
                    Intent i = new Intent(this, ButtonMappingService.class);
                    i.setAction("ACTION_SET_BLUE_LIGHT_PCT");
                    i.putExtra("pct", 0);
                    startService(i);
                } else {
                    // Si está apagado, lo encendemos con el último porcentaje persistido (o 30% default)
                    int lastPct = bPrefs.getInt("blue_light_pct", 30);
                    if (lastPct == 0) lastPct = 30;
                    Intent i = new Intent(this, ButtonMappingService.class);
                    i.setAction("ACTION_SET_BLUE_LIGHT_PCT");
                    i.putExtra("pct", lastPct);
                    startService(i);
                }
                // Además, mostramos el panel deslizante para poder regularlo
                if ("blue_light".equals(openSubPanel)) {
                    closeSubPanels();
                } else {
                    closeSubPanels();
                    panelBlueLight.setVisibility(View.VISIBLE);
                    openSubPanel = "blue_light";
                    menuContainer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
                    if (sliderBlueLight != null) sliderBlueLight.requestFocus();
                }
                buildMenu();
                break;
            case "clock":
                if ("clock_config".equals(openSubPanel)) {
                    closeSubPanels();
                } else {
                    closeSubPanels();
                    panelClockConfig.setVisibility(View.VISIBLE);
                    openSubPanel = "clock_config";
                    menuContainer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
                    updateClockConfigPanel();
                    if (btnClockTextColor != null) btnClockTextColor.requestFocus();
                }
                break;
            case "dimmer":
                toggleOverlay(ButtonMappingService.KEY_DIMMER, "ACTION_TOGGLE_DIMMER");
                buildMenu();
                break;
            case "grayscale":
                sendServiceAction("ACTION_TOGGLE_GRAYSCALE");
                finish();
                break;
            case "cine_mode":
                if ("cine".equals(openSubPanel)) {
                    closeSubPanels();
                } else {
                    closeSubPanels();
                    panelCine.setVisibility(View.VISIBLE);
                    openSubPanel = "cine";
                    menuContainer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
                    updateCineConfigPanel();
                    if (btnCineBlueLightConfig != null) btnCineBlueLightConfig.requestFocus();
                }
                break;
            case "screen_off":
                sendServiceAction("ACTION_SHOW_BLACK_SCREEN");
                finish();
                break;
            case "audio_output":
                sendServiceAction("ACTION_OPEN_AUDIO");
                finish();
                break;
            case "google_home":
                try { startActivity(new Intent("com.google.android.libraries.tv.smarthome.intent.action.OPEN_SMART_HOME").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
                catch (Exception e) { Log.e(TAG, "google_home", e); }
                finish();
                break;
            case "bluetooth":
                try {
                    Intent i = new Intent();
                    i.setClassName("com.android.tv.settings", "com.android.tv.settings.accessories.AddAccessoryActivity");
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                } catch (Exception e) {
                    try { startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
                    catch (Exception e2) { Log.e(TAG, "bluetooth", e2); }
                }
                finish();
                break;
            case "system_info":
                sendServiceAction("ACTION_SHOW_SYSTEM_INFO");
                finish();
                break;
            case "reboot":
                sendServiceAction("ACTION_REBOOT");
                finish();
                break;
            case "config_mute":
            case "config_youtube_190":
            case "config_youtube_189":
                String btnKey = id.substring("config_".length()); // "mute", "youtube_190", "youtube_189"
                if (id.equals(openSubPanel)) {
                    closeSubPanels();
                } else {
                    closeSubPanels();
                    configuringButton = btnKey;
                    panelButtonConfig.setVisibility(View.VISIBLE);
                    openSubPanel = id;
                    menuContainer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
                    updateButtonConfigPanel();
                    if (btnConfigClick1 != null) btnConfigClick1.requestFocus();
                }
                break;
            default:
                Log.w(TAG, "Unknown item: " + id);
        }
    }

    private String getLabelForId(String id, SharedPreferences op, SharedPreferences tp) {
        switch (id) {
            case "manage_apps":  return "Apps  Administrar Apps";
            case "timer":        return getTimerLabel(tp);
            case "blue_light": {
                int pct = op.getInt("blue_light_pct", 0);
                return "Luz Azul  Filtro Luz Azul  [" + (pct == 0 ? "OFF" : pct + "%") + "]";
            }
            case "clock":        return fmtToggle("Reloj  Reloj en Pantalla  (tap=configurar)", op.getBoolean(ButtonMappingService.KEY_CLOCK,  false));
            case "dimmer":       return fmtToggle("Noche  Dimmer de Pantalla",  op.getBoolean(ButtonMappingService.KEY_DIMMER, false));
            case "grayscale":    return fmtToggle("B/N  Escala de Grises",      isGrayscaleOn());
            case "cine_mode":    return fmtToggle("Cine  Modo Cine",            op.getBoolean(ButtonMappingService.KEY_CINE_MODE, false));
            case "screen_off":   return "Sleep  Apagar Pantalla";
            case "audio_output": return "Audio  Salida de Audio";
            case "google_home":  return "Home  Google Home Panel";
            case "bluetooth":    return "BT  Auriculares Bluetooth";
            case "system_info":  return "Info  Info del Sistema";
            case "reboot":       return "Reiniciar Chromecast";
            case "config_mute":        return "Config  Configurar Botón Mute";
            case "config_youtube_190": return "Config  Configurar YouTube (190)";
            case "config_youtube_189": return "Config  Configurar YouTube (189)";
            default:             return id;
        }
    }

    private int getColorForId(String id) { return "reboot".equals(id) ? 0xFFFF6B6B : Color.WHITE; }

    private int getGroupForId(String id) {
        switch (id) {
            case "manage_apps": case "timer": case "blue_light":
            case "clock": case "dimmer": case "grayscale": case "cine_mode":
                return 1;
            case "screen_off": case "audio_output":
                return 2;
            case "google_home": case "bluetooth": case "system_info": case "reboot":
                return 3;
            default:
                return 4;
        }
    }

    private String getTimerLabel(SharedPreferences tp) {
        long endTime   = tp.getLong(SleepTimerService.KEY_END_TIME, 0);
        long remaining = endTime - System.currentTimeMillis();
        if (endTime > 0 && remaining > 0) {
            int mins = (int)(remaining / 60000);
            return "Timer  Sleep Timer - " + mins + " min restantes";
        }
        return "Timer  Sleep Timer  (tap para abrir)";
    }

    private boolean isGrayscaleOn() {
        try { return Settings.Secure.getInt(getContentResolver(), "accessibility_display_daltonizer_enabled", 0) != 0; }
        catch (Exception e) { return false; }
    }

    private String fmtToggle(String label, boolean on) { return label + "   " + (on ? "[ON]" : "[OFF]"); }

    private TextView createBtn(String label, int color, float d) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(color);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        int h = Math.round(52 * d), pad = Math.round(16 * d);
        tv.setPadding(pad, 0, pad, 0);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setBackgroundResource(R.drawable.card_background);
        tv.setFocusable(true); tv.setClickable(true);
        tv.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h));
        return tv;
    }

    private void addDivider(float d) {
        View divider = new View(this);
        divider.setBackgroundColor(Color.argb(60, 255, 255, 255));
        int m = Math.round(6 * d);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, Math.round(d)));
        lp.topMargin = m; lp.bottomMargin = m;
        divider.setLayoutParams(lp);
        menuContainer.addView(divider);
    }

    private void updateTimerCancelButton() {
        SharedPreferences tp = getSharedPreferences(SleepTimerService.PREFS_NAME, MODE_PRIVATE);
        long endTime = tp.getLong(SleepTimerService.KEY_END_TIME, 0);
        boolean active = endTime > 0 && (endTime - System.currentTimeMillis()) > 0;
        if (btnCancelTimer != null) btnCancelTimer.setVisibility(active ? View.VISIBLE : View.GONE);
    }

    private void updateCineConfigPanel() {
        SharedPreferences cp = getSharedPreferences("cine_prefs", MODE_PRIVATE);
        if (btnCineBlueLightConfig != null)
            btnCineBlueLightConfig.setText("   Filtro Azul:  " + (cp.getBoolean("cine_blue_light", true) ? "ON" : "OFF"));
        if (btnCineDimmerConfig != null)
            btnCineDimmerConfig.setText("   Dimmer:  " + (cp.getBoolean("cine_dimmer", false) ? "ON" : "OFF"));
        if (btnCineTimerConfig != null) {
            int t = cp.getInt("cine_timer", 0);
            String tLabel = t == 0 ? "Desactivado" : (t < 60 ? t + " min" : (t / 60) + "h" + (t % 60 > 0 ? " " + (t % 60) + "m" : ""));
            btnCineTimerConfig.setText("   Timer:  " + tLabel + "  (tap=cambiar)");
        }
        if (btnApplyCine != null) {
            boolean active = getOverlayPrefs().getBoolean(ButtonMappingService.KEY_CINE_MODE, false);
            btnApplyCine.setText(active ? "[ Desactivar Modo Cine ]" : "[ Activar Modo Cine ]");
        }
    }

    private void updateButtonConfigPanel() {
        if (configuringButton == null) return;

        SharedPreferences prefs = getOverlayPrefs();
        String btnName = configuringButton; // "mute", "youtube_190", "youtube_189"
        
        // Determine defaults based on the button
        int defClick1 = 0, defClick2 = 0, defClick3 = 0, defLong = 0, defDur = 1000;
        String title = "Configurar Botón: ";
        if ("mute".equals(btnName)) {
            title += "Mute";
            defClick1 = 1; defClick2 = 7; defClick3 = 8; defLong = 2; defDur = 1000;
        } else if ("youtube_190".equals(btnName)) {
            title += "YouTube (190)";
            defClick1 = 5; defClick2 = 4; defClick3 = 0; defLong = 3; defDur = 2000;
        } else if ("youtube_189".equals(btnName)) {
            title += "YouTube (189)";
            defClick1 = 5; defClick2 = 0; defClick3 = 0; defLong = 4; defDur = 2000;
        }

        if (txtConfigTitle != null) txtConfigTitle.setText(title);

        int actClick1 = prefs.getInt("btn_" + btnName + "_click_1_action", defClick1);
        int actClick2 = prefs.getInt("btn_" + btnName + "_click_2_action", defClick2);
        int actClick3 = prefs.getInt("btn_" + btnName + "_click_3_action", defClick3);
        int actLong   = prefs.getInt("btn_" + btnName + "_long_action", defLong);
        int durationMs = prefs.getInt("btn_" + btnName + "_long_duration_ms", defDur);

        if (btnConfigClick1 != null) btnConfigClick1.setText("   Click Simple:  " + getActionName(actClick1));
        if (btnConfigClick2 != null) btnConfigClick2.setText("   Doble Click:  " + getActionName(actClick2));
        if (btnConfigClick3 != null) btnConfigClick3.setText("   Triple Click:  " + getActionName(actClick3));
        if (btnConfigLong != null)   btnConfigLong.setText("   Sostenido:  " + getActionName(actLong));
        
        if (txtConfigDuration != null) {
            double secs = durationMs / 1000.0;
            txtConfigDuration.setText(String.format(java.util.Locale.US, "%.1fs", secs));
        }
    }

    private String getActionName(int actionId) {
        if (actionId >= 0 && actionId < ACTION_NAMES.length) {
            return ACTION_NAMES[actionId];
        }
        return "Desconocido";
    }

    private void showActionPickerDialog(final String configKey, int currentAction) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Seleccionar Acción");
        builder.setSingleChoiceItems(ACTION_NAMES, currentAction, new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface dialog, int which) {
                getOverlayPrefs().edit().putInt(configKey, which).apply();
                updateButtonConfigPanel();
                dialog.dismiss();
            }
        });
        builder.create().show();
    }

    private void adjustDuration(int delta) {
        if (configuringButton == null) return;
        String key = "btn_" + configuringButton + "_long_duration_ms";
        int def = "mute".equals(configuringButton) ? 1000 : 2000;
        int cur = getOverlayPrefs().getInt(key, def);
        int next = cur + delta;
        if (next < 500) next = 500;
        if (next > 5000) next = 5000;
        getOverlayPrefs().edit().putInt(key, next).apply();
        updateButtonConfigPanel();
    }

    private static final String[] CLOCK_SIZE_NAMES = {"Chico (12sp)", "Mediano (16sp)", "Grande (20sp)", "Extra Grande (24sp)"};
    private static final String[] CLOCK_COLOR_NAMES = {"Blanco", "Negro", "Amarillo", "Rojo", "Verde", "Azul"};
    private static final String[] CLOCK_BG_NAMES = {"Negro", "Gris Oscuro", "Azul Marino", "Ninguno (Transparente)"};
    private static final String[] CLOCK_ALPHA_NAMES = {"0% (Transparente)", "25%", "35%", "50%", "75%", "100% (Opaco)"};
    private static final String[] CLOCK_POSITION_NAMES = {"Arriba Derecha", "Arriba Izquierda", "Abajo Derecha", "Abajo Izquierda", "Centro"};

    private void updateClockConfigPanel() {
        SharedPreferences prefs = getOverlayPrefs();
        int colorIdx = prefs.getInt("clock_text_color_idx", 0);
        int bgIdx = prefs.getInt("clock_bg_color_idx", 0);
        int alphaIdx = prefs.getInt("clock_bg_alpha_pct", 2);
        int posIdx = prefs.getInt("clock_position_idx", 0);
        
        int sizeSp = prefs.getInt("clock_size_sp", 16);
        int paddingDp = prefs.getInt("clock_padding_dp", 12);
        int posX = prefs.getInt("clock_pos_x_pct", 5);
        int posY = prefs.getInt("clock_pos_y_pct", 5);
        
        boolean active = prefs.getBoolean(ButtonMappingService.KEY_CLOCK, false);

        if (btnClockTextColor != null) btnClockTextColor.setText("   Color Letra:  " + CLOCK_COLOR_NAMES[colorIdx]);
        if (btnClockBgColor != null) btnClockBgColor.setText("   Color Fondo:  " + CLOCK_BG_NAMES[bgIdx]);
        if (btnClockAlpha != null) btnClockAlpha.setText("   Transparencia Fondo:  " + CLOCK_ALPHA_NAMES[alphaIdx]);
        if (btnClockPosition != null) btnClockPosition.setText("   Posición Base:  " + CLOCK_POSITION_NAMES[posIdx]);
        
        if (txtClockSize != null) txtClockSize.setText(sizeSp + "sp");
        if (txtClockPad != null) txtClockPad.setText(paddingDp + "dp");
        if (txtClockX != null) txtClockX.setText(posX + "%");
        if (txtClockY != null) txtClockY.setText(posY + "%");
        
        if (btnApplyClock != null) btnApplyClock.setText(active ? "[ Desactivar Reloj ]" : "[ Activar Reloj ]");
    }

    private void adjustClockIntPref(String key, int def, int delta, int min, int max) {
        int cur = getOverlayPrefs().getInt(key, def);
        int next = cur + delta;
        if (next < min) next = min;
        if (next > max) next = max;
        getOverlayPrefs().edit().putInt(key, next).apply();
        updateClockConfigPanel();
        sendServiceAction("ACTION_UPDATE_CLOCK");
    }

    private void showClockPickerDialog(final String configKey, final String[] items, int currentSelection) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Seleccionar Opción");
        builder.setSingleChoiceItems(items, currentSelection, new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface dialog, int which) {
                getOverlayPrefs().edit().putInt(configKey, which).apply();
                updateClockConfigPanel();
                sendServiceAction("ACTION_UPDATE_CLOCK");
                dialog.dismiss();
            }
        });
        builder.create().show();
    }

    private String[] getMenuOrder() {
        SharedPreferences prefs = getSharedPreferences(PREFS_ORDER, MODE_PRIVATE);
        String saved = prefs.getString(KEY_ORDER, null);
        if (saved == null || saved.isEmpty()) return ALL_ITEM_IDS.clone();
        String[] loaded = saved.split(",");
        if (loaded.length == ALL_ITEM_IDS.length) return loaded;
        return ALL_ITEM_IDS.clone();
    }

    private void saveMenuOrder(String[] order) {
        getSharedPreferences(PREFS_ORDER, MODE_PRIVATE).edit().putString(KEY_ORDER, TextUtils.join(",", order)).apply();
    }

    private void startTimer(int minutes) {
        Intent intent = new Intent(this, SleepTimerService.class);
        intent.setAction("ACTION_START_TIMER");
        intent.putExtra("minutes", minutes);
        startService(intent);
        finish();
    }

    private void toggleOverlay(String prefKey, String action) {
        boolean current = getOverlayPrefs().getBoolean(prefKey, false);
        sendServiceAction(action);
        getOverlayPrefs().edit().putBoolean(prefKey, !current).apply();
    }

    private void sendServiceAction(String action) {
        sendServiceAction(action, null);
    }

    private void sendServiceAction(String action, android.os.Bundle extras) {
        ButtonMappingService service = ButtonMappingService.instance;
        if (service != null) {
            service.handleAction(action, extras);
        } else {
            Intent i = new Intent(this, ButtonMappingService.class);
            i.setAction(action);
            if (extras != null) i.putExtras(extras);
            startService(i);
        }
    }

    private SharedPreferences getOverlayPrefs() {
        return getSharedPreferences(ButtonMappingService.OVERLAY_PREFS, MODE_PRIVATE);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (openSubPanel != null) { closeSubPanels(); return true; }
            if (isReorderMode) { isReorderMode = false; reorderSelectedIndex = -1; buildMenu(); return true; }
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
package ru.nullch.ottomaticfork;

import android.hardware.input.InputManager;
import android.os.Bundle;
import android.system.Os;
import android.util.Log;
import android.view.InputDevice;
import android.view.ViewGroup;

import org.libsdl.app.SDLActivity;

public class OttoActivity extends SDLActivity {
    public static final String EXTRA_DATA_PATH = "data_path";
    private static final String TAG = "OttoMatic";

    private GamepadOverlay overlay;
    private InputManager inputManager;
    private final InputManager.InputDeviceListener deviceListener = new InputManager.InputDeviceListener() {
        @Override public void onInputDeviceAdded(int id) { updateOverlayVisibility(); }
        @Override public void onInputDeviceRemoved(int id) { updateOverlayVisibility(); }
        @Override public void onInputDeviceChanged(int id) { updateOverlayVisibility(); }
    };

    /** Pushes the on-screen pad state to the game (see game/src/System/AndroidPad.c). */
    static native void nativeSetPad(int buttons, int lx, int ly, int rx, int ry);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            String filesDir = getFilesDir().getAbsolutePath();
            String dataPath = getIntent().getStringExtra(EXTRA_DATA_PATH);
            if (dataPath == null) dataPath = filesDir + "/Data";
            Os.setenv("OTTO_DATA_PATH", dataPath, true);
            Os.setenv("HOME", filesDir, true);
            Os.setenv("XDG_CONFIG_HOME", filesDir, true);
            // gl4es: translate desktop GL 1.x/2.x onto GLES2
            Os.setenv("LIBGL_ES", "2", true);
            Os.setenv("LIBGL_NPOT", "3", true);
            Os.setenv("LIBGL_MIPMAP", "3", true);
            int langId = getIntent().getIntExtra("EXTRA_LANG_ID", -1);
            if (langId >= 0) {
                Os.setenv("OTTO_FORCE_LANG", String.valueOf(langId), true);
            }
        } catch (Exception e) {
            Log.e(TAG, "setenv failed", e);
        }
        super.onCreate(savedInstanceState);

        overlay = new GamepadOverlay(this);
        addContentView(overlay, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        inputManager = (InputManager) getSystemService(INPUT_SERVICE);
        inputManager.registerInputDeviceListener(deviceListener, null);
        updateOverlayVisibility();
    }

    public static void openTelegramChannel() {
        try {
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://t.me/nullchdays"));
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            if (getContext() != null) {
                getContext().startActivity(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to open Telegram URL", e);
        }
    }

    @Override
    protected void onDestroy() {
        if (inputManager != null) inputManager.unregisterInputDeviceListener(deviceListener);
        super.onDestroy();
    }

    /** Hide the touch controls while a physical gamepad is connected. */
    private void updateOverlayVisibility() {
        boolean hasPhysicalPad = false;
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice d = InputDevice.getDevice(id);
            if (d == null || d.isVirtual()) continue;
            int s = d.getSources();
            if ((s & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                    || (s & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
                hasPhysicalPad = true;
                break;
            }
        }
        final boolean show = !hasPhysicalPad;
        runOnUiThread(() -> { if (overlay != null) overlay.setPadVisible(show); });
    }
}

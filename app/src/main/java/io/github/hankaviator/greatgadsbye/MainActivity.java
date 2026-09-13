package io.github.hankaviator.greatgadsbye;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.materialswitch.MaterialSwitch;

public final class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        applySystemBarInsets(findViewById(R.id.root));

        FeatureSettings.ensureDefaults(this);
        bind(R.id.gmail_switch, FeatureSettings.FEATURE_GMAIL);
        bind(R.id.maps_switch, FeatureSettings.FEATURE_MAPS);
    }

    private static void applySystemBarInsets(View root) {
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private void bind(int viewId, String feature) {
        MaterialSwitch toggle = findViewById(viewId);
        toggle.setChecked(FeatureSettings.isEnabled(this, feature));
        toggle.setOnCheckedChangeListener((button, enabled) ->
                FeatureSettings.preferences(this).edit().putBoolean(feature, enabled).apply());
    }
}

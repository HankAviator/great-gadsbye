package io.github.hankaviator.greatgadsbye;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.TextView;

import java.text.Normalizer;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.WeakHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Removes sponsored UI from the supported Google apps. */
public final class GreatGadsbye implements IXposedHookLoadPackage {
    private static final String GMAIL_PACKAGE = "com.google.android.gm";
    private static final String MAPS_PACKAGE = "com.google.android.apps.maps";
    private static final String TAG = "GreatGadsbye";
    private static final int MAX_ANCESTORS = 16;

    private static final Set<String> SPONSORED_LABELS = labels(
            "sponsored", "sponsorisé", "sponsorisée", "gesponsert", "patrocinado",
            "patrocinada", "sponsorizzato", "sponsorizzata", "スポンサー", "스폰서",
            "赞助内容", "贊助內容", "赞助", "贊助", "реклама", "sponsrad"
    );

    private static final Set<Activity> OBSERVED_ACTIVITIES =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final WeakHashMap<View, SavedState> HIDDEN_VIEWS = new WeakHashMap<>();
    private static volatile boolean cachedEnabled = true;
    private static volatile boolean settingsInitialized;
    private static String activeFeature;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        if (!loadPackageParam.packageName.equals(loadPackageParam.processName)) {
            return;
        }
        if (GMAIL_PACKAGE.equals(loadPackageParam.packageName)) {
            activeFeature = FeatureSettings.FEATURE_GMAIL;
        } else if (MAPS_PACKAGE.equals(loadPackageParam.packageName)) {
            activeFeature = FeatureSettings.FEATURE_MAPS;
        } else {
            return;
        }

        XposedBridge.log(TAG + ": loaded in " + loadPackageParam.processName);
        XposedBridge.hookAllMethods(Activity.class, "onPostResume", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                settingsInitialized = false;
                observe((Activity) param.thisObject);
            }
        });

        XposedBridge.hookAllMethods(TextView.class, "setText", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                inspectChangedView((View) param.thisObject);
            }
        });

        XposedBridge.hookAllMethods(View.class, "setContentDescription", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                inspectChangedView((View) param.thisObject);
            }
        });

        XposedBridge.hookAllMethods(ViewGroup.class, "addView", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args.length == 0 || !(param.args[0] instanceof View)) {
                    return;
                }
                ViewGroup parent = (ViewGroup) param.thisObject;
                View child = (View) param.args[0];
                if (FeatureSettings.FEATURE_GMAIL.equals(activeFeature)
                        && isRecyclerView(parent)
                        && isFeatureEnabled(child.getContext())
                        && containsAdSignature(child)) {
                    hide(child);
                }
            }
        });
    }

    private static void inspectChangedView(View view) {
        if (!isFeatureEnabled(view.getContext()) || !isSponsoredMarker(view)) {
            return;
        }
        View target = findRemovalTarget(view);
        if (target != null) {
            hide(target);
        }
    }

    private static void observe(Activity activity) {
        View root = activity.getWindow().getDecorView();
        synchronized (OBSERVED_ACTIVITIES) {
            if (OBSERVED_ACTIVITIES.add(activity)) {
                Runnable settledScan = () -> scan(root);
                root.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
                    root.removeCallbacks(settledScan);
                    root.postDelayed(settledScan, 300L);
                });
                XposedBridge.log(TAG + ": observing " + activity.getClass().getName());
            }
        }
        scan(root);
        root.postDelayed(() -> scan(root), 250L);
        root.postDelayed(() -> scan(root), 1_000L);
        root.postDelayed(() -> scan(root), 2_500L);
        root.postDelayed(() -> scan(root), 5_000L);
    }

    /** Returns true when a visibility change requires the current draw to be retried. */
    private static boolean scan(View root) {
        if (root == null || !root.isAttachedToWindow()) {
            return false;
        }
        if (!isFeatureEnabled(root.getContext())) {
            return restoreAll();
        }

        Set<View> sponsoredViews = Collections.newSetFromMap(new WeakHashMap<>());
        collectSponsoredViews(root, sponsoredViews);
        boolean changed = false;
        synchronized (HIDDEN_VIEWS) {
            for (View oldView : new HashSet<>(HIDDEN_VIEWS.keySet())) {
                if (!sponsoredViews.contains(oldView) && !containsAdSignature(oldView)) {
                    changed |= restore(oldView);
                }
            }
        }
        for (View view : sponsoredViews) {
            changed |= hide(view);
        }
        return changed;
    }

    private static void collectSponsoredViews(View view, Set<View> output) {
        if (isSponsoredMarker(view)) {
            View target = findRemovalTarget(view);
            if (target != null) {
                output.add(target);
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectSponsoredViews(group.getChildAt(i), output);
            }
        }
    }

    private static boolean isSponsoredMarker(View view) {
        if ((view instanceof TextView && isSponsoredText(((TextView) view).getText()))
                || isSponsoredText(view.getContentDescription())) {
            return true;
        }
        if (FeatureSettings.FEATURE_MAPS.equals(activeFeature)) {
            return false;
        }
        String name = resourceName(view);
        return name.contains("sponsor") || name.contains("ad_badge")
                || name.contains("advertisement_label");
    }

    private static boolean isSponsoredText(CharSequence value) {
        if (value == null || value.length() > 160) {
            return false;
        }
        String normalized = normalize(value.toString());
        for (String label : SPONSORED_LABELS) {
            if (normalized.equals(label) || normalized.startsWith(label + ":")
                    || normalized.startsWith(label + ",")
                    || normalized.startsWith(label + " ·")
                    || normalized.startsWith(label + " -")) {
                return true;
            }
        }
        return false;
    }

    private static View findRemovalTarget(View start) {
        if (FeatureSettings.FEATURE_GMAIL.equals(activeFeature)) {
            return findRecyclerRow(start);
        }
        return findMapsAdContainer(start);
    }

    private static View findRecyclerRow(View start) {
        View child = start;
        ViewParent parent = start.getParent();
        for (int depth = 0; parent instanceof ViewGroup && depth < MAX_ANCESTORS; depth++) {
            ViewGroup group = (ViewGroup) parent;
            if (isRecyclerView(group)) {
                return child;
            }
            child = group;
            parent = group.getParent();
        }
        return null;
    }

    private static View findMapsAdContainer(View start) {
        View child = start;
        View fallback = isSponsoredText(start.getContentDescription()) ? start : null;
        ViewParent parent = start.getParent();
        for (int depth = 0; parent instanceof ViewGroup && depth < MAX_ANCESTORS; depth++) {
            ViewGroup group = (ViewGroup) parent;
            String name = resourceName(group);
            if ("business_place_card".equals(name) || isRecyclerView(group)) {
                return child;
            }
            if (isSponsoredText(group.getContentDescription())) {
                fallback = group;
            }
            child = group;
            parent = group.getParent();
        }
        return fallback;
    }

    private static boolean isRecyclerView(View view) {
        Class<?> type = view.getClass();
        while (type != null) {
            if ("androidx.recyclerview.widget.RecyclerView".equals(type.getName())
                    || "android.support.v7.widget.RecyclerView".equals(type.getName())) {
                return true;
            }
            type = type.getSuperclass();
        }
        return false;
    }

    private static boolean containsAdSignature(View view) {
        if (isSponsoredMarker(view)) {
            return true;
        }
        String name = resourceName(view);
        if (name.contains("ad_teaser") || name.contains("promoted_place")) {
            return true;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (containsAdSignature(group.getChildAt(i))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String resourceName(View view) {
        if (view.getId() == View.NO_ID) {
            return "";
        }
        try {
            return view.getResources().getResourceEntryName(view.getId())
                    .toLowerCase(Locale.ROOT);
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static boolean isFeatureEnabled(Context context) {
        if (settingsInitialized) {
            return cachedEnabled;
        }
        try {
            XSharedPreferences preferences = new XSharedPreferences(
                    FeatureSettings.MODULE_PACKAGE, FeatureSettings.PREFERENCES);
            cachedEnabled = preferences.getBoolean(activeFeature, true);
        } catch (RuntimeException error) {
            cachedEnabled = true;
            XposedBridge.log(TAG + ": could not read settings; defaulting enabled: " + error);
        }
        settingsInitialized = true;
        return cachedEnabled;
    }

    private static boolean hide(View view) {
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        boolean changed = view.getVisibility() != View.GONE || view.getAlpha() != 0f
                || (layoutParams != null && layoutParams.height != 0)
                || view.getImportantForAccessibility()
                != View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS;
        synchronized (HIDDEN_VIEWS) {
            if (!HIDDEN_VIEWS.containsKey(view)) {
                HIDDEN_VIEWS.put(view, new SavedState(view.getVisibility(), view.getAlpha(),
                        view.getImportantForAccessibility(), layoutParams));
            }
        }
        if (!changed) {
            return false;
        }
        if (layoutParams != null) {
            layoutParams.height = 0;
            if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) layoutParams;
                margins.topMargin = 0;
                margins.bottomMargin = 0;
            }
            view.setLayoutParams(layoutParams);
        }
        view.setAlpha(0f);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        view.setVisibility(View.GONE);
        view.requestLayout();
        return changed;
    }

    private static boolean restoreAll() {
        boolean changed = false;
        synchronized (HIDDEN_VIEWS) {
            for (View view : new HashSet<>(HIDDEN_VIEWS.keySet())) {
                changed |= restore(view);
            }
        }
        return changed;
    }

    private static boolean restore(View view) {
        SavedState state;
        synchronized (HIDDEN_VIEWS) {
            state = HIDDEN_VIEWS.remove(view);
        }
        if (state == null) {
            return false;
        }
        view.setVisibility(state.visibility);
        view.setAlpha(state.alpha);
        view.setImportantForAccessibility(state.importantForAccessibility);
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (layoutParams != null && state.hadLayoutParams) {
            layoutParams.height = state.layoutHeight;
            if (layoutParams instanceof ViewGroup.MarginLayoutParams && state.hadMargins) {
                ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) layoutParams;
                margins.topMargin = state.topMargin;
                margins.bottomMargin = state.bottomMargin;
            }
            view.setLayoutParams(layoutParams);
        }
        view.requestLayout();
        return true;
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static Set<String> labels(String... values) {
        Set<String> result = new HashSet<>();
        for (String value : values) {
            result.add(normalize(value));
        }
        return Collections.unmodifiableSet(result);
    }

    private static final class SavedState {
        final int visibility;
        final float alpha;
        final int importantForAccessibility;
        final boolean hadLayoutParams;
        final int layoutHeight;
        final boolean hadMargins;
        final int topMargin;
        final int bottomMargin;

        SavedState(int visibility, float alpha, int importantForAccessibility,
                ViewGroup.LayoutParams layoutParams) {
            this.visibility = visibility;
            this.alpha = alpha;
            this.importantForAccessibility = importantForAccessibility;
            hadLayoutParams = layoutParams != null;
            layoutHeight = layoutParams == null ? 0 : layoutParams.height;
            hadMargins = layoutParams instanceof ViewGroup.MarginLayoutParams;
            if (hadMargins) {
                ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) layoutParams;
                topMargin = margins.topMargin;
                bottomMargin = margins.bottomMargin;
            } else {
                topMargin = 0;
                bottomMargin = 0;
            }
        }
    }
}

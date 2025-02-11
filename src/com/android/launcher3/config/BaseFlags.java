/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.config;

/**
 * Defines a set of flags used to control various launcher behaviors.
 * <p>
 * All the flags should be defined here with appropriate default values. To override a value,
 * redefine it in {@link }.
 * <p>
 * This class is kept package-private to prevent direct access.
 */
public abstract class BaseFlags {

    BaseFlags() {
    }

    public static final boolean IS_DOGFOOD_BUILD = false;

    // When enabled allows to use any point on the fast scrollbar to start dragging.
    public static final boolean LAUNCHER3_DIRECT_SCROLL = true;
    // When enabled the promise icon is visible in all apps while installation an app.
    public static final boolean LAUNCHER3_PROMISE_APPS_IN_ALL_APPS = true;
    // When enabled allows use of physics based motions in the Launcher.
    public static final boolean LAUNCHER3_PHYSICS = true;
    // When enabled allows use of spring motions on the icons.
    public static final boolean LAUNCHER3_SPRING_ICONS = true;
    // When enabled the all-apps icon is not added to the hotseat.
    public static final boolean NO_ALL_APPS_ICON = false;
    // When enabled the status bar may show dark icons based on the top of the wallpaper.
    public static final boolean LIGHT_STATUS_BAR = true;
    // When enabled, the qsb will be moved to the hotseat.
    public static final boolean QSB_IN_HOTSEAT = true;
    // Features to control Launcher3Go behavior
    public static final boolean GO_DISABLE_WIDGETS = false;
    // When enabled uses the AllAppsRadialGradientAndScrimDrawable for all apps
    public static final boolean LAUNCHER3_GRADIENT_ALL_APPS = true;
    // Feature flag to enable moving the QSB on the 0th screen of the workspace.
    public static boolean QSB_ON_FIRST_SCREEN = true;
    // When enabled shows a work profile tab in all apps
    public static final boolean ALL_APPS_TABS_ENABLED = true;
    public static boolean LAUNCHER3_DISABLE_ICON_NORMALIZATION = false;

    // When true, custom widgets are loaded using CustomWidgetParser.
    public static final boolean ENABLE_CUSTOM_WIDGETS = true;

}

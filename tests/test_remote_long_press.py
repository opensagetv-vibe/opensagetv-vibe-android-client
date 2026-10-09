import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
PROCESSOR = ROOT / (
    "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/"
    "android/ui/keymaps/KeyMapProcessor.java"
)


class RemoteLongPressTest(unittest.TestCase):
    def test_platform_and_elapsed_long_press_forms_are_supported(self):
        text = PROCESSOR.read_text(encoding="utf-8")
        self.assertIn("event.isLongPress()", text)
        self.assertIn("KeyEvent.FLAG_LONG_PRESS", text)
        self.assertIn("platformLongPress || elapsedLongPress", text)
        self.assertIn("event.getEventTime() - event.getDownTime()", text)
        self.assertIn("boolean releaseLongPress = !longPress", text)
        self.assertIn("handleKeyPress(keyMap, keyCode, event, true)", text)
        self.assertIn("new Handler(Looper.getMainLooper())", text)
        self.assertIn("schedulePendingLongPress(keyMap, keyCode, event)", text)
        self.assertIn("longPressHandler.postDelayed(pendingLongPressTask, delayMs)", text)
        self.assertIn("longPressHandler.removeCallbacks(pendingLongPressTask)", text)

    def test_activity_lifecycle_cancels_pending_remote_hold(self):
        listener = (
            ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/"
            "miniclient/android/ui/MiniClientKeyListener.java"
        ).read_text(encoding="utf-8")
        lifecycle = (
            ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/"
            "miniclient/android/UIActivityLifeCycleHandler.java"
        ).read_text(encoding="utf-8")
        self.assertIn("public void shutdown()", listener)
        self.assertGreaterEqual(lifecycle.count("keyListener.shutdown();"), 3)

    def test_fresh_physical_gesture_cannot_inherit_prior_long_press(self):
        text = PROCESSOR.read_text(encoding="utf-8")
        self.assertIn("private int activeGestureKeyCode", text)
        self.assertIn("private long activeGestureDownTime", text)
        self.assertIn("beginInputGesture(keyCode, event)", text)
        self.assertIn("activeGestureKeyCode != keyCode", text)
        self.assertIn("activeGestureDownTime != event.getDownTime()", text)
        self.assertIn("resetInputGestureState();", text)
        self.assertIn("finishInputGesture();", text)
        self.assertLess(
            text.index("beginInputGesture(keyCode, event)"),
            text.index("if (longPressCancel) return true;"),
        )

    def test_dvd_menu_only_intercepts_short_remote_presses(self):
        text = PROCESSOR.read_text(encoding="utf-8")
        self.assertIn("if (!longPress && dvdMenuCommand != null)", text)
        self.assertNotIn("\n        if (dvdMenuCommand != null)\n", text)

    def test_legacy_dvd_mappings_remain_available_behind_hold_preset(self):
        text = PROCESSOR.read_text(encoding="utf-8")
        self.assertIn("getDvdTitlePlaybackCommand(keyCode, longPress)", text)
        self.assertIn("isDvdSessionPending()", text)
        self.assertIn("client.getPlayer().isDvdMenuNavigationActive()", text)
        self.assertIn('new MediaMappingPreferences("dvdplaying",', text)
        self.assertIn("dvdPlaybackPrefs.getLeftLongPress()", text)
        self.assertIn("dvdPlaybackPrefs.getLeft()", text)
        self.assertIn("dvdPlaybackPrefs.getRightLongPress()", text)
        self.assertIn("dvdPlaybackPrefs.getRight()", text)
        self.assertIn("dvdTitleCommand != SageCommand.NONE", text)

        prefs = (
            ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/"
            "miniclient/android/preferences/MediaMappingPreferences.java"
        ).read_text(encoding="utf-8")
        self.assertIn('case "dvdplaying":', prefs)
        self.assertIn("SageCommand.LEFT_REW.getKey()", prefs)
        self.assertIn("SageCommand.RIGHT_FF.getKey()", prefs)
        # Combined events can be overridden by the STV; do not make a source
        # comment stand in for a physical proof of an exact timeline skip.
        self.assertIn("sendDvdOppositeScanSequence(command, longPress)", text)
        self.assertIn("command = resolveDvdScanCommand(command)", text)
        self.assertIn("SageCommand.DVD_CHAPTER_PREV.getKey()", prefs)
        self.assertIn("SageCommand.DVD_CHAPTER_NEXT.getKey()", prefs)

        xml = (
            ROOT / "source/dev/android-shared/src/main/res/xml/media_mappings_prefs.xml"
        ).read_text(encoding="utf-8")
        for key in (
            "dvdplaying_left",
            "dvdplaying_right",
            "dvdplaying_left_long_press",
            "dvdplaying_right_long_press",
        ):
            self.assertIn(f'android:key="{key}"', xml)

    def test_legacy_dvd_title_hold_does_not_also_emit_short_seek(self):
        text = PROCESSOR.read_text(encoding="utf-8")
        self.assertIn("private boolean isDvdTitleDirectionGesture(int keyCode)", text)
        self.assertIn("&& !isDvdTitleDirectionGesture(keyCode)", text)
        self.assertGreaterEqual(
            text.count("|| isDvdTitleDirectionGesture(keyCode)"), 2
        )
        self.assertIn("hold send both a seek and a chapter command", text)

    def test_dvd_hold_preset_guards_chapters_without_scanning_direction_keys(self):
        text = PROCESSOR.read_text(encoding="utf-8")
        self.assertIn("if (handleDvdHeldArrow(keyCode, event)) return true", text)
        self.assertIn("DvdHeldArrowPolicy.chapterDue", text)
        self.assertIn("SageCommand.DVD_CHAPTER_NEXT : SageCommand.DVD_CHAPTER_PREV", text)
        self.assertIn("event.getDownTime() == heldDvdDownTime", text)
        self.assertIn("event.getDownTime() == suppressedDvdDownTime", text)
        self.assertIn("cancelDvdHeldInput(true)", text)
        held = text.split("private boolean handleDvdHeldArrow", 1)[1].split("private void cancelDvdHeldInput", 1)[0]
        self.assertIn("boolean arrow = keyCode == KeyEvent.KEYCODE_DPAD_UP", held)
        self.assertNotIn("SageCommand.FASTER", held)
        self.assertIn("cancelDvdHeldInput(false)", text)

    def test_dvd_timescroll_cursor_is_scoped_and_dedicated_scan_is_not_replaced(self):
        controller = (PROCESSOR.parent / "DvdTimeScrollController.java").read_text(encoding="utf-8")
        self.assertIn("player != ownerPlayer || connection != ownerConnection", controller)
        self.assertIn("!player.isDvdMenuNavigationActive()", controller)
        self.assertIn("keyMap instanceof VideoPlaybackKeyMap", controller)
        self.assertIn("event.getRepeatCount() > 0", controller)
        self.assertIn("consumedUps.put(key, event.getDownTime())", controller)
        self.assertIn("send(policy.accept())", controller)
        self.assertIn("send(policy.cancel())", controller)
        self.assertIn("main.postDelayed(this, 150L)", controller)
        self.assertIn("main.removeCallbacks(dispatch)", controller)
        self.assertIn("this.commands.size() >= 32", controller)

    def test_dvd_hold_debug_is_async_scoped_and_not_a_release_player_control(self):
        commands = (ROOT / "source/dev/android-tv/src/debug/java/opensagetv/vibe/"
                    "miniclient/android/tv/debug/DebugSessionCommands.java").read_text(encoding="utf-8")
        hold = commands.split("static String dvdArrowHold", 1)[1].split("static String connectServer", 1)[0]
        self.assertIn("main.post(new Runnable()", hold)
        self.assertIn("main.postDelayed(new Runnable()", hold)
        self.assertIn("client.getCurrentConnection() != connection", hold)
        self.assertIn("activity.dispatchKeyEvent", hold)
        self.assertNotIn("getDecorView().dispatchKeyEvent", hold)

    def test_dedicated_dvd_scan_owns_repeat_and_release_without_android_rate_banner(self):
        controller = (PROCESSOR.parent / "DvdScanGestureController.java").read_text(encoding="utf-8")
        self.assertIn("consumedUps.get(code, -1L) == event.getDownTime()", controller)
        self.assertIn("DvdRemoteScanPolicy.isHold", controller)
        self.assertIn("if (resume && validOwner())", controller)
        self.assertIn("now - pendingSince >= 4_000L", controller)
        self.assertIn("!pending && now - lastStep", controller)
        self.assertIn("main.removeCallbacks(this)", controller)
        self.assertIn("player.supportsNativeDvdSkipPulse()", controller)
        self.assertIn("mapping.hasSageCommandOverride(code, false)", controller)
        self.assertNotIn("dvdNavigationOverlay.show", controller)
        overlay = (PROCESSOR.parent / "DvdNavigationOverlay.java").read_text(encoding="utf-8")
        self.assertIn("if (rate != 1f) { hide(); return; }", overlay)
        self.assertNotIn('"FF "', overlay)

    def test_dvd_presets_are_default_on_and_visible_in_main_key_mappings(self):
        prefs = (ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/"
                 "miniclient/android/preferences/MediaMappingPreferences.java").read_text(encoding="utf-8")
        self.assertIn('getBoolean("dvdplaying_hold_scan", true)', prefs)
        xml = (ROOT / "source/dev/android-shared/src/main/res/xml/media_mappings_prefs.xml").read_text(encoding="utf-8")
        self.assertIn('android:key="dvdplaying_hold_scan"', xml)
        self.assertIn('android:title="DVD FF/RW tap and hold"', xml)
        fragment = (ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/"
                    "miniclient/android/ui/settings/MediaMappingsFragment.java").read_text(encoding="utf-8")
        self.assertIn("dvdScan.setChecked(prefsDvdPlaying.isDvdScanHoldControlsEnabled())", fragment)
        controller = (PROCESSOR.parent / "DvdScanGestureController.java").read_text(encoding="utf-8")
        self.assertIn("!prefs.isDvdScanHoldControlsEnabled()", controller)

    def test_dvd_preview_uses_normal_keys_and_all_gesture_owners_guard_fullscreen(self):
        listener = (ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/"
                    "miniclient/android/ui/MiniClientKeyListener.java").read_text(encoding="utf-8")
        self.assertIn("!DvdInputContext.isFullscreen(client)", listener)
        self.assertIn("return keyProcessor.onKey(defaultKeyMap, keyCode, event)", listener)
        for name in ("DvdScanGestureController", "DvdTimeScrollController", "DvdNavigationOverlay", "KeyMapProcessor"):
            with self.subTest(owner=name):
                owner = (PROCESSOR.parent / (name + ".java")).read_text(encoding="utf-8")
                self.assertIn("DvdInputContext.isFullscreen(client)", owner)

    def test_dvd_navigation_position_display_never_steals_focus_or_runs_in_background(self):
        overlay = (PROCESSOR.parent / "DvdNavigationOverlay.java").read_text(encoding="utf-8")
        self.assertIn("view.setFocusable(false)", overlay)
        self.assertIn("view.setClickable(false)", overlay)
        self.assertIn("client.getCurrentConnection() != connection", overlay)
        self.assertIn("client.getPlayer() != player", overlay)
        self.assertIn("player.isDvdMenuNavigationActive()", overlay)
        self.assertIn("main.removeCallbacks(this)", overlay)
        self.assertIn("player.getMediaTimeMillis(0L)", overlay)
        self.assertNotIn("postCommand", overlay)

    def test_miniclient_activities_own_remote_dispatch_above_render_surfaces(self):
        lifecycle = (
            ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/"
            "miniclient/android/UIActivityLifeCycleHandler.java"
        ).read_text(encoding="utf-8")
        self.assertIn("public boolean dispatchKeyEvent(KeyEvent event)", lifecycle)
        self.assertIn("listener.onKey(target, event.getKeyCode(), event)", lifecycle)

        for renderer in ("opengl/MiniClientOpenGLActivity.java", "gdx/MiniClientGDXActivity.java"):
            activity = (
                ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/"
                f"miniclient/android/{renderer}"
            ).read_text(encoding="utf-8")
            self.assertIn("public boolean dispatchKeyEvent(KeyEvent event)", activity)
            self.assertIn("uiActivityLifeCycleHandler.dispatchKeyEvent(event)", activity)

    def test_dvd_scan_gate_preserves_last_probe_and_collects_before_cleanup(self):
        text = (ROOT / "scripts/mcp_dvd_scan_gesture_test.py").read_text(encoding="utf-8")
        self.assertIn('result["lastObservedState"] = sample', text)
        self.assertIn('"health_videoQueuedInput"', text)
        self.assertIn('"dvdDecoderBufferedAheadMs"', text)
        self.assertIn('current.get("playerClass")', text)
        failure = text.split("except Exception as failure:", 1)[1]
        self.assertLess(failure.index('"collect_playback_diagnostics"'), failure.index("finally:"))
        self.assertIn('"dvd-scan-gesture-failure"', failure)

    def test_remote_navigation_dialog_uses_direct_foreground_ui_owner(self):
        text = PROCESSOR.read_text(encoding="utf-8")
        self.assertIn("longPress && command == SageCommand.NAV_OSD", text)
        self.assertIn("uiHandler.showHideSoftRemote(true);", text)

    def test_decoder_scan_isolation_preserves_sagemc_timed_skip_preference(self):
        text = (ROOT / "scripts/mcp_dvd_scan_gesture_test.py").read_text(encoding="utf-8")
        self.assertIn('"--decoder-only"', text)
        isolated = text.split("if args.decoder_only:", 1)[1].split('key("PLAY")', 1)[0]
        self.assertIn('control.media_control(context, "rate", rate=rate)', isolated)
        self.assertIn('(2, 4, 8, 16, 32, 64)', isolated)
        self.assertIn('not remote keys or256x', isolated)
        self.assertIn('"health_audioRendered"', isolated)
        self.assertIn('entry["counterRebases"] += 1', isolated)
        self.assertIn('deadline = time.monotonic() + 15', isolated)
        self.assertIn('entry["samples"].append(compact(after))', isolated)
        self.assertNotIn('SetProperty', isolated)
        self.assertNotIn('dev_set_player_config', isolated)

    def test_all_android_tv_navigation_layouts_contain_active_adjustments(self):
        for qualifier in ("layout", "layout-notouch"):
            layout = (
                ROOT / "source/dev/android-tv/src/main/res" / qualifier / "navigation.xml"
            ).read_text(encoding="utf-8")
            self.assertIn('android:id="@+id/nav_active_player_adjustments"', layout)

        dialog = (
            ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/"
            "miniclient/android/NavigationDialog.java"
        ).read_text(encoding="utf-8")
        self.assertIn("if (activePlayerAdjustments != null)", dialog)


if __name__ == "__main__":
    unittest.main()

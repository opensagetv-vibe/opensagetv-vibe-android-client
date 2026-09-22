import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SHARED = ROOT / "source/dev/android-shared/src/main"


class SmbServerBrowserContracts(unittest.TestCase):
    def read_java(self, relative):
        return (SHARED / "java/opensagetv/vibe/miniclient/android" / relative).read_text(encoding="utf-8")

    def test_server_profiles_are_reusable_and_credential_blob_is_protected(self):
        profile = self.read_java("config/SmbServerProfile.java")
        store = self.read_java("config/SmbServerProfileStore.java")
        core_profile = (ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/config/MiniClientProfile.java").read_text(encoding="utf-8")
        self.assertIn('STORAGE_KEY = "smb_servers/credentials"', store)
        self.assertIn("JSONArray", store)
        self.assertIn("Authentication", (SHARED / "res/xml/smb_profile_prefs.xml").read_text(encoding="utf-8"))
        self.assertIn('lower.startsWith("smb_servers/")', core_profile)
        self.assertIn('lower.startsWith("smb_diagnostics/auth/")', core_profile)
        self.assertIn("public String directoryUrl(String folder)", profile)
        self.assertNotIn("public String toString()", profile)

    def test_folder_browser_is_read_only_smb2_smb3_and_bounded(self):
        browser = self.read_java("config/SmbFolderBrowserRepository.java")
        self.assertIn("share.list(root)", browser)
        self.assertIn("FILE_ATTRIBUTE_DIRECTORY", browser)
        self.assertIn("withTimeout(10, TimeUnit.SECONDS)", browser)
        self.assertIn("withSoTimeout(10, TimeUnit.SECONDS)", browser)
        for mutation in ("openFile(", ".mkdir(", ".rmdir(", ".rm("):
            self.assertNotIn(mutation, browser)

    def test_ui_requires_server_then_browses_each_destination(self):
        xml = (SHARED / "res/xml/smb_profile_prefs.xml").read_text(encoding="utf-8")
        fragment = self.read_java("ui/settings/SmbProfileSettingsFragment.java")
        for key in (
            "smb_servers/selected", "smb_servers/save", "smb_servers/test", "smb_servers/delete",
            "smb_direct/browse", "smb_profiles/browse", "smb_diagnostics/browse",
        ):
            self.assertIn(f'android:key="{key}"', xml)
        self.assertIn('names[0] = "Add a new server..."', fragment)
        self.assertIn('setTitle("Choose SMB server")', fragment)
        self.assertIn('labels.add("..")', fragment)
        self.assertIn("ic_folder_white_24dp", fragment)
        self.assertIn('"Select this folder"', fragment)
        self.assertIn('setTitle("SMB servers")', fragment)
        self.assertIn('credentials.setText("Use authentication")', fragment)
        self.assertIn("SmbServerProfile.parseEndpoint", fragment)
        self.assertIn('setTitle("Media path mappings")', fragment)
        self.assertIn('"Add media path mapping"', fragment)
        self.assertIn('addLabeledRow(form, "SageTV server folder", source)', fragment)
        self.assertIn('addLabeledRow(form, "SMB server", server)', fragment)
        self.assertIn('addLabeledRow(form, "SMB folder", folder)', fragment)
        self.assertIn('.setNeutralButton("Test", null)', fragment)
        self.assertIn('loadFolder(selectedServerId[0], "", Destination.MEDIA', fragment)
        self.assertIn('"Change folder", "Test mappings", "Remove mapping"', fragment)
        self.assertIn("showMediaMappingMenu", fragment)
        self.assertIn("MappingTarget existingTarget = mappingTarget(existing)", fragment)
        self.assertIn("removeMapping", fragment)
        self.assertIn('"Browse or change folder", "Test connection"', fragment)
        self.assertIn("showDestinationMenu", fragment)
        self.assertIn("Destination.MEDIA", fragment)
        self.assertIn("Destination.CONFIGURATION", fragment)
        self.assertIn("Destination.DIAGNOSTICS", fragment)
        self.assertIn('chooseServer(selectedProfileId -> loadFolder(', fragment)

    def test_selected_folder_materializes_legacy_compatible_settings(self):
        fragment = self.read_java("ui/settings/SmbProfileSettingsFragment.java")
        prefs = self.read_java("prefs/AndroidPrefStore.java")
        for key in (
            "SMB_MAPPINGS", "SMB_USERNAME", "SMB_PASSWORD", "SMB_DOMAIN",
            "SMB_PROFILE_DIRECTORY", "SMB_DIAGNOSTICS_DIRECTORY",
        ):
            self.assertIn(key, fragment)
        for key in ("SMB_MEDIA_SERVER_ID", "SMB_PROFILE_SERVER_ID", "SMB_DIAGNOSTICS_SERVER_ID"):
            self.assertIn(key, prefs)
        self.assertIn("synchronizeBoundDestinations", fragment)
        self.assertIn("replaceMapping", fragment)

    def test_tv_remote_focus_enters_server_and_mapping_editor_fields(self):
        fragment = self.read_java("ui/settings/SmbProfileSettingsFragment.java")
        self.assertNotIn("form.requestFocus()", fragment)
        self.assertNotIn("form.setFocusableInTouchMode(true)", fragment)
        self.assertGreaterEqual(fragment.count("form.setFocusable(false)"), 2)
        self.assertIn("scroll.setFocusable(false)", fragment)
        self.assertIn("name.requestFocus()", fragment)
        self.assertIn("source.requestFocus()", fragment)
        self.assertIn("configureServerEditorFocus", fragment)
        self.assertIn("credentials.setNextFocusDownId(username.getId())", fragment)
        self.assertIn("credentials.setNextFocusDownId(save.getId())", fragment)
        self.assertIn("server.setNextFocusDownId(folder.isEnabled()", fragment)

    def test_media_mappings_select_credentials_by_server_and_share(self):
        config = self.read_java("video/smb/SmbDirectConfig.java")
        session = self.read_java("video/smb/SmbDirectSession.java")
        self.assertIn("parseProfileCredentials", config)
        self.assertIn("host.equalsIgnoreCase(mapped.getServer())", config)
        self.assertIn("share.equalsIgnoreCase(mapped.getShare())", config)
        self.assertIn("credentialsFor(mapped)", session)
        self.assertIn("return new Credentials(username, password, domain)", config)
        self.assertIn("for (CredentialRule rule : profileCredentials) rule.clear()", config)


if __name__ == "__main__":
    unittest.main()

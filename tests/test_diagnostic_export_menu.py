from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/'source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/diagnostics'
class ExportMenuContracts(unittest.TestCase):
    def test_every_dialog_footer_has_explicit_tv_focus_and_pressed_states(self):
        menu=(JAVA/'DiagnosticExportMenu.java').read_text()
        style=(JAVA/'DiagnosticDialogFocus.java').read_text()
        for required in ('StateListDrawable states=', 'android.R.attr.state_focused',
                         'android.R.attr.state_pressed', '-android.R.attr.state_enabled',
                         'button.setBackgroundTintList(null)', 'button.setBackground(states)',
                         'button.setFocusable(true)', 'button.setFocusableInTouchMode(true)', '0xff34556e', '0xffb4d9ff'):
            self.assertIn(required,style)
        self.assertIn('DiagnosticDialogFocus.style(activity,b)',menu)
        self.assertIn('footer.addView(item)',menu)
        controller=(JAVA/'DiagnosticExportController.java').read_text()
        self.assertIn('DiagnosticDialogFocus.style(activity,confirmation.getButton',controller)
    def test_dialogs_request_visible_safe_initial_focus(self):
        menu=(JAVA/'DiagnosticExportMenu.java').read_text()
        controller=(JAVA/'DiagnosticExportController.java').read_text()
        self.assertIn('Button initial=confirmsUpload && back!=null?back:first',menu)
        self.assertIn('initial.requestFocus()',menu)
        self.assertIn('confirmation.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus()',controller)
    def test_shared_entry_offers_all_methods_without_enabling_them(self):
        menu=(JAVA/'DiagnosticExportMenu.java').read_text()
        for label in ('Create and share ZIP','Export to SMB','Export to SageTV server','Download in web browser'):
            self.assertIn(label,menu)
        for forbidden in ('prefs.edit()', 'CLIENT_WEB_ENABLED,true','SERVER_DIAGNOSTICS_ENABLED,true','startActivity('):
            self.assertNotIn(forbidden,menu)
        self.assertIn('row.setEnabled(enabled)',menu)
        self.assertIn('receiverAvailable(session)',menu)
        self.assertIn('new ArrayBlockingQueue<Runnable>(1)',menu)
        self.assertIn('ClientDiagnosticWebService.exportAddress()',menu)
    def test_one_report_is_confirmed_and_session_owned_not_automatic_enable(self):
        menu=(JAVA/'DiagnosticExportMenu.java').read_text();owner=(JAVA/'ServerDiagnosticExporter.java').read_text()
        self.assertIn('Send once',menu);self.assertIn('Automatic uploads and your saved settings are unchanged',menu)
        self.assertIn('current!=expected',owner);self.assertIn('connection() == current',owner)
        self.assertIn('if (!oneReport && !enabled()) return;',owner)
        self.assertNotIn('putBoolean(AndroidPrefStore.SERVER_DIAGNOSTICS_ENABLED',owner)
        self.assertNotIn('parent.dismiss()',menu)
    def test_qr_is_exact_offline_url_and_keeps_readable_text(self):
        menu=(JAVA/'DiagnosticExportMenu.java').read_text();qr=(JAVA/'DiagnosticUrlQr.java').read_text()
        self.assertIn('Device IP: ',menu);self.assertIn('DiagnosticUrlQr.encode(url)',menu)
        self.assertIn('Diagnostic web QR: ',menu);self.assertIn('x/scale,y/scale',menu)
        self.assertIn('DiagnosticExportPolicy.validWebUrl(url)',qr)
        self.assertIn('EncodeHintType.MARGIN,4',qr)
        self.assertNotIn('https://',qr)
    def test_debug_mcp_op_opens_shared_ui_without_uploading(self):
        adb=(ROOT/'mcp/src/sagetv_dev_mcp/adb.py').read_text()
        server=(ROOT/'mcp/src/sagetv_dev_mcp/server.py').read_text()
        debug=(ROOT/'source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/DebugSessionCommands.java').read_text()
        self.assertIn('def open_diagnostic_export(',adb)
        self.assertIn('def dev_open_diagnostic_export(',server)
        self.assertIn('DiagnosticExportController.show(',debug)
        self.assertIn('uploadRequested=false',debug)
if __name__=='__main__':unittest.main()

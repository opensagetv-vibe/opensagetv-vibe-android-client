"""Mixed browser/SDK test runtime must not accidentally start MCP in web-Python."""
import importlib.util
import json
import subprocess
import sys
import time
from pathlib import Path
from unittest import TestCase,main
from unittest.mock import patch,MagicMock

spec=importlib.util.spec_from_file_location('diagnostic_mcp_process',Path(__file__).resolve().parents[1]/'scripts/mcp_smoke_test.py')
driver=importlib.util.module_from_spec(spec);spec.loader.exec_module(driver)

class McpChildPython(TestCase):
    def test_default_preserves_current_runtime(self):
        with patch.object(driver.subprocess,'Popen',return_value=MagicMock()) as spawn:
            driver.MCPProcess();self.assertEqual(driver.sys.executable,spawn.call_args.args[0][0])
    def test_mixed_gate_explicitly_uses_sdk_child(self):
        with patch.object(driver.subprocess,'Popen',return_value=MagicMock()) as spawn:
            driver.MCPProcess(python_executable='/opt/opensagetv-vibe/android-python/bin/python3')
            self.assertEqual(['/opt/opensagetv-vibe/android-python/bin/python3','-m','sagetv_dev_mcp.server'],spawn.call_args.args[0])

    def real_child(self,source):
        spawn=subprocess.Popen
        with patch.object(driver.subprocess,'Popen',side_effect=lambda *a,**kw:
                          spawn([sys.executable,'-u','-c',source],**kw)):
            return driver.MCPProcess()

    def test_large_partial_line_cannot_block_protocol_reply(self):
        client=self.real_child("import sys,json;sys.stderr.write('X'*300000+'TAIL');sys.stderr.flush();"
            "q=json.loads(sys.stdin.readline());print(json.dumps({'id':q['id'],'result':{'ok':True}}),flush=True);"
            "sys.stdin.read()")
        try:
            self.assertTrue(client.request('probe',timeout=3)['result']['ok'])
            tail=client.stderr_snapshot()
            self.assertIn('TAIL',tail)
            self.assertLessEqual(len(tail),64*4096)
        finally:client.close()
        self.assertFalse(client._stderr_reader.is_alive())
        self.assertFalse(client._stdout_reader.is_alive())

    def test_timeout_diagnostics_do_not_wait_for_live_server_eof(self):
        client=self.real_child("import sys,time;sys.stderr.write('partial diagnostic');sys.stderr.flush();"
            "sys.stdin.readline();time.sleep(5)")
        started=time.monotonic()
        try:
            with self.assertRaisesRegex(TimeoutError,'partial diagnostic'):
                client.request('probe',timeout=.5)
            self.assertLess(time.monotonic()-started,2)
        finally:client.close()

    def test_notification_and_reply_in_one_write_do_not_hide_buffered_reply(self):
        client=self.real_child("import sys,json,os;q=json.loads(sys.stdin.readline());"
            "os.write(sys.stdout.fileno(),(json.dumps({'jsonrpc':'2.0','method':'notifications/message'})"
            "+'\\n'+json.dumps({'id':q['id'],'result':{'ok':True}})+'\\n').encode());sys.stdin.read()")
        try:
            self.assertTrue(client.request('probe',timeout=.7)['result']['ok'])
        finally:client.close()

    def test_partial_stdout_line_does_not_block_request_deadline(self):
        client=self.real_child("import sys,time;sys.stdin.readline();sys.stdout.write('{');"
            "sys.stdout.flush();time.sleep(5)")
        started=time.monotonic()
        try:
            with self.assertRaises(TimeoutError): client.request('probe',timeout=.3)
            self.assertLess(time.monotonic()-started,1)
        finally:client.close()

    def test_oversize_stdout_is_a_bounded_visible_protocol_failure(self):
        client=self.real_child("import sys;sys.stdin.readline();sys.stdout.write('X'*1100000+'\\n');"
            "sys.stdout.flush();sys.stdin.read()")
        try:
            with self.assertRaisesRegex(RuntimeError,'stdout frame exceeds limit'):
                client.request('probe',timeout=2)
        finally:client.close()

if __name__=='__main__':main()

"""Exercise the pure oracle without requiring a running MCP/Android device."""
import ast
from pathlib import Path
import unittest

class DvdStillOracleTest(unittest.TestCase):
    def test_title_subtitle_is_not_a_still_menu(self):
        source = (Path(__file__).resolve().parents[1] / "mcp/src/sagetv_dev_mcp/server.py").read_text(encoding="utf-8")
        function = next(node for node in ast.parse(source).body
                        if isinstance(node, ast.FunctionDef) and node.name == "_dvd_static_menu_health")
        scope = {}
        exec(compile(ast.Module(body=[function], type_ignores=[]), "oracle", "exec"), scope)
        state = dict(playerActive=True, dvdSessionPending=True, health_videoRendered=12,
                     dvdOverlaysPresented=5, dvdLastOverlayOpaquePixels=1000,
                     health_surfaceValid=True, health_errorState=False)
        self.assertFalse(scope[function.name](state)[0])
        state["dvdHighlightVisible"] = True
        self.assertTrue(scope[function.name](state)[0])
        state["health_errorState"] = True
        self.assertFalse(scope[function.name](state)[0])

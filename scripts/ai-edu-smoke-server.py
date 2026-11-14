"""Local, synthetic academic portal for AI scratch-script testing. No user data."""
import argparse
import json
import secrets
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PROMPT = """这是我在本机搭建的虚构教务测试页。请先读取当前页面并核实页面实际请求的课程接口，再用 TASK_WORKSPACE 写一个标准拾光异步 IIFE 临时脚本；读取文件取得 sha256，用 EDIT 精确修改一段注释后，再用最新 sha256 调用 EDU_WORKSPACE.RUN_SCRIPT。脚本应 fetch 已核实的接口，按真实返回值依次保存配置、作息和课程，全部成功才通知完成。结合接口返回值复核，只生成导入预览，不保存课表、不建议保存适配。最后报告真实运行码、课程数与周次。如果工具没有执行成功，请报告实际错误，不要用文字声称成功。"""

PAGE = """<!doctype html><html lang="zh-CN"><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>临时脚本验证 · 虚构教务</title>
<style>body{font:16px/1.7 system-ui;margin:24px auto;padding:0 20px;max-width:760px;background:#fafafa;color:#222}textarea{box-sizing:border-box;width:100%;min-height:230px;padding:12px;font:14px/1.6 system-ui}button{padding:10px 18px;margin:12px 0}small{color:#555}</style>
<h1>临时脚本验证</h1><p>这是本机生成的虚构教务，没有学校账号，也不会修改真实教务。</p>
<p id="state">正在检查同源课程接口…</p>
<p>本页故意不显示课程明细。请在 App 点击 AI 导入，确认开始后，把下面这段话发给模型。</p>
<textarea id="prompt" readonly>__PROMPT__</textarea>
<button onclick="navigator.clipboard?.writeText(document.getElementById('prompt').value)">复制测试要求</button>
<p><small>预期：临时写文件 → 读哈希 → 精确编辑 → 执行教务脚本 → 2 门课、4 节作息、18 周的预览。课程名称里的运行码应与电脑终端输出一致。验证到预览即可，无需保存课程。</small></p>
<script src="/portal.js"></script></html>""".replace("__PROMPT__", PROMPT)

PORTAL_JS = """fetch('/api/semester', {credentials:'same-origin',cache:'no-store'})
 .then(r=>{if(!r.ok)throw Error('接口不可用');return r.json()})
 .then(d=>{document.getElementById('state').textContent='同源课程接口可用；课程明细仅在接口中提供。';})
 .catch(()=>{document.getElementById('state').textContent='接口暂时不可用，请检查本地测试服务。';});"""


def make_fixture(code):
    return {
        "synthetic": True,
        "runCode": code,
        "config": {"semesterStartDate": "2026-09-07", "semesterTotalWeeks": 18,
                   "defaultClassDuration": 45, "defaultBreakDuration": 10, "firstDayOfWeek": 1},
        "timeSlots": [
            {"number": 1, "startTime": "08:00", "endTime": "08:45"},
            {"number": 2, "startTime": "08:55", "endTime": "09:40"},
            {"number": 3, "startTime": "10:00", "endTime": "10:45"},
            {"number": 4, "startTime": "10:55", "endTime": "11:40"}],
        "courses": [
            {"name": f"脚本验证-{code}-数学", "teacher": "测试教师甲", "position": "测试楼101", "day": 1,
             "startSection": 1, "endSection": 2, "weeks": [1, 2, 3, 4], "isCustomTime": False,
             "customStartTime": "", "customEndTime": "", "credit": 0},
            {"name": f"脚本验证-{code}-程序设计", "teacher": "测试教师乙", "position": "测试楼202", "day": 3,
             "startSection": 3, "endSection": 4, "weeks": [2, 4, 6, 8], "isCustomTime": False,
             "customStartTime": "", "customEndTime": "", "credit": 0}]
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=18765)
    args = parser.parse_args()
    fixture = make_fixture(secrets.token_hex(3).upper())
    routes = {"/": ("text/html; charset=utf-8", PAGE.encode()),
              "/portal.js": ("text/javascript; charset=utf-8", PORTAL_JS.encode()),
              "/api/semester": ("application/json; charset=utf-8", json.dumps(fixture, ensure_ascii=False).encode())}

    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            route = routes.get(self.path.split("?", 1)[0])
            if route is None:
                self.send_error(404)
                return
            mime, body = route
            self.send_response(200)
            self.send_header("Content-Type", mime)
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, *_args):
            pass

    with ThreadingHTTPServer(("127.0.0.1", args.port), Handler) as server:
        print(json.dumps({"url": f"http://127.0.0.1:{server.server_port}", "runCode": fixture["runCode"],
                          "courses": 2, "timeSlots": 4, "weeks": 18}), flush=True)
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            pass


if __name__ == "__main__":
    main()

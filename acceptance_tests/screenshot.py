"""
Screenshot capture utility for STS Arena acceptance tests.

Provides functions to capture screenshots during test execution and
generate an HTML index for browsing.

Supports per-run screenshot organization for Hypothesis stateful tests,
where each test run gets its own subdirectory and index page.
"""

import os
import time
from datetime import datetime
from pathlib import Path
from typing import Optional, List, Dict, Any

try:
    import mss
    import mss.tools
    HAS_MSS = True
except ImportError:
    HAS_MSS = False

try:
    from PIL import Image
    HAS_PIL = True
except ImportError:
    HAS_PIL = False


# Default screenshot directory
DEFAULT_SCREENSHOT_DIR = Path(__file__).parent.parent / "test_screenshots"


# =============================================================================
# Per-run tracking for Hypothesis stateful tests
# =============================================================================

class StatefulRunTracker:
    """
    Tracks screenshots for a single Hypothesis stateful test run.

    Each run (example) in a stateful test gets its own directory and index.
    Screenshots are numbered by step for easy debugging.
    """

    def __init__(self, test_name: str, run_id: int, base_dir: Path):
        """
        Initialize a run tracker.

        Args:
            test_name: Name of the test class (e.g., "ArenaTransitionMachine")
            run_id: Run/example number within this test
            base_dir: Session directory for screenshots
        """
        self.test_name = test_name
        self.run_id = run_id
        self.base_dir = base_dir
        self.step_num = 0
        self.screenshots: List[Dict[str, Any]] = []

        # Create run directory
        self.run_dir = base_dir / f"{test_name}" / f"run_{run_id:03d}"
        self.run_dir.mkdir(parents=True, exist_ok=True)

    def capture_step(
        self,
        action_name: str,
        phase: str = "after",
        extra_info: Optional[str] = None,
    ) -> Optional[Path]:
        """
        Capture a screenshot for a step in this run.

        Args:
            action_name: Name of the action (e.g., "start_fight", "win")
            phase: "before" or "after" the action
            extra_info: Optional extra info to include in the name

        Returns:
            Path to the screenshot, or None if capture failed.
        """
        if not HAS_MSS:
            return None

        try:
            sct = mss.mss()

            # Build filename: step_000_after_action_name.png
            parts = [f"step_{self.step_num:03d}", phase, action_name]
            if extra_info:
                # Sanitize extra_info for filename
                safe_info = "".join(c if c.isalnum() or c in "_-" else "_" for c in extra_info)
                parts.append(safe_info[:50])

            filename = "_".join(parts) + ".png"
            filepath = self.run_dir / filename

            # Capture screen
            sct_img = sct.grab(sct.monitors[0])
            mss.tools.to_png(sct_img.rgb, sct_img.size, output=str(filepath))
            sct.close()

            # Track screenshot
            self.screenshots.append({
                "path": filepath,
                "step": self.step_num,
                "phase": phase,
                "action": action_name,
                "extra": extra_info,
                "timestamp": datetime.now().isoformat(),
            })

            return filepath

        except Exception as e:
            print(f"Warning: Step screenshot failed: {e}")
            return None

    def next_step(self):
        """Increment step counter (call after each action completes)."""
        self.step_num += 1

    def generate_run_index(self) -> Path:
        """
        Generate an HTML index for this specific run.

        Returns:
            Path to the generated index.html file.
        """
        index_path = self.run_dir / "index.html"

        html = f"""<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <title>Run {self.run_id} - {self.test_name}</title>
    <style>
        body {{
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
            max-width: 1600px;
            margin: 0 auto;
            padding: 20px;
            background: #1a1a2e;
            color: #eee;
        }}
        h1 {{ color: #f0a500; }}
        .nav {{ margin-bottom: 20px; }}
        .nav a {{ color: #00adb5; }}
        .step-container {{
            display: flex;
            flex-wrap: wrap;
            gap: 20px;
            margin-bottom: 30px;
        }}
        .step-card {{
            background: #222831;
            border-radius: 8px;
            overflow: hidden;
            box-shadow: 0 4px 6px rgba(0,0,0,0.3);
            width: 400px;
        }}
        .step-card img {{
            width: 100%;
            height: 250px;
            object-fit: cover;
            cursor: pointer;
        }}
        .step-info {{
            padding: 10px;
        }}
        .step-num {{
            font-weight: bold;
            color: #f0a500;
            font-size: 1.2em;
        }}
        .action-name {{
            color: #00adb5;
            margin-top: 5px;
        }}
        .phase {{ color: #888; }}
        .extra {{ font-size: 0.85em; color: #666; word-break: break-all; }}
        .modal {{
            display: none;
            position: fixed;
            z-index: 1000;
            left: 0;
            top: 0;
            width: 100%;
            height: 100%;
            background: rgba(0,0,0,0.9);
        }}
        .modal-content {{
            position: absolute;
            top: 50%;
            left: 50%;
            transform: translate(-50%, -50%);
            max-width: 95%;
            max-height: 95%;
        }}
        .close {{
            position: absolute;
            top: 20px;
            right: 30px;
            color: #fff;
            font-size: 40px;
            cursor: pointer;
        }}
    </style>
</head>
<body>
    <div class="nav"><a href="../../index.html">&larr; Back to session index</a></div>
    <h1>Run {self.run_id} - {self.test_name}</h1>
    <p>{len(self.screenshots)} screenshots captured</p>
    <div class="step-container">
"""

        for ss in self.screenshots:
            rel_path = ss["path"].name
            step = ss["step"]
            phase = ss["phase"]
            action = ss["action"]
            extra = ss.get("extra", "")

            html += f"""
        <div class="step-card">
            <img src="{rel_path}" alt="Step {step}: {action}" onclick="openModal(this.src)">
            <div class="step-info">
                <div class="step-num">Step {step}</div>
                <div class="action-name">{action}</div>
                <div class="phase">{phase}</div>
                {"<div class='extra'>" + extra + "</div>" if extra else ""}
            </div>
        </div>
"""

        html += """
    </div>
    <div id="imageModal" class="modal" onclick="closeModal()">
        <span class="close">&times;</span>
        <img class="modal-content" id="modalImg">
    </div>
    <script>
        function openModal(src) {
            document.getElementById('imageModal').style.display = 'block';
            document.getElementById('modalImg').src = src;
        }
        function closeModal() {
            document.getElementById('imageModal').style.display = 'none';
        }
        document.onkeydown = function(e) {
            if (e.key === 'Escape') closeModal();
        };
    </script>
</body>
</html>
"""

        index_path.write_text(html)
        return index_path


class StatefulTestTracker:
    """
    Tracks all runs for a Hypothesis stateful test class.

    Manages run creation and generates a summary index linking to all runs.
    """

    def __init__(self, test_name: str, session_dir: Path):
        """
        Initialize tracker for a test class.

        Args:
            test_name: Name of the test class
            session_dir: Session directory for screenshots
        """
        self.test_name = test_name
        self.session_dir = session_dir
        self.runs: List[StatefulRunTracker] = []
        self.current_run: Optional[StatefulRunTracker] = None

    def start_run(self) -> StatefulRunTracker:
        """Start tracking a new run."""
        run_id = len(self.runs)
        run = StatefulRunTracker(self.test_name, run_id, self.session_dir)
        self.runs.append(run)
        self.current_run = run
        return run

    def end_run(self):
        """End the current run and generate its index."""
        if self.current_run:
            self.current_run.generate_run_index()
            self.current_run = None

    def generate_test_index(self) -> Optional[Path]:
        """
        Generate an index page for this test class showing all runs.

        Returns:
            Path to the generated index.html file.
        """
        if not self.runs:
            return None

        test_dir = self.session_dir / self.test_name
        test_dir.mkdir(parents=True, exist_ok=True)
        index_path = test_dir / "index.html"

        html = f"""<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <title>{self.test_name} - All Runs</title>
    <style>
        body {{
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
            max-width: 1200px;
            margin: 0 auto;
            padding: 20px;
            background: #1a1a2e;
            color: #eee;
        }}
        h1 {{ color: #f0a500; }}
        .nav {{ margin-bottom: 20px; }}
        .nav a {{ color: #00adb5; }}
        .run-list {{
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
            gap: 20px;
        }}
        .run-card {{
            background: #222831;
            border-radius: 8px;
            padding: 15px;
            box-shadow: 0 4px 6px rgba(0,0,0,0.3);
        }}
        .run-card a {{
            color: #00adb5;
            text-decoration: none;
            font-weight: bold;
            font-size: 1.2em;
        }}
        .run-card a:hover {{ text-decoration: underline; }}
        .run-info {{
            color: #888;
            margin-top: 5px;
        }}
    </style>
</head>
<body>
    <div class="nav"><a href="../index.html">&larr; Back to session index</a></div>
    <h1>{self.test_name}</h1>
    <p>{len(self.runs)} runs captured</p>
    <div class="run-list">
"""

        for run in self.runs:
            html += f"""
        <div class="run-card">
            <a href="run_{run.run_id:03d}/index.html">Run {run.run_id}</a>
            <div class="run-info">{len(run.screenshots)} screenshots, {run.step_num} steps</div>
        </div>
"""

        html += """
    </div>
</body>
</html>
"""

        index_path.write_text(html)
        return index_path


# Global tracker for stateful tests
_stateful_trackers: Dict[str, StatefulTestTracker] = {}


def get_stateful_tracker(test_name: str) -> StatefulTestTracker:
    """Get or create a stateful test tracker."""
    global _stateful_trackers
    capture = get_capture()

    if test_name not in _stateful_trackers:
        _stateful_trackers[test_name] = StatefulTestTracker(test_name, capture.session_dir)

    return _stateful_trackers[test_name]


def finalize_stateful_trackers():
    """Generate test-level indices for all stateful trackers."""
    for tracker in _stateful_trackers.values():
        tracker.generate_test_index()


class ScreenshotCapture:
    """Manages screenshot capture and storage for tests."""

    def __init__(self, base_dir: Optional[Path] = None):
        """
        Initialize the screenshot capture system.

        Args:
            base_dir: Base directory for storing screenshots.
                     Defaults to test_screenshots/ in the project root.
        """
        self.base_dir = base_dir or DEFAULT_SCREENSHOT_DIR
        self.screenshots = []
        self._session_dir = None
        self._sct = None

    @property
    def session_dir(self) -> Path:
        """Get or create the session directory for this test run."""
        if self._session_dir is None:
            timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
            self._session_dir = self.base_dir / f"session_{timestamp}"
            self._session_dir.mkdir(parents=True, exist_ok=True)
        return self._session_dir

    def _get_sct(self):
        """Get or create the mss screenshot instance."""
        if not HAS_MSS:
            raise RuntimeError("mss not installed. Install with: pip install mss")
        if self._sct is None:
            self._sct = mss.mss()
        return self._sct

    def capture(
        self,
        name: Optional[str] = None,
        test_name: Optional[str] = None,
        monitor: int = 0,
    ) -> Optional[Path]:
        """
        Capture a screenshot.

        Args:
            name: Name for the screenshot (without extension).
                 If not provided, uses timestamp.
            test_name: Name of the current test (for organization).
            monitor: Monitor index to capture (0 = all monitors, 1+ = specific).

        Returns:
            Path to the saved screenshot, or None if capture failed.
        """
        if not HAS_MSS:
            print("Warning: mss not installed, skipping screenshot")
            return None

        try:
            sct = self._get_sct()

            # Create test subdirectory if test_name provided
            if test_name:
                test_dir = self.session_dir / test_name.replace("::", "_")
                test_dir.mkdir(parents=True, exist_ok=True)
                save_dir = test_dir
            else:
                save_dir = self.session_dir

            # Generate filename
            timestamp = datetime.now().strftime("%H%M%S_%f")
            if name:
                filename = f"{timestamp}_{name}.png"
            else:
                filename = f"{timestamp}.png"

            filepath = save_dir / filename

            # Capture the screen
            if monitor == 0:
                # Capture all monitors
                sct_img = sct.grab(sct.monitors[0])
            else:
                # Capture specific monitor
                sct_img = sct.grab(sct.monitors[monitor])

            # Save the image
            mss.tools.to_png(sct_img.rgb, sct_img.size, output=str(filepath))

            # Track the screenshot
            self.screenshots.append({
                "path": filepath,
                "name": name or "screenshot",
                "test_name": test_name,
                "timestamp": datetime.now().isoformat(),
            })

            return filepath

        except Exception as e:
            print(f"Warning: Screenshot capture failed: {e}")
            return None

    def capture_on_failure(
        self,
        test_name: str,
        exception: Optional[Exception] = None,
    ) -> Optional[Path]:
        """
        Capture a screenshot when a test fails.

        Args:
            test_name: Name of the failing test.
            exception: The exception that caused the failure.

        Returns:
            Path to the saved screenshot, or None if capture failed.
        """
        name = "failure"
        if exception:
            exc_name = type(exception).__name__
            name = f"failure_{exc_name}"

        return self.capture(name=name, test_name=test_name)

    def generate_index(self) -> Path:
        """
        Generate an HTML index of all screenshots in this session.

        Returns:
            Path to the generated index.html file.
        """
        index_path = self.session_dir / "index.html"

        # Group screenshots by test
        by_test = {}
        for ss in self.screenshots:
            test = ss.get("test_name", "uncategorized")
            if test not in by_test:
                by_test[test] = []
            by_test[test].append(ss)

        # Get stateful test trackers for linking
        global _stateful_trackers
        stateful_tests = list(_stateful_trackers.values())

        # Generate HTML
        html = """<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <title>STS Arena Test Screenshots</title>
    <style>
        body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
            max-width: 1400px;
            margin: 0 auto;
            padding: 20px;
            background: #1a1a2e;
            color: #eee;
        }
        h1 { color: #f0a500; }
        h2 { color: #00adb5; border-bottom: 1px solid #393e46; padding-bottom: 10px; }
        .test-section { margin-bottom: 40px; }
        .stateful-section {
            background: #222831;
            border-radius: 8px;
            padding: 20px;
            margin-bottom: 30px;
        }
        .stateful-section h2 {
            margin-top: 0;
            border-bottom: none;
        }
        .stateful-link {
            display: inline-block;
            background: #393e46;
            padding: 10px 20px;
            border-radius: 5px;
            margin-right: 10px;
            margin-bottom: 10px;
            color: #00adb5;
            text-decoration: none;
        }
        .stateful-link:hover {
            background: #00adb5;
            color: #1a1a2e;
        }
        .stateful-stats { color: #888; font-size: 0.9em; }
        .screenshot-grid {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
            gap: 20px;
        }
        .screenshot-card {
            background: #222831;
            border-radius: 8px;
            overflow: hidden;
            box-shadow: 0 4px 6px rgba(0,0,0,0.3);
        }
        .screenshot-card img {
            width: 100%;
            height: 200px;
            object-fit: cover;
            cursor: pointer;
            transition: transform 0.2s;
        }
        .screenshot-card img:hover {
            transform: scale(1.02);
        }
        .screenshot-info {
            padding: 10px;
        }
        .screenshot-name {
            font-weight: bold;
            color: #f0a500;
        }
        .screenshot-time {
            font-size: 0.85em;
            color: #888;
        }
        .failure { border-left: 4px solid #e74c3c; }
        .modal {
            display: none;
            position: fixed;
            z-index: 1000;
            left: 0;
            top: 0;
            width: 100%;
            height: 100%;
            background: rgba(0,0,0,0.9);
        }
        .modal-content {
            position: absolute;
            top: 50%;
            left: 50%;
            transform: translate(-50%, -50%);
            max-width: 95%;
            max-height: 95%;
        }
        .close {
            position: absolute;
            top: 20px;
            right: 30px;
            color: #fff;
            font-size: 40px;
            cursor: pointer;
        }
    </style>
</head>
<body>
    <h1>STS Arena Test Screenshots</h1>
    <p>Session: """ + self.session_dir.name + """</p>
"""

        # Add stateful test section if we have any
        if stateful_tests:
            html += """
    <div class="stateful-section">
        <h2>Hypothesis Stateful Tests</h2>
        <p>Per-step screenshots for each test run (click to view all runs):</p>
"""
            for tracker in stateful_tests:
                total_screenshots = sum(len(r.screenshots) for r in tracker.runs)
                html += f"""
        <a class="stateful-link" href="{tracker.test_name}/index.html">
            {tracker.test_name}
            <div class="stateful-stats">{len(tracker.runs)} runs, {total_screenshots} screenshots</div>
        </a>
"""
            html += """
    </div>
"""

        # Regular test screenshots
        for test_name, screenshots in sorted(by_test.items()):
            html += f"""
    <div class="test-section">
        <h2>{test_name}</h2>
        <div class="screenshot-grid">
"""
            for ss in screenshots:
                rel_path = ss["path"].relative_to(self.session_dir)
                is_failure = "failure" in str(ss.get("name", "")).lower()
                failure_class = " failure" if is_failure else ""
                time_str = ss.get("timestamp", "").split("T")[-1].split(".")[0]

                html += f"""
            <div class="screenshot-card{failure_class}">
                <img src="{rel_path}" alt="{ss['name']}" onclick="openModal(this.src)">
                <div class="screenshot-info">
                    <div class="screenshot-name">{ss['name']}</div>
                    <div class="screenshot-time">{time_str}</div>
                </div>
            </div>
"""
            html += """
        </div>
    </div>
"""

        html += """
    <div id="imageModal" class="modal" onclick="closeModal()">
        <span class="close">&times;</span>
        <img class="modal-content" id="modalImg">
    </div>
    <script>
        function openModal(src) {
            document.getElementById('imageModal').style.display = 'block';
            document.getElementById('modalImg').src = src;
        }
        function closeModal() {
            document.getElementById('imageModal').style.display = 'none';
        }
        document.onkeydown = function(e) {
            if (e.key === 'Escape') closeModal();
        };
    </script>
</body>
</html>
"""

        index_path.write_text(html)
        return index_path

    def cleanup(self):
        """Clean up resources."""
        if self._sct:
            self._sct.close()
            self._sct = None


# Global screenshot capture instance
_capture = None


def get_capture() -> ScreenshotCapture:
    """Get the global screenshot capture instance."""
    global _capture
    if _capture is None:
        _capture = ScreenshotCapture()
    return _capture


def take_screenshot(
    name: Optional[str] = None,
    test_name: Optional[str] = None,
) -> Optional[Path]:
    """
    Convenience function to take a screenshot.

    Args:
        name: Name for the screenshot.
        test_name: Name of the current test.

    Returns:
        Path to the saved screenshot.
    """
    return get_capture().capture(name=name, test_name=test_name)


def screenshot_on_failure(test_name: str, exception: Optional[Exception] = None):
    """
    Convenience function to capture screenshot on test failure.

    Args:
        test_name: Name of the failing test.
        exception: The exception that caused the failure.
    """
    return get_capture().capture_on_failure(test_name, exception)


def generate_screenshot_index() -> Optional[Path]:
    """
    Generate HTML index of all screenshots.

    Returns:
        Path to the index file.
    """
    capture = get_capture()
    if capture.screenshots:
        return capture.generate_index()
    return None

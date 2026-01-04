"""
Screenshot capture utility for STS Arena acceptance tests.

Provides functions to capture screenshots during test execution and
generate an HTML index for browsing.
"""

import os
import time
from datetime import datetime
from pathlib import Path
from typing import Optional

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

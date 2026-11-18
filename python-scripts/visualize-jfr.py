#!/usr/bin/env python3
"""
JFR Visualizer
Interactive tool for exploring and visualizing JFR recording files.
"""

import subprocess
import sys
import html
import json
from pathlib import Path
from collections import Counter
import re


# ── JFR data extraction ────────────────────────────────────────────────────────

def check_dependencies():
    try:
        subprocess.run(["jfr", "--version"], capture_output=True, check=True)
    except (subprocess.CalledProcessError, FileNotFoundError):
        print("Error: 'jfr' command not found. JDK 11+ is required.")
        sys.exit(1)


def run_jfr(args, timeout=60):
    """Run a jfr command and return stdout, or None on failure."""
    try:
        result = subprocess.run(
            ["jfr"] + args,
            capture_output=True, text=True, timeout=timeout
        )
        return result.stdout if result.returncode == 0 else None
    except Exception as e:
        print(f"Error running jfr {' '.join(args)}: {e}")
        return None


def parse_summary_stats(summary_text):
    stats = {'duration': None, 'event_count': 0, 'event_types': {}}
    for line in summary_text.splitlines():
        line = line.strip()
        if 'Duration' in line or 'Recording' in line:
            m = re.search(r'(\d+\.?\d*)\s*(s|ms|min)', line)
            if m:
                value, unit = float(m.group(1)), m.group(2)
                if unit == 'ms':
                    value /= 1000
                elif unit == 'min':
                    value *= 60
                stats['duration'] = value
        if line.startswith('jdk.'):
            parts = line.split()
            if len(parts) >= 2:
                try:
                    count = int(parts[1].replace(',', ''))
                    stats['event_types'][parts[0]] = count
                    stats['event_count'] += count
                except ValueError:
                    pass
    return stats


def get_top_methods(filepath, limit=15):
    output = run_jfr(["print", "--events", "jdk.ExecutionSample", str(filepath)])
    if not output:
        return []
    method_counts = Counter()
    for line in output.splitlines():
        if 'at ' in line or '  ' in line:
            m = re.search(r'([a-zA-Z_$][a-zA-Z0-9_$]*\.)+[a-zA-Z_$][a-zA-Z0-9_$]*\([^)]*\)', line)
            if m:
                method = m.group(0).split('(')[0]
                method_counts[method] += 1
    return method_counts.most_common(limit)


def get_allocation_stats(filepath, limit=15):
    output = run_jfr(["print", "--events", "jdk.ObjectAllocationInNewTLAB", str(filepath)])
    if not output:
        return {}
    class_counts = Counter()
    for line in output.splitlines():
        m = re.search(r'class\s*=\s*([a-zA-Z_$][a-zA-Z0-9_$.<>\[\]]*)', line)
        if m:
            name = m.group(1)
            class_counts[name.split('.')[-1]] += 1
    return dict(class_counts.most_common(limit))


# ── Display helpers ────────────────────────────────────────────────────────────

def divider(char='─'):
    print(char * 80)


def select_file(jfr_dir):
    """Let the user pick a single file from jfr_dir. Returns Path or None."""
    files = sorted(jfr_dir.glob("*.jfr"), key=lambda f: f.stat().st_mtime, reverse=True)
    if not files:
        print(f"No .jfr files found in '{jfr_dir}'")
        return None
    print()
    print("Available JFR files:")
    divider()
    for i, f in enumerate(files, 1):
        size_mb = f.stat().st_size / (1024 * 1024)
        print(f"  {i:2d}) {f.name} ({size_mb:.2f} MB)")
    print()
    try:
        raw = input("Select file number: ").strip()
        idx = int(raw) - 1
        if 0 <= idx < len(files):
            return files[idx]
        print("Invalid selection.")
        return None
    except (ValueError, KeyboardInterrupt):
        print()
        return None


def select_file_or_all(jfr_dir):
    """Let the user pick one file or all files. Returns list of Paths."""
    files = sorted(jfr_dir.glob("*.jfr"), key=lambda f: f.stat().st_mtime, reverse=True)
    if not files:
        print(f"No .jfr files found in '{jfr_dir}'")
        return []
    print()
    print("Available JFR files:")
    divider()
    for i, f in enumerate(files, 1):
        size_mb = f.stat().st_size / (1024 * 1024)
        print(f"  {i:2d}) {f.name} ({size_mb:.2f} MB)")
    print()
    try:
        raw = input("Select file number (or 'a' for all): ").strip()
        if raw.lower() == 'a':
            return files
        idx = int(raw) - 1
        if 0 <= idx < len(files):
            return [files[idx]]
        print("Invalid selection.")
        return []
    except (ValueError, KeyboardInterrupt):
        print()
        return []


# ── Menu actions ───────────────────────────────────────────────────────────────

def action_ascii_stats(jfr_dir):
    filepath = select_file(jfr_dir)
    if not filepath:
        return

    print(f"\n{'='*80}")
    print(f"JFR STATISTICS: {filepath.name}")
    print('='*80)

    print("\nFetching summary...")
    summary_text = run_jfr(["summary", str(filepath)])
    if not summary_text:
        print("Could not get summary from file.")
        return

    stats = parse_summary_stats(summary_text)

    print()
    divider()
    print("RECORDING STATISTICS")
    divider()
    if stats['duration']:
        print(f"Duration:     {stats['duration']:.2f} seconds")
    print(f"Total Events: {stats['event_count']:,}")
    size_bytes = filepath.stat().st_size
    print(f"File Size:    {size_bytes:,} bytes ({size_bytes / (1024*1024):.2f} MB)")
    if stats['event_count'] > 0:
        print(f"Bytes/Event:  {size_bytes / stats['event_count']:.1f}")

    if stats['event_types']:
        print()
        divider()
        print("TOP EVENT TYPES")
        divider()
        sorted_events = sorted(stats['event_types'].items(), key=lambda x: x[1], reverse=True)
        max_count = max(stats['event_types'].values())
        for i, (name, count) in enumerate(sorted_events[:15], 1):
            bar = '█' * int((count / max_count) * 50)
            display = name.replace('jdk.', '')[:30]
            pct = (count / stats['event_count']) * 100
            print(f"{i:2d}. {display:30s} │{bar:50s}│ {count:8,} ({pct:5.1f}%)")

    print()
    divider()
    print("TOP METHODS (from execution samples)")
    divider()
    print("Extracting method data...")
    top_methods = get_top_methods(filepath)
    if top_methods:
        max_count = top_methods[0][1]
        for i, (method, count) in enumerate(top_methods, 1):
            bar = '▓' * int((count / max_count) * 40)
            display = ('...' + method[-47:]) if len(method) > 50 else method
            print(f"{i:2d}. {display:50s} │{bar:40s}│ {count:6,}")
    else:
        print("No method profiling data available.")

    print()
    divider()
    print("ALLOCATION HOTSPOTS (classes)")
    divider()
    print("Extracting allocation data...")
    allocations = get_allocation_stats(filepath)
    if allocations:
        max_count = max(allocations.values())
        for i, (cls, count) in enumerate(allocations.items(), 1):
            bar = '░' * int((count / max_count) * 35)
            display = cls[:40]
            print(f"{i:2d}. {display:40s} │{bar:35s}│ {count:6,}")
    else:
        print("No allocation data available.")

    print(f"\n{'='*80}\n")


def action_summary(jfr_dir):
    filepath = select_file(jfr_dir)
    if not filepath:
        return
    print(f"\nJFR Summary for: {filepath.name}")
    divider('═')
    output = run_jfr(["summary", str(filepath)])
    if output:
        lines = output.splitlines()
        print('\n'.join(lines[:100]))
        if len(lines) > 100:
            print(f"\n(Showing first 100 lines. Full output: jfr summary {filepath})")
    else:
        print("Could not get summary.")


def action_detailed_print(jfr_dir):
    filepath = select_file(jfr_dir)
    if not filepath:
        return

    print()
    print("What would you like to view?")
    print("  1) Method profiling (ExecutionSample)")
    print("  2) Memory allocations")
    print("  3) Garbage collection")
    print("  4) Thread events")
    print("  5) I/O events")
    print("  6) All events")
    print()
    try:
        choice = input("Select (1-6): ").strip()
    except KeyboardInterrupt:
        print()
        return

    event_map = {
        '1': 'jdk.ExecutionSample',
        '2': 'jdk.ObjectAllocationInNewTLAB,jdk.ObjectAllocationOutsideTLAB',
        '3': 'jdk.GarbageCollection,jdk.YoungGarbageCollection,jdk.OldGarbageCollection',
        '4': 'jdk.ThreadStart,jdk.ThreadEnd,jdk.ThreadPark',
        '5': 'jdk.FileRead,jdk.FileWrite,jdk.SocketRead,jdk.SocketWrite',
    }

    if choice in event_map:
        args = ["print", "--events", event_map[choice], str(filepath)]
    elif choice == '6':
        args = ["print", str(filepath)]
    else:
        print("Invalid choice.")
        return

    output = run_jfr(args, timeout=120)
    if output:
        # Page output with a simple pager
        lines = output.splitlines()
        page = 40
        for i in range(0, len(lines), page):
            print('\n'.join(lines[i:i+page]))
            if i + page < len(lines):
                try:
                    raw = input("\n-- Press Enter for more, q to quit -- ").strip()
                    if raw.lower() == 'q':
                        break
                except KeyboardInterrupt:
                    break
    else:
        print("No output or error running jfr print.")


def action_export_json(jfr_dir):
    filepath = select_file(jfr_dir)
    if not filepath:
        return

    output_path = filepath.with_suffix('.json')
    print(f"\nExporting to JSON: {output_path}")
    output = run_jfr(["print", "--json", str(filepath)], timeout=120)
    if output:
        output_path.write_text(output)
        size = output_path.stat().st_size / (1024 * 1024)
        print(f"✓ Exported: {output_path} ({size:.2f} MB)")
    else:
        print("Export failed.")


def action_html_report(jfr_dir):
    files = select_file_or_all(jfr_dir)
    if not files:
        return

    if len(files) == 1:
        output_path = files[0].with_suffix('.html')
    else:
        from datetime import datetime
        output_path = jfr_dir / f"jfr-report-{datetime.now().strftime('%Y%m%d-%H%M%S')}.html"

    print(f"\nGenerating HTML report for {len(files)} file(s)...")

    parts = ['<!DOCTYPE html><html><head><title>JFR Report</title><style>',
             'body{font-family:monospace;padding:20px;background:#1e1e1e;color:#d4d4d4}',
             'pre{background:#2d2d2d;padding:15px;overflow-x:auto;border-radius:5px}',
             'h1,h2{color:#4ec9b0}h3{color:#569cd6}',
             '.section{margin:20px 0}.file-section{border-left:3px solid #4ec9b0;padding-left:15px;margin:30px 0}',
             '</style></head><body><h1>JFR Recording Report</h1>']

    for f in files:
        print(f"  Processing: {f.name}")
        size = f.stat().st_size / (1024 * 1024)
        summary = run_jfr(["summary", str(f)]) or "(summary unavailable)"
        safe_name = html.escape(f.name)
        safe_summary = html.escape(summary)
        parts.append(f'<div class="file-section"><h2>{safe_name}</h2>')
        parts.append(f'<div class="section"><h3>File Information</h3><pre>File: {safe_name}\nSize: {size:.2f} MB</pre></div>')
        parts.append(f'<div class="section"><h3>Summary</h3><pre>{safe_summary}</pre></div></div>')

    parts.append('</body></html>')
    output_path.write_text('\n'.join(parts))
    print(f"✓ Generated: {output_path}")


def action_list_files(jfr_dir):
    files = sorted(jfr_dir.glob("*.jfr"), key=lambda f: f.stat().st_mtime, reverse=True)
    print()
    print("Available JFR files:")
    divider('═')
    if not files:
        print("No .jfr files found.")
        return
    for f in files:
        size = f.stat().st_size / (1024 * 1024)
        mtime = f.stat().st_mtime
        import time
        ts = time.strftime('%Y-%m-%d %H:%M:%S', time.localtime(mtime))
        print(f"  {f.name:<55} {size:6.2f} MB  {ts}")
    print(f"\nTotal: {len(files)} file(s)")


def action_compare(jfr_dir):
    print("\nSelect first file:")
    f1 = select_file(jfr_dir)
    if not f1:
        return
    print("\nSelect second file:")
    f2 = select_file(jfr_dir)
    if not f2:
        return

    s1 = f1.stat().st_size
    s2 = f2.stat().st_size
    diff = s2 - s1
    pct = (diff * 100 / s1) if s1 else 0

    print()
    divider('═')
    print(f"File 1: {f1.name}  ({s1:,} bytes)")
    print(f"File 2: {f2.name}  ({s2:,} bytes)")
    print(f"Difference: {diff:+,} bytes ({pct:+.1f}%)")
    divider()

    try:
        raw = input("Show event type counts for both? [y/N]: ").strip()
    except KeyboardInterrupt:
        print()
        return

    if raw.lower() == 'y':
        for label, f in [("File 1", f1), ("File 2", f2)]:
            output = run_jfr(["summary", str(f)])
            if output:
                stats = parse_summary_stats(output)
                print(f"\n{label} — {f.name}")
                for name, count in sorted(stats['event_types'].items(), key=lambda x: x[1], reverse=True)[:10]:
                    print(f"  {name:<45} {count:>8,}")


def action_event_types(jfr_dir):
    filepath = select_file(jfr_dir)
    if not filepath:
        return
    output = run_jfr(["metadata", str(filepath)])
    if not output:
        print("Could not read metadata.")
        return
    print(f"\nEvent types in: {filepath.name}")
    divider('═')
    types = sorted(line.strip() for line in output.splitlines() if 'jdk.' in line)
    for t in types:
        print(f"  {t}")
    print(f"\nTotal: {len(types)} event type(s)")


def action_install_jmc():
    print()
    if sys.platform == 'darwin':
        print("Installing JDK Mission Control via Homebrew...")
        subprocess.run(["brew", "install", "--cask", "jdk-mission-control"])
    elif sys.platform.startswith('linux'):
        print("Download JMC from: https://jdk.java.net/jmc/")
        print("Or via package manager:")
        print("  Ubuntu/Debian: sudo apt install jmc")
        print("  Fedora:        sudo dnf install jmc")
    else:
        print("Download JMC from: https://jdk.java.net/jmc/")


def action_open_jmc(jfr_dir):
    filepath = select_file(jfr_dir)
    if not filepath:
        return
    print(f"\nOpening {filepath.name} in JDK Mission Control...")
    launched = False
    for cmd in ["jmc", "jmc.app"]:
        if subprocess.run(["which", cmd], capture_output=True).returncode == 0:
            subprocess.Popen([cmd, "-open", str(filepath)])
            launched = True
            break
    if not launched and sys.platform == 'darwin':
        app = "/Applications/JDK Mission Control.app"
        if Path(app).exists():
            subprocess.Popen(["open", "-a", "JDK Mission Control", str(filepath)])
            launched = True
    if launched:
        print("✓ JMC launched")
    else:
        print("✗ JMC not found. Install it with option 9 or download from: https://jdk.java.net/jmc/")


# ── Main ───────────────────────────────────────────────────────────────────────

def show_menu():
    print()
    print("╔═══════════════════════════════════════════════════════════════════════╗")
    print("║                    JFR File Visualization Tools                       ║")
    print("╚═══════════════════════════════════════════════════════════════════════╝")
    print()
    print("  1) ASCII stats (event distribution, hot methods, allocations)")
    print("  2) Summary (jfr summary)")
    print("  3) Detailed event print (jfr print)")
    print("  4) Export to JSON")
    print("  5) Generate HTML report")
    print("  6) List all .jfr files")
    print("  7) Compare two snapshots")
    print("  8) View event types in recording")
    print("  9) Open in JDK Mission Control (JMC) - RECOMMENDED")
    print(" 10) Install JMC (macOS/Linux)")
    print("  0) Exit")
    print()


def main():
    if len(sys.argv) < 2:
        print("Usage: visualize-jfr.py <folder>")
        print("  e.g.: visualize-jfr.py apps/jfr-profiler-based-app/jfr-exports")
        sys.exit(1)

    jfr_dir = Path(sys.argv[1])
    if not jfr_dir.is_dir():
        print(f"Error: '{jfr_dir}' is not a directory.")
        sys.exit(1)

    check_dependencies()

    while True:
        show_menu()
        try:
            choice = input(f"Enter choice (0-10) [{jfr_dir}]: ").strip()
        except KeyboardInterrupt:
            print("\nGoodbye!")
            sys.exit(0)

        if   choice == '1': action_ascii_stats(jfr_dir)
        elif choice == '2': action_summary(jfr_dir)
        elif choice == '3': action_detailed_print(jfr_dir)
        elif choice == '4': action_export_json(jfr_dir)
        elif choice == '5': action_html_report(jfr_dir)
        elif choice == '6': action_list_files(jfr_dir)
        elif choice == '7': action_compare(jfr_dir)
        elif choice == '8': action_event_types(jfr_dir)
        elif choice == '9': action_open_jmc(jfr_dir)
        elif choice == '10': action_install_jmc()
        elif choice == '0':
            print("Goodbye!")
            sys.exit(0)
        else:
            print("Invalid choice.")

        try:
            input("\nPress Enter to continue...")
        except KeyboardInterrupt:
            print("\nGoodbye!")
            sys.exit(0)


if __name__ == "__main__":
    main()

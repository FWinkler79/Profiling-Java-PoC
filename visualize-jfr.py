#!/usr/bin/env python3
"""
JFR Event Visualizer
Creates visual charts and statistics from JFR files using matplotlib
"""

import subprocess
import json
import sys
from pathlib import Path
from collections import defaultdict, Counter
import re

def check_dependencies():
    """Check if required tools are available"""
    missing = []

    # Check for jfr command
    try:
        subprocess.run(["jfr", "--version"], capture_output=True, check=True)
    except (subprocess.CalledProcessError, FileNotFoundError):
        missing.append("jfr (JDK 11+ required)")

    if missing:
        print("Missing dependencies:")
        for dep in missing:
            print(f"  - {dep}")
        sys.exit(1)

def get_jfr_summary(filepath):
    """Get summary statistics from JFR file"""
    try:
        result = subprocess.run(
            ["jfr", "summary", str(filepath)],
            capture_output=True,
            text=True,
            timeout=30
        )

        if result.returncode != 0:
            return None

        return result.stdout

    except Exception as e:
        print(f"Error getting summary: {e}")
        return None

def parse_summary_stats(summary_text):
    """Parse key statistics from summary output"""
    stats = {
        'duration': None,
        'event_count': 0,
        'event_types': {},
    }

    for line in summary_text.splitlines():
        line = line.strip()

        # Extract duration
        if 'Duration' in line or 'Recording' in line:
            match = re.search(r'(\d+\.?\d*)\s*(s|ms|min)', line)
            if match:
                value = float(match.group(1))
                unit = match.group(2)
                if unit == 'ms':
                    value /= 1000
                elif unit == 'min':
                    value *= 60
                stats['duration'] = value

        # Count events by type
        if line.startswith('jdk.'):
            parts = line.split()
            if len(parts) >= 2:
                event_name = parts[0]
                try:
                    count = int(parts[1].replace(',', ''))
                    stats['event_types'][event_name] = count
                    stats['event_count'] += count
                except ValueError:
                    pass

    return stats

def get_top_methods(filepath, limit=20):
    """Extract top methods from execution samples"""
    try:
        result = subprocess.run(
            ["jfr", "print", "--events", "jdk.ExecutionSample", str(filepath)],
            capture_output=True,
            text=True,
            timeout=30
        )

        if result.returncode != 0:
            return []

        # Parse method names from stack traces
        method_counts = Counter()

        for line in result.stdout.splitlines():
            # Look for method signatures
            if 'at ' in line or '  ' in line:
                # Extract method name
                match = re.search(r'([a-zA-Z_$][a-zA-Z0-9_$]*\.)+[a-zA-Z_$][a-zA-Z0-9_$]*\([^)]*\)', line)
                if match:
                    method = match.group(0)
                    # Simplify method name
                    if '(' in method:
                        method = method.split('(')[0]
                    method_counts[method] += 1

        return method_counts.most_common(limit)

    except Exception as e:
        print(f"Error extracting methods: {e}")
        return []

def get_allocation_stats(filepath):
    """Get allocation statistics"""
    try:
        result = subprocess.run(
            ["jfr", "print", "--events", "jdk.ObjectAllocationInNewTLAB", str(filepath)],
            capture_output=True,
            text=True,
            timeout=30
        )

        if result.returncode != 0:
            return {}

        class_counts = Counter()

        for line in result.stdout.splitlines():
            # Look for class names
            match = re.search(r'class\s*=\s*([a-zA-Z_$][a-zA-Z0-9_$.<>[\]]*)', line)
            if match:
                class_name = match.group(1)
                # Simplify class name
                if '.' in class_name:
                    class_name = class_name.split('.')[-1]
                class_counts[class_name] += 1

        return dict(class_counts.most_common(15))

    except Exception as e:
        print(f"Error getting allocation stats: {e}")
        return {}

def visualize_ascii(filepath):
    """Create ASCII visualization of JFR data"""
    print(f"\n{'='*80}")
    print(f"JFR VISUALIZATION: {filepath.name}")
    print('='*80)

    # Get summary
    print("\nFetching summary...")
    summary_text = get_jfr_summary(filepath)

    if not summary_text:
        print("Could not get summary from file")
        return

    stats = parse_summary_stats(summary_text)

    # Basic stats
    print(f"\n{'─'*80}")
    print("RECORDING STATISTICS")
    print('─'*80)

    if stats['duration']:
        print(f"Duration:     {stats['duration']:.2f} seconds")
    print(f"Total Events: {stats['event_count']:,}")

    size_bytes = filepath.stat().st_size
    size_mb = size_bytes / (1024 * 1024)
    print(f"File Size:    {size_bytes:,} bytes ({size_mb:.2f} MB)")

    if stats['event_count'] > 0:
        bytes_per_event = size_bytes / stats['event_count']
        print(f"Bytes/Event:  {bytes_per_event:.1f}")

    # Event type distribution
    if stats['event_types']:
        print(f"\n{'─'*80}")
        print("TOP EVENT TYPES")
        print('─'*80)

        # Sort by count
        sorted_events = sorted(stats['event_types'].items(), key=lambda x: x[1], reverse=True)

        max_count = max(stats['event_types'].values())
        bar_width = 50

        for i, (event_name, count) in enumerate(sorted_events[:15], 1):
            bar_length = int((count / max_count) * bar_width)
            bar = '█' * bar_length
            percent = (count / stats['event_count']) * 100

            # Shorten event name
            display_name = event_name.replace('jdk.', '')
            if len(display_name) > 30:
                display_name = display_name[:27] + '...'

            print(f"{i:2d}. {display_name:30s} │{bar:50s}│ {count:8,} ({percent:5.1f}%)")

    # Method profiling
    print(f"\n{'─'*80}")
    print("TOP METHODS (from execution samples)")
    print('─'*80)
    print("Extracting method data...")

    top_methods = get_top_methods(filepath, limit=15)

    if top_methods:
        max_count = top_methods[0][1]
        bar_width = 40

        for i, (method, count) in enumerate(top_methods, 1):
            bar_length = int((count / max_count) * bar_width)
            bar = '▓' * bar_length

            # Shorten method name
            display_name = method
            if len(display_name) > 50:
                display_name = '...' + display_name[-47:]

            print(f"{i:2d}. {display_name:50s} │{bar:40s}│ {count:6,}")
    else:
        print("No method profiling data available")

    # Allocation hotspots
    print(f"\n{'─'*80}")
    print("ALLOCATION HOTSPOTS (classes)")
    print('─'*80)
    print("Extracting allocation data...")

    allocations = get_allocation_stats(filepath)

    if allocations:
        max_count = max(allocations.values())
        bar_width = 35

        for i, (class_name, count) in enumerate(allocations.items(), 1):
            bar_length = int((count / max_count) * bar_width)
            bar = '░' * bar_length

            display_name = class_name
            if len(display_name) > 40:
                display_name = display_name[:37] + '...'

            print(f"{i:2d}. {display_name:40s} │{bar:35s}│ {count:6,}")
    else:
        print("No allocation data available")

    print(f"\n{'='*80}\n")

def main():
    print("="*80)
    print("JFR EVENT VISUALIZER")
    print("="*80)

    # Check dependencies
    check_dependencies()

    # Find JFR files
    jfr_files = []
    for directory in ['jfr-snapshots', 'jfr-recordings', '.']:
        path = Path(directory)
        if path.exists():
            jfr_files.extend(path.glob('*.jfr'))

    if not jfr_files:
        print("\nNo .jfr files found in current directory, jfr-snapshots/, or jfr-recordings/")
        if len(sys.argv) > 1:
            filepath = Path(sys.argv[1])
            if filepath.exists():
                jfr_files = [filepath]
            else:
                print(f"File not found: {sys.argv[1]}")
                sys.exit(1)
        else:
            sys.exit(1)

    # Sort by modification time
    jfr_files = sorted(jfr_files, key=lambda f: f.stat().st_mtime, reverse=True)

    # Select file
    if len(sys.argv) > 1:
        filepath = Path(sys.argv[1])
        if not filepath.exists():
            print(f"File not found: {sys.argv[1]}")
            sys.exit(1)
    else:
        print("\nAvailable JFR files:")
        for i, f in enumerate(jfr_files[:20], 1):
            size = f.stat().st_size / (1024 * 1024)
            print(f"  {i:2d}. {f.name} ({size:.2f} MB)")

        if len(jfr_files) > 20:
            print(f"  ... and {len(jfr_files) - 20} more")

        try:
            choice = input("\nSelect file number (or press Enter for most recent): ").strip()

            if not choice:
                filepath = jfr_files[0]
            else:
                idx = int(choice) - 1
                if 0 <= idx < len(jfr_files):
                    filepath = jfr_files[idx]
                else:
                    print("Invalid selection")
                    sys.exit(1)

        except (ValueError, KeyboardInterrupt):
            print("\nCancelled")
            sys.exit(0)

    # Visualize
    visualize_ascii(filepath)

    print("TIP: For interactive visualization, use JDK Mission Control:")
    print(f"  jmc -open {filepath}")

if __name__ == "__main__":
    main()

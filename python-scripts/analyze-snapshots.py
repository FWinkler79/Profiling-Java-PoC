#!/usr/bin/env python3
"""
JFR Snapshot Analyzer
Analyzes JFR snapshot files to understand size patterns and event distributions
"""

import os
import sys
import subprocess
import json
from pathlib import Path
from datetime import datetime
from collections import defaultdict

class JFRAnalyzer:
    def __init__(self, directory):
        self.directory = Path(directory)
        if not self.directory.exists():
            print(f"Error: Directory {directory} does not exist")
            sys.exit(1)

    def get_jfr_files(self):
        """Get all JFR files in directory"""
        files = list(self.directory.glob("*.jfr"))
        return sorted(files, key=lambda x: x.stat().st_mtime)

    def analyze_file_sizes(self, files):
        """Analyze file sizes"""
        if not files:
            print("No JFR files found")
            return

        print("\n" + "="*80)
        print("FILE SIZE ANALYSIS")
        print("="*80)

        sizes = []
        for i, filepath in enumerate(files, 1):
            size = filepath.stat().st_size
            sizes.append(size)
            size_mb = size / (1024 * 1024)
            mtime = datetime.fromtimestamp(filepath.stat().st_mtime)

            print(f"{i:3d}. {filepath.name}")
            print(f"     Size: {size:,} bytes ({size_mb:.2f} MB)")
            print(f"     Modified: {mtime.strftime('%Y-%m-%d %H:%M:%S')}")

        print("\n" + "-"*80)
        print("SUMMARY STATISTICS")
        print("-"*80)
        print(f"Total files:     {len(sizes)}")
        print(f"Total size:      {sum(sizes):,} bytes ({sum(sizes)/(1024*1024):.2f} MB)")
        print(f"Average size:    {sum(sizes)/len(sizes):,.0f} bytes ({sum(sizes)/len(sizes)/(1024*1024):.2f} MB)")
        print(f"Minimum size:    {min(sizes):,} bytes ({min(sizes)/(1024*1024):.2f} MB)")
        print(f"Maximum size:    {max(sizes):,} bytes ({max(sizes)/(1024*1024):.2f} MB)")
        print(f"Size variation:  {max(sizes) - min(sizes):,} bytes ({(max(sizes)-min(sizes))/(1024*1024):.2f} MB)")

        if len(sizes) > 1:
            # Calculate growth trend
            growth = (sizes[-1] - sizes[0]) / sizes[0] * 100
            print(f"Growth (first to last): {growth:+.1f}%")

    def get_event_summary(self, filepath):
        """Get event summary from JFR file using jfr tool"""
        try:
            # Run jfr print --events to get event list
            cmd = ["jfr", "print", "--events", str(filepath)]
            result = subprocess.run(
                cmd,
                capture_output=True,
                text=True,
                timeout=30
            )

            if result.returncode != 0:
                return None

            # Parse output to count events
            event_counts = defaultdict(int)
            for line in result.stdout.splitlines():
                line = line.strip()
                if line and not line.startswith("---") and ":" in line:
                    parts = line.split(":")
                    if len(parts) >= 2:
                        event_name = parts[0].strip()
                        if event_name.startswith("jdk."):
                            event_counts[event_name] += 1

            return event_counts

        except Exception as e:
            print(f"Error analyzing {filepath.name}: {e}")
            return None

    def analyze_sample_file(self, filepath):
        """Detailed analysis of a single file"""
        print("\n" + "="*80)
        print(f"DETAILED ANALYSIS: {filepath.name}")
        print("="*80)

        size = filepath.stat().st_size
        print(f"File size: {size:,} bytes ({size/(1024*1024):.2f} MB)")

        print("\nAttempting to extract event summary...")
        print("(This may take a moment...)")

        events = self.get_event_summary(filepath)

        if events:
            print("\nTop Event Types:")
            sorted_events = sorted(events.items(), key=lambda x: x[1], reverse=True)
            for i, (event, count) in enumerate(sorted_events[:15], 1):
                print(f"  {i:2d}. {event:40s} : {count:,} occurrences")

            total_events = sum(events.values())
            print(f"\nTotal event occurrences: {total_events:,}")
            if total_events > 0:
                avg_bytes_per_event = size / total_events
                print(f"Average bytes per event: {avg_bytes_per_event:.1f}")
        else:
            print("Could not extract event data. Use JMC or 'jfr print' for detailed analysis.")

    def run(self):
        """Run the analysis"""
        print("="*80)
        print("JFR SNAPSHOT ANALYZER")
        print("="*80)
        print(f"Directory: {self.directory.absolute()}")

        files = self.get_jfr_files()

        if not files:
            print("\nNo .jfr files found in directory")
            return

        self.analyze_file_sizes(files)

        # Offer detailed analysis of a sample file
        if files:
            print("\n" + "="*80)
            print("DETAILED ANALYSIS OPTION")
            print("="*80)
            print("Would you like to analyze a specific file in detail?")
            print("\nOptions:")
            print("  1. First file (oldest)")
            print(f"  2. Last file (newest)")
            print("  3. Largest file")
            print("  4. Skip detailed analysis")

            try:
                choice = input("\nEnter choice (1-4): ").strip()

                if choice == "1":
                    self.analyze_sample_file(files[0])
                elif choice == "2":
                    self.analyze_sample_file(files[-1])
                elif choice == "3":
                    largest = max(files, key=lambda f: f.stat().st_size)
                    self.analyze_sample_file(largest)
                else:
                    print("\nSkipping detailed analysis")

            except (EOFError, KeyboardInterrupt):
                print("\n\nAnalysis complete")

def main():
    if len(sys.argv) > 1:
        directory = sys.argv[1]
    else:
        # Default to jfr-snapshots directory
        directory = "jfr-snapshots"

        # If that doesn't exist, try jfr-recordings
        if not Path(directory).exists():
            directory = "jfr-recordings"

        # If neither exists, ask user
        if not Path(directory).exists():
            print("JFR Snapshot Analyzer")
            print("")
            directory = input("Enter directory containing .jfr files: ").strip()

    analyzer = JFRAnalyzer(directory)
    analyzer.run()

if __name__ == "__main__":
    main()

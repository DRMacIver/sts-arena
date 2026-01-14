#!/usr/bin/env python3
"""
Test file that runs the exploratory agent to find bugs.
"""

import pytest
from exploratory_agent import run_exploration


class TestExploratoryAgent:
    """Run exploratory testing agents to find bugs."""

    def test_systematic_exploration(self):
        """
        Run systematic exploration testing all character/encounter combinations.
        This takes about 2 minutes and tests various arena flows.
        """
        report = run_exploration(strategy="systematic", duration=120)

        print("\n" + "="*60)
        print("SYSTEMATIC EXPLORATION REPORT")
        print("="*60)
        print(f"Duration: {report['duration_seconds']:.1f}s")
        print(f"Actions: {report['actions_taken']}")
        print(f"Characters tried: {report['characters_tried']}")
        print(f"Encounters tried: {len(report['encounters_tried'])}")
        print()
        print("FINDINGS:")
        print(f"  Crashes: {report['summary']['crashes']}")
        print(f"  Errors: {report['summary']['errors']}")
        print(f"  Anomalies: {report['summary']['anomalies']}")

        if report['findings']:
            print("\nDETAILED FINDINGS:")
            for i, finding in enumerate(report['findings']):
                print(f"\n  [{i+1}] {finding['severity'].upper()}: {finding['category']}")
                print(f"      {finding['description']}")

        # Fail if we found any crashes
        assert report['summary']['crashes'] == 0, \
            f"Found {report['summary']['crashes']} crashes during exploration"

    def test_random_exploration(self):
        """
        Run random exploration to find edge cases.
        This uses random actions to uncover unexpected states.
        """
        report = run_exploration(strategy="random", duration=90)

        print("\n" + "="*60)
        print("RANDOM EXPLORATION REPORT")
        print("="*60)
        print(f"Duration: {report['duration_seconds']:.1f}s")
        print(f"Actions: {report['actions_taken']}")
        print(f"Findings: {report['summary']['total_findings']}")

        if report['findings']:
            print("\nFINDINGS:")
            for finding in report['findings']:
                print(f"  - {finding['severity']}: {finding['description']}")

        assert report['summary']['crashes'] == 0, \
            f"Found {report['summary']['crashes']} crashes"

    def test_chaos_exploration(self):
        """
        Run chaos exploration to find robustness issues.
        This sends commands at wrong times and with invalid parameters.
        """
        report = run_exploration(strategy="chaos", duration=60)

        print("\n" + "="*60)
        print("CHAOS EXPLORATION REPORT")
        print("="*60)
        print(f"Duration: {report['duration_seconds']:.1f}s")
        print(f"Actions: {report['actions_taken']}")
        print(f"Errors encountered: {report['errors_encountered']}")

        # Chaos mode expects some errors (that's the point)
        # But crashes are still bad
        assert report['summary']['crashes'] == 0, \
            f"Found {report['summary']['crashes']} crashes during chaos testing"


if __name__ == "__main__":
    pytest.main([__file__, "-v", "-s"])

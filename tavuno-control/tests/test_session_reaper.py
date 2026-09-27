"""Tests for session reaper functionality."""

import unittest
from datetime import datetime, timedelta

from app.session_reaper import reap_expired_sessions


class FakeConnection:
    """Fake database connection for testing session reaper."""
    
    def __init__(self):
        self.statements = []
        self.query = ""
        self.parameters = ()
        self._sessions = []
        self._rowcount = 0
    
    def execute(self, query, parameters=()):
        self.query = " ".join(query.split())
        self.parameters = parameters
        self.statements.append((self.query, parameters))
        
        if "UPDATE tavuno_playback_sessions" in self.query:
            # Find sessions matching the criteria
            # status = 'active' AND expires_at < NOW()
            updated_count = 0
            for session in self._sessions:
                if session["status"] == "active" and session["expires_at"] < datetime.now():
                    session["status"] = "expired"
                    updated_count += 1
            self._rowcount = updated_count
            return self
        
        return self
    
    @property
    def rowcount(self):
        return self._rowcount
    
    def commit(self):
        pass
    
    def rollback(self):
        pass
    
    def fetchall(self):
        # Return IDs of updated sessions
        result = []
        for s in self._sessions:
            if s["status"] == "expired":
                result.append({"id": s["id"]})
        return result


class SessionReaperTests(unittest.TestCase):
    """Test session reaper functionality."""
    
    def test_reap_expired_sessions_updates_status(self):
        """Test that expired sessions are updated to 'expired' status."""
        connection = FakeConnection()
        
        # Add an expired session
        connection._sessions.append({
            "id": 1,
            "profile": 1,
            "device": 1,
            "content_type": "live",
            "content_key": "channel_1",
            "status": "active",
            "expires_at": datetime.now() - timedelta(minutes=10)
        })
        
        # Add a future session (should not be reaped)
        connection._sessions.append({
            "id": 2,
            "profile": 1,
            "device": 1,
            "content_type": "live",
            "content_key": "channel_2",
            "status": "active",
            "expires_at": datetime.now() + timedelta(minutes=10)
        })
        
        # Add a stopped session (should not be reaped)
        connection._sessions.append({
            "id": 3,
            "profile": 1,
            "device": 1,
            "content_type": "live",
            "content_key": "channel_3",
            "status": "stopped",
            "expires_at": datetime.now() - timedelta(minutes=10)
        })
        
        reaped_count = reap_expired_sessions(connection)
        
        # Assert one session was reaped
        self.assertEqual(reaped_count, 1)
        
        # Assert the expired session was updated
        self.assertEqual(connection._sessions[0]["status"], "expired")
        
        # Assert the future session was not touched
        self.assertEqual(connection._sessions[1]["status"], "active")
        
        # Assert the stopped session was not touched
        self.assertEqual(connection._sessions[2]["status"], "stopped")
    
    def test_reap_expired_sessions_no_expired_sessions(self):
        """Test that reaper returns 0 when no sessions are expired."""
        connection = FakeConnection()
        
        # Add only future sessions
        connection._sessions.append({
            "id": 1,
            "profile": 1,
            "device": 1,
            "content_type": "live",
            "content_key": "channel_1",
            "status": "active",
            "expires_at": datetime.now() + timedelta(minutes=10)
        })
        
        reaped_count = reap_expired_sessions(connection)
        
        # Assert no sessions were reaped
        self.assertEqual(reaped_count, 0)
        
        # Assert the session status was not changed
        self.assertEqual(connection._sessions[0]["status"], "active")


if __name__ == "__main__":
    unittest.main()

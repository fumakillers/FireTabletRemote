package com.fumakillers.fireremoteserver.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerRecoveryPolicyTest {
    @Test
    fun defaultsToStoppedAndDoesNotRecover() {
        val policy = ServerRecoveryPolicy(InMemoryStorage())

        assertEquals(ServerDesiredState.STOPPED, policy.desiredState)
        assertFalse(policy.shouldRecover)
    }

    @Test
    fun runningDesiredStateIsPersistedAndRestored() {
        val storage = InMemoryStorage()
        ServerRecoveryPolicy(storage).userRequestedStart()

        val recreatedPolicy = ServerRecoveryPolicy(storage)

        assertEquals(ServerDesiredState.RUNNING, recreatedPolicy.desiredState)
        assertTrue(recreatedPolicy.shouldRecover)
    }

    @Test
    fun userStopDisablesRecovery() {
        val storage = InMemoryStorage()
        val policy = ServerRecoveryPolicy(storage)
        policy.userRequestedStart()

        policy.userRequestedStop()

        assertEquals(ServerDesiredState.STOPPED, policy.desiredState)
        assertFalse(policy.shouldRecover)
    }

    @Test
    fun threeFailuresWithinWindowBlockAutomaticRestart() {
        val storage = InMemoryStorage()
        var now = 1_000L
        val policy = ServerRecoveryPolicy(storage) { now }
        policy.userRequestedStart()

        assertFalse(policy.recordStartFailure().restartBlocked)
        now += 1_000
        assertFalse(policy.recordStartFailure().restartBlocked)
        now += 1_000
        val thirdFailure = policy.recordStartFailure()

        assertEquals(3, thirdFailure.failureCount)
        assertTrue(thirdFailure.restartBlocked)
        assertFalse(policy.shouldRecover)
        assertEquals(ServerDesiredState.RUNNING, policy.desiredState)
    }

    @Test
    fun failuresOutsideWindowDoNotAccumulate() {
        val storage = InMemoryStorage()
        var now = 1_000L
        val policy = ServerRecoveryPolicy(storage) { now }
        policy.userRequestedStart()
        policy.recordStartFailure()
        policy.recordStartFailure()

        now += ServerRecoveryPolicy.FAILURE_WINDOW_MS + 1
        val decision = policy.recordStartFailure()

        assertEquals(1, decision.failureCount)
        assertFalse(decision.restartBlocked)
        assertTrue(policy.shouldRecover)
    }

    @Test
    fun manualStartAndSuccessfulStartClearFailureBlock() {
        val storage = InMemoryStorage()
        val policy = ServerRecoveryPolicy(storage) { 1_000L }
        policy.userRequestedStart()
        repeat(3) { policy.recordStartFailure() }
        assertFalse(policy.shouldRecover)

        policy.userRequestedStart()
        assertTrue(policy.shouldRecover)
        policy.recordStartFailure()
        policy.recordStartSucceeded()

        assertEquals(0, storage.snapshot.failureCount)
        assertFalse(storage.snapshot.restartBlocked)
    }

    private class InMemoryStorage : ServerRecoveryStorage {
        var snapshot = ServerRecoverySnapshot()

        override fun load(): ServerRecoverySnapshot = snapshot

        override fun save(snapshot: ServerRecoverySnapshot) {
            this.snapshot = snapshot
        }
    }
}

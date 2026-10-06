package com.oblutack.timenote.testutil

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * Runs a test with an unconfined dispatcher installed as Dispatchers.Main (what viewModelScope
 * uses) and as the test dispatcher, so launched work runs eagerly while delay() still follows
 * the test scheduler's virtual time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun runAppTest(block: suspend TestScope.() -> Unit): TestResult {
    val dispatcher = UnconfinedTestDispatcher()
    Dispatchers.setMain(dispatcher)
    return try {
        runTest(dispatcher, testBody = block)
    } finally {
        Dispatchers.resetMain()
    }
}

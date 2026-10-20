package com.example

import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun studentCode_matchesPattern() {
    val allowedChars = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    val randomPart = (1..5)
        .map { allowedChars.random() }
        .joinToString("")
    val code = "HS-$randomPart"
    assertTrue(code.startsWith("HS-"))
    assertEquals(8, code.length)
  }
}


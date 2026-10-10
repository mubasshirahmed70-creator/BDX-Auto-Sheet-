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
  fun testUsaNameGenerator_producesValidNames() {
    val name = com.example.data.model.UsaNameGenerator.nextRandomName()
    assertTrue(name.firstName.isNotBlank())
    assertTrue(name.lastName.isNotBlank())
    assertEquals("${name.firstName} ${name.lastName}", name.fullName)
    assertTrue(com.example.data.model.UsaNameGenerator.MALE_FIRST_NAMES.size >= 100)
    assertTrue(com.example.data.model.UsaNameGenerator.SURNAMES.size >= 100)
  }

  @Test
  fun testUsaNameGenerator_historyNavigation() {
    val first = com.example.data.model.UsaNameGenerator.nextRandomName()
    val second = com.example.data.model.UsaNameGenerator.nextRandomName()
    assertTrue(com.example.data.model.UsaNameGenerator.hasPrevious())
    val prev = com.example.data.model.UsaNameGenerator.previousName()
    assertNotNull(prev)
    assertEquals(first.fullName, prev?.fullName)
  }
}

package dev.keeps.core

import org.junit.Assert.*
import org.junit.Test

class FileNamesTest {
    @Test fun conflictRetainsExtensionAndDoesNotOverwriteCaseVariants() {
        assertEquals("Photo (3).jpg", FileNames.available("Photo.jpg", listOf("photo.JPG", "Photo (2).jpg")))
        assertEquals(".nomedia (2)", FileNames.available(".nomedia", listOf(".nomedia")))
        assertEquals("नोट.txt", FileNames.available(" नोट.txt ", emptyList()))
    }
    @Test fun rejectsTraversalAndUnsafeNames() {
        listOf("", ".", "..", "../photo.jpg", "folder\\file", "a\u0000b", "अ".repeat(81)).forEach {
            assertThrows(IllegalArgumentException::class.java) { FileNames.validate(it) }
        }
    }
}

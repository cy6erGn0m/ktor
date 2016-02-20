package org.jetbrains.ktor.content

import org.jetbrains.ktor.util.*
import java.nio.file.attribute.*
import java.time.*
import java.util.*

interface HasVersion2 {
    val version: Version
}

interface Version

data class LastModifiedVersion(val lastModified: LocalDateTime) : Version {
    constructor(lastModified: FileTime) : this(LocalDateTime.ofInstant(lastModified.toInstant(), ZoneId.systemDefault()))
    constructor(lastModified: Date) : this(lastModified.toLocalDateTime())
}
data class EntityTagVersion(val etag: String) : Version

object SameVersion : Version
object AlwaysDifferentVersion : Version



package vn.lienket.server

import org.sqlite.SQLiteConfig
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.time.Instant

data class Link(val id: Long, val shortCode: String, val longUrl: String, val redirectStatus: Int, val createdAt: String)
data class Creation(val link: Link, val reused: Boolean)

class Database(private val path: Path) {
    init { Class.forName("org.sqlite.JDBC") }

    private fun connect(): Connection {
        val config = SQLiteConfig().apply {
            setBusyTimeout(10_000)
            setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE)
        }
        return DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}", config.toProperties())
    }

    fun initialize() {
        Files.createDirectories(path.toAbsolutePath().parent)
        connect().use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("PRAGMA journal_mode=WAL")
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS links (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        short_code TEXT COLLATE BINARY UNIQUE,
                        long_url TEXT NOT NULL,
                        redirect_status INTEGER NOT NULL CHECK (redirect_status IN (301, 302)),
                        created_at TEXT NOT NULL,
                        UNIQUE (long_url, redirect_status)
                    )
                """.trimIndent())
            }
        }
    }

    fun createOrGet(longUrl: String, redirectStatus: Int): Creation = connect().use { connection ->
        connection.autoCommit = false // SQLiteConfig mở transaction IMMEDIATE trước SELECT.
        try {
            val existing = connection.prepareStatement("SELECT * FROM links WHERE long_url = ? AND redirect_status = ?").use { query ->
                query.setString(1, longUrl)
                query.setInt(2, redirectStatus)
                query.executeQuery().use { result -> if (result.next()) result.toLink() else null }
            }
            if (existing != null) {
                connection.commit()
                return@use Creation(existing, true)
            }
            val createdAt = Instant.now().toString()
            connection.prepareStatement("INSERT INTO links (long_url, redirect_status, created_at) VALUES (?, ?, ?)").use {
                it.setString(1, longUrl)
                it.setInt(2, redirectStatus)
                it.setString(3, createdAt)
                it.executeUpdate()
            }
            val id = connection.createStatement().use { statement ->
                statement.executeQuery("SELECT last_insert_rowid()").use { it.next(); it.getLong(1) }
            }
            val code = Base62.encode(id)
            connection.prepareStatement("UPDATE links SET short_code = ? WHERE id = ?").use {
                it.setString(1, code)
                it.setLong(2, id)
                it.executeUpdate()
            }
            connection.commit()
            Creation(Link(id, code, longUrl, redirectStatus, createdAt), false)
        } catch (error: Exception) {
            runCatching { connection.rollback() }
            throw error
        }
    }

    fun find(code: String): Link? = connect().use { connection ->
        connection.prepareStatement("SELECT * FROM links WHERE short_code = ?").use {
            it.setString(1, code)
            it.executeQuery().use { result -> if (result.next()) result.toLink() else null }
        }
    }

    fun healthy() = connect().use { connection ->
        connection.createStatement().use { it.executeQuery("SELECT 1 FROM links LIMIT 1").use { result -> result.next() } }
        true
    }

    private fun ResultSet.toLink() = Link(getLong("id"), getString("short_code"), getString("long_url"), getInt("redirect_status"), getString("created_at"))
}

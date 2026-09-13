package ir.tvgram.telegram.model

/**
 * Reads the proxy links people actually share.
 *
 * Telegram hands them out in four shapes — `tg://proxy`, `tg://socks` and the
 * `t.me` web equivalents — and they all carry the same handful of query
 * parameters. Parsing them here means a link only ever has to be opened once
 * rather than copied field by field into a form.
 */
object ProxyLink {

    fun parse(raw: String): TgProxy? {
        val text = raw.trim()
        if (text.isEmpty()) return null

        val kind = when {
            text.startsWith("tg://proxy", ignoreCase = true) -> ProxyKind.MTPROTO
            text.startsWith("tg://socks", ignoreCase = true) -> ProxyKind.SOCKS5
            text.contains("/proxy?", ignoreCase = true) -> ProxyKind.MTPROTO
            text.contains("/socks?", ignoreCase = true) -> ProxyKind.SOCKS5
            else -> return null
        }

        val query = text.substringAfter('?', "")
        if (query.isEmpty()) return null
        val fields = query.split('&').mapNotNull { pair ->
            val name = pair.substringBefore('=', "")
            val value = pair.substringAfter('=', "")
            if (name.isEmpty()) null else name.lowercase() to decode(value)
        }.toMap()

        val server = fields["server"].orEmpty()
        val port = fields["port"]?.toIntOrNull() ?: 0
        if (server.isEmpty() || port !in 1..65535) return null

        val secret = fields["secret"].orEmpty()
        // An MTProto link without a secret is not usable, and a SOCKS5 one with
        // a secret is not MTProto — take the link at its word either way.
        if (kind == ProxyKind.MTPROTO && secret.isEmpty()) return null

        return TgProxy(
            id = 0,
            server = server,
            port = port,
            kind = kind,
            secret = secret,
            username = fields["user"] ?: fields["username"].orEmpty(),
            password = fields["pass"] ?: fields["password"].orEmpty(),
        )
    }

    /** Percent-decoding, done by hand so the module stays free of Android. */
    private fun decode(value: String): String {
        if ('%' !in value && '+' !in value) return value
        val out = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            when (val character = value[index]) {
                '+' -> {
                    out.append(' ')
                    index++
                }

                '%' -> {
                    val hex = value.substring(index + 1, minOf(index + 3, value.length))
                    val byte = hex.toIntOrNull(16)
                    if (hex.length == 2 && byte != null) {
                        out.append(byte.toChar())
                        index += 3
                    } else {
                        out.append(character)
                        index++
                    }
                }

                else -> {
                    out.append(character)
                    index++
                }
            }
        }
        return out.toString()
    }
}

package dev.neyham.moshvr.voice

/** Process-wide MIC handle so Activities can abandon recording on pause / focus loss. */
object VoiceSession {
    @Volatile
    private var active: VoiceInput? = null

    @Volatile
    private var host: Any? = null

    fun attach(input: VoiceInput, host: Any? = null) {
        active = input
        this.host = host
    }

    fun detach(input: VoiceInput) {
        if (active === input) {
            active = null
            host = null
        }
    }

    fun isActive(input: VoiceInput): Boolean = active === input

    fun abandon() {
        active?.abandon()
    }

    /** Only the Activity/context that currently owns the MIC may abandon it. */
    fun abandonFrom(caller: Any) {
        if (sameHost(host, caller)) active?.abandon()
    }

    internal fun sameHost(a: Any?, b: Any?): Boolean {
        if (a == null || b == null) return false
        if (a === b) return true
        val left = unwrapActivity(a)
        val right = unwrapActivity(b)
        return left != null && left === right
    }

    private fun unwrapActivity(value: Any): Any? {
        var ctx = value as? android.content.Context ?: return null
        while (ctx is android.content.ContextWrapper) {
            if (ctx is android.app.Activity) return ctx
            ctx = ctx.baseContext
        }
        return ctx
    }
}

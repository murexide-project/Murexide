package android.util

/** No-op JVM implementation for Android logging used by offline unit tests. */
class Log private constructor() {
    companion object {
        @JvmStatic fun v(tag: String?, message: String?): Int = 0
        @JvmStatic fun v(tag: String?, message: String?, throwable: Throwable?): Int = 0
        @JvmStatic fun d(tag: String?, message: String?): Int = 0
        @JvmStatic fun d(tag: String?, message: String?, throwable: Throwable?): Int = 0
        @JvmStatic fun i(tag: String?, message: String?): Int = 0
        @JvmStatic fun i(tag: String?, message: String?, throwable: Throwable?): Int = 0
        @JvmStatic fun w(tag: String?, message: String?): Int = 0
        @JvmStatic fun w(tag: String?, message: String?, throwable: Throwable?): Int = 0
        @JvmStatic fun w(tag: String?, throwable: Throwable?): Int = 0
        @JvmStatic fun e(tag: String?, message: String?): Int = 0
        @JvmStatic fun e(tag: String?, message: String?, throwable: Throwable?): Int = 0
        @JvmStatic fun e(tag: String?, throwable: Throwable?): Int = 0
        @JvmStatic fun wtf(tag: String?, message: String?): Int = 0
        @JvmStatic fun wtf(tag: String?, throwable: Throwable?): Int = 0
        @JvmStatic fun wtf(tag: String?, message: String?, throwable: Throwable?): Int = 0
        @JvmStatic fun println(priority: Int, tag: String?, message: String?): Int = 0
        @JvmStatic fun isLoggable(tag: String?, priority: Int): Boolean = false
        @JvmStatic fun getStackTraceString(throwable: Throwable?): String = throwable?.stackTraceToString().orEmpty()
    }
}
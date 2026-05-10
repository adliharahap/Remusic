package timber.log

import android.util.Log

class Timber {
    companion object {
        fun tag(tag: String): Tree {
            return Tree(tag)
        }
        
        fun d(message: String, vararg args: Any?) {
            Log.d("Timber", formatMessage(message, args))
        }
        fun d(t: Throwable?, message: String, vararg args: Any?) {
            Log.d("Timber", formatMessage(message, args), t)
        }
        fun e(t: Throwable?, message: String, vararg args: Any?) {
            Log.e("Timber", formatMessage(message, args), t)
        }
        fun e(message: String, vararg args: Any?) {
            Log.e("Timber", formatMessage(message, args))
        }
        fun i(message: String, vararg args: Any?) {
            Log.i("Timber", formatMessage(message, args))
        }
        fun i(t: Throwable?, message: String, vararg args: Any?) {
            Log.i("Timber", formatMessage(message, args), t)
        }
        fun w(message: String, vararg args: Any?) {
            Log.w("Timber", formatMessage(message, args))
        }
        fun w(t: Throwable?, message: String, vararg args: Any?) {
            Log.w("Timber", formatMessage(message, args), t)
        }
        fun v(message: String, vararg args: Any?) {
            Log.v("Timber", formatMessage(message, args))
        }
        fun v(t: Throwable?, message: String, vararg args: Any?) {
            Log.v("Timber", formatMessage(message, args), t)
        }

        private fun formatMessage(message: String, args: Array<out Any?>): String {
            return if (args.isNotEmpty()) {
                try {
                    String.format(message, *args)
                } catch (e: Exception) {
                    message
                }
            } else {
                message
            }
        }
    }

    class Tree(private val tag: String) {
        fun d(message: String, vararg args: Any?) {
            Log.d(tag, formatMessage(message, args))
        }
        fun d(t: Throwable?, message: String, vararg args: Any?) {
            Log.d(tag, formatMessage(message, args), t)
        }
        fun e(t: Throwable?, message: String, vararg args: Any?) {
            Log.e(tag, formatMessage(message, args), t)
        }
        fun e(message: String, vararg args: Any?) {
            Log.e(tag, formatMessage(message, args))
        }
        fun i(message: String, vararg args: Any?) {
            Log.i(tag, formatMessage(message, args))
        }
        fun i(t: Throwable?, message: String, vararg args: Any?) {
            Log.i(tag, formatMessage(message, args), t)
        }
        fun w(message: String, vararg args: Any?) {
            Log.w(tag, formatMessage(message, args))
        }
        fun w(t: Throwable?, message: String, vararg args: Any?) {
            Log.w(tag, formatMessage(message, args), t)
        }
        fun v(message: String, vararg args: Any?) {
            Log.v(tag, formatMessage(message, args))
        }
        fun v(t: Throwable?, message: String, vararg args: Any?) {
            Log.v(tag, formatMessage(message, args), t)
        }

        private fun formatMessage(message: String, args: Array<out Any?>): String {
            return if (args.isNotEmpty()) {
                try {
                    String.format(message, *args)
                } catch (e: Exception) {
                    message
                }
            } else {
                message
            }
        }
    }
}

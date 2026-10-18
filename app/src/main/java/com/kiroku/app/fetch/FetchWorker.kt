package com.kiroku.app.fetch

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kiroku.app.KirokuApp
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Runs when the phone has internet: fetches one link's metadata and stores it.
 * WorkManager keeps the job across app restarts and retries it with backoff.
 */
class FetchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.failure()
        val repository = (applicationContext as KirokuApp).container.links

        return try {
            repository.fetchAndApply(id)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isRetryable(e) && runAttemptCount < MAX_ATTEMPTS - 1) {
                Result.retry()
            } else {
                repository.markFetchFailed(id, e.toUserMessage())
                Result.success()
            }
        }
    }

    private fun isRetryable(e: Exception): Boolean = when (e) {
        is FetchHttpException -> e.code == 408 || e.code == 429 || e.code >= 500
        is UnknownHostException -> true
        is IOException -> true
        else -> false
    }

    companion object {
        const val KEY_ID = "link_id"
        private const val MAX_ATTEMPTS = 4

        fun Exception.toUserMessage(): String = when (this) {
            is FetchHttpException -> when (code) {
                401, 403 -> "The site blocked the request ($code)"
                404, 410 -> "Page not found ($code)"
                429 -> "The site is rate-limiting requests (429)"
                else -> "The site returned an error ($code)"
            }
            is UnknownHostException -> "Couldn't find that website"
            is SocketTimeoutException -> "The site took too long to respond"
            is SSLException -> "Secure connection to the site failed"
            else -> message ?: javaClass.simpleName
        }
    }
}
